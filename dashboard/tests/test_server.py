import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError

spec=importlib.util.spec_from_file_location('runner',Path(__file__).resolve().parents[1]/'server.py')
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)

class RunnerTests(unittest.TestCase):
    def test_no_results_unknown_or_only_skips_are_not_passes(self):
        self.assertEqual(runner.completion_status(0,runner.summary([])),'failed')
        self.assertEqual(runner.completion_status(0,runner.summary([{'status':'unknown'}])),'failed')
        self.assertEqual(runner.completion_status(0,runner.summary([{'status':'skipped'}])),'skipped')
        self.assertEqual(runner.completion_status(1,runner.summary([{'status':'passed'}])),'failed')
        self.assertEqual(runner.completion_status(0,runner.summary([{'status':'passed'}])),'passed')
    def test_rejects_shell_and_unexpected_options(self):
        invalid=[{'browser':'chrome & calc'},{'group':'ui; echo no'},{'command':'whoami'},
                 {'baseUrl':'https://example.com/$(whoami)'},{'baseUrl':'https://user:password@example.com'},
                 {'baseUrl':'https://example.com/?token=secret'},{'timeout':True},{'timeout':0},
                 {'headless':'true'},{'windowSize':'9999x1000'},{'windowSize':1440},{'baseUrl':'http://localhost:999999/'}]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ValueError):runner.options(value)
    def test_predefined_test_arguments(self):
        args=runner.test_arguments(runner.options({'group':'smoke','browser':'edge'}))
        self.assertEqual(args[0],'test');self.assertIn('-Dgroups=smoke',args);self.assertIn('-Dbrowser=edge',args)
        self.assertNotIn('clean',args)
    def test_runner_rejects_overlap(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner,'DATA',Path(directory)), patch.object(threading.Thread,'start'):
            try:
                first=runner.begin({})
                self.assertEqual(first['status'],'running')
                with self.assertRaises(FileExistsError):runner.begin({})
            finally:runner.ACTIVE=None
    def test_parse_real_shape_including_fixture_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory)
            (folder/'one-result.json').write_text(json.dumps({'uuid':'one','name':'Readable test','status':'failed',
                'start':100,'stop':350,'labels':[{'name':'feature','value':'Inputs'}],
                'statusDetails':{'message':'secret-value','trace':'AssertionError secret-value\n at org.example.tests.Input.test(Input.java:3)'}}))
            (folder/'fixture-container.json').write_text(json.dumps({'children':['one'],'afters':[{'attachments':[{'type':'image/png','source':'capture.png'}]}]}))
            rows=runner.result_rows(folder)
            self.assertEqual(rows[0]['screenshots'],['capture.png']);self.assertEqual(rows[0]['duration'],250)
            self.assertNotIn('secret-value',json.dumps(rows));self.assertEqual(runner.summary(rows)['failed'],1)
    def test_incomplete_result_files_are_not_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory)
            (folder/'partial-result.json').write_text('{"uuid":')
            self.assertEqual(runner.result_rows(folder),[])

    def test_starting_run_does_not_publish_stale_results(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner,'DATA',Path(directory)), patch.object(threading.Thread,'start'):
            try:
                run=runner.begin({})
                self.assertEqual(run['phase'],'starting')
                self.assertEqual(run['summary']['total'],0)
            finally: runner.ACTIVE=None

    def test_failed_run_still_generates_and_archives_report(self):
        import io
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);results=root/'results';report=root/'report';data=root/'runs'
            results.mkdir();report.mkdir()
            run={'id':'b'*32,'started':1,'status':'running','phase':'starting','options':runner.options({}),
                 'results':[],'summary':runner.summary([]),'log':[],'reportUrl':None}
            (data/run['id']).mkdir(parents=True)
            calls=[]
            class Process:
                def __init__(self,*args,**kwargs):
                    calls.append(args[0]); self.stdout=io.StringIO('[TEST] completed\n')
                    if len(calls)==1:
                        (results/'test-result.json').write_text(json.dumps({'uuid':'failed-case','name':'Failure fixture','status':'failed','start':1,'stop':2}))
                    else: (report/'index.html').write_text('<html>Report fixture</html>')
                def __enter__(self):return self
                def __exit__(self,*args):pass
                def wait(self):return 1 if len(calls)==1 else 0
            with patch.object(runner,'RESULTS',results),patch.object(runner,'REPORT',report),patch.object(runner,'DATA',data),patch.object(runner,'maven_command',side_effect=lambda args:args),patch.object(runner.subprocess,'Popen',Process):
                runner.execute(run)
            self.assertEqual(run['status'],'failed')
            self.assertEqual(run['summary']['failed'],1)
            self.assertEqual(calls[1],['allure:report'])
            self.assertTrue((data/run['id']/'report'/'index.html').exists())
            self.assertEqual(runner.read_json(data/run['id']/'run.json')['status'],'failed')

    def test_archive_survives_target_removal(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner,'DATA',Path(directory)/'runs'):
            run={'id':'a'*32,'started':1,'status':'passed','results':[]}
            runner.save(run);self.assertEqual(runner.launches()[0]['id'],run['id'])
    def test_http_csrf_host_validation_and_path_isolation(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(runner,'DATA',Path(directory)):
            server=runner.ThreadingHTTPServer(('127.0.0.1',0),runner.Handler)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            base=f'http://127.0.0.1:{server.server_port}'
            try:
                state=json.load(urlopen(base+'/api/state'));self.assertIn('token',state)
                requests=[(Request(base+'/api/runs',data=b'{}',headers={'Content-Type':'application/json'}),403),
                          (Request(base+'/api/runs',data=b'{"browser":"bad"}',headers={'Content-Type':'application/json','X-Run-Token':runner.TOKEN}),400),
                          (Request(base+'/api/state',headers={'Host':'evil.example'}),403),
                          (Request(base+'/api/runs',data=b'{}',headers={'Content-Type':'application/json','X-Run-Token':runner.TOKEN,'Origin':'https://evil.example'}),403),
                          (Request(base+'/%2e%2e/server.py'),404)]
                for request,status in requests:
                    with self.subTest(url=request.full_url),self.assertRaises(HTTPError) as error:urlopen(request)
                    self.assertEqual(error.exception.code,status);error.exception.close()
            finally:server.shutdown();server.server_close()

if __name__=='__main__':unittest.main()
