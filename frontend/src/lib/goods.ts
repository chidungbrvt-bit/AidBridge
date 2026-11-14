import type { ReceiptRequest } from "../../../docs/fixtures/f02/goods-flow.types";
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

export type StaffCaseDetail = CaseDetailResponse & { outstandingCommitments: OutstandingCommitment[] };

type DeliveryItem = { campaignItemId: string; quantity: number };

export type DeliveryRequest =
  | { outcome: "DELIVERED"; items: DeliveryItem[]; evidenceVersionIds: string[]; handedOverAt?: string }
  | { outcome: "FAILED"; items: []; evidenceVersionIds: []; reason: string; handedOverAt?: never };

// Fields needed by F09. The response is currently supplied by the D01 fixture.
export type DeliveryResponse = {
  id: string;
  caseId: string;
  recorderId: string;
  recordedAt: string;
  auditEventId: string;
} & (
  | { outcome: "DELIVERED"; items: DeliveryItem[]; evidenceVersionIds: string[]; reviewStatus: "PENDING"; blockchainStatus: "PENDING_ON_CHAIN" }
  | { outcome: "FAILED"; items: []; evidenceVersionIds: []; reviewStatus: "NOT_APPLICABLE"; blockchainStatus: "NOT_APPLICABLE"; auditUrl: null }
);
