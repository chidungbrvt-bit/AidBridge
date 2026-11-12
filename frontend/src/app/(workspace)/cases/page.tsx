import { CaseList } from "@/components/cases/case-list";

export default async function CasesPage({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const { status } = await searchParams;
  return <CaseList initialStatus={status ?? ""} />;
}
