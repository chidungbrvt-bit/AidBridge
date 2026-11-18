'use client';

import { useRef, useState } from 'react';
import { DeliveryReviewPanel } from './DeliveryReviewPanel';
import { applyReview, deliveryRequest, emptyReview, hasOpenDispute, type ReviewActor, type ReviewCommand } from './delivery-review';
import { recordFixtureDelivery } from './fixture-delivery';
import fixture from './f02-snapshot.json';
import type { DeliveryResponse } from '../../src/lib/goods';
import { useSession } from '../../src/components/session-provider';
import { useI18n } from '../../src/components/i18n-provider';
import { apply, contributionId, evidenceIds, GoodsError, initialSnapshot, limits, permitted, type Action, type Actor } from './model';

export function GoodsDemo() {
  const { mode } = useSession();
  const { t } = useI18n();
  const [snapshot, setSnapshot] = useState(() => initialSnapshot());
  const current = useRef(snapshot);
  const [review, setReview] = useState(emptyReview);
  const currentReview = useRef(review);
  const deliveryJournal = useRef<Parameters<typeof recordFixtureDelivery>[2]>(new Map());
  const [fixtureGeneration, setFixtureGeneration] = useState(0);
  const disputeOpen = hasOpenDispute(review);
  const [action, setAction] = useState<Action>('receipt');
  const [actor, setActor] = useState<Actor>('organizationAdmin');
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [outcome, setOutcome] = useState<'DELIVERED' | 'FAILED'>('DELIVERED');
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [at, setAt] = useState('2026-11-09T01:49');
  const [evidence, setEvidence] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const locked = useRef(false);
  const [view, setView] = useState<'ready' | 'loading' | 'empty' | 'error'>('ready');
  const [conflict, setConflict] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(false);
  const [lineError, setLineError] = useState<string>();
  const [result, setResult] = useState<{ outcome: string; reason: string; delivery?: DeliveryResponse; lines: { name: string; unit: string; quantity: number }[] }>();
  const summary = useRef<HTMLParagraphElement>(null);
  if (mode !== 'mock') return <section className="card"><h1>{t("Goods mock workspace unavailable")}</h1><p>{t("Use the case detail page to record real receipts. Delivery and review fixtures are available in mock mode.")}</p></section>;
  const rows = limits(snapshot, action);
  const failed = action === 'delivery' && outcome === 'FAILED';
  function reset(partial: boolean) {
    if (locked.current) return;
    const next = initialSnapshot(partial); current.current = next; setSnapshot(next);
    currentReview.current = emptyReview();
    deliveryJournal.current.clear();
    setReview(currentReview.current);
    setFixtureGeneration(value => value + 1);
    setDraft({}); setEvidence([]); setReason(''); setNote(''); setMessage(''); setView('ready'); setConflict(false); setResult(undefined); setLineError(undefined); setError(false);
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (locked.current || hasOpenDispute(currentReview.current)) return;
    locked.current = true; setBusy(true); setMessage('Recording mock result…'); setError(false); setLineError(undefined); setResult(undefined);
    // Capture the immutable request. No HTTP request, session capability change or real write.
    const time = Date.parse(`${at}Z`);
    const command = { action, actor, outcome, reason: failed ? reason : '', note,
      at: failed || (!at && action === 'delivery') ? undefined : Number.isFinite(time) ? new Date(time).toISOString() : 'invalid',
      evidence: failed ? [] : [...evidence],
      lines: failed ? [] : rows.filter(row => draft[row.id] !== undefined).map(row => ({ id: row.id, quantity: Number(draft[row.id]) })) };
    await new Promise(resolve => setTimeout(resolve, 300));
    try {
      const recorded = action === 'delivery' ? recordFixtureDelivery(current.current, currentReview.current, deliveryJournal.current,
        { caseId: fixture.caseId, actor, key: crypto.randomUUID(), body: deliveryRequest(command) },
        { id: crypto.randomUUID(), auditEventId: crypto.randomUUID(), recordedAt: new Date().toISOString(), staleStock: conflict },
      ) : undefined;
      const next = recorded?.snapshot ?? apply(current.current, command, conflict);
      const delivery = recorded?.response;
      const nextReview = recorded?.review ?? currentReview.current;
      current.current = next;
      currentReview.current = nextReview;
      setSnapshot(next);
      setReview(nextReview);
      setResult({delivery, outcome: delivery?.outcome ?? next.focalContribution.status, reason: command.reason,
        lines: command.lines.map(line => {const row = rows.find(row => row.id === line.id)!;return {name: row.name,unit: row.unit,quantity: line.quantity};})});
      setDraft({}); setConflict(false);
      setMessage(action === 'receipt' ? 'Mock receipt recorded. Outstanding quantities updated.' : failed ? 'Mock failed attempt recorded. Stock unchanged; blockchain NOT_APPLICABLE.' : 'Mock delivery recorded. Awaiting independent confirmation; confirmed quantities unchanged.');
    } catch (cause) { setError(true); setLineError(cause instanceof GoodsError ? cause.lineId : undefined); setMessage(cause instanceof GoodsError ? cause.message + ' — No quantities changed.' : 'Mock operation failed. No quantities changed.'); }
    finally { locked.current = false; setBusy(false); summary.current?.focus(); }
  }
  function submitReview(actor: ReviewActor, command: ReviewCommand) {
    if (locked.current || view !== 'ready') throw new GoodsError(409, 'INVALID_STATE');
    const result = applyReview(current.current, currentReview.current, actor, command, crypto.randomUUID());
    current.current = result.snapshot;
    currentReview.current = result.review;
    setSnapshot(result.snapshot);
    setReview(result.review);
  }

  return <>
    <header className="page-header"><div><p className="eyebrow">{t("F08 · F02 FIXTURES · MOCK DATA ONLY")}</p><h1>{t("Receive and deliver goods")}</h1><p>{t("No B08/B09 requests, database writes or blockchain transactions. Data resets when you leave this page.")}</p></div></header>
    <section className="card"><h2>{t("Simulated flood relief essentials")}</h2><p>{t('Fictional case')} · IN_PROGRESS · {disputeOpen ? t('Open dispute') : t('No open disputes')}. {t('Campaign totals are a shared pool, not donor-specific delivery provenance.')}</p>
      <div style={{ overflowX: 'auto' }}><table className="data-table"><caption>{t("Mock campaign quantities")}</caption><thead><tr><th>{t("Item")}</th><th>{t("Pledged")}</th><th>{t("Received")}</th><th>{t("Delivered")}</th><th>{t("Confirmed")}</th></tr></thead><tbody>{snapshot.campaign.items.map(item => <tr key={item.id} data-testid={`totals-${item.unit}`}><th>{item.name} ({item.unit})</th><td>{item.pledgedQty}</td><td>{item.receivedQty}</td><td>{item.deliveredQty}</td><td>{item.confirmedQty}</td></tr>)}</tbody></table></div>
    </section>
    <section className="card"><div className="actions"><button className="button secondary" disabled={busy} aria-pressed={action === 'receipt'} onClick={() => {setAction('receipt');setDraft({});setMessage('');}}>{t("R01 Receipt")}</button><button className="button secondary" disabled={busy} aria-pressed={action === 'delivery'} onClick={() => {setAction('delivery');setDraft({});setMessage('');}}>{t("D01 Delivery")}</button></div>
      <h2>{action === 'receipt' ? t('Record goods receipt') : t('Record delivery')}</h2>
      <p>{t("Fixture actor below is simulated, independent of your logged-in account. It does not grant any real role.")}</p>
      <label className="field">{t("Simulated actor")}<select value={actor} disabled={busy} onChange={e => {setActor(e.target.value as Actor);setMessage('');}}><option value="organizationAdmin">{t("Organization admin (R01)")}</option><option value="fieldVolunteer">{t("Field volunteer (D01)")}</option><option value="caseVerifier">{t("Case verifier (read only)")}</option><option value="donor">{t("Donor (no staff permission)")}</option></select></label>
      {disputeOpen && <p className="notice" role="status">{t("Receipt and delivery are blocked while a dispute is open.")}</p>}
      {!permitted(action, actor) && <p className="notice error" role="alert">403 FORBIDDEN — {t('This simulated actor cannot record {action}. No real permissions are changed.', { action: t(action) })}</p>}
      <p id="goods-feedback" ref={summary} tabIndex={-1} role={error ? 'alert' : 'status'} className={error ? 'notice error' : 'notice'}>{t(message)}</p>
      {result && <section aria-label="Latest mock result" className="notice"><h3>{t("Latest mock result")} · {result.outcome}</h3>
        <p>{t("In-memory simulation only; no saved backend record or blockchain transaction.")}</p>
        {result.lines.map(line => <p key={line.name}>{line.name}: {line.quantity} {line.unit}</p>)}
        {result.delivery && <><p className="mono">Delivery ID: {result.delivery.id}</p><p>{t('Review:')} {result.delivery.reviewStatus} · {t('Blockchain:')} {result.delivery.blockchainStatus}</p>{result.reason && <p>{t('Reason:')} {result.reason}</p>}</>}
      </section>}
      {view === 'loading' ? <p role="status">{t("Loading mock context…")}</p> : view === 'error' ? <div role="alert">{t('Mock context could not be loaded.')} <button className="button secondary" onClick={() => setView('ready')}>{t("Retry mock read")}</button></div> : view === 'empty' ? <p>{t("No eligible commitments or available goods in this simulated context.")}</p> : <form noValidate onSubmit={submit}>
        <fieldset disabled={busy || disputeOpen || !permitted(action, actor)} style={{ border: 0, padding: 0, minWidth: 0 }}>
          {action === 'receipt' && <label className="field">{t("Eligible mock commitment")}<select value={contributionId} onChange={() => {}}><option value={contributionId}>F02 pledge · {snapshot.focalContribution.status}</option></select></label>}
          {action === 'delivery' && <label className="field">{t("Outcome")}<select value={outcome} onChange={e => {setOutcome(e.target.value as typeof outcome);setDraft({});setEvidence([]);}}><option value="DELIVERED">DELIVERED</option><option value="FAILED">{t("FAILED · no goods effect")}</option></select></label>}
          {!failed && rows.map(row => <div className="field-grid" key={row.id}><label className="checkbox"><input type="checkbox" checked={draft[row.id] !== undefined} onChange={e => setDraft(old => {const next = {...old}; if (e.target.checked) next[row.id] = '1'; else delete next[row.id]; return next;})}/>{row.name} ({row.unit}) · {action === 'receipt' ? t('Unreceived') : t('Available')}: {row.available}</label><label className="field">{t("{item} quantity", { item: row.name })}<input type="number" aria-invalid={lineError === row.id} aria-describedby={lineError === row.id ? "goods-feedback" : undefined} min="1" max={row.available} step="1" disabled={draft[row.id] === undefined} value={draft[row.id] ?? ''} onChange={e => setDraft(old => ({...old, [row.id]: e.target.value}))}/></label></div>)}
          {!failed && <label className="field">{action === 'receipt' ? t('Received at (UTC)') : t('Handed over at (UTC, optional)')}<input type="datetime-local" value={at} onChange={e => setAt(e.target.value)}/></label>}
          {action === 'receipt' && <label className="field">{t("Receipt note (optional)")}<textarea maxLength={500} value={note} onChange={e => setNote(e.target.value)}/></label>}
          {failed && <label className="field">{t("Delivery reason (required)")}<textarea maxLength={2000} value={reason} onChange={e => setReason(e.target.value)}/></label>}
          {action === 'delivery' && !failed && <fieldset><legend>{t("Allowlisted fictional evidence versions (select at least one)")}</legend>{evidenceIds.map((id, i) => <label className="checkbox" key={id}><input type="checkbox" checked={evidence.includes(id)} onChange={e => setEvidence(old => e.target.checked ? [...old, id] : old.filter(x => x !== id))}/>{t("Fictional evidence {n}", { n: i + 1 })}</label>)}</fieldset>}
          <button className="button" type="submit">{busy ? t('Recording…') : action === 'receipt' ? t('Record mock receipt') : t('Record mock delivery')}</button>
        </fieldset>
      </form>}
    </section>
    {view === 'ready' && <DeliveryReviewPanel key={fixtureGeneration} state={review} snapshot={snapshot} disabled={busy} onReview={submitReview} />}
    <section className="card"><h2>{t("Developer-only fixture controls")}</h2><p>{t("F02 private read projection simulation. No shared donor state or session is modified.")}</p><div className="actions"><button disabled={busy} className="button secondary" onClick={() => reset(false)}>{t("Load S1 pledge fixture")}</button><button disabled={busy} className="button secondary" onClick={() => reset(true)}>{t("Load S2 partial receipt fixture")}</button>{(['ready','loading','empty','error'] as const).map(state => <button disabled={busy} className="button secondary" key={state} onClick={() => setView(state)}>{t("Show {state} context", { state: t(state) })}</button>)}</div><label className="checkbox"><input type="checkbox" disabled={busy} checked={conflict} onChange={e => setConflict(e.target.checked)}/>{t("Reject next write with 409 (stale stock simulation)")}</label></section>
  </>;
}
