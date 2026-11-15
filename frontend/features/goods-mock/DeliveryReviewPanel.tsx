import { useRef, useState, type FormEvent } from 'react';
import { GoodsError, type Snapshot } from './model';
import {
  canCompleteCampaign, hasOpenDispute, reviewPermission, type ReviewActor, type ReviewCommand, type ReviewState,
} from './delivery-review';

const actorLabels: Record<ReviewActor, string> = {
  independentConfirmer: 'Independent delivery confirmer',
  fieldVolunteer: 'Delivery recorder with confirmer role',
  organizationAdmin: 'Organization admin',
  caseVerifier: 'Case verifier',
  platformAdmin: 'Platform admin',
  donor: 'Donor',
};

type Props = {
  state: ReviewState;
  snapshot: Snapshot;
  disabled: boolean;
  onReview: (actor: ReviewActor, command: ReviewCommand) => void;
};

export function DeliveryReviewPanel({ state, snapshot, disabled, onReview }: Props) {
  const [operation, setOperation] = useState<ReviewCommand['operation']>('D02');
  const [actor, setActor] = useState<ReviewActor>('independentConfirmer');
  const [deliveryId, setDeliveryId] = useState('');
  const [disputeId, setDisputeId] = useState('');
  const [decision, setDecision] = useState<'CONFIRMED' | 'REQUIRES_REVIEW'>('CONFIRMED');
  const [resolution, setResolution] = useState<'UPHOLD' | 'VOID_DELIVERY'>('UPHOLD');
  const [reason, setReason] = useState('');
  const [reviewed, setReviewed] = useState<string[]>([]);
  const [message, setMessage] = useState<{ text: string; error: boolean }>();
  const feedback = useRef<HTMLParagraphElement>(null);
  const deliveries = state.deliveries.filter(delivery => delivery.outcome === 'DELIVERED' && delivery.status !== 'VOIDED');
  const pending = deliveries.filter(delivery => delivery.status === 'PENDING');
  const open = state.disputes.filter(dispute => dispute.status === 'OPEN');
  const dispute = open.find(entry => entry.id === disputeId) ?? open[0];
  const selected = operation === 'D02'
    ? pending.find(delivery => delivery.id === deliveryId) ?? pending[0]
    : deliveries.find(delivery => delivery.id === deliveryId);
  const allowed = reviewPermission(operation, actor, selected);
  const isSelf = operation === 'D02' && actor === 'fieldVolunteer' && !allowed;
  const openDispute = hasOpenDispute(state);
  const resolutionDelivery = deliveries.find(delivery => delivery.id === dispute?.deliveryId);
  const versions = (operation === 'D02' ? selected : resolutionDelivery)?.evidenceVersionIds ?? [];
  const requiresReason = operation !== 'D02' || decision === 'REQUIRES_REVIEW';
  const canSubmit = !disabled && allowed &&
    (operation === 'D02' ? !!selected && !openDispute : operation === 'S03' ? !!dispute : true);

  function switchOperation(next: ReviewCommand['operation']) {
    setOperation(next);
    setActor(next === 'D02' ? 'independentConfirmer' : next === 'S01' ? 'organizationAdmin' : 'platformAdmin');
    setDeliveryId('');
    setDisputeId('');
    setResolution('UPHOLD');
    setReason('');
    setReviewed([]);
    setMessage(undefined);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) return;
    try {
      if (operation === 'D02' && selected) {
        onReview(actor, { operation, deliveryId: selected.id, body: {
          decision, reviewedEvidenceVersionIds: reviewed, ...(reason.trim() ? { reason: reason.trim() } : {}),
        } });
      } else if (operation === 'S01') {
        onReview(actor, { operation, body: { ...(selected ? { deliveryId: selected.id } : {}), reason: reason.trim() } });
      } else if (operation === 'S03' && dispute) {
        onReview(actor, { operation, disputeId: dispute.id, body: {
          outcome: resolution, reason: reason.trim(), reviewEvidenceVersionIds: reviewed,
        } });
      }
      setReason('');
      setReviewed([]);
      setResolution('UPHOLD');
      setMessage({ text: 'Mock action recorded.', error: false });
    } catch (cause) {
      const detail = operation === 'D02' && cause instanceof GoodsError && cause.status === 400
        ? ' Review every evidence version and provide a reason when requesting review.'
        : '';
      setMessage({ text: (cause instanceof GoodsError ? cause.message : 'Mock action failed.') + detail, error: true });
    }
    requestAnimationFrame(() => feedback.current?.focus());
  }

  return <section className="card stack" aria-labelledby="delivery-review-title">
    <header>
      <p className="eyebrow">F09 · F02 FIXTURES</p>
      <h2 id="delivery-review-title">Delivery confirmation and disputes</h2>
      <p className="muted">Simulated actors use the deliveries recorded above. This preview sends no requests to the backend or Fuji.</p>
    </header>
    <div className="actions">
      <button type="button" className="button secondary" disabled={disabled} aria-pressed={operation === 'D02'} onClick={() => switchOperation('D02')}>D02 Confirm delivery</button>
      <button type="button" className="button secondary" disabled={disabled} aria-pressed={operation === 'S01'} onClick={() => switchOperation('S01')}>S01 Open dispute</button>
      <button type="button" className="button secondary" disabled={disabled} aria-pressed={operation === 'S03'} onClick={() => switchOperation('S03')}>S03 Resolve dispute</button>
    </div>
    <p>Campaign: <strong>{snapshot.campaign.status}</strong> · Open disputes: <strong>{open.length}</strong></p>
    <p>Completion eligibility: <strong>{openDispute ? 'Blocked by open dispute' : canCompleteCampaign(snapshot, state)
      ? 'Eligible for admin close' : 'Waiting for confirmed quantities and delivery review'}</strong></p>
    <form onSubmit={submit} className="stack">
      <fieldset className="form-fieldset stack" disabled={disabled}>
        <label className="field">Simulated review actor
          <select aria-label="Simulated review actor" value={actor} onChange={event => { setActor(event.target.value as ReviewActor); setMessage(undefined); }}>
            {Object.entries(actorLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </label>
        {!allowed && <p className="notice error" role="alert">{isSelf ? '403 SELF_CONFIRMATION · A recorder cannot confirm their own delivery.' : '403 FORBIDDEN · This actor cannot perform this operation.'}</p>}
        {operation === 'D02' && openDispute && <p className="notice" role="status">Resolve all open disputes before confirming a delivery.</p>}
        {operation === 'D02' && !selected && <p>Record a successful D01 delivery above to start independent review.</p>}
        {operation === 'S03' && !dispute && <p>There are no open disputes to resolve.</p>}
        <fieldset className="form-fieldset stack" disabled={!canSubmit}>
          {operation !== 'S03' && <label className="field">Delivery for review
            <select aria-label="Delivery for review" value={selected?.id ?? ''} onChange={event => { setDeliveryId(event.target.value); setReviewed([]); }}>
              {operation === 'S01' && <option value="">Case-level dispute</option>}
              {(operation === 'D02' ? pending : deliveries).map((delivery, index) =>
                <option key={delivery.id} value={delivery.id}>Delivery {index + 1} · {delivery.status}</option>)}
            </select>
          </label>}
          {operation === 'D02' && <label className="field">Confirmation decision
            <select aria-label="Confirmation decision" value={decision} onChange={event => setDecision(event.target.value as typeof decision)}>
              <option value="CONFIRMED">Confirm delivery</option><option value="REQUIRES_REVIEW">Requires review</option>
            </select>
          </label>}
          {operation === 'S03' && <><label className="field">Open dispute
            <select aria-label="Open dispute" value={dispute?.id ?? ''} onChange={event => { setDisputeId(event.target.value); setResolution('UPHOLD'); setReviewed([]); }}>
              {open.map(entry => <option value={entry.id} key={entry.id}>{entry.reason}</option>)}
            </select>
          </label><label className="field">Resolution
            <select aria-label="Resolution" value={resolution} onChange={event => setResolution(event.target.value as typeof resolution)}>
              <option value="UPHOLD">Uphold</option>
              <option value="VOID_DELIVERY" disabled={!resolutionDelivery}>Void delivery</option>
            </select>
          </label></>}
          {operation !== 'S01' && versions.length > 0 && <fieldset>
            <legend>{operation === 'D02' ? 'Review every delivery evidence version' : 'Evidence reviewed for resolution'}</legend>
            {versions.map((id, index) => <label className="checkbox" key={id}>
              <input type="checkbox" checked={reviewed.includes(id)} onChange={event =>
                setReviewed(previous => event.target.checked ? [...previous, id] : previous.filter(value => value !== id))} />
              <span>Evidence version {index + 1} · <code>{id}</code></span>
            </label>)}
          </fieldset>}
          <label className="field">Review reason {requiresReason ? '(required)' : '(optional)'}
            <textarea value={reason} maxLength={2000} required={requiresReason} onChange={event => setReason(event.target.value)} />
          </label>
          <button type="submit" className="button">{operation === 'D02' ? 'Submit mock confirmation' : operation === 'S01' ? 'Open mock dispute' : 'Resolve mock dispute'}</button>
        </fieldset>
      </fieldset>
    </form>
    {message && <p ref={feedback} tabIndex={-1} role={message.error ? 'alert' : 'status'} className={message.error ? 'notice error' : 'notice'}>{message.text}</p>}
    {state.deliveries.length > 0 && <div>
      <h3>Delivery records</h3>
      <ul>{state.deliveries.map((delivery, index) => <li key={delivery.id}>Delivery {index + 1}: {delivery.outcome} · {delivery.status}</li>)}</ul>
    </div>}
    {state.disputes.length > 0 && <div>
      <h3>Dispute records</h3>
      <ul>{state.disputes.map(dispute => <li key={dispute.id}>{dispute.status} · {dispute.reason}
        {dispute.resolution && <> · {dispute.resolution} · {dispute.resolutionReason}</>}
      </li>)}</ul>
    </div>}
    {state.events.length > 0 && <div>
      <h3>Simulated audit events</h3>
      <ul>{state.events.map(event => <li key={event.id}>{event.label} · {event.blockchainStatus}</li>)}</ul>
    </div>}
  </section>;
}
