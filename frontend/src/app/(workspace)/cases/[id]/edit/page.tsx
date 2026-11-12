import { EditCase } from "@/components/cases/edit-case";

export default async function EditCasePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <EditCase id={id} />;
}
