import type { CreateCaseRequest, EvidenceFixture, MeResponse } from "./types";

/** Fictional mock credentials only. Never used to bootstrap a real backend. */
export const MOCK_ORGANIZATIONS = [
  { id: "10000000-0000-4000-8000-000000000001", name: "Riverlight Aid" },
  { id: "10000000-0000-4000-8000-000000000002", name: "Harbor Relief" },
] as const;
export const MOCK_CASE_ID = "30000000-0000-4000-8000-000000000001";
export const MOCK_PASSWORD = "Mock-only-123!";
export const MOCK_IDENTITIES: { email: string; password: string; user: MeResponse }[] = [
  { email: "minh.a@example.test", password: MOCK_PASSWORD, user: { userId: "20000000-0000-4000-8000-000000000001", fullName: "Alex Nguyen", status: "ACTIVE", platformRole: "USER", donorEnabled: false, memberships: [{ organizationId: MOCK_ORGANIZATIONS[0].id, roles: ["VOLUNTEER", "VERIFIER"], status: "ACTIVE" }, { organizationId: MOCK_ORGANIZATIONS[1].id, roles: ["ORG_ADMIN"], status: "DISABLED" }] } },
  { email: "minh.b@example.test", password: MOCK_PASSWORD, user: { userId: "20000000-0000-4000-8000-000000000002", fullName: "Blair Tran", status: "ACTIVE", platformRole: "USER", donorEnabled: false, memberships: [{ organizationId: MOCK_ORGANIZATIONS[0].id, roles: ["VERIFIER"], status: "ACTIVE" }] } },
  { email: "minh.reader@example.test", password: MOCK_PASSWORD, user: { userId: "20000000-0000-4000-8000-000000000003", fullName: "Casey Le", status: "ACTIVE", platformRole: "USER", donorEnabled: true, memberships: [{ organizationId: MOCK_ORGANIZATIONS[0].id, roles: ["DELIVERY_CONFIRMER"], status: "ACTIVE" }] } },
  { email: "minh.outsider@example.test", password: MOCK_PASSWORD, user: { userId: "20000000-0000-4000-8000-000000000004", fullName: "Drew Pham", status: "ACTIVE", platformRole: "PLATFORM_ADMIN", donorEnabled: false, memberships: [{ organizationId: MOCK_ORGANIZATIONS[1].id, roles: ["VOLUNTEER", "VERIFIER"], status: "ACTIVE" }] } },
  { email: "minh.admin@example.test", password: MOCK_PASSWORD, user: { userId: "20000000-0000-4000-8000-000000000005", fullName: "Morgan Lin", status: "ACTIVE", platformRole: "USER", donorEnabled: false, memberships: [{ organizationId: MOCK_ORGANIZATIONS[0].id, roles: ["ORG_ADMIN"], status: "ACTIVE" }] } },
];
export const MOCK_EVIDENCE: Record<string, EvidenceFixture[]> = {
  [MOCK_ORGANIZATIONS[0].id]: [{ key: "riverlight-intake-v1", label: "Fictional intake record", mimeType: "text/plain", byteSize: 0 }, { key: "riverlight-needs-v1", label: "Fictional needs checklist", mimeType: "text/plain", byteSize: 0 }],
  [MOCK_ORGANIZATIONS[1].id]: [{ key: "harbor-intake-v1", label: "Fictional intake record", mimeType: "text/plain", byteSize: 0 }],
};
export const MOCK_CREATE_CASE: CreateCaseRequest = {
  organizationId: MOCK_ORGANIZATIONS[0].id, beneficiaryAlias: "Household Willow", approximateArea: "North River district", internalTitle: "Essential supplies for Household Willow", publicTitle: "Two weeks of essential supplies", publicSummary: "Fictional demonstration household requesting food and hygiene supplies.", priority: "NORMAL",
  consent: { basis: "CONSENT", scope: "Fictional aid assessment and independent review", capturedAt: "2026-11-09T08:00:00.000Z", publicSummaryAllowed: true },
  needs: [{ name: "Rice", unit: "bag", quantity: 2 }, { name: "Hygiene kit", unit: "kit", quantity: 2 }],
  evidenceFixtureKeys: ["riverlight-intake-v1", "riverlight-needs-v1"],
};
