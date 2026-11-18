"use client";

import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { useSession } from "@/components/session-provider";
import { describeError, ErrorNotice, type ErrorInfo } from "@/components/cases/shared";
import { auditChainView, isPublicCaseAuditUrl, type InternalAuditEvent, type PublicAuditEvent } from "@/lib/audit";
import type { PageResponse } from "@/lib/types";
import "./audit.css";

type AuditScope = { caseId: string } | { publicUrl: string; showShareLink?: boolean };

export function AuditDisclosure({ label, scope, revision }: { label: string; scope: AuditScope; revision?: number }) {
  const [open, setOpen] = useState(false);
  return <details className="card" onToggle={event => setOpen(event.currentTarget.open)}>
    <summary>{label}</summary>
    {open && <AuditFeed scope={scope} revision={revision} />}
  </details>;
}

export function AuditFeed({ scope, revision = 0 }: { scope: AuditScope; revision?: number }) {
  const { api } = useSession();
  const headingId = useId();
  const caseId = "caseId" in scope ? scope.caseId : undefined;
  const publicUrl = "publicUrl" in scope ? scope.publicUrl : undefined;
  const publicRef = isPublicCaseAuditUrl(publicUrl) ? publicUrl.split("/")[4] : undefined;
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<PageResponse<InternalAuditEvent | PublicAuditEvent>>();
  const [error, setError] = useState<ErrorInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!caseId && !publicRef) return;
    let active = true;
    setLoading(true);
    setResult(undefined);
    setError(null);
    const request = caseId ? api.getCaseAudit(caseId, page) : api.getPublicAudit(publicUrl!, page);
    void request.then(value => { if (active) setResult(value); })
      .catch(cause => { if (active) setError(describeError(cause)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [api, caseId, publicUrl, publicRef, page, retry, revision]);

  return <section className="stack" aria-labelledby={headingId}>
    <div className="toolbar">
      <h2 id={headingId}>{caseId ? "Case audit timeline" : "Public audit timeline"}</h2>
      {(caseId || publicRef) && <button type="button" className="button secondary" disabled={loading}
        onClick={() => setRetry(value => value + 1)}>Refresh audit</button>}
    </div>
    <p className="muted">On-chain status describes record integrity. Physical delivery is confirmed separately.</p>
    {!caseId && !publicRef ? <p>Public audit is unavailable for this campaign.</p> : <>
      {loading && <p role="status">Loading audit events…</p>}
      {error && <><ErrorNotice error={error} /><button type="button" className="button secondary" onClick={() => setRetry(value => value + 1)}>Retry audit</button></>}
      {result && <>
        {publicRef && "showShareLink" in scope && scope.showShareLink && <Link href={`/audit/${publicRef}`}>Open public audit page</Link>}
        {result.items.length === 0 ? <p>No audit events are available.</p> :
          <ol className="audit-timeline">
            {result.items.map(event => {
              const chain = auditChainView(event.blockchain);
              const actor = caseId && "actor" in event ? event.actor?.fullName
                : !caseId && "publicActorRole" in event ? event.publicActorRole : null;
              return <li className="audit-event" key={event.eventId}>
                <div className="toolbar audit-event-header">
                  <h3>{event.type.replaceAll("_", " ")}</h3>
                  <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString("en-GB", { timeZone: "UTC" })} UTC</time>
                </div>
                <dl className="metadata audit-proof">
                  {actor && <div><dt>{caseId ? "Recorded by" : "Role"}</dt><dd>{actor}</dd></div>}
                  {event.chainSequence != null && <div><dt>Sequence</dt><dd>{event.chainSequence}</dd></div>}
                  <div><dt>Blockchain sync</dt><dd><span className={["badge", "audit-badge", chain.badge].join(" ")}>{chain.label}</span></dd></div>
                  {chain.txHash && <div><dt>Transaction hash (Fuji)</dt><dd><code>{chain.txHash}</code></dd></div>}
                  {chain.blockNumber != null && <div><dt>Block number</dt><dd>{chain.blockNumber}</dd></div>}
                </dl>
                {chain.explorerUrl && <a href={chain.explorerUrl} target="_blank" rel="noopener noreferrer">View confirmed transaction on Fuji</a>}
              </li>;
            })}
          </ol>}
        {result.totalElements > result.size && <nav className="actions" aria-label="Audit pages">
          <button type="button" disabled={result.page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {result.page + 1}</span>
          <button type="button" disabled={(result.page + 1) * result.size >= result.totalElements} onClick={() => setPage(page + 1)}>Next</button>
        </nav>}
      </>}
    </>}
  </section>;
}
