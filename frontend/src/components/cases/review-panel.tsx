"use client";

import { useEffect, useRef, useState } from "react";
import { useSession } from "@/components/session-provider";
import { canReviewCases } from "@/lib/api-client";
import type { CaseDetailResponse, DecisionRequest, DecisionResponse, ReviewAction } from "@/lib/types";
import { describeError, ErrorNotice, FieldError, focusFirstError, StatusBadge, type ErrorInfo, type FieldIssue } from "./shared";

type ReviewAttempt = { action: ReviewAction; key: string; body: DecisionRequest };

export function ReviewPanel({ detail, onDecision, onRefresh, onBusyChange }: {
  detail: CaseDetailResponse;
  onDecision: (decision: DecisionResponse) => void;
  onRefresh: () => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const { api, user, setNavigationLocked } = useSession();
  const [reviewed, setReviewed] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [issues, setIssues] = useState<FieldIssue[]>([]);
  const [busy, setBusy] = useState(false);
  const [mustRefresh, setMustRefresh] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [success, setSuccess] = useState<DecisionResponse | null>(null);
  const pending = useRef<ReviewAttempt | null>(null);
  const submitting = useRef(false);
  const mounted = useRef(true);
  const form = useRef<HTMLFormElement>(null);
  const isCreator = user?.userId === detail.creator.id;
  const roleAllowed = canReviewCases(user, detail.organizationId);
  const canAct = roleAllowed && !isCreator && detail.status === "SUBMITTED" && detail.evidenceVersions.length > 0;
  const locked = !canAct || busy || mustRefresh || uncertain;

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { setNavigationLocked(busy || uncertain); return () => setNavigationLocked(false); }, [busy, uncertain, setNavigationLocked]);
  useEffect(() => { onBusyChange(busy || uncertain); }, [busy, uncertain, onBusyChange]);
  useEffect(() => {
    if (!uncertain) return;
    const protect = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener("beforeunload", protect);
    return () => window.removeEventListener("beforeunload", protect);
  }, [uncertain]);

  async function send(attempt: ReviewAttempt) {
    if (submitting.current) return;
    submitting.current = true;
    pending.current = attempt;
    setBusy(true);
    setError(null);
    setIssues([]);
    try {
      const response = await api.reviewCase(detail.id, attempt.action, attempt.body, attempt.key);
      if (!mounted.current) return;
      pending.current = null;
      setUncertain(false);
      setSuccess(response);
      onDecision(response);
    } catch (cause) {
      if (!mounted.current) return;
      const info = describeError(cause);
      setError(info);
      setIssues(info.fieldErrors);
      const unresolved = info.unknownOutcome || (uncertain && info.code === "CSRF_INVALID");
      setUncertain(unresolved);
      if (!unresolved) pending.current = null;
      if (["VERSION_CONFLICT", "INVALID_STATE", "IDEMPOTENCY_CONFLICT"].includes(info.code)) setMustRefresh(true);
      focusFirstError(form, info.fieldErrors);
    } finally {
      submitting.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  function decide(action: ReviewAction) {
    if (locked || submitting.current) return;
    const fieldErrors: FieldIssue[] = [];
    if (action !== "verify" && !reason.trim()) fieldErrors.push({ field: "reason", code: "REQUIRED" });
    if (reviewed.length !== detail.evidenceVersions.length || detail.evidenceVersions.some(item => !reviewed.includes(item.id))) fieldErrors.push({ field: "reviewedEvidenceVersionIds", code: "REVIEW_ALL_CURRENT_VERSIONS" });
    if (fieldErrors.length) { setIssues(fieldErrors); setError({ code: "VALIDATION_ERROR", message: "Check the highlighted review fields.", fieldErrors, unknownOutcome: false }); focusFirstError(form, fieldErrors); return; }
    void send({ action, key: crypto.randomUUID(), body: { expectedVersion: detail.version, reviewedEvidenceVersionIds: [...reviewed], ...(reason.trim() ? { reason: reason.trim() } : {}) } });
  }

  return <aside className="card stack review-panel" aria-label="Independent review">
    <div><p className="eyebrow">INDEPENDENT REVIEW</p><h2>A second pair of eyes.</h2><p className="muted">Review case revision <strong>{detail.version}</strong> and its evidence snapshot.</p></div>
    {success && <div className="notice success" role="status"><strong>Review recorded</strong><p>Business status: <StatusBadge status={success.caseStatus} /></p><p>Chain status: <StatusBadge status={success.blockchainStatus} /></p>{success.caseStatus === "VERIFIED" && <p>Approval is complete in the application. A pending chain record is not a confirmed transaction.</p>}</div>}
    {!roleAllowed && <p className="notice">An active VERIFIER membership is required for review.</p>}
    {isCreator && <p className="notice">You created this case. Another person must review it, even if you also have the VERIFIER role.</p>}
    {detail.status !== "SUBMITTED" && !success && <p className="notice">This case is {detail.status}. Review actions are available only for submitted cases.</p>}
    <form ref={form} onSubmit={event => event.preventDefault()} className="stack">
      <ErrorNotice error={error} />
      {mustRefresh && <button type="button" className="button secondary" onClick={onRefresh}>Refresh before reviewing again</button>}
      {uncertain && <div className="notice" role="status"><strong>The review outcome is unknown.</strong><p>The same action, snapshot, reason, and Idempotency-Key will be reused. Retry before leaving this page.</p><button className="button" type="button" disabled={busy} onClick={() => { if (pending.current) void send(pending.current); }}>{busy ? "Resolving review…" : "Retry same request"}</button></div>}
      <fieldset disabled={locked} className="form-fieldset stack">
        <legend className="field-legend">Confirm the reviewed evidence</legend>
        <div className="stack evidence-review">{detail.evidenceVersions.map((item, index) => <label className="checkbox" key={item.id}><input type="checkbox" name="reviewedEvidenceVersionIds" value={item.id} checked={reviewed.includes(item.id)} onChange={event => setReviewed(current => event.target.checked ? [...current, item.id] : current.filter(id => id !== item.id))} aria-describedby="error-reviewedEvidenceVersionIds" /><span><strong>Evidence {index + 1} · version {item.versionNo}</strong></span></label>)}</div>
        <FieldError name="reviewedEvidenceVersionIds" issues={issues} />
        <FieldError name="expectedVersion" issues={issues} />
        <label className="field"><span>Review reason</span><textarea name="reason" aria-label="Review reason" value={reason} onChange={event => setReason(event.target.value)} rows={4} maxLength={2000} placeholder="Required for rejection or a request for changes" aria-invalid={issues.some(issue => issue.field === "reason")} aria-describedby={issues.some(issue => issue.field === "reason") ? "error-reason" : "reason-help"} /><span id="reason-help" className="muted">Required for reject and request changes; optional for approval.</span><FieldError name="reason" issues={issues} /></label>
        <div className="stack review-actions"><button type="button" className="button" onClick={() => decide("verify")}>{busy ? "Submitting review…" : "Approve case"}</button><button type="button" className="button secondary" onClick={() => decide("request-changes")}>Request changes</button><button type="button" className="button danger" onClick={() => decide("reject")}>Reject case</button></div>
      </fieldset>
    </form>
    <p className="muted footnote">The API checks your identity, role, case revision, and evidence versions again when you submit.</p>
  </aside>;
}
