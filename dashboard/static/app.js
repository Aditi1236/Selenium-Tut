'use strict';
const $ = id => document.getElementById(id);
let state = {runs: [], activeId: null}, selectedId = null, token = '', autoScroll = true, pending = false, connected = false, resultSignature = '', historySignature = '';
const duration = seconds => seconds < 60 ? `${seconds.toFixed(1)}s` : `${Math.floor(seconds / 60)}m ${Math.floor(seconds % 60)}s`;
const element = (tag, text, className) => { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; if (className) node.className = className; return node; };
const badge = status => element('span', status.charAt(0).toUpperCase() + status.slice(1), `badge ${status}`);
let resultNodes = new Map(), resultRunId = null;
const selected = () => state.runs.find(run => run.id === selectedId);
const localTime = value => new Date(value * 1000).toLocaleString(undefined, {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});
function notice(message) { $('notice').hidden = !message; $('notice').textContent = message; }
function showDetails(row) {
  const run = selected(); $('detail-title').textContent = row.name;
  const content = $('detail-content'); content.replaceChildren();
  const meta = element('div', undefined, 'detail-meta'); meta.append(badge(row.status),element('span',row.feature),element('span',duration(row.duration/1000))); content.append(meta);
  if (row.scenario) content.append(element('p',`Scenario: ${row.scenario}`));
  content.append(element('p',row.status === 'passed' ? 'This test completed successfully and its assertions passed.' : 'Inspect the diagnostic location and captured evidence below. Full structured steps are available in Allure.'));
  if (row.details) content.append(element('pre',row.details));
  if (row.screenshots.length) {
    content.append(element('h3','Captured evidence'));
    for (const file of row.screenshots) { const image = element('img'); image.src = `/runs/${run.id}/results/${encodeURIComponent(file)}`; image.alt = 'Failure screenshot with form inputs hidden'; image.loading = 'lazy'; content.append(image); }
  } else if (['failed','broken','skipped'].includes(row.status)) content.append(element('p','No screenshot is available. Browser setup failures can happen before a screenshot can be captured.'));
  if (run.reportUrl) { const link = element('a','Explore this run in Allure ↗','button secondary'); link.href=run.reportUrl; link.target='_blank'; link.rel='noopener'; content.append(link); }
  $('test-dialog').showModal();
}
function renderResults(run) {
  const query = $('search').value.trim().toLowerCase(), filter = $('status-filter').value;
  const rows = (run?.results || []).filter(row => (filter === 'all' || row.status === filter) && `${row.name} ${row.feature} ${row.scenario}`.toLowerCase().includes(query));
  const signature=JSON.stringify([run?.id,run?.results.length,rows,query,filter,run?.status]);
  if(signature===resultSignature) return;
  resultSignature=signature;
  const focusedResult=document.activeElement?.dataset.resultId;
  if(resultRunId!==run?.id){resultNodes.clear();resultRunId=run?.id;}
  $('result-count').textContent = run?.results.length || 0; $('result-rows').replaceChildren();
  for (const row of rows) {
    const rowSignature=JSON.stringify(row),cached=resultNodes.get(row.id);
    if(cached?.signature===rowSignature){$('result-rows').append(cached.node);continue;}
    const tr=element('tr'), name=element('td',row.name,'result-name');
    if(row.scenario) name.append(element('small',row.scenario));
    const status=element('td'); status.append(badge(row.status));
    const action=element('td'), button=element('button','↗','detail-button'); button.type='button'; button.dataset.resultId=row.id; button.setAttribute('aria-label',`View details: ${row.name}`); button.onclick=()=>showDetails(row); action.append(button);
    const feature=element('td',row.feature), timing=element('td',duration(row.duration/1000));
    feature.dataset.label='Feature'; status.dataset.label='Status'; timing.dataset.label='Duration';
    action.className='result-action'; button.textContent='View details ↗';
    tr.append(name,feature,status,timing,action); resultNodes.set(row.id,{signature:rowSignature,node:tr}); $('result-rows').append(tr);
  }
  if(focusedResult) [...$('result-rows').querySelectorAll('button')].find(button=>button.dataset.resultId===focusedResult)?.focus({preventScroll:true});
  $('results-empty').hidden = rows.length > 0;
  $('results-empty').querySelector('h3').textContent = query || filter !== 'all' ? 'No matching tests.' : run?.status === 'running' ? 'Tests are on their way.' : run ? 'No test results were produced.' : 'Every result has a story.';
  $('results-empty').querySelector('p').textContent = query || filter !== 'all' ? 'Try a different search or status filter.' : run ? 'Check the execution log for progress or setup errors.' : 'Run a suite to see test outcomes, timings, and failure evidence.';
}
function render() {
  const run=selected(), counts=run?.summary;
  $('total').textContent=counts?.total ?? '—'; $('passed').textContent=counts?.passed ?? '—';
  $('failed').textContent=counts ? counts.failed+counts.broken : '—'; $('skipped').textContent=counts?.skipped ?? '—';
  const rate=counts?.passRate;
  $('rate').textContent=rate == null ? '—' : `${rate}%`; $('ring-value').textContent=$('rate').textContent;
  $('pass-ring').style.setProperty('--rate',`${rate || 0}%`);
  $('pass-ring').style.setProperty('--ring',counts && counts.failed+counts.broken ? '#d16c61' : '#259e74');
  const elapsed=run ? run.finished ? run.duration : (Date.now()/1000-run.started) : 0;
  $('duration').textContent=run ? duration(Math.max(0,elapsed)) : '—';
  $('summary-label').textContent=run ? run.options.group === 'all' ? 'Full test suite' : `${run.options.group} suite` : 'No run selected';
  const running=run?.status === 'running';
  const status=run?.status || 'neutral'; $('run-status').className=`badge ${status}`; $('run-status').textContent=running ? run.phase === 'reporting' ? 'Building report' : 'Running' : run ? status.charAt(0).toUpperCase()+status.slice(1) : 'Ready';
  $('phase-label').textContent=running ? 'EXECUTION IN PROGRESS' : run ? 'RUN COMPLETE' : 'READY WHEN YOU ARE';
  $('execution-title').textContent=running ? run.phase==='reporting' ? 'Preparing the full picture.' : 'Putting your suite to work.' : run ? status==='passed' ? 'All checks, clear.' : status==='interrupted' ? 'This run was interrupted.' : 'A closer look is needed.' : 'A clear view of every run.';
  $('execution-description').textContent=run ? `${counts.total} test results reported. ${counts.passed} passed, ${counts.failed+counts.broken} failed or broken, ${counts.skipped} skipped.${run.reportExitCode ? ' Allure generation failed; inspect the log.' : ''}` : 'Choose a browser and test scope, then run your suite. Results and evidence appear here automatically.';
  $('run-subtitle').textContent=run ? `${run.options.browser.toUpperCase()} · ${run.options.headless?'Headless':'Visible browser'} · ${localTime(run.started)}` : 'Your next quality checkpoint starts here.';
  $('progress-text').textContent=run ? running ? `${counts.total} results received · ${run.phase==='reporting'?'Generating Allure report':'Suite running'}` : `Finished in ${duration(run.duration)} · ${counts.total} results` : 'No tests have run in this dashboard yet.';
  $('run-id').textContent=run ? `RUN ${run.id.slice(0,8).toUpperCase()}` : 'AWAITING RUN';
  $('activity-track').classList.toggle('running',running); $('activity-track').firstElementChild.style.width=running?'30%':run?'100%':'0';
  $('run-button').disabled=!connected || !!state.activeId || pending; $('run-label').textContent=!connected?'Reconnecting…':state.activeId?'Run in progress':pending?'Starting…':'Run tests';
  $('connection-status').classList.toggle('offline',!connected); $('connection-status').lastChild.textContent=connected?' LOCAL RUNNER':' DISCONNECTED';
  const link=$('allure-link'); link.classList.toggle('disabled',!run?.reportUrl); link.setAttribute('aria-disabled',String(!run?.reportUrl)); if(run?.reportUrl) link.href=run.reportUrl; else link.removeAttribute('href');
  renderResults(run);
  const output=run?.log.join('\n') || 'Waiting for a test run…'; if($('log-output').textContent!==output) { $('log-output').textContent=output; if(autoScroll) $('log-output').scrollTop=$('log-output').scrollHeight; }
  const nextHistorySignature=JSON.stringify([selectedId,state.runs.map(item=>[item.id,item.status,item.summary,item.finished,item.duration])]);
  if(nextHistorySignature===historySignature) return;
  historySignature=nextHistorySignature;
  $('history-list').replaceChildren();
  for(const item of state.runs) {
    const button=element('button',undefined,`history-item${item.id===selectedId?' selected':''}`); button.type='button'; button.setAttribute('aria-pressed',String(item.id===selectedId));
    const info=element('span'); info.append(element('strong',`${item.options.browser} / ${item.options.group}`),element('small',`${localTime(item.started)} · ${item.summary.total} tests${item.finished?' · '+duration(item.duration):''}`)); button.append(info,badge(item.status));
    button.onclick=()=>{selectedId=item.id;render();}; $('history-list').append(button);
  }
  if(!state.runs.length) $('history-list').append(element('p','Your run history will appear here.','empty-inline'));
}
async function poll() {
  try {
    const response=await fetch('/api/state',{signal:AbortSignal.timeout(10000)}); if(!response.ok) throw new Error('Unable to load workspace');
    const data=await response.json(); token=data.token; state=data; connected=true;
    if(!selectedId || !state.runs.some(run=>run.id===selectedId)) selectedId=state.activeId || state.runs[0]?.id;
    if($('notice').dataset.connection==='lost') {notice('');delete $('notice').dataset.connection;}
    render();
  } catch(error) {connected=false;render();notice('Connection lost. Keep the local dashboard server running; reconnecting automatically…');$('notice').dataset.connection='lost';}
  finally {setTimeout(poll,2000);}
}
$('run-form').addEventListener('submit',async event=>{
  event.preventDefault(); if(!connected || pending || state.activeId) return;
  const form=new FormData(event.target), options=Object.fromEntries(form.entries()); options.headless=event.target.elements.headless.checked; options.timeout=Number(options.timeout); options.pageTimeout=Number(options.pageTimeout);
  pending=true;render();notice('');
  try {
    const response=await fetch('/api/runs',{method:'POST',headers:{'Content-Type':'application/json','X-Run-Token':token},body:JSON.stringify(options)});
    const data=await response.json(); if(!response.ok) throw new Error(data.error || 'Unable to start run');
    selectedId=data.id;
    const snapshot=await fetch('/api/state',{signal:AbortSignal.timeout(10000)}); if(snapshot.ok){state=await snapshot.json();token=state.token;}
  } catch(error) {notice(error.message);}
  finally {pending=false;render();}
});
$('search').addEventListener('input',()=>renderResults(selected())); $('status-filter').addEventListener('change',()=>renderResults(selected()));
$('use-local').onclick=()=>{document.querySelector('[name="baseUrl"]').value=location.origin+'/fixture/web-form.html';};
$('close-dialog').onclick=()=>$('test-dialog').close(); $('test-dialog').addEventListener('click',event=>{if(event.target===$('test-dialog')){const box=event.target.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)event.target.close();}});
$('log-toggle').onclick=()=>{autoScroll=!autoScroll;$('log-toggle').textContent=`Auto-scroll ${autoScroll?'on':'off'}`;$('log-toggle').setAttribute('aria-pressed',String(autoScroll));};
document.querySelectorAll('.nav-item').forEach(link=>link.addEventListener('click',()=>{document.querySelectorAll('.nav-item').forEach(item=>item.classList.remove('active'));link.classList.add('active');$('page-context').textContent=link.textContent.replace(/[▦☷◷⌘]/g,'').trim();}));
poll();


