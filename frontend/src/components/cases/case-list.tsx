"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "@/components/session-provider";
import { canCreateCases, canReadCases } from "@/lib/api-client";
import type { CaseSummary, PageResponse } from "@/lib/types";
import { describeError, ErrorNotice, LoadingCard, StatusBadge, type ErrorInfo } from "./shared";

export function CaseList({ initialStatus = "" }: { initialStatus?: string }) {
  const { user, organizationId } = useSession();
  return <ScopedCaseList key={`${user?.userId}:${organizationId}:${initialStatus}`} initialStatus={initialStatus} />;
}

function ScopedCaseList({ initialStatus }: { initialStatus: string }) {
  const { api, user, organizationId } = useSession();
  const [status, setStatus] = useState(initialStatus);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<PageResponse<CaseSummary> | null>(null);
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const readable = canReadCases(user, organizationId);
  const creatable = canCreateCases(user, organizationId);

  useEffect(() => {
    if (!readable) { setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setResult(null);
    api.listCases({ organizationId, status: status || undefined, page, size: 10 })
      .then(data => { if (!cancelled) setResult(data); })
      .catch(reason => { if (!cancelled) setError(describeError(reason)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, organizationId, status, page, refresh, readable]);

  if (!readable) return <div className="card"><h1>No case access</h1><p className="muted">Select an active organization membership with a staff role.</p></div>;

  return <div className="stack">
    <header className="page-header">
      <div><p className="eyebrow">CASE MANAGEMENT</p><h1>Every need starts here.</h1><p className="muted">Create, track, and independently review aid requests in your organization.</p></div>
      {creatable && <Link className="button" href="/cases/new">+ Create case</Link>}
    </header>

    <section className="card">
      <div className="toolbar">
        <div><h2>Organization cases</h2><p className="muted">{result ? `${result.totalElements} case${result.totalElements === 1 ? "" : "s"}` : "Private staff workspace"}</p></div>
        <div className="actions"><label className="field" htmlFor="status-filter"><span>Status</span><select id="status-filter" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">All statuses</option><option value="SUBMITTED">Submitted</option><option value="VERIFIED">Verified</option><option value="CHANGES_REQUESTED">Changes requested</option><option value="REJECTED">Rejected</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option>
        </select></label><button className="button secondary" type="button" onClick={() => setRefresh(value => value + 1)} disabled={loading}>Refresh</button></div>
      </div>
      {loading ? <LoadingCard /> : error ? <ErrorNotice error={error} /> : result && result.items.length === 0 ? <div className="empty-state"><span className="empty-icon" aria-hidden="true">↗</span><h3>No cases {status ? "with this status" : "yet"}</h3><p className="muted">{status ? "Choose another status to see more cases." : "Add a fictional aid request to start the workflow."}</p>{creatable && <Link className="button secondary" href="/cases/new">Create your first case</Link>}</div> : result ? <>
        <div className="table-wrap"><table className="data-table"><caption className="sr-only">Cases in the selected organization</caption><thead><tr><th scope="col">Case</th><th scope="col">Status</th><th scope="col">Created at</th><th scope="col">Revision</th></tr></thead><tbody>{result.items.map(item => <tr key={item.id}>
          <td><Link className="case-link" href={`/cases/${item.id}`}>{item.internalTitle}</Link></td>
          <td><StatusBadge status={item.status} />{item.priority === "URGENT" && <span className="badge">Urgent</span>}</td><td><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString("en-GB", { timeZone: "UTC" })}</time></td><td>v{item.version}</td>
        </tr>)}</tbody></table></div>
        <div className="toolbar pagination"><span className="muted">Page {page + 1} of {Math.max(1, Math.ceil(result.totalElements / result.size))}</span><div className="actions"><button className="button secondary" type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><button className="button secondary" type="button" disabled={(page + 1) * result.size >= result.totalElements} onClick={() => setPage(value => value + 1)}>Next</button></div></div>
      </> : null}
    </section>
    <p className="muted footnote">Only active organization memberships grant access. Verification must be completed by a different person.</p>
  </div>;
}
