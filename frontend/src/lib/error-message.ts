// User-facing wording for API and simulated errors. Status numbers and codes such as
// "400 VALIDATION_ERROR" stay in data attributes and logs, never in the visible text.

const MESSAGES: Readonly<Record<string, string>> = {
  VALIDATION_ERROR: "Check the highlighted fields and try again.",
  FORBIDDEN: "Your current membership does not allow this action.",
  RESOURCE_NOT_FOUND: "This resource is unavailable in your organization.",
  SELF_APPROVAL: "The creator cannot review their own case, even with a verifier role.",
  VERSION_CONFLICT: "This case changed. Refresh the case and review the new revision before submitting again.",
  INVALID_STATE: "This case is no longer available for this action. Refresh the case.",
  QUANTITY_EXCEEDED: "The quantity exceeds the current availability. Check the refreshed totals.",
  EVIDENCE_REQUIRED: "Select at least one evidence file before recording a delivery.",
  DISPUTE_OPEN: "Resolve the open dispute before continuing.",
  SELF_CONFIRMATION: "The person who recorded the delivery cannot confirm it.",
  ALREADY_CONFIRMED: "This delivery already has a decision. Refresh the case.",
  IDEMPOTENCY_CONFLICT: "This request key was already used for a different submission. Refresh before starting another action.",
  CSRF_INVALID: "The security token expired. Try the same action again after the token refresh.",
  UNAUTHENTICATED: "Your session expired. Sign in again.",
  DEPENDENCY_UNAVAILABLE: "The service is unavailable. Retry the same request to safely resolve its outcome.",
  GATEWAY_TIMEOUT: "The service took too long to respond. Retry the same request to safely resolve its outcome.",
  TOO_MANY_REQUESTS: "Too many requests. Wait a moment before trying again.",
  NETWORK_ERROR: "The request could not be completed. Check the connection and try again.",
};

const FALLBACK = "The request could not be completed. Please try again.";

/** Plain-language message for an error code. */
export function errorMessage(code: string | undefined): string {
  return (code && MESSAGES[code]) || FALLBACK;
}

/** Prefer the specific detail the server or simulator gave; otherwise explain the code. */
export function readableError(code: string | undefined, detail?: string | null): string {
  const text = detail?.trim();
  return text ? text : errorMessage(code);
}
