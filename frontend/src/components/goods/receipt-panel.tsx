"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useSession } from "@/components/session-provider";
import { canManageCampaigns } from "@/lib/api-client";
import type { OutstandingCommitment, ReceiptRequest, ReceiptResponse } from "@/lib/goods";
import type { ReceiptAttempt } from "@/lib/session-work";
import type { CaseDetailResponse } from "@/lib/types";
import { describeError, ErrorNotice, FieldError, StatusBadge, type ErrorInfo } from "@/components/cases/shared";

export function ReceiptPanel({ detail }: { detail: CaseDetailResponse }) {
  const { mode, user } = useSession();
  if (mode !== "real" || !user || !canManageCampaigns(user, detail.organizationId)) return null;
  return <StaffReceipt caseId={detail.id} organizationId={detail.organizationId} actorId={user.userId} />;
}

function StaffReceipt({ caseId, organizationId, actorId }: { caseId: string; organizationId: string; actorId: string }) {
  const { api, work } = useSession();
  const scope = `${actorId}:${organizationId}:${caseId}`;
  const [commitments, setCommitments] = useState<OutstandingCommitment[]>([]);
  const [commitmentId, setCommitmentId] = useState("");
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [receivedAt, setReceivedAt] = useState("");
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [readError, setReadError] = useState<ErrorInfo | null>(null);
  const [writeError, setWriteError] = useState<ErrorInfo | null>(null);
  const [result, setResult] = useState<ReceiptResponse | null>(null);
  const [retry, setRetry] = useState<ReceiptAttempt | null>(() => work.receipts.get(scope) ?? null);
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const mounted = useRef(false);
  const commitment = commitments.find(row => row.contributionId === commitmentId) ?? commitments[0];

  useEffect(() => {
    mounted.current = true;
    void refresh();
    return () => { mounted.current = false; };
  }, [api, caseId, organizationId]);

  async function refresh() {
    setLoading(true);
    setReadError(null);
    try {
      const staff = await api.getStaffCase(caseId);
      if (!mounted.current) return;
      if (staff.organizationId !== organizationId) {
        setCommitments([]);
        setReadError(describeError({ status: 404, code: "RESOURCE_NOT_FOUND" }));
        return;
      }
      setCommitments(staff.outstandingCommitments);
    } catch (cause) {
      if (mounted.current) setReadError(describeError(cause));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }

  async function send(attempt: ReceiptAttempt) {
    if (submitting.current || work.receipts.get(scope)?.inFlight) return;
    const isRetry = work.receipts.get(scope) === attempt;
    submitting.current = true;
    attempt.inFlight = true;
    work.receipts.set(scope, attempt);
    setBusy(true);
    setWriteError(null);
    try {
      if (isRetry) {
        const current = await api.me();
        if (current.userId !== actorId || !canManageCampaigns(current, organizationId)) {
          throw { status: 403, code: "FORBIDDEN" };
        }
      }
      const saved = await api.recordReceipt(attempt.commitmentId, attempt.body, attempt.key);
      if (work.receipts.get(scope) === attempt) work.receipts.delete(scope);
      if (!mounted.current) return;
      setRetry(null);
      setResult(saved);
      setQuantities({});
      setNote("");
      await refresh();
    } catch (cause) {
      const issue = describeError(cause);
      const canRetry = issue.unknownOutcome || issue.code === "CSRF_INVALID";
      if (!canRetry && work.receipts.get(scope) === attempt) work.receipts.delete(scope);
      if (!mounted.current) return;
      setWriteError(issue);
      setRetry(canRetry ? attempt : null);
      if (issue.status === 409 || issue.status === 404) {
        setQuantities({});
        await refresh();
      }
    } finally {
      attempt.inFlight = false;
      submitting.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!commitment || busy || loading || readError || retry) return;
    const items: ReceiptRequest["items"] = commitment.items.filter(item => quantities[item.contributionItemId]).map(item => ({
      contributionItemId: item.contributionItemId, quantity: Number(quantities[item.contributionItemId]),
    }));
    const invalidItem = items.find(line => {
      const row = commitment.items.find(item => item.contributionItemId === line.contributionItemId);
      return !row || !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > Math.min(row.outstandingQty, 2_147_483_647);
    });
    const time = receivedAt ? Date.parse(receivedAt + "Z") : Date.now();
    if (!items.length || invalidItem || !Number.isFinite(time) || /[<>\0]|:\/\//.test(note)) {
      setWriteError({
        status: 400, code: "VALIDATION_ERROR", unknownOutcome: false, fieldErrors: [],
        message: invalidItem ? "Enter a whole quantity within the outstanding amount."
          : !items.length ? "Enter a quantity for at least one line."
          : !Number.isFinite(time) ? "Enter a valid UTC receipt time." : "Use plain text in the note.",
      });
      return;
    }
    setResult(null);
    void send({ commitmentId: commitment.contributionId, key: crypto.randomUUID(), body: {
      receivedAt: new Date(time).toISOString(), items, ...(note.trim() && { note: note.trim() }),
    } });
  }

  const fieldErrors = writeError?.fieldErrors ?? [];
  const locked = busy || loading || !!retry || !!readError;
  const submittedItems = commitment?.items.filter(item => quantities[item.contributionItemId]) ?? [];

  return <section className="card stack" aria-label="Goods receipt">
    <div><h2>Record goods receipt</h2><p className="muted">Select the commitment and quantities received by your organization.</p></div>
    {result && <div className="notice success" role="status">
      <strong>Receipt recorded</strong><p className="mono">Receipt ID: {result.id}</p>
      <p>Blockchain sync: <StatusBadge status={result.blockchainStatus} /></p>
    </div>}
    <ErrorNotice error={writeError} />
    <ErrorNotice error={readError} />
    {readError && <button className="button secondary" type="button" disabled={busy || loading} onClick={() => void refresh()}>Reload receipt context</button>}
    {loading && <p role="status">Loading outstanding commitments…</p>}
    {retry && <button type="button" className="button secondary" disabled={busy || loading} onClick={() => void send(retry)}>Retry same receipt</button>}
    {!loading && !readError && !commitment && <p>No commitments have goods outstanding.</p>}
    {commitment && <form className="stack" onSubmit={submit}>
      <fieldset className="form-fieldset stack" disabled={locked}>
        <label className="field">Outstanding commitment
          <select value={commitment.contributionId} onChange={event => { setCommitmentId(event.target.value); setQuantities({}); setWriteError(null); }}>
            {commitments.map(row => <option key={row.contributionId} value={row.contributionId}>{row.status} · {row.contributionId}</option>)}
          </select>
        </label>
        {commitment.items.map(row => {
          const sentIndex = submittedItems.findIndex(item => item.contributionItemId === row.contributionItemId);
          return <label key={row.contributionItemId} className="field">{row.itemName} quantity ({row.unit})
            <span className="muted" data-testid={`outstanding-${row.contributionItemId}`}>Outstanding: {row.outstandingQty}</span>
            <input aria-label={`${row.itemName} receipt quantity (${row.unit})`} type="number" min={1} max={row.outstandingQty} step={1}
              value={quantities[row.contributionItemId] ?? ""} onChange={event => setQuantities({ ...quantities, [row.contributionItemId]: event.target.value })} />
            {sentIndex >= 0 && <FieldError name={`items[${sentIndex}].quantity`} issues={fieldErrors} />}
          </label>;
        })}
        <label className="field">Received at (UTC, defaults to now)
          <input type="datetime-local" value={receivedAt} onChange={event => setReceivedAt(event.target.value)} />
          <FieldError name="receivedAt" issues={fieldErrors} />
        </label>
        <label className="field">Receipt note (optional)
          <textarea maxLength={500} value={note} onChange={event => setNote(event.target.value)} />
          <FieldError name="note" issues={fieldErrors} />
        </label>
        <button className="button" type="submit">Record receipt</button>
      </fieldset>
    </form>}
  </section>;
}
