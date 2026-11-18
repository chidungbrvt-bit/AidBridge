"use client";

import Link from 'next/link';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSession } from '@/components/session-provider';
import { describeError, ErrorNotice, FieldError, type ErrorInfo } from '@/components/cases/shared';
import { canManageCampaigns } from '@/lib/api-client';
import type { CaseDetailResponse } from '@/lib/types';
import type { CampaignWork } from '@/lib/session-work';
import { GoodsProgressTable } from './goods-progress-table';
import { useI18n } from '@/components/i18n-provider';

type Attempt = NonNullable<CampaignWork['pending']>;

export function CampaignPanel({ detail, onBusyChange, onPublished }: {
  detail: CaseDetailResponse; onBusyChange: (value: boolean) => void; onPublished: () => void;
}) {
  const { user, organizationId } = useSession();
  if (!user || !canManageCampaigns(user, organizationId) || detail.organizationId !== organizationId) return null;
  if (detail.status !== 'VERIFIED' && detail.status !== 'IN_PROGRESS') return null;
  return <ScopedCampaignPanel detail={detail} actorId={user.userId} onBusyChange={onBusyChange} onPublished={onPublished} />;
}

function ScopedCampaignPanel({ detail, actorId, onBusyChange, onPublished }: {
  detail: CaseDetailResponse; actorId: string; onBusyChange: (value: boolean) => void; onPublished: () => void;
}) {
  const { api, work, organizationId, setNavigationLocked } = useSession();
  const { t } = useI18n();
  const scope = `${actorId}:${organizationId}:${detail.id}`;
  const [entry, setEntry] = useState<CampaignWork>(() => work.campaigns.get(scope) ?? {});
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState(detail.publicTitle ?? '');
  const [summary, setSummary] = useState(detail.publicSummary ?? '');
  const [error, setError] = useState<ErrorInfo | null>(null);
  const mounted = useRef(false);
  const pending = entry.pending;
  const locked = busy || !!pending;
  const draft = entry.draft;
  const canStart = detail.status === 'VERIFIED' && detail.consent.publicSummaryAllowed;

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    setNavigationLocked(locked);
    onBusyChange(locked);
    return () => { setNavigationLocked(false); onBusyChange(false); };
  }, [locked, onBusyChange, setNavigationLocked]);

  function save(value: CampaignWork) {
    work.campaigns.set(scope, value);
    if (mounted.current) setEntry(value);
  }

  async function send(attempt: Attempt) {
    if (attempt.inFlight || work.campaigns.get(scope)?.pending?.inFlight) return;
    const existing = work.campaigns.get(scope) ?? entry;
    const retry = existing.pending === attempt;
    attempt.inFlight = true;
    const stored = { ...existing, pending: attempt };
    save(stored);
    setBusy(true);
    setError(null);
    try {
      if (retry) {
        const current = await api.me();
        if (current.userId !== actorId || !canManageCampaigns(current, organizationId)) {
          throw new Error('Campaign permission must be confirmed before retrying.');
        }
      }
      const result = attempt.action === 'create'
        ? await api.createCampaign(attempt.body, attempt.key)
        : await api.publishCampaign(attempt.campaignId, attempt.key);
      // A logout or identity switch may have cleared this entry while the request was running.
      if (work.campaigns.get(scope) === stored) {
        save({ draft: result });
        if (mounted.current && attempt.action === 'publish') onPublished();
      }
    } catch (reason) {
      const info = describeError(reason);
      if (mounted.current) setError(info);
      if (work.campaigns.get(scope) === stored && !retry && !info.unknownOutcome) save({ draft: existing.draft });
    } finally {
      attempt.inFlight = false;
      if (mounted.current) setBusy(false);
    }
  }

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked || !canStart) return;
    const body = { caseId: detail.id, title: title.trim(), publicSummary: summary.trim() };
    if (!body.title || !body.publicSummary) {
      setError({ code: 'VALIDATION_ERROR', message: 'Enter a title and a public summary.', fieldErrors: [], unknownOutcome: false });
      return;
    }
    void send({ action: 'create', body, key: crypto.randomUUID() });
  }

  return <section className="card stack" aria-labelledby="campaign-panel-title">
    <h2 id="campaign-panel-title">{t("Aid campaign")}</h2>
    <ErrorNotice error={error?.code === 'INVALID_STATE' || error?.code === 'VERSION_CONFLICT'
      ? { ...error, message: 'The case or campaign is no longer eligible for this action. Refresh the case and check its current status.' }
      : error} />
    {pending && <div className="notice">
      <p role="status">{busy ? t("Saving campaign…") : t("The request is unresolved. Retry with the same details and request key.")}</p>
      {!busy && <button type="button" className="button secondary" onClick={() => void send(pending)}>{t("Retry same campaign request")}</button>}
    </div>}
    {draft ? <>
      <div><span className="badge">{t(draft.status)}</span><h3>{draft.title}</h3><p>{draft.publicSummary}</p><p className="muted">{draft.approximateArea}</p></div>
      <GoodsProgressTable items={draft.items} />
      {draft.status === 'DRAFT' ? <>
        <p className="muted">{t("Only administrators of this organization can see the draft. Review the public copy and the approved item targets before publishing.")}</p>
        <button type="button" className="button" disabled={locked || !canStart} onClick={() => void send({
          action: 'publish', campaignId: draft.id, key: crypto.randomUUID(),
          body: {},
        })}>{t("Publish campaign")}</button>
      </> : <div className="actions">
        <Link className="button" href={`/campaigns/${encodeURIComponent(draft.id)}`}>{t("View published campaign")}</Link>
        <Link className="button secondary" href="/campaigns">{t("All published campaigns")}</Link>
      </div>}
    </> : canStart ? <form className="stack" onSubmit={create}>
      <fieldset className="form-fieldset stack" disabled={locked}>
        <p className="muted">{t("The campaign uses the approved items above. Public copy should only describe the needs and approximate area.")}</p>
        <label className="field"><span>{t("Campaign title")}</span><input name="title" value={title} onChange={event => setTitle(event.target.value)} required maxLength={160} /><FieldError name="title" issues={error?.fieldErrors ?? []} /></label>
        <label className="field"><span>{t("Campaign public summary")}</span><textarea name="publicSummary" value={summary} onChange={event => setSummary(event.target.value)} required maxLength={2000} rows={3} /><FieldError name="publicSummary" issues={error?.fieldErrors ?? []} /></label>
        <button className="button" type="submit">{t("Create campaign draft")}</button>
      </fieldset>
    </form> : detail.status === 'IN_PROGRESS' ?
      <Link href="/campaigns">{t("Find the published campaign")}</Link> :
      <p className="muted">{t("This case does not permit a public summary.")}</p>}
  </section>;
}
