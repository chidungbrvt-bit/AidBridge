"use client";

import { useI18n } from "@/components/i18n-provider";

export function AuditPreviewHeader() {
  const { t } = useI18n();
  return <header className="page-header"><div><p className="eyebrow">{t("FIXTURE PREVIEW")}</p><h1>{t("Audit status examples")}</h1></div></header>;
}
