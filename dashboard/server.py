"""Local-only Selenium control room. Python 3.10+, no third-party packages."""
from __future__ import annotations
import argparse
import hmac
import json
import mimetypes
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, unquote

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / 'dashboard' / 'static'
DATA = ROOT / '.dashboard' / 'runs'
RESULTS = ROOT / 'target' / 'allure-results'
REPORT = ROOT / '.reports' / 'allure'
TOKEN = secrets.token_urlsafe(32)
LOCK = threading.RLock()
ACTIVE = None
PROCESS = None
STOPPING = False
WORKER = None
DEFAULT_URL = 'https://www.selenium.dev/selenium/web/web-form.html'


def options(raw):
    if not isinstance(raw, dict) or set(raw) - {'browser','headless','group','windowSize','baseUrl','timeout','pageTimeout'}:
        raise ValueError('Unsupported run options')
    value = {'browser':'chrome','headless':True,'group':'all','windowSize':'1440x1000',
             'baseUrl':DEFAULT_URL,'timeout':15,'pageTimeout':45} | raw
    if value['browser'] not in ('chrome','edge','firefox'): raise ValueError('Choose a supported browser')
    if type(value['headless']) is not bool: raise ValueError('Headless must be a boolean')
    if value['group'] not in ('all','smoke','ui'): raise ValueError('Choose all, smoke, or ui')
    if not isinstance(value['windowSize'], str): raise ValueError('Invalid window size')
    size = re.fullmatch(r'([0-9]{3,4})x([0-9]{3,4})', value['windowSize'])
    if not size or not (320 <= int(size[1]) <= 3840 and 320 <= int(size[2]) <= 2160): raise ValueError('Invalid window size')
    url = value['baseUrl']
    if not isinstance(url,str) or len(url)>300 or not re.fullmatch(r'https?://[A-Za-z0-9._~:/-]+',url): raise ValueError('Use an HTTP(S) URL without credentials, query, or fragment')
    parsed=urlsplit(url)
    if not parsed.hostname: raise ValueError('Target URL needs a hostname')
    try: parsed.port
    except ValueError: raise ValueError('Invalid target port')
    for key, maximum in [('timeout',120),('pageTimeout',180)]:
        if type(value[key]) is not int or not 1 <= value[key] <= maximum: raise ValueError('Invalid timeout')
    return value


def maven_command(arguments):
    """Invoke Maven's Java launcher directly, avoiding cmd.exe and shell interpolation."""
    executable = shutil.which('mvn')
    if not executable: raise RuntimeError('Maven was not found on PATH')
    home = Path(executable).resolve().parents[1]
    jars = list((home/'boot').glob('plexus-classworlds-*.jar'))
    if not jars: raise RuntimeError('Cannot find Maven launcher under ' + str(home))
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home)/'bin'/('java.exe' if os.name=='nt' else 'java')) if java_home else shutil.which('java')
    if not java: raise RuntimeError('Java was not found')
    return [java,'-Djdk.net.unixdomain.tmpdir=.',f'-Dmaven.home={home}',
            f'-Dmaven.multiModuleProjectDirectory={ROOT}',f'-Dclassworlds.conf={home / "bin" / "m2.conf"}',
            '-classpath',str(jars[0]),'org.codehaus.plexus.classworlds.launcher.Launcher','-B','-ntp',*arguments]


def test_arguments(value):
    args=['test',f'-Dbrowser={value["browser"]}',f'-Dheadless={str(value["headless"]).lower()}',
          f'-DwindowSize={value["windowSize"]}',f'-DbaseUrl={value["baseUrl"]}',
          f'-Dtimeout={value["timeout"]}',f'-DpageTimeout={value["pageTimeout"]}']
    if value['group']!='all': args.append('-Dgroups='+value['group'])
    return args


def read_json(file, default=None):
    try: return json.loads(file.read_text(encoding='utf-8-sig'))
    except (OSError,ValueError): return default


def save(run):
    folder=DATA/run['id']; folder.mkdir(parents=True,exist_ok=True)
    temporary=folder/'run.tmp'
    temporary.write_text(json.dumps(run,ensure_ascii=False),encoding='utf-8')
    temporary.replace(folder/'run.json')


def result_rows(directory):
    rows=[]
    # Index fixture evidence once instead of rereading every container for every case.
    fixtures={}
    for container in directory.glob('*-container.json'):
        fixture=read_json(container,{})
        nodes=fixture.get('befores',[])+fixture.get('afters',[])
        for child in fixture.get('children',[]):
            fixtures.setdefault(child,[]).extend(nodes)
    for file in directory.glob('*-result.json'):
        value=read_json(file,{})
        if not value or not value.get('uuid'): continue
        labels={entry['name']:entry['value'] for entry in value.get('labels',[])}
        attachments=[]
        def collect(node):
            attachments.extend(node.get('attachments',[]))
            for child in node.get('steps',[]): collect(child)
        collect(value)
        # Configuration evidence lives in Allure container fixtures.
        for node in fixtures.get(value['uuid'],[]): collect(node)
        screenshots=[item['source'] for item in attachments if item.get('type')=='image/png' and re.fullmatch(r'[A-Za-z0-9_.-]+',item.get('source',''))]
        details=value.get('statusDetails',{})
        # Do not echo arbitrary exception messages or test parameter values into the dashboard.
        trace=details.get('trace','')
        safe_trace='\n'.join(line for line in trace.splitlines() if line.strip().startswith('at org.example.'))
        scenario=next((item.get('value','') for item in value.get('parameters',[]) if item.get('name')=='Scenario'),'')
        rows.append({'id':value.get('uuid',file.stem),'name':value.get('name','Unnamed test'),
                     'scenario':scenario,'feature':labels.get('feature','Smoke & sanity'),
                     'status':value.get('status','unknown'),'duration':max(0,value.get('stop',0)-value.get('start',0)),
                     'details':safe_trace or ('See diagnostic attachments in Allure.' if details else ''),
                     'screenshots':screenshots})
    return sorted(rows,key=lambda row:(row['feature'],row['name'],row['scenario']))


def summary(rows):
    counts={key:sum(row['status']==key for row in rows) for key in ['passed','failed','broken','skipped','unknown']}
    return {'total':len(rows),**counts,'passRate':round(100*counts['passed']/len(rows),1) if rows else None}


def completion_status(exit_code, counts):
    if exit_code != 0 or not counts['total'] or counts['failed'] + counts['broken'] + counts['unknown']:
        return 'failed'
    return 'passed' if counts['passed'] else 'skipped'


def launches():
    DATA.mkdir(parents=True,exist_ok=True)
    runs=[read_json(path) for path in DATA.glob('*/run.json')]
    return sorted([run for run in runs if run],key=lambda run:run['started'],reverse=True)


def log(run, line):
    # Maven logs from this project deliberately omit entered field values.
    with LOCK:
        run['log'].append(line.rstrip())
        run['log']=run['log'][-1800:]


def execute(run):
    global ACTIVE, PROCESS
    try:
        def invoke(arguments):
            with subprocess.Popen(maven_command(arguments),cwd=ROOT,stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',
                                  creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0,
                                  start_new_session=os.name!='nt',
                                  env=os.environ | {'SE_AVOID_STATS':'true'}) as process:
                with LOCK: PROCESS=process
                for line in process.stdout:
                    log(run,line)
                    if run['phase']=='testing':
                        with LOCK:
                            run['results']=result_rows(RESULTS)
                            run['summary']=summary(run['results'])
                code=process.wait()
                with LOCK: PROCESS=None
                return code
        # Never mix stale results into a new launch when Maven fails before TestNG starts.
        RESULTS.mkdir(parents=True,exist_ok=True)
        for file in RESULTS.glob('*'):
            if file.is_file(): file.unlink()
        with LOCK: run['phase']='testing'
        run['exitCode']=invoke(test_arguments(run['options']))
        with LOCK:
            run['results']=result_rows(RESULTS); run['summary']=summary(run['results'])
            run['phase']='reporting'
        if run['results'] and not STOPPING:
            log(run,'Generating Allure report…')
            run['reportExitCode']=invoke(['allure:report'])
        folder=DATA/run['id']
        shutil.copytree(RESULTS,folder/'results',dirs_exist_ok=True)
        if run.get('reportExitCode')==0 and (REPORT/'index.html').exists():
            shutil.copytree(REPORT,folder/'report',dirs_exist_ok=True)
            run['reportUrl']=f'/runs/{run["id"]}/report/index.html'
        run['status']=completion_status(run['exitCode'],run['summary'])
    except Exception as failure:
        run['status']='failed'; log(run,'Runner error: '+str(failure))
    finally:
        with LOCK:
            run['phase']='complete'; run['status']='interrupted' if STOPPING else run['status']; run['finished']=time.time(); run['duration']=run['finished']-run['started']
            save(run); ACTIVE=None


def begin(raw):
    global ACTIVE, WORKER
    value=options(raw)
    with LOCK:
        if ACTIVE is not None: raise FileExistsError('A test run is already in progress')
        run={'id':uuid.uuid4().hex,'started':time.time(),'finished':None,'duration':0,'status':'running',
             'phase':'starting','options':value,'results':[],'summary':summary([]),'log':[],'reportUrl':None}
        save(run); ACTIVE=run
        WORKER=threading.Thread(target=execute,args=(run,),daemon=True)
        WORKER.start()
        return run


class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def send(self,status,body,kind='application/json'):
        if isinstance(body,(dict,list)): body=json.dumps(body).encode()
        elif isinstance(body,str): body=body.encode()
        self.send_response(status)
        self.send_header('Content-Type',kind)
        self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('X-Frame-Options','DENY')
        self.end_headers(); self.wfile.write(body)
    def trusted(self):
        return self.headers.get('Host') in (f'127.0.0.1:{self.server.server_port}',f'localhost:{self.server.server_port}')
    def do_GET(self):
        if not self.trusted(): return self.send(403,{'error':'Invalid host'})
        path=unquote(urlsplit(self.path).path)
        if path=='/api/state':
            with LOCK:
                runs=launches()
                if ACTIVE:
                    if ACTIVE['phase']=='testing':
                        ACTIVE['results']=result_rows(RESULTS)
                        ACTIVE['summary']=summary(ACTIVE['results'])
                    runs=[ACTIVE]+[run for run in runs if run['id']!=ACTIVE['id']]
                return self.send(200,{'token':TOKEN,'activeId':ACTIVE['id'] if ACTIVE else None,'runs':runs[:30]})
        if path.startswith('/api/'): return self.send(404,{'error':'Not found'})
        if path.startswith('/fixture/'):
            base=ROOT/'dashboard'/'tests'/'fixtures'
            relative=path.removeprefix('/fixture/') or 'index.html'
        elif path.startswith('/runs/'):
            parts=path.strip('/').split('/')
            if len(parts)<4 or not re.fullmatch('[a-f0-9]{32}',parts[1]): return self.send(404,{'error':'Not found'})
            if parts[2]=='report': base=DATA/parts[1]/'report'; relative='/'.join(parts[3:])
            elif parts[2]=='results' and len(parts)==4 and parts[3].endswith('.png'): base=DATA/parts[1]/'results'; relative=parts[3]
            else: return self.send(404,{'error':'Not found'})
        else:
            base=STATIC; relative='index.html' if path=='/' else path.lstrip('/')
        file=(base/relative).resolve()
        if not file.is_relative_to(base.resolve()) or not file.is_file(): return self.send(404,{'error':'File not found'})
        self.send(200,file.read_bytes(),mimetypes.guess_type(file.name)[0] or 'application/octet-stream')
    def do_POST(self):
        if not self.trusted(): return self.send(403,{'error':'Invalid host'})
        origin=self.headers.get('Origin')
        if origin and origin not in (f'http://localhost:{self.server.server_port}',f'http://127.0.0.1:{self.server.server_port}'):
            return self.send(403,{'error':'Invalid origin'})
        if not hmac.compare_digest(self.headers.get('X-Run-Token',''),TOKEN): return self.send(403,{'error':'Refresh the dashboard before starting a run'})
        if self.path!='/api/runs': return self.send(404,{'error':'Not found'})
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=4096: raise ValueError('Invalid request size')
            if self.headers.get('Content-Type','').split(';')[0]!='application/json': raise ValueError('JSON required')
            run=begin(json.loads(self.rfile.read(length)))
            self.send(202,{'id':run['id']})
        except FileExistsError as error: self.send(409,{'error':str(error)})
        except (ValueError,TypeError) as error: self.send(400,{'error':str(error)})


def process_lock():
    folder=ROOT/'.dashboard'; folder.mkdir(exist_ok=True)
    handle=open(folder/'server.lock','a+b'); handle.seek(0); handle.write(b'0'); handle.flush(); handle.seek(0)
    try:
        if os.name=='nt':
            import msvcrt
            msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except OSError: raise SystemExit('Another dashboard is already running for this project.')
    return handle


def main():
    global STOPPING
    parser=argparse.ArgumentParser(); parser.add_argument('--port',type=int,default=8765); args=parser.parse_args()
    handle=process_lock()
    for run in launches():
        if run['status']=='running':
            run.update(status='interrupted',phase='complete',finished=time.time())
            run['log'].append('Previous dashboard stopped before this run completed.'); save(run)
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler)
    print(f'SeleniumTut dashboard: http://127.0.0.1:{args.port}',flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally:
        STOPPING=True
        server.server_close()
        with LOCK: process=PROCESS
        if process and process.poll() is None:
            if os.name=='nt':
                subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
            else:
                import signal
                os.killpg(process.pid,signal.SIGTERM)
        if WORKER: WORKER.join(timeout=10)
        handle.close()

if __name__=='__main__': main()
