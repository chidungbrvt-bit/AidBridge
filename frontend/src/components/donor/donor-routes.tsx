"use client";
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { useSession } from '@/components/session-provider';
import { AuditDisclosure } from '@/components/audit/audit-feed';
import { isPublicCaseAuditUrl } from '@/lib/audit';
import { ApiError } from '@/lib/api-client';
import { useI18n } from '@/components/i18n-provider';
import { DonorApp } from '../../../features/donor/DonorApp';
import { createDonorAdapter } from '../../../features/donor/adapter';
import type { CampaignResponse, Page } from '../../../features/donor/model';

/** Persistent donor layout: G01/G02 route changes do not remount an unresolved mutation. */
export function DonorRoutes() {
  const { api, user, mode, setNavigationLocked } = useSession();
  const pathname = usePathname();
  const router = useRouter();
  const adapter = useMemo(() => createDonorAdapter(api), [api]);
  const matched = /^\/campaigns\/([^/]+)$/.exec(pathname)?.[1];
  const [lastCampaign, setLastCampaign] = useState(matched ?? '');
  useEffect(() => { if (matched) setLastCampaign(matched); }, [matched]);
  if (pathname === '/campaigns') return <CampaignList/>;
  const campaignId = matched ?? lastCampaign;
  return <DonorApp api={adapter} campaignId={campaignId} mode={mode}
    renderAudit={mode === 'real' ? campaign => campaign.status !== 'DRAFT' && isPublicCaseAuditUrl(campaign.auditUrl)
      ? <AuditDisclosure label="Public audit trail" scope={{ publicUrl: campaign.auditUrl, showShareLink: true }} /> : null : undefined}
    donorEnabled={user?.status === 'ACTIVE' && user.donorEnabled === true}
    view={pathname === '/me/commitments' ? 'history' : 'pledge'} onLock={setNavigationLocked}
    navigate={view => router.push(view === 'history' ? '/me/commitments' : campaignId ? `/campaigns/${encodeURIComponent(campaignId)}` : '/campaigns')}/>;
}

function CampaignList() {
  const { api } = useSession();
  const { t, status } = useI18n();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Page<CampaignResponse>>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let live = true; setLoading(true); setData(undefined); setError('');
    void api.listCampaigns(page).then(value => { if (live) setData(value); })
      .catch(cause => { if (live) setError(cause instanceof ApiError ? `${cause.status} ${cause.code}: ${cause.message}` : t("Unable to load public campaigns.")); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [api, page, revision]);
  return <section aria-label={t("Public campaigns")}><div className="page-header"><div><h1>{t("Explore campaigns")}</h1><p className="muted">{t("Published essential-goods needs. Pledging requires donor access.")}</p></div><Link href="/me/commitments" className="button secondary">{t("My pledges")}</Link></div>
    {loading && <p role="status">{t("Loading campaigns...")}</p>}
    {error && <div role="alert" className="notice error">{error} <button onClick={() => setRevision(x => x + 1)}>{t("Retry campaigns")}</button></div>}
    {data?.items.length === 0 && <p role="status">{t("No public campaigns are available.")}</p>}
    <div className="stack">{data?.items.map(c => <article className="card" key={c.id}><p className="eyebrow">{status(c.status)} · {c.organization.name}</p><h2>{c.title}</h2><p>{c.publicSummary}</p><p className="muted">{c.approximateArea}</p><Link className="button" href={`/campaigns/${encodeURIComponent(c.id)}`}>{t("View needs and pledge")}</Link></article>)}</div>
    {data && data.totalElements > data.size && <nav className="pagination" aria-label={t("Campaign pages")}><button disabled={page === 0 || loading} onClick={() => setPage(x => x - 1)}>{t("Previous")}</button><span>{t("Page {n}", { n: page + 1 })}</span><button disabled={(page + 1) * data.size >= data.totalElements || loading} onClick={() => setPage(x => x + 1)}>{t("Next")}</button></nav>}
  </section>;
}
