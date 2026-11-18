"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { describeError, ErrorNotice, StatusBadge, type ErrorInfo } from "@/components/cases/shared";
import { useSession } from "@/components/session-provider";
import { canConfirmDelivery, canManageCampaigns, canOpenCaseDispute } from "@/lib/api-client";
import type { CaseDetailResponse } from "@/lib/types";
import type { StaffCaseDetail } from "@/lib/goods";
import { formatUtc } from "@/lib/display";
import { useGoodsReviewWrite } from "./goods-review-write";
import { useI18n } from "@/components/i18n-provider";

export function DeliveryReviewPanel({ detail, revision, onSaved }: {
  detail: CaseDetailResponse; revision: number; onSaved: () => void;
}) {
  const { api, user } = useSession();
  const { t, status } = useI18n();
  const [staff, setStaff] = useState<StaffCaseDetail>();
  const [readError, setReadError] = useState<ErrorInfo | null>(null);
  const [reload, setReload] = useState(0);
  const [deliveryId, setDeliveryId] = useState("");
  const [reviewed, setReviewed] = useState(false);
  const [reason, setReason] = useState("");
  const refresh = () => { setStaff(undefined); setReload(value => value + 1); };
  const write = useGoodsReviewWrite(`/cases/${encodeURIComponent(detail.id)}`, () => {
    setDeliveryId("");
    setReviewed(false);
    setReason("");
    refresh();
    onSaved();
  }, refresh, detail.organizationId);

  useEffect(() => {
    let active = true;
    setStaff(undefined);
    setReadError(null);
    void api.getStaffCase(detail.id).then(value => {
      if (!active) return;
      if (value.id !== detail.id || value.organizationId !== detail.organizationId)
        throw { status: 404, code: "RESOURCE_NOT_FOUND" };
      setStaff(value);
    }).catch(cause => { if (active) setReadError(describeError(cause)); });
    return () => { active = false; };
  }, [api, detail.id, detail.organizationId, revision, reload]);

  const deliveries = staff?.deliveries;
  const disputes = staff?.disputes;
  const totals = staff?.goodsTotals;
  const campaign = staff?.reviewCampaign;
  const selected = deliveries?.find(row => row.id === deliveryId);
  const openCount = disputes?.filter(row => row.status === "OPEN").length;
  const selfRecorded = selected?.recorderId === user?.userId;
  const canConfirm = canConfirmDelivery(user, detail.organizationId) && selected && !selfRecorded &&
    selected.reviewStatus === "PENDING" && selected.evidenceVersionIds.length > 0 && openCount === 0;
  const canDispute = canOpenCaseDispute(user, detail.organizationId) && campaign;
  const reasonReady = reason.trim().length > 0 && !/[<>\x00]/.test(reason);

  return <section className="card stack" aria-label={t("Delivery review")}>
    <div className="toolbar"><h2>{t("Delivery review")}</h2>
      <button type="button" className="button secondary" disabled={write.disabled} onClick={refresh}>{t("Refresh delivery review")}</button>
    </div>
    {write.feedback}
    <ErrorNotice error={readError} />
    {!staff && !readError && <p role="status">{t("Loading delivery review…")}</p>}
    {staff && (!deliveries || !disputes || !totals || staff.reviewCampaign === undefined) &&
      <p>{t("Delivery review is unavailable on this API.")}</p>}
    {staff && deliveries && disputes && totals && staff.reviewCampaign !== undefined && <>
      {campaign && <p>{t("Campaign status")} <StatusBadge status={campaign.status} /></p>}
      <p>{t("Open disputes: {n}", { n: openCount ?? 0 })}</p>
      {!!totals.length && <div className="table-wrap"><table className="data-table">
        <caption className="sr-only">{t("Delivery totals")}</caption>
        <thead><tr><th scope="col">{t("Item")}</th><th scope="col">{t("Target")}</th><th scope="col">{t("Received")}</th><th scope="col">{t("Delivered")}</th><th scope="col">{t("Confirmed")}</th></tr></thead>
        <tbody>{totals.map(row => <tr key={row.campaignItemId}><th scope="row">{row.itemName} ({row.unit})</th><td>{row.targetQty}</td><td>{row.receivedQty}</td><td>{row.deliveredQty}</td><td>{row.confirmedQty}</td></tr>)}</tbody>
      </table></div>}
      {deliveries.length === 0 ? <p>{t("No deliveries recorded.")}</p> : <label>{t("Delivery")}
        <select aria-label={t("Delivery to review")} value={deliveryId} disabled={write.disabled} onChange={event => {
          setDeliveryId(event.target.value);
          setReviewed(false);
        }}>
          <option value="">{t("Select a delivery")}</option>
          {deliveries.filter(row => row.outcome === "DELIVERED").map((row, index) =>
            <option key={row.id} value={row.id}>{t("Delivery {n}", { n: index + 1 })} · {formatUtc(row.recordedAt)} · {status(row.reviewStatus)}</option>)}
        </select>
      </label>}
      {selected && <>
        <p>{t("Review status")} <StatusBadge status={selected.reviewStatus} /></p>
        <div className="table-wrap"><table className="data-table">
          <caption>{t("Selected delivery items")}</caption>
          <thead><tr><th scope="col">{t("Item")}</th><th scope="col">{t("Unit")}</th><th scope="col">{t("Quantity")}</th></tr></thead>
          <tbody>{selected.items.map(item => {
            const metadata = totals.find(total => total.campaignItemId === item.campaignItemId);
            return <tr key={item.campaignItemId}>
              <th scope="row">{metadata?.itemName ?? t("Unknown item")}</th>
              <td>{metadata?.unit ?? t("Unavailable")}</td><td>{item.quantity}</td>
            </tr>;
          })}</tbody>
        </table></div>
        <ul>{selected.evidenceVersionIds.map((id, index) => <li key={id}>
          <a href={`/api/evidence-versions/${encodeURIComponent(id)}/content`}>{t("Delivery evidence {n}", { n: index + 1 })}</a>
        </li>)}</ul>
        {selfRecorded && <p>{t("You recorded this delivery. Another account must confirm it.")}</p>}
      </>}
      {canConfirm && <label><input type="checkbox" checked={reviewed} disabled={write.disabled}
        onChange={event => setReviewed(event.target.checked)} /> {t("I reviewed the delivery evidence")}</label>}
      {(canConfirm || canDispute) && <label>{t("Dispute or review reason")}
        <textarea value={reason} maxLength={2000} disabled={write.disabled} onChange={event => setReason(event.target.value)} />
      </label>}
      <div className="actions">
        {canConfirm && <>
          <button className="button" type="button" disabled={!reviewed || write.disabled}
            onClick={() => write.start({ action: "confirm", id: selected.id, body: { decision: "CONFIRMED", reviewedEvidenceVersionIds: selected.evidenceVersionIds } })}>{t("Confirm delivery")}</button>
          <button className="button secondary" type="button" disabled={!reviewed || !reasonReady || write.disabled}
            onClick={() => write.start({ action: "confirm", id: selected.id, body: { decision: "REQUIRES_REVIEW", reason: reason.trim(), reviewedEvidenceVersionIds: selected.evidenceVersionIds } })}>{t("Request delivery review")}</button>
        </>}
        {canDispute && <button className="button secondary" type="button" disabled={!reasonReady || write.disabled}
          onClick={() => write.start({ action: "dispute", id: detail.id, body: { reason: reason.trim(), ...(selected ? { deliveryId: selected.id } : {}) } })}>{t("Open dispute")}</button>}
        {campaign && canManageCampaigns(user, detail.organizationId) && campaign.status !== "CLOSED" &&
          <button className="button secondary" type="button" disabled={write.disabled || campaign.status !== "FULFILLED" || openCount !== 0}
            onClick={() => write.start({ action: "close", id: campaign.id })}>{t("Close campaign")}</button>}
      </div>
      {!!disputes.length && <ul className="evidence-list">{disputes.map((dispute, index) => <li key={dispute.id}>
        <p><StatusBadge status={dispute.status} /> {dispute.reason}</p>
        {dispute.status === "OPEN" && <Link href={`/disputes/${encodeURIComponent(dispute.id)}`}>{t("Review dispute {n}", { n: index + 1 })}</Link>}
      </li>)}</ul>}
    </>}
  </section>;
}
