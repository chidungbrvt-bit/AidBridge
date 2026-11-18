"use client";

import { useEffect, useState } from "react";
import { describeError, ErrorNotice, type ErrorInfo } from "@/components/cases/shared";
import { useSession } from "@/components/session-provider";
import type { DisputeReview, ResolveDisputeRequest } from "@/lib/goods";
import { useGoodsReviewWrite } from "./goods-review-write";

export function DisputeReviewPanel({ id }: { id: string }) {
  const { api, mode, user } = useSession();
  const authorized = mode === "real" && user?.status === "ACTIVE" && user.platformRole === "PLATFORM_ADMIN";
  const [detail, setDetail] = useState<DisputeReview>();
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [reason, setReason] = useState("");
  const [reviewed, setReviewed] = useState<string[]>([]);
  const [resolved, setResolved] = useState(false);
  const write = useGoodsReviewWrite(`/disputes/${encodeURIComponent(id)}`, () => { setResolved(true); setDetail(undefined); });
  useEffect(() => {
    if (!authorized) return;
    let active = true;
    void api.getDisputeReview(id).then(value => { if (active) setDetail(value); })
      .catch(cause => { if (active) setError(describeError(cause)); });
    return () => { active = false; };
  }, [api, authorized, id]);
  function resolve(outcome: ResolveDisputeRequest["outcome"]) {
    write.start({ action: "resolve", id, body: { outcome, reason: reason.trim(), reviewEvidenceVersionIds: reviewed } });
  }
  if (!authorized) return <section className="card"><h1>Dispute review</h1><p>Open this link with an active platform administrator account.</p></section>;
  const disabled = write.disabled || !reason.trim() || /[<>\x00]/.test(reason) || detail?.status !== "OPEN";
  return <section className="card stack">
    <h1>Dispute review</h1>
    {write.feedback}
    <ErrorNotice error={error} />
    {resolved && <p>Dispute resolved. Refresh the case in the organization workspace to see the current totals.</p>}
    {!detail && !error && !resolved && <p role="status">Loading dispute…</p>}
    {detail && <>
      <p>{detail.reason}</p>
      {detail.delivery && <>
        <h2>Delivery snapshot</h2>
        <ul>{detail.delivery.items.map(item => <li key={item.campaignItemId}>{item.itemName}: {item.quantity} {item.unit}</li>)}</ul>
        {detail.delivery.evidenceVersionIds.map((versionId, index) => <div key={versionId}>
          <a href={`/api/disputes/${encodeURIComponent(id)}/review/evidence-versions/${encodeURIComponent(versionId)}/content`}>Download snapshot evidence {index + 1}</a>
          <label><input type="checkbox" disabled={write.disabled} checked={reviewed.includes(versionId)}
            onChange={event => setReviewed(event.target.checked ? [...reviewed, versionId] : reviewed.filter(value => value !== versionId))} /> Reviewed evidence {index + 1}</label>
        </div>)}
      </>}
      <label>Resolution reason<textarea value={reason} maxLength={2000} disabled={write.disabled} onChange={event => setReason(event.target.value)} /></label>
      <div className="actions">
        <button type="button" className="button" disabled={disabled} onClick={() => resolve("UPHOLD")}>Uphold dispute decision</button>
        {detail.delivery && <button type="button" className="button secondary" disabled={disabled} onClick={() => resolve("VOID_DELIVERY")}>Void delivery</button>}
      </div>
    </>}
  </section>;
}
