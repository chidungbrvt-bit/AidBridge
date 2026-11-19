import { notFound } from "next/navigation";
import { AuditFeed } from "@/components/audit/audit-feed";
import { isPublicCaseRef } from "@/lib/audit";
import { LanguageToggle } from "@/components/i18n-provider";
import { ThemeToggle } from "@/components/theme-toggle";

export default async function PublicAuditPage({ params }: { params: Promise<{ publicCaseRef: string }> }) {
  const { publicCaseRef } = await params;
  if (!isPublicCaseRef(publicCaseRef) || process.env.NEXT_PUBLIC_API_MODE === "mock") notFound();
  return <main className="main-content">
    <div className="public-audit-header"><LanguageToggle /><ThemeToggle /></div>
    <AuditFeed scope={{ publicUrl: `/api/public/cases/${publicCaseRef}/audit` }} />
  </main>;
}
