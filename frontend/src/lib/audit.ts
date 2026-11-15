import type { PublicCaseAuditUrl } from "../../../docs/fixtures/f02/goods-flow.types";

export type { PublicCaseAuditUrl };

export type AuditChain = {
  status: string;
  chainId: number | null;
  network: string | null;
  contractAddress: string | null;
  txHash: string | null;
  blockNumber: number | null;
  explorerUrl: string | null;
};

type AuditEvent = {
  eventId: string;
  type: string;
  occurredAt: string;
  logicalEventId: string | null;
  chainSequence: number | null;
  blockchain: AuditChain;
};

export type InternalAuditEvent = AuditEvent & {
  entityType: string;
  actor: { id: string; fullName: string } | null;
  blockchain: AuditChain & { errorCode: string | null };
};
export type PublicAuditEvent = AuditEvent & { publicActorRole: string | null };

export function isPublicCaseRef(value: string): value is `0x${string}` {
  return /^0x[0-9a-f]{64}$/.test(value);
}

export function isPublicCaseAuditUrl(value: unknown): value is PublicCaseAuditUrl {
  return typeof value === "string" && /^\/api\/public\/cases\/0x[0-9a-f]{64}\/audit$/.test(value);
}

export function auditChainView(chain: AuditChain) {
  const isFuji = chain.chainId === 43113;
  const hasChainActivity = ["PENDING_ON_CHAIN", "FAILED_ON_CHAIN", "CONFIRMED_ON_CHAIN"].includes(chain.status);
  const txHash = isFuji && hasChainActivity && /^0x[0-9a-f]{64}$/i.test(chain.txHash ?? "") ? chain.txHash : null;
  const blockNumber = txHash && Number.isSafeInteger(chain.blockNumber) && Number(chain.blockNumber) > 0
    ? chain.blockNumber : null;
  const verified = Boolean(blockNumber && /^0x[0-9a-f]{40}$/i.test(chain.contractAddress ?? ""));
  let label = "Chain status unavailable";
  if (chain.status === "PENDING_ON_CHAIN") label = "Awaiting on-chain verification";
  if (chain.status === "FAILED_ON_CHAIN") label = "Blockchain sync failed";
  if (chain.status === "NOT_APPLICABLE") label = "Not applicable";
  if (chain.status === "CONFIRMED_ON_CHAIN") label = verified ? "Confirmed on Fuji" : "Proof unavailable";

  let explorerUrl: string | null = null;
  if (chain.status === "CONFIRMED_ON_CHAIN" && verified && txHash &&
      chain.explorerUrl?.toLowerCase() === `https://testnet.snowtrace.io/tx/${txHash.toLowerCase()}`) {
    explorerUrl = chain.explorerUrl;
  }
  const badge = hasChainActivity && (chain.status !== "CONFIRMED_ON_CHAIN" || verified) ? chain.status.toLowerCase() : "unknown";
  return { label, txHash, blockNumber, explorerUrl, badge };
}
