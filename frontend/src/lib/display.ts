/** Human-facing labels. Internal UUIDs stay in values and data attributes, not in visible text. */
export function formatUtc(iso: string | null | undefined): string {
  if (!iso) return "time unavailable";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "time unavailable"
    : `${date.toLocaleString("en-GB", { timeZone: "UTC", dateStyle: "short", timeStyle: "short" })} UTC`;
}

/** Short, non-secret reference a donor can quote to support staff. */
export function shortReference(id: string): string {
  return id.replaceAll("-", "").slice(0, 8).toUpperCase();
}
