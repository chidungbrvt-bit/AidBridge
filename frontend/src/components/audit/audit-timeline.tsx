import type { BlockchainStatus } from '@/lib/types';
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
  return <section className="card stack" aria-labelledby="audit-heading">
    <div>
      <h2 id="audit-heading">Audit timeline</h2>
      <p className="muted">Fixture preview of three independent CASE_VERIFIED records. Live case status requires the audit API.</p>
    </div>
    <ol className="audit-timeline">
      {events.map(event => <li className="audit-event" key={event.id}>
        <div className="toolbar audit-event-header">
          <div><p className="eyebrow">{event.source}</p><h3>Case verified</h3></div>
          <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toISOString().replace('T', ' ').slice(0, 16)} UTC</time>
        </div>
        <dl className="metadata">
          <div><dt>Business status</dt><dd>VERIFIED</dd></div>
          <div><dt>Evidence review</dt><dd>APPROVED</dd></div>
          <div><dt>Actor role</dt><dd>VERIFIER</dd></div>
          <div><dt>Blockchain sync</dt><dd><span className={`badge audit-badge ${event.blockchainStatus.toLowerCase()}`} title={event.blockchainStatus}>{statusLabel[event.blockchainStatus]}</span></dd></div>
        </dl>
        <p>{event.explanation}</p>
        {event.proof && <dl className="metadata audit-proof">
          <div><dt>Network</dt><dd>Avalanche Fuji testnet (43113)</dd></div>
          <div><dt>Block</dt><dd>{event.proof.blockNumber}</dd></div>
          <div><dt>Registry address</dt><dd><code>{event.proof.contractAddress}</code></dd></div>
          <div><dt>Transaction hash</dt><dd><code>{event.proof.transactionHash}</code></dd></div>
        </dl>}
      </li>)}
    </ol>
    <p className="muted">Blockchain confirmation supports record integrity. Delivery of physical goods requires separate evidence.</p>
  </section>;
}
