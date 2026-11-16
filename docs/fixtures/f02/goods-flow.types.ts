// F02 documentation declarations only. Not generated from backend OpenAPI.
// No API client or frontend implementation is provided here.
export type UUID = string;
export type PublicCaseAuditUrl = `/api/public/cases/0x${string}/audit`;
export type UtcTimestamp = string;
export type IntegerQuantity = number; // Runtime validation: integer, 1..1_000_000 for mutations.
export type EndpointId = 'G01' | 'R01' | 'D01';
export type ChainStatus = 'PENDING_ON_CHAIN' | 'CONFIRMED_ON_CHAIN' | 'FAILED_ON_CHAIN' | 'NOT_APPLICABLE';
export interface CampaignResponse {
  id: UUID; status: 'DRAFT' | 'PUBLISHED' | 'FULFILLED' | 'CLOSED';
  title: string; publicSummary: string; approximateArea: string;
  organization: { name: string; publicRef: string };
  items: { id: UUID; name: string; unit: string; targetQty: number; pledgedQty: number;
    receivedQty: number; deliveredQty: number; confirmedQty: number; remainingPledgeQty: number }[];
  auditUrl: PublicCaseAuditUrl;
}
export interface CommitmentRequest { items: { campaignItemId: UUID; quantity: IntegerQuantity }[] }
export interface CommitmentResponse {
  id: UUID; campaignId: UUID; items: { id: UUID; campaignItemId: UUID; pledgedQty: number;
    cancelledQty: number; receivedQty: number; outstandingQty: number }[];
  status: 'PLEDGED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
  createdAt: UtcTimestamp; auditEventId: UUID; blockchainStatus: ChainStatus;
}
export interface ReceiptRequest {
  receivedAt: UtcTimestamp; items: { contributionItemId: UUID; quantity: IntegerQuantity }[]; note?: string;
}
// F02-A03 OPEN: response line schema is not frozen in API_SPECIFICATION.md.
export interface ReceiptResponseMockAssumption {
  id: UUID; contributionId: UUID; items: { contributionItemId: UUID; quantity: IntegerQuantity }[];
  receivedAt: UtcTimestamp; recordedAt: UtcTimestamp; recorderId: UUID; auditEventId: UUID; blockchainStatus: ChainStatus;
}
export type DeliveryRequest = {
  outcome: 'DELIVERED'; handedOverAt?: UtcTimestamp;
  items: { campaignItemId: UUID; quantity: IntegerQuantity }[]; evidenceVersionIds: UUID[]; reason?: string;
} | {
  outcome: 'FAILED'; handedOverAt?: UtcTimestamp; items: []; evidenceVersionIds: []; reason: string;
};
// F02-A03 OPEN: response items and NOT_APPLICABLE reviewStatus are mock defaults.
export interface DeliveryResponseMockAssumption {
  id: UUID; caseId: UUID; recorderId: UUID; outcome: 'DELIVERED' | 'FAILED';
  reviewStatus: 'PENDING' | 'NOT_APPLICABLE';
  items: { campaignItemId: UUID; quantity: IntegerQuantity }[]; evidenceVersionIds: UUID[];
  recordedAt: UtcTimestamp; auditEventId: UUID; blockchainStatus: ChainStatus;
}
export interface ProblemDetail {
  type: string; title: string; status: number; code: string; detail: string;
  instance: string; traceId: string; fieldErrors: { field: string; code: string }[];
}
// These are fixture view models, never fields added to a production HTTP DTO.
export interface ReceiptContextMockAssumption {
  selectedContributionId: UUID | null; selectableCommitments: { id: UUID; status: string }[];
  items: { contributionItemId: UUID; campaignItemId: UUID; name: string; unit: string;
    pledgedQty: number; receivedQty: number; outstandingQty: number }[];
}
export interface CaseContextMockAssumption {
  caseId: UUID; organizationId: UUID; campaignId: UUID; status: 'IN_PROGRESS'; openDisputeCount: number;
  availableEvidenceVersionIds: UUID[];
  deliveryAvailableItems: { campaignItemId: UUID; name: string; unit: string; availableQty: number }[];
}
