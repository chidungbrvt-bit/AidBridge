"use client";

import type { RefObject } from "react";
import { useI18n } from "@/components/i18n-provider";

export type FieldIssue = { field: string; code: string; message?: string };
export type ErrorInfo = { status?: number; code: string; message: string; fieldErrors: FieldIssue[]; unknownOutcome: boolean };

// Keep transport details out of the screen and never render an arbitrary server body.
export function describeError(error: unknown): ErrorInfo {
  const value = error && typeof error === "object" ? error as Record<string, unknown> : {};
  const problem = value.problem && typeof value.problem === "object"
    ? value.problem as Record<string, unknown> : value;
  const status = typeof problem.status === "number" ? problem.status : typeof value.status === "number" ? value.status : undefined;
  const code = typeof problem.code === "string" ? problem.code : "NETWORK_ERROR";
  const messages: Record<string, string> = {
    VALIDATION_ERROR: "Check the highlighted fields and try again.",
    FORBIDDEN: "Your current membership does not allow this action.",
    RESOURCE_NOT_FOUND: "This resource is unavailable in your organization.",
    SELF_APPROVAL: "The creator cannot review their own case, even with a verifier role.",
    VERSION_CONFLICT: "This case changed. Refresh the case and review the new revision before submitting again.",
    INVALID_STATE: "This case is no longer available for this action. Refresh the case.",
    QUANTITY_EXCEEDED: "The quantity exceeds the current availability. Check the refreshed totals.",
    DISPUTE_OPEN: "Resolve the open dispute before continuing.",
    SELF_CONFIRMATION: "The person who recorded the delivery cannot confirm it.",
    ALREADY_CONFIRMED: "This delivery already has a decision. Refresh the case.",
    IDEMPOTENCY_CONFLICT: "This request key was already used for a different submission. Refresh before starting another action.",
    CSRF_INVALID: "The security token expired. Try the same action again after the token refresh.",
    UNAUTHENTICATED: "Your session expired. Sign in again.",
    DEPENDENCY_UNAVAILABLE: "The service is unavailable. Retry the same request to safely resolve its outcome.",
    TOO_MANY_REQUESTS: "Too many requests. Wait a moment before trying again.",
    NETWORK_ERROR: "The request could not be completed. Check the connection and try again.",
  };
  const rawFields = Array.isArray(problem.fieldErrors) ? problem.fieldErrors : [];
  const fieldErrors = rawFields.filter((item): item is FieldIssue => !!item && typeof item === "object" && typeof item.field === "string" && typeof item.code === "string");
  return { status, code, message: messages[code] ?? "The request could not be completed. Please try again.", fieldErrors, unknownOutcome: status === undefined || status >= 500 };
}

export function focusFirstError(form: RefObject<HTMLFormElement | null>, issues: FieldIssue[]) {
  requestAnimationFrame(() => {
    const name = issues[0]?.field;
    if (name && form.current) {
      const element = form.current.elements.namedItem(name);
      if (element instanceof HTMLElement) { element.focus(); return; }
      if (element instanceof RadioNodeList && element[0] instanceof HTMLElement) { element[0].focus(); return; }
      if (name.startsWith("evidenceFixtureKeys")) {
        form.current.querySelector<HTMLInputElement>('input[name="evidenceFixtureKeys"]')?.focus();
        return;
      }
    }
    form.current?.querySelector<HTMLElement>('[role="alert"]')?.focus();
  });
}

export function FieldError({ name, issues }: { name: string; issues: FieldIssue[] }) {
  const { t } = useI18n();
  const matching = issues.filter(issue => issue.field === name);
  if (!matching.length) return null;
  return <span className="field-error" id={`error-${name}`}>{matching.map(issue => t(issue.code.replaceAll("_", " ").toLowerCase())).join("; ")}</span>;
}

export function ErrorNotice({ error }: { error: ErrorInfo | null }) {
  const { t } = useI18n();
  if (!error) return null;
  return <div className="notice error" role="alert" tabIndex={-1}><strong>{t(error.message)}</strong><div className="muted">{t("Error code: {code}", { code: error.code })}</div></div>;
}

export function StatusBadge({ status }: { status: string }) {
  const { status: label } = useI18n();
  return <span className={`status status-${status.toLowerCase()}`}>{label(status)}</span>;
}

export function LoadingCard({ label = "Loading cases…" }: { label?: string }) {
  const { t } = useI18n();
  return <div className="card loading" role="status" aria-live="polite"><span className="loading-dot" />{t(label)}</div>;
}
