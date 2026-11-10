"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useSession } from "@/components/session-provider";
import type { CaseDetailResponse, DecisionResponse } from "@/lib/types";
import { describeError, ErrorNotice, LoadingCard, StatusBadge, type ErrorInfo } from "./shared";
import { ReviewPanel } from "./review-panel";

export function CaseDetail({ id }: { id: string }) {
  const { user, organizationId } = useSession();
  return <ScopedCaseDetail key={`${user?.userId}:${organizationId}:${id}`} id={id} />;
}

function ScopedCaseDetail({ id }: { id: string }) {
  const { api, organizationId, mode } = useSession();
  const [detail, setDetail] = useState<CaseDetailResponse | null>(null);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshCount, setRefreshCount] = useState(0);
  const [controlMessage, setControlMessage] = useState("");
  const [controlBusy, setControlBusy] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDetail(null);
    api.getCase(id).then(result => {
      if (cancelled) return;
      if (result.organizationId !== organizationId) {
        setError({ code: "RESOURCE_NOT_FOUND", message: "This case is unavailable in the selected organization.", status: 404, fieldErrors: [], unknownOutcome: false });
        return;
      }
      setDetail(result);
    }).catch(reason => { if (!cancelled) setError(describeError(reason)); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, id, organizationId, refreshCount]);

  const applyDecision = (decision: DecisionResponse) => setDetail(current => current ? {
    ...current, status: decision.caseStatus, version: decision.caseVersion, blockchainStatus: decision.blockchainStatus,
    reviewHistory: [...current.reviewHistory, decision],
  } : current);

  async function mockAction(action: "stale-case" | "lose-next-response") {
    setControlBusy(true);
    setControlMessage("");
    try {
      await api.mockControl(action, action === "stale-case" ? id : undefined);
      if (mounted.current) setControlMessage(action === "stale-case" ? "The mock server revision changed. Submit the displayed revision to exercise VERSION_CONFLICT, then refresh." : "The next mutation will commit but return an unknown outcome. Use Retry same request to recover the original result.");
    } catch (reason) { if (mounted.current) setControlMessage(describeError(reason).message); }
    finally { if (mounted.current) setControlBusy(false); }
  }

  if (loading) return <LoadingCard label="Loading case details…" />;
  if (error || !detail) return <div className="stack"><Link href="/cases" className="back-link">← All cases</Link><ErrorNotice error={error} /><button type="button" className="button secondary" onClick={() => setRefreshCount(value => value + 1)}>Retry loading case</button></div>;

  return <div className="stack">
    <header className="page-header"><div><Link href="/cases" className="back-link">← All cases</Link><p className="eyebrow">CASE · REVISION {detail.version}</p><h1>{detail.internalTitle}</h1><p className="muted">{detail.beneficiaryAlias} · {detail.approximateArea}</p></div><button type="button" className="button secondary" disabled={reviewBusy} onClick={() => setRefreshCount(value => value + 1)}>Refresh case</button></header>
    <div className="status-grid">
      <section className="card status-card"><span className="eyebrow">BUSINESS STATUS</span><StatusBadge status={detail.status} /><p className="muted">The current state of the aid request.</p></section>
      <section className="card status-card"><span className="eyebrow">BLOCKCHAIN SYNC</span><StatusBadge status={detail.blockchainStatus} /><p className="muted">{detail.blockchainStatus === "PENDING_ON_CHAIN" ? "Approved in the application; the chain record is still pending." : detail.blockchainStatus === "NOT_REQUIRED" ? "No chain milestone is required for this state." : detail.blockchainStatus === "CONFIRMED" ? "The chain projection reports confirmation." : "The chain operation needs attention."}{mode === "mock" ? " Simulated status." : ""}</p></section>
    </div>
    <div className="review-layout">
      <div className="stack">
        <section className="card stack"><div className="toolbar"><div><p className="eyebrow">ESSENTIAL GOODS</p><h2>Requested support</h2></div><span className="badge">{detail.needs.length} items</span></div><div className="table-wrap"><table className="data-table"><caption className="sr-only">Requested goods</caption><thead><tr><th scope="col">Item</th><th scope="col">Unit</th><th scope="col">Quantity</th></tr></thead><tbody>{detail.needs.map(need => <tr key={need.id}><td>{need.name}</td><td>{need.unit}</td><td>{need.quantity.toLocaleString()}</td></tr>)}</tbody></table></div></section>
        <section className="card stack"><div><p className="eyebrow">PRIVATE RECORD</p><h2>Case context</h2></div><dl className="metadata"><div><dt>Created by</dt><dd>{detail.creator.displayName}</dd></div><div><dt>Priority</dt><dd>{detail.priority}</dd></div><div><dt>Created at</dt><dd><time dateTime={detail.createdAt}>{new Date(detail.createdAt).toLocaleString("en-GB", { timeZone: "UTC" })} UTC</time></dd></div><div><dt>Consent basis</dt><dd>{detail.consent.basis}</dd></div><div><dt>Consent scope</dt><dd>{detail.consent.scope}</dd></div><div><dt>Public summary permitted</dt><dd>{detail.consent.publicSummaryAllowed ? "Yes" : "No"}</dd></div><div><dt>Case ID</dt><dd className="mono">{detail.id}</dd></div></dl></section>
        <section className="card stack"><div><p className="eyebrow">IMMUTABLE SNAPSHOT</p><h2>Evidence versions</h2><p className="muted">These version IDs form the exact snapshot for the current review.</p></div>{detail.evidenceVersions.length === 0 ? <p className="muted">No evidence versions are available. Review is unavailable.</p> : <ul className="evidence-list">{detail.evidenceVersions.map((evidence, index) => <li key={evidence.id}><div className="toolbar"><strong>Evidence {index + 1}</strong><span className="badge">Version {evidence.versionNo}</span></div><p className="mono">{evidence.id}</p><p className="muted">{evidence.mimeType} · {evidence.byteSize.toLocaleString()} bytes</p></li>)}</ul>}</section>
      </div>
      <ReviewPanel detail={detail} onDecision={applyDecision} onRefresh={() => setRefreshCount(value => value + 1)} onBusyChange={setReviewBusy} />
    </div>
    {mode === "mock" && <details className="card mock-controls"><summary>Mock review test controls</summary><p className="muted">These controls affect this local simulation only.</p><div className="actions"><button type="button" className="button secondary" disabled={controlBusy || reviewBusy || detail.status !== "SUBMITTED"} onClick={() => void mockAction("stale-case")}>Simulate a stale revision</button><button type="button" className="button secondary" disabled={controlBusy || reviewBusy || detail.status !== "SUBMITTED"} onClick={() => void mockAction("lose-next-response")}>Lose next mutation response</button></div>{controlMessage && <p role="status">{controlMessage}</p>}</details>}
  </div>;
}
