from pathlib import Path
import sys,os
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
os.chdir(ROOT)
import json,shutil
from dashboard.server import result_rows
results=Path('target/allure-results');rows=result_rows(results)
assert len(rows)==1 and rows[0]['status']=='failed'
assert len(rows[0]['screenshots'])==1,rows
alltext='\n'.join(f.read_text(encoding='utf-8-sig') for f in results.iterdir() if f.suffix in ['.txt','.json'])
assert 'Demo-only-value' not in alltext
assert 'Public evidence fixture' not in alltext
assert 'private-fixture-token' not in alltext
assert 'private-fragment' not in alltext
names=[]
for f in results.glob('*-container.json'):
    item=json.loads(f.read_text(encoding='utf-8-sig'))
    for node in item.get('afters',[]):names.extend(a['name'] for a in node.get('attachments',[]))
assert any('Diagnostic summary' in name for name in names),names
assert any('Current URL' in name for name in names),names
report=Path('.reports/allure')
assert (report/'index.html').exists()
assert any('inputs hidden' in f.read_text(encoding='utf-8') for f in (report/'data/test-cases').glob('*.json'))
folder=Path('.dashboard/verification/failure-probe');shutil.copytree(results,folder/'results',dirs_exist_ok=True);shutil.copytree(report,folder/'report',dirs_exist_ok=True)
(folder/'verification.json').write_text(json.dumps({'status':'passed','expectedTestFailure':True,'linkedScreenshots':1,'enteredValuesAbsent':True,'attachments':names},indent=2))
print('FAILURE EVIDENCE CHECK PASSED:',names)
print('Screenshot:',(results/rows[0]['screenshots'][0]).resolve())
