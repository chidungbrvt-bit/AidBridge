import { notFound } from "next/navigation";
import { AuditFeed } from "@/components/audit/audit-feed";
import { isPublicCaseRef } from "@/lib/audit";

export default async function PublicAuditPage({ params }: { params: Promise<{ publicCaseRef: string }> }) {
  const { publicCaseRef } = await params;
  if (!isPublicCaseRef(publicCaseRef) || process.env.NEXT_PUBLIC_API_MODE === "mock") notFound();
  return <main className="main-content">
    <AuditFeed scope={{ publicUrl: `/api/public/cases/${publicCaseRef}/audit` }} />
  </main>;
}
