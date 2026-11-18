'use client';
import { useEffect, useRef, useState } from 'react';
import { ApiError, cancellationOutcomeUnknown, validateCancellation } from './model';
import { shortReference } from '../../src/lib/display';
import type { CampaignResponse, CancellationRequest, CommitmentResponse, DonorApi, Draft } from './model';

interface Props {
  api: DonorApi; commitment: CommitmentResponse; campaign?: CampaignResponse; ready: boolean;
  onChanged: (saved?: CommitmentResponse) => void; onClose: () => void; onLock: (locked: boolean) => void;
}
export function CancellationForm({ api, commitment, campaign, ready, onChanged, onClose, onLock }: Props) {
  const [draft, setDraft] = useState<Draft>({});
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState(false);
  const [busy, setBusy] = useState(false);
  const [unknown, setUnknown] = useState(false);
  const pending = useRef<{ body: CancellationRequest; key: string } | null>(null);
  const locked = useRef(false), alive = useRef(true);
  const summary = useRef<HTMLDivElement>(null);
  useEffect(() => { alive.current = true; return () => { alive.current = false; onLock(false); }; }, [onLock]);
  useEffect(() => { if (message) summary.current?.focus(); }, [message]);
  const update = (id: string, patch: Partial<Draft[string]>) => {
    setDraft(old => ({ ...old, [id]: { ...(old[id] ?? { selected: false, quantity: '' }), ...patch } }));
    setMessage(''); setErrors({}); setSuccess(false);
  };
  async function submit() {
    if (locked.current || (!ready && !unknown)) return;
    let request = pending.current;
    if (!unknown) {
      const checked = validateCancellation(commitment, draft, reason);
      if (Object.keys(checked.errors).length) { setErrors(checked.errors); setSuccess(false); setMessage('Check your cancellation before submitting.'); return; }
      request = { body: checked.body, key: crypto.randomUUID() }; pending.current = request;
    }
    if (!request) return;
    locked.current = true; setBusy(true); onLock(true); setMessage(''); setErrors({}); setSuccess(false);
    try {
      const saved = await api.cancel(commitment.id, request.body, request.key);
      if (!alive.current) return;
      pending.current = null; setUnknown(false); setDraft({}); setReason(''); setSuccess(true);
      setMessage('Cancellation saved. Received goods are unchanged. Pending on-chain.'); onChanged(saved); onLock(false);
    } catch (error) {
      if (!alive.current) return;
      if (error instanceof ApiError && error.problem.code !== 'CSRF_INVALID' && !cancellationOutcomeUnknown(error)) {
        pending.current = null; setUnknown(false); onLock(false);
        setMessage(`${error.problem.status === 409 ? '409 Conflict' : error.problem.status} ${error.problem.code}: ${error.problem.detail}`);
        const fieldErrors: Record<string, string> = {};
        for (const field of error.problem.fieldErrors) {
          const index = /^items\[(\d+)\]\.quantity$/.exec(field.field)?.[1];
          const id = index === undefined ? undefined : request.body.items[Number(index)]?.contributionItemId;
          if (id) fieldErrors[id] = 'Review the current unreceived quantity.';
          if (field.field === 'reason') fieldErrors.reason = 'Check the cancellation reason.';
        }
        setErrors(fieldErrors);
        if (error.problem.status === 409) onChanged();
      } else {
        setUnknown(true);
        setMessage(error instanceof ApiError && error.problem.code === 'CSRF_INVALID' ? 'The security token expired. Retry the same cancellation with a refreshed token.' : `${error instanceof ApiError ? `${error.problem.status} ${error.problem.code}: ` : ''}Cancellation outcome is unknown. Retry the same cancellation to recover safely.`);
      }
    } finally { locked.current = false; if (alive.current) setBusy(false); }
  }
  const disabled = busy || unknown || !ready;
  const hasOutstanding = commitment.items.some(item => item.outstandingQty > 0);
  return <section className="ab-panel ab-cancellation" aria-label="Cancel unreceived goods">
    <div className="ab-section-title"><h2>Cancel unreceived goods</h2><button disabled={busy || unknown} onClick={onClose}>Close cancellation</button></div>
    <p className="ab-caption">Reference: {shortReference(commitment.id)}. Only outstanding goods can be cancelled. Original pledges and receipts remain in history.</p>
    {message && <div ref={summary} tabIndex={-1} role={success ? 'status' : 'alert'} className={success ? 'ab-success' : 'ab-error'}><strong>{message}</strong>{Object.entries(errors).map(([id, error]) => <p key={id}>{id === 'form' ? error : <a href={id === 'reason' ? '#cancel-reason' : `#cancel-quantity-${id}`}>{error}</a>}</p>)}</div>}
    <form noValidate onSubmit={event => { event.preventDefault(); void submit(); }}>
      {commitment.items.map(item => {
        const meta = campaign?.id === commitment.campaignId ? campaign.items.find(x => x.id === item.campaignItemId) : undefined;
        return <div className="ab-item" key={item.id}>
          <label className="ab-item-check"><input type="checkbox" checked={draft[item.id]?.selected ?? false} disabled={disabled || item.outstandingQty <= 0} onChange={event => update(item.id, { selected: event.target.checked })}/><span>Cancel {meta?.name ?? item.campaignItemId}<small>{item.receivedQty} received · {item.cancelledQty} cancelled · {item.outstandingQty} outstanding {meta?.unit ?? '(unit unavailable)'}</small></span></label>
          <label htmlFor={`cancel-quantity-${item.id}`} className="ab-caption">Cancel quantity ({meta?.unit ?? item.campaignItemId})</label>
          <input id={`cancel-quantity-${item.id}`} type="number" min="1" max={Math.min(1_000_000, item.outstandingQty)} step="1" inputMode="numeric" value={draft[item.id]?.quantity ?? ''} disabled={disabled || !draft[item.id]?.selected || item.outstandingQty <= 0} aria-invalid={Boolean(errors[item.id])} aria-describedby={errors[item.id] ? `cancel-error-${item.id}` : undefined} onChange={event => update(item.id, { quantity: event.target.value })}/>
          {errors[item.id] && <p id={`cancel-error-${item.id}`} className="ab-field-error">{errors[item.id]}</p>}
        </div>;
      })}
      <label htmlFor="cancel-reason">Cancellation reason</label><textarea id="cancel-reason" value={reason} maxLength={500} rows={3} disabled={disabled} aria-invalid={Boolean(errors.reason)} onChange={event => { setReason(event.target.value); setMessage(''); setSuccess(false); }}/>
      {!ready && <p role="status">Refresh pledge history and campaign information before another cancellation.</p>}
      {!hasOutstanding && <p role="status">No unreceived goods remain for cancellation.</p>}
      <button className="ab-primary" disabled={busy || ((!ready || !hasOutstanding) && !unknown)}>{busy ? 'Saving cancellation...' : unknown ? 'Retry same cancellation' : 'Cancel selected goods'}</button>
    </form>
  </section>;
}
