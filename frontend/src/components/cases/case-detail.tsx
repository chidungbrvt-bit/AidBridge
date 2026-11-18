"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useSession } from "@/components/session-provider";
import { canCreateCases } from "@/lib/api-client";
import type { CaseDetailResponse, DecisionResponse } from "@/lib/types";
import { useI18n } from "@/components/i18n-provider";
import { describeError, ErrorNotice, LoadingCard, StatusBadge, type ErrorInfo } from "./shared";
import { CampaignPanel } from "@/components/campaigns/campaign-panel";
import { ReviewPanel } from "./review-panel";
import { ReceiptPanel } from "@/components/goods/receipt-panel";
import { DeliveryPanel } from "@/components/goods/delivery-panel";
import { DeliveryReviewPanel } from "@/components/goods/delivery-review-panel";
import { AuditDisclosure } from "@/components/audit/audit-feed";

export function CaseDetail({ id }: { id: string }) {
  const { user, organizationId } = useSession();
  return <ScopedCaseDetail key={`${user?.userId}:${organizationId}:${id}`} id={id} />;
}

function ScopedCaseDetail({ id }: { id: string }) {
  const { api, user, organizationId, mode } = useSession();
  const { t, status } = useI18n();
  const [detail, setDetail] = useState<CaseDetailResponse | null>(null);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [caseRead, setCaseRead] = useState({ background: false });
  const [controlMessage, setControlMessage] = useState("");
  const [controlBusy, setControlBusy] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [campaignBusy, setCampaignBusy] = useState(false);
  const [receiptRevision, setReceiptRevision] = useState(0);
  const [auditRevision, setAuditRevision] = useState(0);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let cancelled = false;
    if (!caseRead.background) setLoading(true);
    setError(null);
    if (!caseRead.background) setDetail(null);
    api.getCase(id).then(result => {
      if (cancelled) return;
      if (result.organizationId !== organizationId) {
        setError({ code: "RESOURCE_NOT_FOUND", message: "This case is unavailable in the selected organization.", status: 404, fieldErrors: [], unknownOutcome: false });
        return;
      }
      setDetail(result);
    }).catch(reason => { if (!cancelled) setError(describeError(reason)); }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, id, organizationId, caseRead]);

  function applyDecision(decision: DecisionResponse) {
    setDetail(current => current ? {
      ...current, status: decision.caseStatus, version: decision.caseVersion, blockchainStatus: decision.blockchainStatus,
      reviewHistory: [...(current.reviewHistory ?? []), decision],
    } : current);
    setAuditRevision(value => value + 1);
  }

  async function mockAction(action: "stale-case" | "lose-next-response") {
    setControlBusy(true);
    setControlMessage("");
    try {
      await api.mockControl(action, action === "stale-case" ? id : undefined);
      if (mounted.current) setControlMessage(action === "stale-case" ? "The mock server revision changed. Submit the displayed revision to exercise VERSION_CONFLICT, then refresh." : "The next mutation will commit but return an unknown outcome. Use Retry same request to recover the original result.");
    } catch (reason) { if (mounted.current) setControlMessage(describeError(reason).message); }
    finally { if (mounted.current) setControlBusy(false); }
  }

  if (loading) return <LoadingCard label={t("Loading case details…")} />;
  if (error || !detail) return <div className="stack"><Link href="/cases" className="back-link">{t("← All cases")}</Link><ErrorNotice error={error} /><button type="button" className="button secondary" onClick={() => setCaseRead({ background: false })}>{t("Retry loading case")}</button></div>;
  const canEdit = canCreateCases(user, organizationId) && detail.creator.id === user?.userId && ["SUBMITTED", "CHANGES_REQUESTED"].includes(detail.status);
  const latestChanges = [...(detail.reviewHistory ?? [])].reverse().find(review => review.decision === "CHANGES_REQUESTED");

  return <div className="stack">
    <header className="page-header"><div><Link href="/cases" className="back-link">{t("← All cases")}</Link><p className="eyebrow">{t("CASE · REVISION {n}", { n: detail.version })}</p><h1>{detail.internalTitle}</h1><p className="muted">{detail.beneficiaryAlias} · {detail.approximateArea}</p></div><div className="actions">{canEdit && !reviewBusy && <Link className="button" href={`/cases/${detail.id}/edit`}>{t("Edit case")}</Link>}<button type="button" className="button secondary" disabled={reviewBusy || campaignBusy} onClick={() => setCaseRead({ background: false })}>{t("Refresh case")}</button></div></header>
    {detail.status === "CHANGES_REQUESTED" && <section className="notice"><strong>{t("Reviewer requested changes")}</strong><p>{latestChanges?.reason || t("Review feedback is not included in this API projection.")}</p>{canEdit && <p>{t("Open Edit case, save the requested revision, then explicitly resubmit for independent review.")}</p>}</section>}
    <div className="status-grid">
      <section className="card status-card"><span className="eyebrow">{t("BUSINESS STATUS")}</span><StatusBadge status={detail.status} /><p className="muted">{t("The current state of the aid request.")}</p></section>
      <section className="card status-card"><span className="eyebrow">{t("BLOCKCHAIN SYNC")}</span><StatusBadge status={detail.blockchainStatus} /><p className="muted">{detail.blockchainStatus === "UNKNOWN" ? t("This API response does not include blockchain synchronization status.") : detail.blockchainStatus === "PENDING_ON_CHAIN" ? t("The business decision is saved; blockchain confirmation is still pending.") : t("Blockchain synchronization is separate from the business decision.")}</p>{mode === "mock" && <p className="muted">{t("Simulated status. No Fuji transaction was created.")}</p>}</section>
    </div>
    <div className="review-layout">
      <div className="stack">
        <section className="card stack"><div className="toolbar"><div><p className="eyebrow">{t("ESSENTIAL GOODS")}</p><h2>{t("Requested support")}</h2></div><span className="badge">{t("{n} items", { n: detail.needs.length })}</span></div><div className="table-wrap"><table className="data-table"><caption className="sr-only">{t("Requested goods")}</caption><thead><tr><th scope="col">{t("Item")}</th><th scope="col">{t("Unit")}</th><th scope="col">{t("Quantity")}</th></tr></thead><tbody>{detail.needs.map(need => <tr key={need.id}><td>{need.name}</td><td>{need.unit}</td><td>{need.quantity.toLocaleString()}</td></tr>)}</tbody></table></div></section>
        <section className="card stack"><div><p className="eyebrow">{t("PRIVATE RECORD")}</p><h2>{t("Case context")}</h2></div><dl className="metadata"><div><dt>{t("Created by")}</dt><dd>{detail.creator.displayName}</dd></div><div><dt>{t("Priority")}</dt><dd>{status(detail.priority)}</dd></div><div><dt>{t("Created at")}</dt><dd><time dateTime={detail.createdAt}>{new Date(detail.createdAt).toLocaleString("en-GB", { timeZone: "UTC" })} UTC</time></dd></div><div><dt>{t("Consent basis")}</dt><dd>{status(detail.consent.basis)}</dd></div><div><dt>{t("Consent scope")}</dt><dd>{detail.consent.scope}</dd></div><div><dt>{t("Public summary permitted")}</dt><dd>{detail.consent.publicSummaryAllowed ? t("Yes") : t("No")}</dd></div></dl></section>
        <section className="card stack"><div><p className="eyebrow">{t("IMMUTABLE SNAPSHOT")}</p><h2>{t("Evidence versions")}</h2><p className="muted">{t("These evidence versions form the exact snapshot for the current review.")}</p></div>{detail.evidenceVersions.length === 0 ? <p className="muted">{t("No evidence versions are available. Review is unavailable.")}</p> : <ul className="evidence-list">{detail.evidenceVersions.map((evidence, index) => <li key={evidence.id}><div className="toolbar"><strong>{t("Evidence {n}", { n: index + 1 })}</strong><span className="badge">{t("Version {n}", { n: evidence.versionNo })}</span></div><p className="muted">{evidence.mimeType} · {t("{n} bytes", { n: evidence.byteSize.toLocaleString() })}</p><a href={`/api/evidence-versions/${encodeURIComponent(evidence.id)}/content`} className="back-link">{t("Download evidence {n}", { n: index + 1 })}</a></li>)}</ul>}</section>
      </div>
      <ReviewPanel detail={detail} onDecision={applyDecision} onRefresh={() => setCaseRead({ background: false })} onBusyChange={setReviewBusy} />
    </div>
    <CampaignPanel detail={detail} onBusyChange={setCampaignBusy} onPublished={() => setCaseRead({ background: false })} />
    <ReceiptPanel detail={detail} onRecorded={() => {
      setReceiptRevision(value => value + 1);
      setAuditRevision(value => value + 1);
    }} />
    <DeliveryPanel detail={detail} receiptRevision={receiptRevision} onRecorded={() => setAuditRevision(value => value + 1)} />
    {mode === "real" && ["IN_PROGRESS", "COMPLETED"].includes(detail.status) && <DeliveryReviewPanel detail={detail} revision={auditRevision} onSaved={() => {
      setCaseRead({ background: true });
      setAuditRevision(value => value + 1);
      setReceiptRevision(value => value + 1);
    }} />}
    {mode === "real" && <AuditDisclosure label={t("Case audit trail")} scope={{ caseId: detail.id }} revision={auditRevision} />}
    {mode === "mock" && <details className="card mock-controls"><summary>{t("Mock review test controls")}</summary><p className="muted">{t("These controls affect this local simulation only.")}</p><div className="actions"><button type="button" className="button secondary" disabled={controlBusy || reviewBusy || detail.status !== "SUBMITTED"} onClick={() => void mockAction("stale-case")}>{t("Simulate a stale revision")}</button><button type="button" className="button secondary" disabled={controlBusy || reviewBusy || detail.status !== "SUBMITTED"} onClick={() => void mockAction("lose-next-response")}>{t("Lose next mutation response")}</button></div>{controlMessage && <p role="status">{t(controlMessage)}</p>}</details>}
  </div>;
}
