"use client";

import type { RefObject } from "react";
import { useI18n } from "@/components/i18n-provider";
import { errorMessage } from "@/lib/error-message";

export type FieldIssue = { field: string; code: string; message?: string };
export type ErrorInfo = { status?: number; code: string; message: string; fieldErrors: FieldIssue[]; unknownOutcome: boolean };

// Keep transport details out of the screen and never render an arbitrary server body.
export function describeError(error: unknown): ErrorInfo {
  const value = error && typeof error === "object" ? error as Record<string, unknown> : {};
  const problem = value.problem && typeof value.problem === "object"
    ? value.problem as Record<string, unknown> : value;
  const status = typeof problem.status === "number" ? problem.status : typeof value.status === "number" ? value.status : undefined;
  const code = typeof problem.code === "string" ? problem.code : "NETWORK_ERROR";
  const rawFields = Array.isArray(problem.fieldErrors) ? problem.fieldErrors : [];
  const fieldErrors = rawFields.filter((item): item is FieldIssue => !!item && typeof item === "object" && typeof item.field === "string" && typeof item.code === "string");
  return { status, code, message: errorMessage(code), fieldErrors, unknownOutcome: status === undefined || status >= 500 };
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
  return <div className="notice error" role="alert" tabIndex={-1} data-error-code={error.code}>{t(error.message)}</div>;
}

export function StatusBadge({ status }: { status: string }) {
  const { status: label } = useI18n();
  return <span className={`status status-${status.toLowerCase()}`}>{label(status)}</span>;
}

export function LoadingCard({ label = "Loading cases…" }: { label?: string }) {
  const { t } = useI18n();
  return <div className="card loading" role="status" aria-live="polite"><span className="loading-dot" />{t(label)}</div>;
}
