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

export type StaffCaseDetail = CaseDetailResponse & { campaignId?: string | null; outstandingCommitments: OutstandingCommitment[] };

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
