"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "@/components/session-provider";
import { canCreateCases, canReadCases } from "@/lib/api-client";
import type { CaseSummary, PageResponse } from "@/lib/types";
import { useI18n } from "@/components/i18n-provider";
import { describeError, ErrorNotice, LoadingCard, StatusBadge, type ErrorInfo } from "./shared";
import { Icon } from "@/components/icons";

export function CaseList({ initialStatus = "" }: { initialStatus?: string }) {
  const { user, organizationId } = useSession();
  return <ScopedCaseList key={`${user?.userId}:${organizationId}:${initialStatus}`} initialStatus={initialStatus} />;
}

function ScopedCaseList({ initialStatus }: { initialStatus: string }) {
  const { api, user, organizationId } = useSession();
  const { t } = useI18n();
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

  if (!readable) return <div className="card"><h1>{t("No case access")}</h1><p className="muted">{t("Select an active organization membership with a staff role.")}</p></div>;

  return <div className="stack">
    <header className="page-header">
      <div><p className="eyebrow">{t("Case management")}</p><h1>{t("Every need starts here.")}</h1><p className="muted">{t("Create, track, and independently review aid requests in your organization.")}</p></div>
      {creatable && <Link className="button" href="/cases/new">{t("+ Create case")}</Link>}
    </header>

    <section className="card">
      <div className="toolbar">
        <div><h2>{t("Organization cases")}</h2><p className="muted">{result ? t(result.totalElements === 1 ? "{n} case" : "{n} cases", { n: result.totalElements }) : t("Private staff workspace")}</p></div>
        <div className="actions"><label className="field" htmlFor="status-filter"><span>{t("Status")}</span><select id="status-filter" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">{t("All statuses")}</option><option value="SUBMITTED">{t("Submitted")}</option><option value="VERIFIED">{t("Verified")}</option><option value="CHANGES_REQUESTED">{t("Changes requested")}</option><option value="REJECTED">{t("Rejected")}</option><option value="IN_PROGRESS">{t("In progress")}</option><option value="COMPLETED">{t("Completed")}</option>
        </select></label><button className="button secondary" type="button" onClick={() => setRefresh(value => value + 1)} disabled={loading}>{t("Refresh")}</button></div>
      </div>
      {loading ? <LoadingCard /> : error ? <ErrorNotice error={error} /> : result && result.items.length === 0 ? <div className="empty-state"><span className="empty-icon"><Icon name="cases" size={24} /></span><h3>{status ? t("No cases with this status") : t("No cases yet")}</h3><p className="muted">{status ? t("Choose another status to see more cases.") : t("Add a fictional aid request to start the workflow.")}</p>{creatable && <Link className="button secondary" href="/cases/new">{t("Create your first case")}</Link>}</div> : result ? <>
        <div className="table-wrap"><table className="data-table"><caption className="sr-only">{t("Cases in the selected organization")}</caption><thead><tr><th scope="col">{t("Case")}</th><th scope="col">{t("Status")}</th><th scope="col">{t("Created at")}</th><th scope="col">{t("Revision")}</th></tr></thead><tbody>{result.items.map(item => <tr key={item.id}>
          <td><Link className="case-link" href={`/cases/${item.id}`}>{item.internalTitle}</Link></td>
          <td><StatusBadge status={item.status} />{item.priority === "URGENT" && <span className="badge">{t("Urgent")}</span>}</td><td><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString("en-GB", { timeZone: "UTC" })}</time></td><td>v{item.version}</td>
        </tr>)}</tbody></table></div>
        <div className="toolbar pagination"><span className="muted">{t("Page {page} of {total}", { page: page + 1, total: Math.max(1, Math.ceil(result.totalElements / result.size)) })}</span><div className="actions"><button className="button secondary" type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>{t("Previous")}</button><button className="button secondary" type="button" disabled={(page + 1) * result.size >= result.totalElements} onClick={() => setPage(value => value + 1)}>{t("Next")}</button></div></div>
      </> : null}
    </section>
    <p className="muted footnote">{t("Only active organization memberships grant access. Verification must be completed by a different person.")}</p>
  </div>;
}
