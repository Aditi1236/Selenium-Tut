"""Snapshot Allure 2 trend history before clean, then verify continuity after reporting."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SNAPSHOT=ROOT/'.dashboard/verification/history-before-clean.json'
HISTORY=ROOT/'.reports/allure/history'
p=argparse.ArgumentParser();p.add_argument('--snapshot',action='store_true');args=p.parse_args()
if args.snapshot:
    SNAPSHOT.parent.mkdir(parents=True,exist_ok=True)
    data={file.name:json.loads(file.read_text()) for file in HISTORY.glob('*.json')}
    assert data.get('history-trend.json'),'Generate an initial Allure report first'
    SNAPSHOT.write_text(json.dumps(data,indent=2));print('Saved history snapshot')
else:
    previous=json.loads(SNAPSHOT.read_text())['history-trend.json']
    current=json.loads((HISTORY/'history-trend.json').read_text())
    offsets=[i for i in range(1,len(current)) if current[i:]==previous[:len(current)-i]]
    assert offsets,'Prior trend entries were not retained in the new report'
    evidence={'status':'passed','priorEntries':len(previous),'currentEntries':len(current),'newReports':offsets[0]}
    (SNAPSHOT.parent/'history-verification.json').write_text(json.dumps(evidence,indent=2))
    print('HISTORY CONTINUITY CHECK PASSED:',evidence)
