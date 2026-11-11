import { createRoot } from 'react-dom/client';
import { useEffect, useState } from 'react';
import { DonorApp } from '../../features/donor/DonorApp';
import { createMockDonorApi, DEMO_CAMPAIGN_ID } from '../../features/donor/mock';
import type { MockFault } from '../../features/donor/mock';
const mock = createMockDonorApi();
function Preview() {
  const [view, setView] = useState<'pledge' | 'history'>(location.pathname === '/me/commitments' ? 'history' : 'pledge');
  const [dataRevision, setDataRevision] = useState(0);
  const [epoch, setEpoch] = useState(0);
  const [enabled, setEnabled] = useState(true);
  const [fault, setFault] = useState<MockFault>('none');
  useEffect(() => { const update = () => setView(location.pathname === '/me/commitments' ? 'history' : 'pledge'); window.addEventListener('popstate', update); return () => window.removeEventListener('popstate', update); }, []);
  if (new URLSearchParams(location.search).get('mode') === 'real') return <main><h1>Real API is not connected</h1><p>This development preview never bypasses real session permissions. Supply Minh’s shared client adapter in the application shell.</p></main>;
  function navigate(next: 'pledge' | 'history') { history.pushState({}, '', next === 'history' ? '/me/commitments' : `/campaigns/${DEMO_CAMPAIGN_ID}`); setView(next); window.scrollTo(0,0); }
  return <><aside style={{padding:'10px 20px',background:'#152c32',color:'white',display:'flex',flexWrap:'wrap',gap:12,alignItems:'center',font:'12px Segoe UI'}} aria-label="Mock controls"><strong>F07 developer preview</strong><label>Mock response <select aria-label="Mock response" value={fault} onChange={event => { const value = event.target.value as MockFault; setFault(value); mock.setFault(value); }}>{['none','400','409','403','load-error','history-error','timeout-after-save'].map(value => <option key={value}>{value}</option>)}</select></label><label><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)}/> Mock donor enabled</label><button onClick={() => { mock.reset(); setFault('none'); setEpoch(x=>x+1); }}>Reset mock data</button><button onClick={() => { mock.loadPartialReceiptFixture(); setFault('none'); setEpoch(x => x + 1); navigate('history'); }}>Load partial receipt fixture</button><button onClick={() => { mock.receiveRemainingForDemo(); setDataRevision(x => x + 1); }}>Mock receipt: receive remaining goods</button><span>No real account, API, payment or transaction.</span></aside><DonorApp key={epoch} api={mock.api} dataRevision={dataRevision} campaignId={DEMO_CAMPAIGN_ID} mode="mock" donorEnabled={enabled} view={view} navigate={navigate}/></>;
}
createRoot(document.getElementById('root')!).render(<Preview/>);
