import type { CampaignAuditUrl, ReceiptRequest } from "../../../docs/fixtures/f02/goods-flow.types";
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
// The API client maps legacy D01 audit URLs to null.
export type DeliveryResponse = DeliveryResult<CampaignAuditUrl | null>;

export function isCampaignAuditUrl(url: unknown): url is CampaignAuditUrl {
  return typeof url === "string" && /^\/api\/campaigns\/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\/audit-trail$/i.test(url);
}
