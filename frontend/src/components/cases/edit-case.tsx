"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useSession } from "@/components/session-provider";
import { canCreateCases } from "@/lib/api-client";
import type { CaseDetailResponse, NeedInput, PatchCaseRequest } from "@/lib/types";
import { useI18n } from "@/components/i18n-provider";
import { describeError, ErrorNotice, FieldError, focusFirstError, LoadingCard, StatusBadge, type ErrorInfo, type FieldIssue } from "./shared";

type NeedRow = NeedInput & { rowKey: string };
type Attempt = { action: "save"; key: string; body: PatchCaseRequest } | { action: "resubmit"; key: string; body: { expectedVersion: number } };

export function EditCase({ id }: { id: string }) {
  const { user, organizationId } = useSession();
  return <ScopedEditCase key={`${user?.userId}:${organizationId}:${id}`} id={id} />;
}

function ScopedEditCase({ id }: { id: string }) {
  const { api, organizationId } = useSession();
  const { t } = useI18n();
  const [detail, setDetail] = useState<CaseDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setDetail(null); setError(null);
    api.getCase(id).then(result => {
      if (!active) return;
      if (result.organizationId !== organizationId) {
        setError({ code: "RESOURCE_NOT_FOUND", status: 404, message: "This case is unavailable in the selected organization.", fieldErrors: [], unknownOutcome: false });
      } else setDetail(result);
    }).catch(cause => { if (active) setError(describeError(cause)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [api, id, organizationId, refresh]);
  if (loading) return <LoadingCard label={t("Loading editable case…")} />;
  if (error || !detail) return <div className="stack"><Link href={`/cases/${id}`} className="back-link">{t("← Case details")}</Link><ErrorNotice error={error} /><button className="button secondary" onClick={() => setRefresh(value => value + 1)}>{t("Retry loading case")}</button></div>;
  return <CaseRevisionForm key={`${id}:${refresh}`} initial={detail} onRefresh={() => setRefresh(value => value + 1)} />;
}

function CaseRevisionForm({ initial, onRefresh }: { initial: CaseDetailResponse; onRefresh: () => void }) {
  const { api, user, setNavigationLocked } = useSession();
  const { t } = useI18n();
  const router = useRouter();
  const [detail, setDetail] = useState(initial);
  const [title, setTitle] = useState(initial.internalTitle);
  const [area, setArea] = useState(initial.approximateArea);
  const [priority, setPriority] = useState(initial.priority);
  const [publicTitle, setPublicTitle] = useState(initial.publicTitle ?? "");
  const [publicSummary, setPublicSummary] = useState(initial.publicSummary ?? "");
  const [needs, setNeeds] = useState<NeedRow[]>(initial.needs.map(need => ({ ...need, rowKey: need.id })));
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [issues, setIssues] = useState<FieldIssue[]>([]);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [mustRefresh, setMustRefresh] = useState(false);
  const [saved, setSaved] = useState(false);
  const [changed, setChanged] = useState(false);
  const mounted = useRef(true);
  const inFlight = useRef(false);
  const pending = useRef<Attempt | null>(null);
  const form = useRef<HTMLFormElement>(null);
  const allowed = user?.userId === detail.creator.id && canCreateCases(user, detail.organizationId) && ["SUBMITTED", "CHANGES_REQUESTED"].includes(detail.status);
  const latestChange = [...(detail.reviewHistory ?? [])].reverse().find(review => review.decision === "CHANGES_REQUESTED");
  const historyUnavailable = detail.reviewHistoryAvailable === false;
  const revisedAfterRequest = saved || historyUnavailable || !!(latestChange && detail.version > latestChange.caseVersion);
  const locked = busy || uncertain || mustRefresh || !allowed;

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { setNavigationLocked(busy || uncertain); return () => setNavigationLocked(false); }, [busy, uncertain, setNavigationLocked]);
  useEffect(() => {
    if (!uncertain) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [uncertain]);
  const dirty = () => { setChanged(true); setSaved(false); };
  const updateNeed = (index: number, patch: Partial<NeedInput>) => { dirty(); setNeeds(current => current.map((need, i) => i === index ? { ...need, ...patch } : need)); };
  const invalid = (name: string) => issues.some(issue => issue.field === name);
  const description = (name: string) => invalid(name) ? `error-${name}` : undefined;

  async function send(attempt: Attempt) {
    if (inFlight.current) return;
    inFlight.current = true; pending.current = attempt;
    setBusy(true); setError(null); setIssues([]);
    try {
      const result = attempt.action === "save" ? await api.patchCase(detail.id, attempt.body, attempt.key) : await api.resubmitCase(detail.id, attempt.body, attempt.key);
      if (!mounted.current) return;
      pending.current = null; setUncertain(false); setDetail(result); setChanged(false);
      if (attempt.action === "save") {
        setSaved(true); setTitle(result.internalTitle); setArea(result.approximateArea); setPriority(result.priority);
        setPublicTitle(result.publicTitle ?? ""); setPublicSummary(result.publicSummary ?? "");
        setNeeds(result.needs.map(need => ({ ...need, rowKey: need.id })));
      } else router.push(`/cases/${result.id}`);
    } catch (cause) {
      if (!mounted.current) return;
      const info = describeError(cause); setError(info); setIssues(info.fieldErrors);
      const unresolved = info.unknownOutcome || (uncertain && info.code === "CSRF_INVALID");
      setUncertain(unresolved);
      if (!unresolved) pending.current = null;
      if (["VERSION_CONFLICT", "INVALID_STATE", "IDEMPOTENCY_CONFLICT"].includes(info.code)) setMustRefresh(true);
      focusFirstError(form, info.fieldErrors);
    } finally { inFlight.current = false; if (mounted.current) setBusy(false); }
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked || inFlight.current || !changed) return;
    const body: PatchCaseRequest = { expectedVersion: detail.version, internalTitle: title.trim(), approximateArea: area.trim(), priority,
      publicTitle: publicTitle.trim(), publicSummary: publicSummary.trim(),
      needs: needs.map(({ name, unit, quantity }) => ({ name: name.trim(), unit: unit.trim(), quantity })) };
    void send({ action: "save", body, key: crypto.randomUUID() });
  }

  if (!allowed) return <section className="card"><h1>{t("Editing unavailable")}</h1><p className="muted">{t("Only the creator with an active VOLUNTEER membership can edit a submitted case or a case with requested changes.")}</p><Link className="button secondary" href={`/cases/${detail.id}`}>{t("Back to case")}</Link></section>;

  return <div className="stack">
    <header className="page-header"><div>{!uncertain && <Link className="back-link" href={`/cases/${detail.id}`}>{t("← Case details")}</Link>}<p className="eyebrow">{t("CASE · REVISION {n}", { n: detail.version })}</p><h1>{t("Edit case")}</h1><p className="muted">{detail.beneficiaryAlias} · {t("Save a new revision before requesting another independent review.")}</p></div><StatusBadge status={detail.status} /></header>
    {detail.status === "CHANGES_REQUESTED" && <section className="notice"><strong>{t("Changes requested")}</strong><p>{latestChange?.reason || t("Review feedback is not included in this API projection. Check the reviewer’s feedback before revising the case.")}</p>{historyUnavailable && <p>{t("This API does not expose review history. If a revision was already saved, you may resubmit it; the server checks whether the required changes have been saved.")}</p>}</section>}
    <form className="stack" ref={form} onSubmit={save}>
      <ErrorNotice error={error} />
      {mustRefresh && <button type="button" className="button secondary" onClick={onRefresh}>{t("Refresh before editing again")}</button>}
      {uncertain && <div className="notice" role="status"><strong>{t("The outcome is unknown.")}</strong><p>{t("The exact action, body, revision, and Idempotency-Key are retained in memory. Retry before leaving this page.")}</p><button type="button" className="button" disabled={busy} onClick={() => { if (pending.current) void send(pending.current); }}>{t("Retry same request")}</button></div>}
      {saved && !uncertain && <p className="notice" role="status">{t("Revision saved.")} {detail.status === "CHANGES_REQUESTED" ? t("Use Resubmit for review when you are ready. Saving alone does not resubmit the case.") : t("The case remains submitted for independent review.")}</p>}
      <fieldset className="form-fieldset stack" disabled={locked}>
        <section className="card stack"><h2>{t("Case information")}</h2><p className="muted">{t("The organization, creator, beneficiary alias, consent, and evidence snapshot stay unchanged by this form.")}</p>
          <div className="grid-2"><label className="field"><span>{t("Internal title")}</span><input name="internalTitle" aria-label={t("Internal title")} value={title} onChange={event => { dirty(); setTitle(event.target.value); }} required maxLength={160} aria-invalid={invalid("internalTitle")} aria-describedby={description("internalTitle")} /><FieldError name="internalTitle" issues={issues} /></label>
          <label className="field"><span>{t("Approximate area")}</span><input name="approximateArea" aria-label={t("Approximate area")} value={area} onChange={event => { dirty(); setArea(event.target.value); }} required maxLength={160} aria-invalid={invalid("approximateArea")} aria-describedby={description("approximateArea")} /><FieldError name="approximateArea" issues={issues} /></label></div>
          <label className="field"><span>{t("Priority")}</span><select name="priority" value={priority} onChange={event => { dirty(); setPriority(event.target.value as "NORMAL" | "URGENT"); }}><option value="NORMAL">{t("Normal")}</option><option value="URGENT">{t("Urgent")}</option></select><FieldError name="priority" issues={issues} /></label>
          {detail.consent.publicSummaryAllowed && <><label className="field"><span>{t("Public title (optional)")}</span><input name="publicTitle" aria-label={t("Public title")} value={publicTitle} onChange={event => { dirty(); setPublicTitle(event.target.value); }} maxLength={160} /><FieldError name="publicTitle" issues={issues} /></label><label className="field"><span>{t("Public summary (optional)")}</span><textarea name="publicSummary" aria-label={t("Public summary")} value={publicSummary} onChange={event => { dirty(); setPublicSummary(event.target.value); }} maxLength={2000} /><FieldError name="publicSummary" issues={issues} /></label></>}
        </section>
        <section className="card stack"><div className="toolbar"><h2>{t("Requested goods")}</h2><button type="button" className="button secondary" disabled={needs.length >= 20} onClick={() => { dirty(); setNeeds(current => [...current, { rowKey: crypto.randomUUID(), name: "", unit: "", quantity: 1 }]); }}>{t("+ Add item")}</button></div><FieldError name="needs" issues={issues} />
          {needs.map((need, index) => <div className="need-row" key={need.rowKey}><span className="item-number">{index + 1}</span>
            <label className="field"><span>{t('Item {n} name', { n: index + 1 })}</span><input name={`needs[${index}].name`} aria-label={t('Item {n} name', { n: index + 1 })} value={need.name} onChange={event => updateNeed(index, { name: event.target.value })} required maxLength={120} aria-invalid={invalid(`needs[${index}].name`)} aria-describedby={description(`needs[${index}].name`)} /><FieldError name={`needs[${index}].name`} issues={issues} /></label>
            <label className="field"><span>{t("Unit")}</span><input name={`needs[${index}].unit`} aria-label={t('Item {n} unit', { n: index + 1 })} value={need.unit} onChange={event => updateNeed(index, { unit: event.target.value })} required maxLength={40} aria-invalid={invalid(`needs[${index}].unit`)} aria-describedby={description(`needs[${index}].unit`)} /><FieldError name={`needs[${index}].unit`} issues={issues} /></label>
            <label className="field"><span>{t("Quantity")}</span><input type="number" name={`needs[${index}].quantity`} aria-label={t('Item {n} quantity', { n: index + 1 })} value={Number.isNaN(need.quantity) ? "" : need.quantity} onChange={event => updateNeed(index, { quantity: event.target.valueAsNumber })} required min={1} max={1000000} step={1} aria-invalid={invalid(`needs[${index}].quantity`)} aria-describedby={description(`needs[${index}].quantity`)} /><FieldError name={`needs[${index}].quantity`} issues={issues} /></label>
            <button className="button secondary remove-item" type="button" aria-label={t('Remove item {n}', { n: index + 1 })} disabled={needs.length <= 1} onClick={() => { dirty(); setNeeds(current => current.filter((_, i) => i !== index)); setIssues([]); }}>{t("Remove")}</button>
          </div>)}
        </section>
        <div className="form-footer actions"><button className="button" type="submit" disabled={!changed}>{busy ? t("Saving…") : t("Save revision")}</button>
          {detail.status === "CHANGES_REQUESTED" && <button type="button" className="button secondary" disabled={!revisedAfterRequest || changed} onClick={() => { if (!locked && !changed && revisedAfterRequest) void send({ action: "resubmit", key: crypto.randomUUID(), body: { expectedVersion: detail.version } }); }}>{t("Resubmit for review")}</button>}
        </div>
      </fieldset>
      {detail.status === "CHANGES_REQUESTED" && !revisedAfterRequest && <p className="muted">{t("Update the requested information and save a revision to enable resubmission. A separate verifier must review it again.")}</p>}
    </form>
  </div>;
}
