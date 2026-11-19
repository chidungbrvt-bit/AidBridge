'use client';

import type { BlockchainStatus } from '@/lib/types';
import { useI18n } from '@/components/i18n-provider';
import './audit.css';

export type AuditExample = {
  id: string;
  source: string;
  occurredAt: string;
  blockchainStatus: Extract<BlockchainStatus, 'PENDING_ON_CHAIN' | 'FAILED_ON_CHAIN' | 'CONFIRMED_ON_CHAIN'>;
  explanation: string;
  proof?: { transactionHash: string; blockNumber: number; contractAddress: string };
};

const statusLabel = {
  PENDING_ON_CHAIN: 'Pending on chain',
  FAILED_ON_CHAIN: 'Failed on chain',
  CONFIRMED_ON_CHAIN: 'Confirmed on chain',
};

export function AuditTimeline({ events }: { events: readonly AuditExample[] }) {
  const { t, status } = useI18n();
  return <section className="card stack" aria-labelledby="audit-heading">
    <div>
      <h2 id="audit-heading">{t("Audit timeline")}</h2>
      <p className="muted">{t("Fixture preview of three independent CASE_VERIFIED records. Live case status requires the audit API.")}</p>
    </div>
    <ol className="audit-timeline">
      {events.map(event => <li className="audit-event" key={event.id}>
        <div className="toolbar audit-event-header">
          <div><p className="eyebrow">{t(event.source)}</p><h3>{t("Case verified")}</h3></div>
          <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toISOString().replace('T', ' ').slice(0, 16)} UTC</time>
        </div>
        <dl className="metadata">
          <div><dt>{t("Business status")}</dt><dd>{status("VERIFIED")}</dd></div>
          <div><dt>{t("Evidence review")}</dt><dd>{status("APPROVED")}</dd></div>
          <div><dt>{t("Actor role")}</dt><dd>{status("VERIFIER")}</dd></div>
          <div><dt>{t("Blockchain sync")}</dt><dd><span className={`badge audit-badge ${event.blockchainStatus.toLowerCase()}`} title={event.blockchainStatus}>{t(statusLabel[event.blockchainStatus])}</span></dd></div>
        </dl>
        <p>{t(event.explanation)}</p>
        {event.proof && <dl className="metadata audit-proof">
          <div><dt>{t("Network")}</dt><dd>{t("Avalanche Fuji testnet (43113)")}</dd></div>
          <div><dt>{t("Block")}</dt><dd>{event.proof.blockNumber}</dd></div>
          <div><dt>{t("Registry address")}</dt><dd><code>{event.proof.contractAddress}</code></dd></div>
          <div><dt>{t("Transaction hash")}</dt><dd><code>{event.proof.transactionHash}</code></dd></div>
        </dl>}
      </li>)}
    </ol>
    <p className="muted">{t("Blockchain confirmation supports record integrity. Delivery of physical goods requires separate evidence.")}</p>
  </section>;
}
