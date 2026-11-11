'use client';
import { useEffect, useRef, useState } from 'react';
import type { CampaignResponse, CommitmentRequest, CommitmentResponse, DonorApi, Draft, Page } from './model';
import { ApiError, validateDraft } from './model';
import { CancellationForm } from './CancellationForm';
import './donor.css';

export interface DonorAppProps {
  api: DonorApi;
  campaignId: string;
  view: 'pledge' | 'history';
  navigate: (view: 'pledge' | 'history') => void;
  // Supply real capability from the existing session client; never infer from staff roles.
  donorEnabled: boolean;
  mode: 'mock' | 'real';
}
export function DonorApp(props: DonorAppProps) {
  return <DonorWorkspace key={props.campaignId} {...props}/>;
}
function DonorWorkspace({ api, campaignId, view, navigate, donorEnabled, mode }: DonorAppProps) {
  const [campaign, setCampaign] = useState<CampaignResponse>();
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState<Draft>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<CommitmentResponse>();
  const [unknown, setUnknown] = useState(false);
  const pending = useRef<{ body: CommitmentRequest; key: string } | null>(null);
  const locked = useRef(false);
  const access = useRef(donorEnabled); access.current = donorEnabled;
  const summary = useRef<HTMLDivElement>(null);
  const resultRef = useRef<HTMLDivElement>(null);
  const [history, setHistory] = useState<Page<CommitmentResponse>>();
  const [historyError, setHistoryError] = useState('');
  const [historyLoading, setHistoryLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [historyRevision, setHistoryRevision] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [cancelTarget, setCancelTarget] = useState<CommitmentResponse>();
  const [cancelLocked, setCancelLocked] = useState(false);
  useEffect(() => {
    let active = true; setLoading(true); setLoadError(''); setCampaign(undefined);
    api.getCampaign(campaignId).then(value => { if (active) setCampaign(value); }).catch(() => { if (active) setLoadError('Unable to load campaign information.'); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [api, campaignId, refresh]);
  useEffect(() => {
    if (view !== 'history' || !donorEnabled) { setHistory(undefined); return; }
    let active = true; setHistoryLoading(true); setHistoryError(''); setHistory(undefined);
    api.getCommitments(page).then(value => { if (active) { setHistory(value); setCancelTarget(old => old ? value.items.find(item => item.id === old.id) ?? old : undefined); } }).catch(() => { if (active) setHistoryError('Unable to load your pledge history.'); }).finally(() => { if (active) setHistoryLoading(false); });
    return () => { active = false; };
  }, [api, view, page, refresh, donorEnabled, historyRevision]);
  useEffect(() => { if (message) summary.current?.focus(); }, [message, errors]);
  useEffect(() => { if (result) resultRef.current?.focus(); }, [result]);
  useEffect(() => { if (!donorEnabled) { setHistory(undefined); setDraft({}); setResult(undefined); pending.current = null; setUnknown(false); } }, [donorEnabled]);
  useEffect(() => { if (!donorEnabled) { setCancelTarget(undefined); setCancelLocked(false); } }, [donorEnabled]);
  const update = (id: string, patch: Partial<Draft[string]>) => { setDraft(old => ({ ...old, [id]: { ...(old[id] ?? { selected: false, quantity: '' }), ...patch } })); setErrors(old => ({ ...old, [id]: '' })); setMessage(''); setResult(undefined); };
  async function submit(retry = false) {
    if (locked.current || !donorEnabled || !campaign) return;
    setResult(undefined); setMessage(''); setErrors({});
    let request = pending.current;
    if (!retry) {
      const checked = validateDraft(campaign, draft);
      if (Object.keys(checked.errors).length) { setErrors(checked.errors); setMessage('Check your pledge before submitting.'); return; }
      request = { body: checked.body, key: crypto.randomUUID() }; pending.current = request;
    }
    if (!request) return;
    locked.current = true; setBusy(true);
    try {
      const saved = await api.pledge(campaignId, request.body, request.key);
      if (!access.current) { pending.current = null; setUnknown(false); return; }
      pending.current = null; setUnknown(false); setDraft({}); setPage(0); setHistoryRevision(x => x + 1); setResult(saved);
      // Read the authoritative view, never infer campaign totals from a donor-level response.
      try { setCampaign(await api.getCampaign(campaignId)); } catch { setLoadError('Pledge saved. Refresh campaign information before making another pledge.'); }
    } catch (error) {
      if (!access.current) { pending.current = null; setUnknown(false); return; }
      if (error instanceof ApiError) {
        setUnknown(false); pending.current = null;
        const fieldErrors: Record<string, string> = {};
        for (const field of error.problem.fieldErrors) {
          const index = /^items\[(\d+)\]\.quantity$/.exec(field.field)?.[1];
          const id = index === undefined ? undefined : request.body.items[Number(index)]?.campaignItemId;
          if (id) fieldErrors[id] = error.problem.code === 'QUANTITY_EXCEEDED' ? 'Quantity is no longer available. Review the current limit.' : 'Check this quantity.';
        }
        setErrors(fieldErrors); setMessage(`${error.problem.status} ${error.problem.code}: ${error.problem.detail}`);
        if (error.problem.code === 'QUANTITY_EXCEEDED') {
          try { setCampaign(await api.getCampaign(campaignId)); } catch { setLoadError('Unable to refresh quantities. Retry loading before submitting.'); }
        }
      } else { setUnknown(true); setMessage('The result is not yet known. Retry the same request to safely recover its outcome.'); }
    } finally { locked.current = false; setBusy(false); }
  }
  const totalPledges = history?.totalElements ?? 0;
  const actionable = campaign?.status === 'PUBLISHED' && campaign.items.some(x => x.remainingPledgeQty > 0);
  return <div className="ab-donor">
    <header className="ab-header"><a href="#main" className="ab-brand">a<span>↗</span> AidBridge</a><nav aria-label="Donor navigation"><button disabled={cancelLocked} aria-current={view === 'pledge' ? 'page' : undefined} onClick={() => { setCancelTarget(undefined); navigate('pledge'); }}>Explore campaign</button><button disabled={cancelLocked} aria-current={view === 'history' ? 'page' : undefined} onClick={() => navigate('history')}>My pledges</button></nav><span className="ab-user">Donor workspace</span></header>
    <div className="ab-mode">{mode === 'mock' ? 'MOCK DEMO · Simulated donor capability and goods. Changes reset on reload. No backend or blockchain calls.' : 'REAL API · Capability and data provided by the connected session client.'}</div>
    <main id="main">
      <p className="ab-eyebrow">ESSENTIAL GOODS · SHARED PROGRESS</p>
      <h1>{view === 'pledge' ? 'A little support.\nA meaningful difference.' : 'Your support, clearly tracked.'}</h1>
      <p className="ab-lede">{view === 'pledge' ? 'Pledge the essentials a community needs. Follow each step, from a promise to independently confirmed delivery.' : 'Your commitments in one place. A pledge is a promise of goods — not a record of receipt or delivery.'}</p>
      {!donorEnabled && <section className="ab-notice" role="status"><h2>Donor access is not enabled</h2><p>You can view public campaign information. Ask your organization to review your account before pledging or viewing private history.</p></section>}
      {view === 'pledge' ? <>
        {loading && <div className="ab-panel" role="status">Loading campaign...</div>}
        {loadError && <div className="ab-notice" role="alert"><p>{loadError}</p><button onClick={() => setRefresh(x => x + 1)}>Retry campaign loading</button></div>}
        {campaign && !loading && <div className="ab-layout"><div>
          <section className="ab-campaign"><div className="ab-campaign-art" aria-hidden="true"><span>CARE<br/>IN EVERY<br/>COMMITMENT.</span><i>↗</i></div><div className="ab-campaign-body"><span className="ab-pill">{campaign.status}</span><p className="ab-caption">{campaign.organization.name} · {campaign.approximateArea}</p><h2>{campaign.title}</h2><p>{campaign.publicSummary}</p><div className="ab-trust"><span>✓ Needs reviewed before publication</span><span>↗ Goods only · no payments</span></div></div></section>
          <section className="ab-panel"><div className="ab-section-title"><h2>Every stage counts</h2><span>Campaign totals</span></div><p className="ab-caption">Pooled campaign progress. It does not identify which donor’s goods reached a household.</p><div className="ab-table-scroll" tabIndex={0} role="region" aria-label="Goods quantities"><table><caption className="ab-sr">Campaign goods progress</caption><thead><tr><th scope="col">Item</th><th scope="col">Target</th><th scope="col">Pledged</th><th scope="col">Received</th><th scope="col">Delivered</th><th scope="col">Confirmed</th></tr></thead><tbody>{campaign.items.map(item => <tr key={item.id}><th scope="row">{item.name}<small>{item.unit}</small></th><td>{item.targetQty}</td><td>{item.pledgedQty}</td><td>{item.receivedQty}</td><td>{item.deliveredQty}</td><td>{item.confirmedQty}</td></tr>)}</tbody></table></div></section>
          <section className="ab-explainer"><span>01 · PLEDGE</span><span>02 · RECEIVE</span><span>03 · DELIVER</span><span>04 · CONFIRM</span><p>A separate reviewer confirms delivery. A pending on-chain status is not proof that goods have been delivered.</p></section>
        </div><section className="ab-panel ab-form"><p className="ab-eyebrow">MAKE A COMMITMENT</p><h2>Pledge essential goods</h2><p className="ab-caption">Choose one or more items. Units are fixed for this campaign.</p>
          {message && <div className="ab-error" role="alert" tabIndex={-1} ref={summary}><strong>{message}</strong>{Object.entries(errors).filter(([,value]) => value).map(([id,value]) => <p key={id}>{id === 'form' ? value : <a href={`#quantity-${id}`}>{campaign.items.find(x => x.id === id)?.name}: {value}</a>}</p>)}</div>}
          {result && <div className="ab-success" role="status" tabIndex={-1} ref={resultRef}><strong>Goods pledged. Pending on-chain.</strong><p>Your pledge is saved{mode === 'mock' ? ' in this mock session' : ''}. It has not been received or delivered.</p><small>Reference: {result.id}</small><button onClick={() => navigate('history')}>View my pledges →</button></div>}
          <form noValidate onSubmit={event => { event.preventDefault(); void submit(); }}>
            {campaign.items.map(item => <div className="ab-item" key={item.id}><label className="ab-item-check"><input type="checkbox" checked={draft[item.id]?.selected ?? false} disabled={busy || unknown || !donorEnabled || item.remainingPledgeQty <= 0} onChange={event => update(item.id, { selected: event.target.checked })}/><span>{item.name}<small>{item.remainingPledgeQty} {item.unit} available to pledge</small></span></label><label htmlFor={`quantity-${item.id}`} className="ab-caption">Quantity ({item.unit})</label><input id={`quantity-${item.id}`} type="number" min="1" max={Math.min(1_000_000,item.remainingPledgeQty)} step="1" inputMode="numeric" value={draft[item.id]?.quantity ?? ''} disabled={busy || unknown || !donorEnabled || !draft[item.id]?.selected} aria-invalid={Boolean(errors[item.id])} aria-describedby={errors[item.id] ? `error-${item.id}` : undefined} onChange={event => update(item.id, { quantity: event.target.value })}/>{errors[item.id] && <p className="ab-field-error" id={`error-${item.id}`}>{errors[item.id]}</p>}</div>)}
            {!actionable && <p role="status">No remaining pledge capacity. This campaign is currently read-only.</p>}
            <p className="ab-caption">Only pledge goods you intend to provide. This action does not record a donation of money.</p>
            {unknown ? <button className="ab-primary" type="button" disabled={busy || !donorEnabled} onClick={() => void submit(true)}>{busy ? 'Recovering...' : 'Retry same request'}</button> : <button className="ab-primary" disabled={busy || !donorEnabled || !actionable || Boolean(loadError)}>{busy ? 'Saving pledge...' : 'Pledge goods →'}</button>}
            <span className="ab-sr" role="status">{busy ? 'Pledge request in progress.' : ''}</span>
          </form>
        </section></div>}
      </> : <section aria-label="Pledge history"><div className="ab-section-title"><h2>My pledges {history && <small>({totalPledges})</small>}</h2><button onClick={() => setRefresh(x => x + 1)} disabled={historyLoading || !donorEnabled || cancelLocked}>Refresh history</button></div>
        {historyLoading && <div className="ab-panel" role="status">Loading your pledges...</div>}
        {historyError && <div className="ab-notice" role="alert"><p>{historyError}</p><button onClick={() => setRefresh(x => x + 1)}>Retry history loading</button></div>}
        {history && history.items.length === 0 && <div className="ab-panel ab-empty"><span aria-hidden="true">↗</span><h2>No pledges yet</h2><p>Make your first commitment to a verified campaign.</p><button className="ab-primary" onClick={() => navigate('pledge')}>Explore campaign →</button></div>}
        {history?.items.map(pledge => <article className="ab-panel ab-history-card" key={pledge.id}><div className="ab-section-title"><div><p className="ab-caption">{new Date(pledge.createdAt).toLocaleString()}</p><h2>{campaign?.id === pledge.campaignId ? campaign.title : `Campaign ${pledge.campaignId}`}</h2></div><span className="ab-pill">{pledge.status.replaceAll('_', ' ')}</span></div><p className="ab-caption">Reference: {pledge.id}</p><div className="ab-table-scroll" tabIndex={0} role="region" aria-label="Goods quantities"><table><caption className="ab-sr">Your contribution quantities</caption><thead><tr><th scope="col">Item</th><th scope="col">Pledged</th><th scope="col">Cancelled</th><th scope="col">Received</th><th scope="col">Outstanding</th></tr></thead><tbody>{pledge.items.map(item => <tr key={item.id}><th scope="row">{campaign?.items.find(x => x.id === item.campaignItemId)?.name ?? item.campaignItemId}<small>{campaign?.items.find(x => x.id === item.campaignItemId)?.unit ?? 'Unit unavailable'}</small></th><td>{item.pledgedQty}</td><td>{item.cancelledQty}</td><td>{item.receivedQty}</td><td>{item.outstandingQty}</td></tr>)}</tbody></table></div><p className="ab-caption">Campaign-level delivery and confirmation: {campaign?.id === pledge.campaignId ? campaign.items.map(item => `${item.name}: ${item.deliveredQty} ${item.unit} delivered, ${item.confirmedQty} confirmed`).join('; ') : 'Campaign totals unavailable.'}</p><p className="ab-chain">{pledge.blockchainStatus.replaceAll('_', ' ')}</p><p className="ab-caption">Receipt quantities belong to your contribution. Delivery and confirmation totals are campaign-level only.</p><button disabled={cancelLocked || busy || unknown || !donorEnabled || !pledge.items.some(item => item.outstandingQty > 0)} onClick={() => setCancelTarget(pledge)}>Cancel unreceived goods</button></article>)}
        {cancelTarget && donorEnabled && <CancellationForm key={cancelTarget.id} api={api} commitment={cancelTarget} campaign={campaign} ready={Boolean(history) && !historyLoading && !historyError && !loading && !loadError} onLock={setCancelLocked} onClose={() => setCancelTarget(undefined)} onChanged={saved => { if (saved) setCancelTarget(saved); setRefresh(x => x + 1); }}/>}
        {history && history.totalElements > history.size && <nav aria-label="History pages" className="ab-pagination"><button disabled={page === 0 || historyLoading || cancelLocked} onClick={() => { setCancelTarget(undefined); setPage(x => x - 1); }}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * history.size >= history.totalElements || historyLoading || cancelLocked} onClick={() => { setCancelTarget(undefined); setPage(x => x + 1); }}>Next</button></nav>}
      </section>}
    </main><footer className="ab-footer">AidBridge · Bridging verified needs with transparent aid.<span>F07 · Goods commitments</span></footer>
  </div>;
}
