import type { PublicCaseAuditUrl, ReceiptRequest } from "../../../docs/fixtures/f02/goods-flow.types";
import type { CaseDetailResponse } from "./types";

export type { ReceiptRequest };

export type ReceiptResponse = {
  id: string;
  contributionId: string;
  items: ReceiptRequest["items"];
  receivedAt: string;
  recordedAt: string;
  recorderId: string;
  auditEventId: string;
  blockchainStatus: "PENDING_ON_CHAIN";
};

export type OutstandingCommitment = {
  contributionId: string;
  status: string;
  pledgedAt: string;
  items: {
    contributionItemId: string;
    campaignItemId: string;
    itemName: string;
    unit: string;
    pledgedQty: number;
    receivedQty: number;
    outstandingQty: number;
  }[];
};

export type GoodsTotal = {
  campaignItemId: string; itemName: string; unit: string; targetQty: number;
  pledgedQty: number; receivedQty: number; deliveredQty: number; confirmedQty: number; availableToDeliverQty: number;
};

export type DeliveryReview = {
  id: string; recorderId: string; outcome: string; reviewStatus: string;
  items: DeliveryItem[]; evidenceVersionIds: string[]; recordedAt: string;
};

export type DisputeSummary = { id: string; deliveryId: string | null; status: string; reason: string; createdAt: string };
export type DisputeReview = {
  id: string; caseId: string; status: string; reason: string; createdAt: string;
  delivery: { id: string; recordedAt: string; items: (DeliveryItem & { itemName: string; unit: string })[]; evidenceVersionIds: string[] } | null;
};

export type StaffCaseDetail = Omit<CaseDetailResponse, 'goodsTotals'> & {
  campaignId?: string | null;
  reviewCampaign?: { id: string; status: string } | null;
  outstandingCommitments: OutstandingCommitment[];
  goodsTotals?: GoodsTotal[];
  deliveries?: DeliveryReview[];
  disputes?: DisputeSummary[];
};

export type DisputeRequest = { deliveryId?: string; reason: string };
export type ResolveDisputeRequest = { outcome: 'UPHOLD' | 'VOID_DELIVERY'; reason: string; reviewEvidenceVersionIds: string[] };
export type GoodsReviewAction =
  | { action: 'confirm'; id: string; body: ConfirmationRequest }
  | { action: 'dispute'; id: string; body: DisputeRequest }
  | { action: 'resolve'; id: string; body: ResolveDisputeRequest }
  | { action: 'close'; id: string };

export type DisputeResponse = { id: string; caseId: string; deliveryId?: string; status: string; resolution?: string };
export type ConfirmationResponse = {
  id: string; deliveryId: string; decision: ConfirmationRequest['decision']; reviewerId: string;
  disputeId?: string; goodsTotals: GoodsTotal[]; auditEventId: string; blockchainStatus: string;
};

type DeliveryItem = { campaignItemId: string; quantity: number };

export type ConfirmationRequest = {
  decision: "CONFIRMED" | "REQUIRES_REVIEW";
  reviewedEvidenceVersionIds: string[];
  reason?: string;
};

export type DeliveryRequest =
  | { outcome: "DELIVERED"; items: DeliveryItem[]; evidenceVersionIds: string[]; handedOverAt?: string }
  | { outcome: "FAILED"; items: []; evidenceVersionIds: []; reason: string; handedOverAt?: never };

type DeliveryResult<AuditUrl> = {
  id: string;
  caseId: string;
  recorderId: string;
  recordedAt: string;
  auditEventId: string;
} & (
  | { outcome: "DELIVERED"; items: DeliveryItem[]; evidenceVersionIds: string[]; reviewStatus: "PENDING"; blockchainStatus: "PENDING_ON_CHAIN"; auditUrl: AuditUrl }
  | { outcome: "FAILED"; items: []; evidenceVersionIds: []; reviewStatus: "NOT_APPLICABLE"; blockchainStatus: "NOT_APPLICABLE"; auditUrl: null }
);

export type DeliveryWireResponse = DeliveryResult<string | null>;
export type DeliveryResponse = DeliveryResult<PublicCaseAuditUrl | null>;
