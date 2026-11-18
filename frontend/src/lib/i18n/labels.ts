/**
 * Human-readable English labels for API codes (roles, statuses, audit event types).
 * Codes stay in data and CSS classes; only the label is shown. Vietnamese translates
 * these English labels through vi.ts like any other text.
 */
const labels: Readonly<Record<string, string>> = {
  // roles
  ORG_ADMIN: "Organization admin", ORGANIZATION_ADMIN: "Organization admin", VERIFIER: "Verifier", CASE_VERIFIER: "Case verifier",
  VOLUNTEER: "Volunteer", FIELD_VOLUNTEER: "Field volunteer", DELIVERY_CONFIRMER: "Delivery confirmer",
  PLATFORM_ADMIN: "Platform admin", DONOR: "Donor", USER: "User",
  // case
  DRAFT: "Draft", SUBMITTED: "Submitted", VERIFIED: "Verified", REJECTED: "Rejected", CHANGES_REQUESTED: "Changes requested",
  IN_PROGRESS: "In progress", COMPLETED: "Completed", DISPUTED: "Disputed", CANCELLED: "Cancelled",
  URGENT: "Urgent", NORMAL: "Normal", CONSENT: "Consent", REPRESENTATIVE: "Representative",
  // blockchain
  NOT_REQUIRED: "Not required", NOT_APPLICABLE: "Not applicable", PENDING_ON_CHAIN: "Pending on chain",
  CONFIRMED_ON_CHAIN: "Confirmed on chain", FAILED_ON_CHAIN: "Failed on chain", CONFIRMED: "Confirmed", FAILED: "Failed", UNKNOWN: "Unknown",
  // campaign, pledge, delivery, dispute
  PUBLISHED: "Published", FULFILLED: "Fulfilled", CLOSED: "Closed", PLEDGED: "Pledged", PARTIALLY_RECEIVED: "Partially received",
  RECEIVED: "Received", PARTIALLY_CANCELLED: "Partially cancelled", DELIVERED: "Delivered", PENDING: "Pending",
  REQUIRES_REVIEW: "Needs review", OPEN: "Open", RESOLVED: "Resolved", VOIDED: "Voided", UPHOLD: "Upheld", UPHELD: "Upheld",
  VOID_DELIVERY: "Delivery voided", APPROVED: "Approved",
  // audit event types
  CASE_CREATED: "Case created", CASE_UPDATED: "Case updated", CASE_RESUBMITTED: "Case resubmitted", CASE_VERIFIED: "Case verified",
  CASE_REJECTED: "Case rejected", CASE_CHANGES_REQUESTED: "Case changes requested", CASE_COMPLETED: "Case completed",
  CAMPAIGN_CREATED: "Campaign created", CAMPAIGN_PUBLISHED: "Campaign published", CAMPAIGN_CLOSED: "Campaign closed",
  CAMPAIGN_FULFILLED: "Campaign fulfilled", AID_PLEDGED: "Goods pledged", COMMITMENT_REDUCED: "Pledge reduced",
  GOODS_RECEIVED: "Goods received", DELIVERY_RECORDED: "Delivery recorded", DELIVERY_ATTEMPT_FAILED: "Delivery attempt failed",
  DELIVERY_CONFIRMED: "Delivery confirmed", DELIVERY_REVIEW_REQUIRED: "Delivery needs review", DELIVERY_VOIDED: "Delivery voided",
  DISPUTE_OPENED: "Dispute opened", DISPUTE_RESOLVED: "Dispute resolved", EVIDENCE_VERSION_CREATED: "Evidence version created",
  EVIDENCE_CONTENT_READ: "Evidence content read",
};

/** "SOME_NEW_CODE" → "Some new code" when a code has no label yet. */
export function codeLabel(code: string): string {
  const known = labels[code];
  if (known) return known;
  const words = code.replaceAll("_", " ").toLowerCase().trim();
  return words ? words[0].toUpperCase() + words.slice(1) : code;
}
