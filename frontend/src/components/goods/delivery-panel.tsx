"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { describeError, ErrorNotice, StatusBadge, type ErrorInfo } from "@/components/cases/shared";
import { useSession } from "@/components/session-provider";
import { useI18n } from "@/components/i18n-provider";
import { isVolunteer } from "@/lib/api-client";
import type { DeliveryRequest, DeliveryResponse } from "@/lib/goods";
import type { PendingWrite } from "@/lib/session-work";
import type { CaseDetailResponse, EvidenceVersion } from "@/lib/types";

type AvailableItem = { id: string; name: string; unit: string; available: number };
type DeliveryContext = { items: AvailableItem[]; evidence: EvidenceVersion[] };

export function DeliveryPanel({ detail, receiptRevision, onRecorded }: {
  detail: CaseDetailResponse; receiptRevision: number; onRecorded: () => void;
}) {
  const { mode, user, organizationId } = useSession();
  if (mode !== "real" || !user || !isVolunteer(user, organizationId) ||
      detail.organizationId !== organizationId || detail.status !== "IN_PROGRESS") return null;

  return <StaffDelivery caseId={detail.id} organizationId={organizationId} actorId={user.userId}
    receiptRevision={receiptRevision} onRecorded={onRecorded} />;
}

function StaffDelivery({ caseId, organizationId, actorId, receiptRevision, onRecorded }: {
  caseId: string; organizationId: string; actorId: string; receiptRevision: number; onRecorded: () => void;
}) {
  const { api, work } = useSession();
  const { t } = useI18n();
  const scope = `${actorId}:${organizationId}:${caseId}`;
  const [context, setContext] = useState<DeliveryContext | null>(null);
  const [loading, setLoading] = useState(true);
  const [readError, setReadError] = useState<ErrorInfo | null>(null);
  const [writeError, setWriteError] = useState<ErrorInfo | null>(null);
  const [outcome, setOutcome] = useState<DeliveryRequest["outcome"]>("DELIVERED");
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [evidence, setEvidence] = useState<string[]>([]);
  const [handover, setHandover] = useState("");
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<DeliveryResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState(() => work.deliveries.get(scope));
  const readVersion = useRef(0);

  const refresh = useCallback(async () => {
    const version = ++readVersion.current;
    setLoading(true);
    setReadError(null);
    try {
      const staff = await api.getStaffCase(caseId);
      if (staff.id !== caseId || staff.organizationId !== organizationId) {
        throw { status: 404, code: "RESOURCE_NOT_FOUND" };
      }
      if (staff.status !== "IN_PROGRESS") throw { status: 409, code: "INVALID_STATE" };
      const campaign = staff.campaignId ? await api.getCampaign(staff.campaignId) : undefined;
      if (campaign && (campaign.status !== "PUBLISHED" || campaign.id !== staff.campaignId)) {
        throw { status: 409, code: "INVALID_STATE" };
      }
      const items = campaign?.items.map(item => ({
        id: item.id, name: item.name, unit: item.unit, available: Math.max(0, item.receivedQty - item.deliveredQty),
      })) ?? [];
      if (version !== readVersion.current) return;
      setContext({ items, evidence: staff.evidenceVersions });
      setEvidence(current => current.filter(id => staff.evidenceVersions.some(version => version.id === id)));
    } catch (cause) {
      if (version !== readVersion.current) return;
      setContext(null);
      setReadError(describeError(cause));
    } finally {
      if (version === readVersion.current) setLoading(false);
    }
  }, [api, caseId, organizationId]);

  useEffect(() => {
    void refresh();
    return () => { readVersion.current += 1; };
  }, [refresh, receiptRevision]);

  async function send(attempt: PendingWrite<DeliveryRequest>) {
    const previous = work.deliveries.get(scope);
    if (attempt.inFlight || previous?.inFlight || (previous && previous !== attempt)) return;
    attempt.inFlight = true;
    work.deliveries.set(scope, attempt);
    setPending(attempt);
    setBusy(true);
    setWriteError(null);
    try {
      if (previous) {
        const current = await api.me();
        if (current.userId !== actorId || !isVolunteer(current, organizationId)) {
          throw { status: 403, code: "FORBIDDEN" };
        }
        if (work.deliveries.get(scope) !== attempt) return;
      }
      const saved = await api.recordDelivery(caseId, attempt.body, attempt.key);
      if (work.deliveries.get(scope) !== attempt) return;
      if (saved.caseId !== caseId || saved.recorderId !== actorId || saved.outcome !== attempt.body.outcome) {
        throw { status: 502, code: "INVALID_RESPONSE" };
      }
      work.deliveries.delete(scope);
      setPending(undefined);
      setResult(saved);
      onRecorded();
      setQuantities({});
      setEvidence([]);
      setHandover("");
      setReason("");
      await refresh();
    } catch (cause) {
      if (work.deliveries.get(scope) !== attempt) return;
      const issue = describeError(cause);
      setWriteError(issue);
      if (!issue.unknownOutcome && issue.code !== "CSRF_INVALID") {
        work.deliveries.delete(scope);
        setPending(undefined);
        if (issue.status === 409) setQuantities({});
        if ([400, 404, 409].includes(issue.status ?? 0)) await refresh();
      }
    } finally {
      attempt.inFlight = false;
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!context || busy || loading || readError || work.deliveries.has(scope)) return;
    let body: DeliveryRequest;
    if (outcome === "FAILED") {
      const trimmed = reason.trim();
      if (!trimmed || trimmed.length > 2000 || /[<>\0]/.test(trimmed)) {
        setWriteError(describeError({ status: 400, code: "VALIDATION_ERROR" }));
        return;
      }
      body = { outcome, reason: trimmed, items: [], evidenceVersionIds: [] };
    } else {
      const selected = context.items.filter(item => quantities[item.id]);
      const evidenceVersionIds = context.evidence.filter(version => evidence.includes(version.id)).map(version => version.id);
      const time = handover ? Date.parse(handover + "Z") : undefined;
      if (!selected.length || !evidenceVersionIds.length || selected.some(item => {
        const amount = Number(quantities[item.id]);
        return !Number.isInteger(amount) || amount < 1 || amount > Math.min(item.available, 1_000_000);
      }) || (time !== undefined && !Number.isFinite(time))) {
        setWriteError({
          ...describeError({ status: 400, code: "VALIDATION_ERROR" }),
          message: "Select evidence and enter at least one whole quantity within the available stock.",
        });
        return;
      }
      body = {
        outcome, evidenceVersionIds,
        items: selected.map(item => ({ campaignItemId: item.id, quantity: Number(quantities[item.id]) })),
        ...(time !== undefined ? { handedOverAt: new Date(time).toISOString() } : {}),
      };
    }
    setResult(null);
    void send({ body, key: crypto.randomUUID() });
  }

  const canDeliver = context?.items.some(item => item.available > 0) && context.evidence.length > 0;
  return <section className="card stack" aria-label={t("Goods delivery")}>
    <h2>{t("Record goods delivery")}</h2>
    <button className="button secondary" type="button" disabled={busy || loading || !!pending} onClick={() => void refresh()}>{t("Refresh delivery stock")}</button>
    {result && <div className="notice success" role="status" data-delivery-id={result.id}>
      <strong>{result.outcome === "DELIVERED" ? t("Delivery recorded") : t("Failed attempt recorded")}</strong>
      <p>{t("Review:")} <StatusBadge status={result.reviewStatus} /></p>
      <p>{t("Blockchain sync:")} <StatusBadge status={result.blockchainStatus} /></p>
    </div>}
    <ErrorNotice error={readError} />
    <ErrorNotice error={writeError} />
    {loading && <p role="status">{t("Loading delivery context…")}</p>}
    {readError && <button className="button secondary" type="button" disabled={busy || loading} onClick={() => void refresh()}>{t("Reload delivery context")}</button>}
    {pending && <button className="button secondary" type="button" disabled={busy} onClick={() => void send(pending)}>{t("Retry same delivery")}</button>}
    {context && <form className="stack" onSubmit={submit}>
      <fieldset className="form-fieldset stack" disabled={busy || loading || !!pending}>
        <label className="field">{t("Delivery outcome")}
          <select value={outcome} onChange={event => { setOutcome(event.target.value as DeliveryRequest["outcome"]); setWriteError(null); }}>
            <option value="DELIVERED">{t("Delivered")}</option>
            <option value="FAILED">{t("Failed attempt")}</option>
          </select>
        </label>
        {outcome === "DELIVERED" ? <>
          {!canDeliver && <p>{t("Recording a handover requires available campaign stock and case evidence.")}</p>}
          {context.items.length > 0 && <div className="table-wrap"><table className="data-table goods-entry">
            <caption className="sr-only">{t("Goods to deliver")}</caption>
            <thead><tr><th scope="col">{t("Item")}</th><th scope="col">{t("Available")}</th><th scope="col">{t("Quantity delivered")}</th></tr></thead>
            <tbody>{context.items.map(item => <tr key={item.id}>
              <th scope="row">{item.name} <span className="muted">({item.unit})</span></th>
              <td><span className="sr-only">{t("Available:")}</span> {item.available}</td>
              <td><input aria-label={t("{item} delivery quantity ({unit})", { item: item.name, unit: item.unit })} type="number" min={1} max={Math.min(item.available, 1_000_000)}
                step={1} disabled={item.available === 0} value={quantities[item.id] ?? ""}
                onChange={event => setQuantities({ ...quantities, [item.id]: event.target.value })} /></td>
            </tr>)}</tbody>
          </table></div>}
          <label className="field">{t("Handed over at (UTC, optional)")}
            <input type="datetime-local" value={handover} onChange={event => setHandover(event.target.value)} />
          </label>
          {context.evidence.length > 0 && <fieldset>
            <legend>{t("Evidence versions attached to this case")}</legend>
            {context.evidence.map((version, index) => <label className="checkbox" key={version.id}>
              <input type="checkbox" checked={evidence.includes(version.id)} onChange={event => setEvidence(current =>
                event.target.checked ? [...current, version.id] : current.filter(id => id !== version.id))} />
              <span>{t("Evidence {n} · Version {v}", { n: index + 1, v: version.versionNo })}</span>
            </label>)}
          </fieldset>}
        </> : <label className="field">{t("Reason for failed attempt")}
          <textarea required maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} />
        </label>}
        <button className="button" type="submit" disabled={outcome === "DELIVERED" && !canDeliver}>{t("Record delivery")}</button>
      </fieldset>
    </form>}
  </section>;
}
