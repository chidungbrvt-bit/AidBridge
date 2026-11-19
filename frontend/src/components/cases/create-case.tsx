"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useSession } from "@/components/session-provider";
import { ApiError, canCreateCases } from "@/lib/api-client";
import type { CreateCaseRequest, EvidenceFixture, NeedInput } from "@/lib/types";
import type { PendingWrite } from "@/lib/session-work";
import { useI18n } from "@/components/i18n-provider";
import { describeError, ErrorNotice, FieldError, focusFirstError, LoadingCard, type ErrorInfo, type FieldIssue } from "./shared";

type NeedRow = NeedInput & { rowKey: string };
type Attempt = PendingWrite<CreateCaseRequest>;

export function CreateCase() {
  const { user, organizationId } = useSession();
  return <ScopedCreateCase key={`${user?.userId}:${organizationId}`} />;
}

function ScopedCreateCase() {
  const { api, user, organizationId, setNavigationLocked, work } = useSession();
  const { t } = useI18n();
  const actorId = user?.userId;
  const attemptScope = `${actorId}:${organizationId}`;
  const [initialAttempt] = useState(() => work.caseCreates.get(attemptScope));
  const restored = initialAttempt?.body;
  const router = useRouter();
  const form = useRef<HTMLFormElement>(null);
  const mounted = useRef(true);

  const [fixtures, setFixtures] = useState<EvidenceFixture[]>([]);
  const [fixtureLoading, setFixtureLoading] = useState(true);
  const [fixtureError, setFixtureError] = useState<ErrorInfo | null>(null);
  const [fixtureRefresh, setFixtureRefresh] = useState(0);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [issues, setIssues] = useState<FieldIssue[]>([]);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(!!initialAttempt);
  const [beneficiaryAlias, setAlias] = useState(restored?.beneficiaryAlias ?? "");
  const [internalTitle, setTitle] = useState(restored?.internalTitle ?? "");
  const [approximateArea, setArea] = useState(restored?.approximateArea ?? "");
  const [priority, setPriority] = useState<"NORMAL" | "URGENT">(restored?.priority ?? "NORMAL");
  const [basis, setBasis] = useState<"CONSENT" | "REPRESENTATIVE">("CONSENT");
  const [scope, setScope] = useState("Use fictional household data to coordinate essential goods.");
  const [capturedAt, setCapturedAt] = useState(() => new Date().toISOString().slice(0, 16));
  const [consentConfirmed, setConsentConfirmed] = useState(false);
  const [publicSummaryAllowed, setPublicSummaryAllowed] = useState(false);
  const [publicTitle, setPublicTitle] = useState("");
  const [publicSummary, setPublicSummary] = useState("");
  const [evidenceKeys, setEvidenceKeys] = useState<string[]>([]);
  const [needs, setNeeds] = useState<NeedRow[]>(restored?.needs.map((need, index) => ({ ...need, rowKey: String(index) })) ?? [
    { rowKey: "initial-food", name: "Rice", unit: "bag", quantity: 2 },
    { rowKey: "initial-hygiene", name: "Hygiene kit", unit: "kit", quantity: 2 },
  ]);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { setNavigationLocked(busy || uncertain); return () => setNavigationLocked(false); }, [busy, uncertain, setNavigationLocked]);
  useEffect(() => {
    let cancelled = false;
    setFixtureLoading(true);
    setFixtureError(null);
    api.evidenceFixtures(organizationId)
      .then(data => { if (!cancelled) { setFixtures(data); setEvidenceKeys(keys => keys.filter(key => data.some(item => item.key === key))); } })
      .catch(reason => { if (!cancelled) setFixtureError(describeError(reason)); })
      .finally(() => { if (!cancelled) setFixtureLoading(false); });
    return () => { cancelled = true; };
  }, [api, organizationId, fixtureRefresh]);
  useEffect(() => {
    if (!uncertain) return;
    const protect = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener("beforeunload", protect);
    return () => window.removeEventListener("beforeunload", protect);
  }, [uncertain]);

  const updateNeed = (index: number, patch: Partial<NeedInput>) => setNeeds(current => current.map((need, position) => position === index ? { ...need, ...patch } : need));
  const hasIssue = (name: string) => issues.some(issue => issue.field === name);
  const describe = (name: string) => hasIssue(name) ? `error-${name}` : undefined;

  async function send(attempt: Attempt) {
    if (work.caseCreates.get(attemptScope)?.inFlight) return;
    const retry = work.caseCreates.get(attemptScope) === attempt;
    attempt.inFlight = true;
    work.caseCreates.set(attemptScope, attempt);
    setBusy(true);
    setError(null);
    setIssues([]);
    try {
      if (retry) {
        const current = await api.me();
        if (current.userId !== actorId || !canCreateCases(current, attempt.body.organizationId)) {
          throw new ApiError({ type: "urn:aidbridge:error:forbidden", title: "Case creation permission changed", code: "FORBIDDEN", status: 403 });
        }
      }
      const submitOnce = () => api.createCase(attempt.body, attempt.key);
      const result = await submitOnce().catch(reason => {
        // The backend rejected the token before the controller. Retry once with the original key and body.
        if (reason instanceof ApiError && reason.code === "CSRF_INVALID") return submitOnce();
        throw reason;
      });
      if (!mounted.current || work.caseCreates.get(attemptScope) !== attempt) return;
      work.caseCreates.delete(attemptScope);
      setUncertain(false);
      router.push(`/cases/${result.id}`);
    } catch (reason) {
      if (!mounted.current) return;
      const info = describeError(reason);
      setError(info);
      setIssues(info.fieldErrors);
      const unresolved = info.unknownOutcome || info.code === "CSRF_INVALID" || retry;
      setUncertain(unresolved);
      if (!unresolved && work.caseCreates.get(attemptScope) === attempt) work.caseCreates.delete(attemptScope);
      focusFirstError(form, info.fieldErrors);
    } finally {
      attempt.inFlight = false;
      if (mounted.current) setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (uncertain || work.caseCreates.has(attemptScope)) return;
    const localIssues: FieldIssue[] = [];
    if (!consentConfirmed) localIssues.push({ field: "consentConfirmed", code: "CONFIRM_CONSENT" });
    if (evidenceKeys.length < 1) localIssues.push({ field: "evidenceFixtureKeys", code: "SELECT_EVIDENCE" });
    if (evidenceKeys.length > 10) localIssues.push({ field: "evidenceFixtureKeys", code: "MAXIMUM_10_FIXTURES" });
    const timestamp = new Date(`${capturedAt}:00Z`);
    if (!Number.isFinite(timestamp.getTime())) localIssues.push({ field: "consent.capturedAt", code: "INVALID_DATE" });
    if (localIssues.length) { setIssues(localIssues); setError({ code: "VALIDATION_ERROR", message: "Check the highlighted fields and try again.", fieldErrors: localIssues, unknownOutcome: false }); focusFirstError(form, localIssues); return; }
    const body: CreateCaseRequest = {
      organizationId, beneficiaryAlias: beneficiaryAlias.trim(), approximateArea: approximateArea.trim(), internalTitle: internalTitle.trim(), priority,
      consent: { basis, scope: scope.trim(), capturedAt: timestamp.toISOString(), publicSummaryAllowed },
      needs: needs.map(({ name, unit, quantity }) => ({ name: name.trim(), unit: unit.trim(), quantity })),
      evidenceFixtureKeys: [...evidenceKeys],
      ...(publicSummaryAllowed && publicTitle.trim() ? { publicTitle: publicTitle.trim() } : {}),
      ...(publicSummaryAllowed && publicSummary.trim() ? { publicSummary: publicSummary.trim() } : {}),
    };
    void send({ key: crypto.randomUUID(), body });
  }

  if (!canCreateCases(user, organizationId)) return <div className="card"><h1>{t("Creation unavailable")}</h1><p className="muted">{t("An active VOLUNTEER membership is required to create a case.")}</p><Link href="/cases" className="button secondary">{t("Back to cases")}</Link></div>;

  return <div className="stack">
    <header className="page-header"><div><Link href="/cases" className="back-link">{t("← All cases")}</Link><p className="eyebrow">{t("NEW AID REQUEST")}</p><h1>{t("Create a case")}</h1><p className="muted">{t("Start with a fictional household, its needs, and the evidence to review.")}</p></div><span className="badge">{t("Private organization record")}</span></header>
    <form ref={form} onSubmit={submit} className="stack">
      <ErrorNotice error={error} />
      {uncertain && <div className="notice" role="status"><strong>{t("The outcome is unknown.")}</strong><p>{t("Your exact request and Idempotency-Key are kept in memory. Retry safely before leaving this page; the form stays locked to prevent a different submission.")}</p><button type="button" className="button" disabled={busy} onClick={() => { const saved = work.caseCreates.get(attemptScope); if (saved) void send(saved); }}>{busy ? t("Resolving request…") : t("Retry same request")}</button></div>}
      <fieldset disabled={busy || uncertain} className="form-fieldset stack">
        <section className="card stack"><div><p className="eyebrow">{t("01 · HOUSEHOLD")}</p><h2>{t("The request")}</h2><p className="muted">{t("Use aliases only. Do not enter real names, contact details, or precise addresses.")}</p></div>
          <div className="grid-2"><label className="field"><span>{t("Fictional beneficiary alias")}</span><input name="beneficiaryAlias" value={beneficiaryAlias} onChange={event => setAlias(event.target.value)} placeholder="Demo household 18" required maxLength={120} aria-invalid={hasIssue("beneficiaryAlias")} aria-describedby={describe("beneficiaryAlias")} /><FieldError name="beneficiaryAlias" issues={issues} /></label>
          <label className="field"><span>{t("Approximate area")}</span><input name="approximateArea" value={approximateArea} onChange={event => setArea(event.target.value)} placeholder="Sample North District" required maxLength={160} aria-invalid={hasIssue("approximateArea")} aria-describedby={describe("approximateArea")} /><FieldError name="approximateArea" issues={issues} /></label></div>
          <div className="field-grid"><label className="field"><span>{t("Internal title")}</span><input name="internalTitle" value={internalTitle} onChange={event => setTitle(event.target.value)} placeholder="Essential goods for a demo household" required maxLength={160} aria-invalid={hasIssue("internalTitle")} aria-describedby={describe("internalTitle")} /><FieldError name="internalTitle" issues={issues} /></label>
          <label className="field"><span>{t("Priority")}</span><select name="priority" value={priority} onChange={event => setPriority(event.target.value as "NORMAL" | "URGENT")}><option value="NORMAL">{t("Normal")}</option><option value="URGENT">{t("Urgent")}</option></select><FieldError name="priority" issues={issues} /></label></div>
        </section>

        <section className="card stack"><div className="toolbar"><div><p className="eyebrow">{t("02 · ESSENTIAL GOODS")}</p><h2>{t("What is needed?")}</h2><p className="muted">{t("Use fixed units and whole quantities. Every item is tracked separately.")}</p></div><button type="button" className="button secondary" disabled={needs.length >= 20} onClick={() => setNeeds(current => [...current, { rowKey: crypto.randomUUID(), name: "", unit: "", quantity: 1 }])}>{t("+ Add item")}</button></div>
          <FieldError name="needs" issues={issues} />
          {needs.map((need, index) => <div className="need-row" key={need.rowKey}><span className="item-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
            <label className="field"><span>{t('Item {n} name', { n: index + 1 })}</span><input name={`needs[${index}].name`} aria-label={t('Item {n} name', { n: index + 1 })} value={need.name} onChange={event => updateNeed(index, { name: event.target.value })} required maxLength={120} aria-invalid={hasIssue(`needs[${index}].name`)} aria-describedby={describe(`needs[${index}].name`)} /><FieldError name={`needs[${index}].name`} issues={issues} /></label>
            <label className="field"><span>{t("Unit")}</span><input name={`needs[${index}].unit`} value={need.unit} onChange={event => updateNeed(index, { unit: event.target.value })} required maxLength={40} aria-label={t('Item {n} unit', { n: index + 1 })} aria-invalid={hasIssue(`needs[${index}].unit`)} aria-describedby={describe(`needs[${index}].unit`)} /><FieldError name={`needs[${index}].unit`} issues={issues} /></label>
            <label className="field"><span>{t("Quantity")}</span><input type="number" name={`needs[${index}].quantity`} value={Number.isNaN(need.quantity) ? "" : need.quantity} onChange={event => updateNeed(index, { quantity: event.target.valueAsNumber })} required min={1} max={1000000} step={1} aria-label={t('Item {n} quantity', { n: index + 1 })} aria-invalid={hasIssue(`needs[${index}].quantity`)} aria-describedby={describe(`needs[${index}].quantity`)} /><FieldError name={`needs[${index}].quantity`} issues={issues} /></label>
            <button type="button" className="button secondary remove-item" disabled={needs.length === 1} aria-label={t('Remove item {n}', { n: index + 1 })} onClick={() => { setNeeds(current => current.filter((_, position) => position !== index)); setIssues([]); }}>{t("Remove")}</button>
          </div>)}
        </section>

        <section className="card stack"><div><p className="eyebrow">{t("03 · CONSENT")}</p><h2>{t("A clear basis for coordination")}</h2></div>
          <div className="grid-2"><label className="field"><span>{t("Consent basis")}</span><select name="consent.basis" value={basis} onChange={event => setBasis(event.target.value as "CONSENT" | "REPRESENTATIVE")}><option value="CONSENT">{t("Consent")}</option><option value="REPRESENTATIVE">{t("Representative")}</option></select><FieldError name="consent.basis" issues={issues} /></label>
          <label className="field"><span>{t("Captured at (UTC)")}</span><input type="datetime-local" name="consent.capturedAt" required value={capturedAt} onChange={event => setCapturedAt(event.target.value)} aria-invalid={hasIssue("consent.capturedAt")} aria-describedby={describe("consent.capturedAt")} /><FieldError name="consent.capturedAt" issues={issues} /></label></div>
          <label className="field"><span>{t("Consent scope")}</span><textarea name="consent.scope" value={scope} onChange={event => setScope(event.target.value)} required maxLength={200} rows={2} aria-invalid={hasIssue("consent.scope")} aria-describedby={describe("consent.scope")} /><FieldError name="consent.scope" issues={issues} /></label>
          <label className="checkbox"><input type="checkbox" name="consentConfirmed" checked={consentConfirmed} onChange={event => setConsentConfirmed(event.target.checked)} required aria-describedby={describe("consentConfirmed")} /><span>{t("I confirm this is a simulated consent record for fictional data.")}</span></label><FieldError name="consentConfirmed" issues={issues} />
          <label className="checkbox"><input type="checkbox" name="consent.publicSummaryAllowed" checked={publicSummaryAllowed} onChange={event => setPublicSummaryAllowed(event.target.checked)} /><span>{t("A sanitized public summary is allowed.")}</span></label><FieldError name="consent.publicSummaryAllowed" issues={issues} />
          {publicSummaryAllowed && <div className="stack"><label className="field"><span>{t("Public title (optional)")}</span><input name="publicTitle" value={publicTitle} onChange={event => setPublicTitle(event.target.value)} maxLength={160} /><FieldError name="publicTitle" issues={issues} /></label><label className="field"><span>{t("Public summary (optional)")}</span><textarea name="publicSummary" value={publicSummary} onChange={event => setPublicSummary(event.target.value)} maxLength={2000} rows={3} /><FieldError name="publicSummary" issues={issues} /></label></div>}
        </section>

        <section className="card stack"><div><p className="eyebrow">{t("04 · EVIDENCE")}</p><h2>{t("Select organization fixtures")}</h2><p className="muted">{t("Choose 1–10 allowlisted fixtures. This workflow does not upload files.")}</p></div>
          {fixtureLoading ? <LoadingCard label={t("Loading allowed fixtures…")} /> : fixtureError ? <><ErrorNotice error={fixtureError} /><button type="button" className="button secondary" onClick={() => setFixtureRefresh(value => value + 1)}>{t("Reload fixtures")}</button></> : fixtures.length === 0 ? <p className="muted">{t("No evidence fixtures are available. Ask the backend owner to provision fixtures for this organization.")}</p> : <div className="evidence-options">{fixtures.map(fixture => <label className="checkbox evidence-option" key={fixture.key}><input type="checkbox" name="evidenceFixtureKeys" value={fixture.key} checked={evidenceKeys.includes(fixture.key)} onChange={event => setEvidenceKeys(current => event.target.checked ? [...current, fixture.key] : current.filter(key => key !== fixture.key))} aria-describedby={describe("evidenceFixtureKeys")} /><span><strong>{fixture.label}</strong><span className="muted">{fixture.mimeType} · {t('{n} bytes', { n: fixture.byteSize.toLocaleString() })}</span></span></label>)}</div>}
          <FieldError name="evidenceFixtureKeys" issues={issues} />
          {issues.filter(issue => issue.field.startsWith("evidenceFixtureKeys[")).map(issue => <p className="field-error" key={issue.field}>{issue.field}: {t(issue.code.replaceAll("_", " ").toLowerCase())}</p>)}
        </section>
        <div className="form-footer actions"><Link href="/cases" className="button secondary">{t("Cancel")}</Link><button type="submit" className="button" disabled={fixtureLoading || !!fixtureError || fixtures.length === 0}>{busy ? t("Submitting…") : t("Submit case")}</button></div>
      </fieldset>
    </form>
  </div>;
}
