/** Only the dispute detail route can be carried through the login query. */
export function disputeReturnPath(value: string | null): string | null {
  return value && /^\/disputes\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    ? value : null;
}
