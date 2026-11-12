"use client";
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { useSession } from '@/components/session-provider';
import { ApiError } from '@/lib/api-client';
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
    donorEnabled={user?.status === 'ACTIVE' && user.donorEnabled === true}
    view={pathname === '/me/commitments' ? 'history' : 'pledge'} onLock={setNavigationLocked}
    navigate={view => router.push(view === 'history' ? '/me/commitments' : campaignId ? `/campaigns/${encodeURIComponent(campaignId)}` : '/campaigns')}/>;
}

function CampaignList() {
  const { api } = useSession();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Page<CampaignResponse>>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let live = true; setLoading(true); setData(undefined); setError('');
    void api.listCampaigns(page).then(value => { if (live) setData(value); })
      .catch(cause => { if (live) setError(cause instanceof ApiError ? `${cause.status} ${cause.code}: ${cause.message}` : 'Unable to load public campaigns.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [api, page, revision]);
  return <section aria-label="Public campaigns"><div className="page-header"><div><h1>Explore campaigns</h1><p className="muted">Published essential-goods needs. Pledging requires donor access.</p></div><Link href="/me/commitments" className="button secondary">My pledges</Link></div>
    {loading && <p role="status">Loading campaigns...</p>}
    {error && <div role="alert" className="notice error">{error} <button onClick={() => setRevision(x => x + 1)}>Retry campaigns</button></div>}
    {data?.items.length === 0 && <p role="status">No public campaigns are available.</p>}
    <div className="stack">{data?.items.map(c => <article className="card" key={c.id}><p className="eyebrow">{c.status} · {c.organization.name}</p><h2>{c.title}</h2><p>{c.publicSummary}</p><p className="muted">{c.approximateArea}</p><Link className="button" href={`/campaigns/${encodeURIComponent(c.id)}`}>View needs and pledge</Link></article>)}</div>
    {data && data.totalElements > data.size && <nav className="pagination" aria-label="Campaign pages"><button disabled={page === 0 || loading} onClick={() => setPage(x => x - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * data.size >= data.totalElements || loading} onClick={() => setPage(x => x + 1)}>Next</button></nav>}
  </section>;
}
