import { DisputeReviewPanel } from "@/components/goods/dispute-review-panel";

export default async function DisputePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <DisputeReviewPanel key={id} id={id} />;
}
