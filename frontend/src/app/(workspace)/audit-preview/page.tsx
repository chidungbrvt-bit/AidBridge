import { AuditTimeline, type AuditExample } from '@/components/audit/audit-timeline';
import proof from '../../../../../docs/fixtures/c05/fuji-case-verified.proof.json';

const events: AuditExample[] = [
  {
    id: 'pending',
    source: 'Simulated fixture',
    occurredAt: '2026-11-11T08:00:00Z',
    blockchainStatus: 'PENDING_ON_CHAIN',
    explanation: 'Verified in AidBridge. The on-chain record is awaiting a checked receipt.',
  },
  {
    id: 'failed',
    source: 'Simulated fixture',
    occurredAt: '2026-11-11T08:10:00Z',
    blockchainStatus: 'FAILED_ON_CHAIN',
    explanation: 'On-chain recording failed. The verification decision remains valid in AidBridge.',
  },
  {
    id: 'confirmed',
    source: 'Independent Fuji proof fixture',
    occurredAt: new Date(Number(proof.decoded.timestamp) * 1000).toISOString(),
    blockchainStatus: 'CONFIRMED_ON_CHAIN',
    explanation: 'C05 checked the receipt and event in an independent contract smoke test. This fixture is separate from any backend case or worker job.',
    proof: { transactionHash: proof.transactionHash, blockNumber: proof.blockNumber, contractAddress: proof.contractAddress },
  },
];

export default function AuditPreviewPage() {
  return <div className="stack">
    <header className="page-header"><div><p className="eyebrow">FIXTURE PREVIEW</p><h1>Audit status examples</h1></div></header>
    <AuditTimeline events={events} />
  </div>;
}
