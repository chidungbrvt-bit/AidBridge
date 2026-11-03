# API Specification

Ngày tạo: 03/11/2026. Owner: B, reviewer F/C. Status: contract proposal, chưa có endpoints chạy. Routes gốc từ PDF p.13 được giữ; routes bổ sung cần thiết cho receipt, retries, changes, disputes và multi-item UI được ghi rõ.

## Common contract

Base `/api`; JSON UTF-8; fields camelCase; UUID strings; ISO-8601 UTC timestamps; quantities integer 1..1,000,000. List pagination `page=0&size=20`, max100, stable sort `(createdAt,id)`; response `{items,page,size,totalElements}`. Resource IDs ngoài quyền trả 404, không reveal tồn tại; action role thiếu trên resource đã được phép đọc trả 403. Body không cho actorId/org override.

Success trả resource DTO trực tiếp. POST create 201 + Location; actions 200; logout 204. SQL success không có nghĩa blockchain success: response gồm `auditEventId`, `blockchainStatus` khi action có milestone, initial PENDING_ON_CHAIN; HTTP không chờ RPC.

Error `application/problem+json`:

```json
{
  "type": "urn:aidbridge:error:quantity-exceeded",
  "title": "Quantity exceeds remaining capacity",
  "status": 409,
  "code": "QUANTITY_EXCEEDED",
  "detail": "Refresh the campaign and try again.",
  "instance": "/api/campaigns/00000000-0000-4000-8000-000000000001/commitments",
  "traceId": "opaque-request-id",
  "fieldErrors": [{"field":"items[0].quantity","code":"EXCEEDS_REMAINING"}]
}
```

Status/code: 400 VALIDATION_ERROR; 401 UNAUTHENTICATED/INVALID_CREDENTIALS; 403 FORBIDDEN/SELF_APPROVAL/SELF_CONFIRMATION/CSRF_INVALID; 404 RESOURCE_NOT_FOUND; 409 INVALID_STATE/QUANTITY_EXCEEDED/VERSION_CONFLICT/IDEMPOTENCY_CONFLICT/ALREADY_CONFIRMED/DISPUTE_OPEN; 429 TOO_MANY_REQUESTS; 503 DEPENDENCY_UNAVAILABLE khi database/service không thể commit. RPC down sau DB commit vẫn business success + pending, không trả lỗi khiến user nghĩ operation chưa tồn tại. Không stack traces hoặc PII trong error.

## Authentication, ownership và idempotency

CSRF required cho mọi browser mutation kể cả login; GET csrf trả token/headerName, no-store. Cookie JSESSIONID HttpOnly/Secure production/SameSite=Lax; session rotate login, invalidate logout, idle timeout30m. Frontend proxy pass cookies + CSRF header, không log chúng. FE clear private state khi logout/401. Same-origin production; CORS mặc định deny external origins.

Business POST/PATCH ngoài login/logout yêu cầu `Idempotency-Key` 16..128 chars opaque UUID recommended. Scope `(actorId, operation including resourceId, key)`; digest canonical validated payload. Trong transaction claim unique key, execute, persist resource response. Concurrent same request đợi commit rồi replay; rollback không để permanent IN_PROGRESS row. Same key/different digest 409; request retry same key trả original status/body/Location, thêm `Idempotent-Replayed: true`. Retain keys suốt demo lifetime. DB unknown outcome: frontend retry cùng key, không tạo key mới. Case PATCH/resubmit/review actions dùng `expectedVersion` của case; optimistic mismatch 409. Current actor/ownership được kiểm tra cả khi replay để không rò data sau revoke.

Bất kỳ command có nhiều item: unique item IDs, >=1 line, không zero/negative, no duplicate line, atomic all-or-nothing. Thời điểm do actor ghi (`receivedAt`, `handedOverAt`) không tương lai quá5 phút và không trước case/campaign tương ứng; server `recordedAt` là audit time độc lập. Text trim, length limits, không nhận HTML.

## DTO dictionary

| DTO | Fields và visibility |
|---|---|
| LoginRequest | email:string<=254, password:string; không log/persist request |
| MeResponse | userId, displayName, donorEnabled, platformAdmin, memberships[{organizationId,roles,active}] |
| CreateOrganizationRequest | name<=160, relayerAddress?:EVM address; server random publicRef; demo admin provisioning only |
| OrganizationResponse | id,name,status,rolesOfCurrentUser; relayer public address khi admin, no secrets |
| NeedInput | name<=120, unit<=40, quantity:int; no duplicate(name,unit); 1..20 need lines/case |
| ConsentInput | basis:CONSENT/REPRESENTATIVE, scope<=200, capturedAt, publicSummaryAllowed:boolean; simulated record |
| CreateCaseRequest | organizationId (chỉ chọn active membership), beneficiaryAlias<=120, approximateArea<=160, internalTitle<=160, publicTitle<=160?, publicSummary<=2000?, priority:NORMAL/URGENT, consent:ConsentInput, needs:NeedInput[], evidenceFixtureKeys:string[1..10], supersedesCaseId?:UUID cùng org |
| PatchCaseRequest | expectedVersion, internalTitle?, publicTitle?, publicSummary?, approximateArea?, priority?, needs?:NeedInput[]; replace needs chỉ khi editable; evidence qua endpoint riêng |
| CaseDetailResponse | id,organizationId,creator{id,displayName},status,version,needs[{id,name,unit,quantity}],consent summary, evidenceVersions[],reviewHistory[],campaignId?,disputeSummary,goodsTotals,createdAt; internal only |
| DecisionRequest | expectedVersion, reason?:<=2000, reviewedEvidenceVersionIds:UUID[]; reason required reject/request-changes; approval phải exact current selected versions |
| DecisionResponse | verificationId,caseId,decision,caseStatus,caseVersion,reviewerId,reviewedAt,auditEventId,blockchainStatus |
| EvidenceVersionRequest | expectedCaseVersion,evidenceFileId?:UUID,fixtureKey:allowlisted key,supersedesVersionId?:UUID; new file nếu no fileId; versionNo server assigned |
| EvidenceVersionResponse | id,evidenceFileId,versionNo,mimeType,byteSize,sha256,createdAt; no physical path/public URL |
| CreateCampaignRequest | caseId,title<=160,publicSummary<=2000; item targets server copy all approved case needs, không nhận arbitrary item IDs/targets |
| CampaignResponse | id,status,title,publicSummary,approximateArea,organization{name,publicRef},items[{id,name,unit,targetQty,pledgedQty,receivedQty,deliveredQty,confirmedQty,remainingPledgeQty}],auditUrl; no beneficiary/user identity |
| CommitmentRequest | items:[{campaignItemId,quantity}]; donor từ session |
| CommitmentResponse | id,campaignId,items[{id,campaignItemId,pledgedQty,cancelledQty,receivedQty,outstandingQty}],status,createdAt,auditEventId,blockchainStatus |
| CancellationRequest | items:[{contributionItemId,quantity}],reason<=500; only unreceived remainder |
| ReceiptRequest | receivedAt,items:[{contributionItemId,quantity}],note?:<=500 |
| ReceiptResponse | id,contributionId,items,receivedAt,recordedAt,recorderId,auditEventId,blockchainStatus |
| DeliveryRequest | outcome:DELIVERED/FAILED,handedOverAt?,items:[{campaignItemId,quantity}],evidenceVersionIds:UUID[],reason?:<=2000; FAILED items=[],reason required; success >=1 item and >=1 evidence |
| DeliveryResponse | id,caseId,recorderId,outcome,reviewStatus,items,evidenceVersionIds,recordedAt,auditEventId,blockchainStatus |
| ConfirmationRequest | decision:CONFIRMED/REQUIRES_REVIEW,reviewedEvidenceVersionIds,reason?:<=2000; evidence set must equal delivery snapshot |
| ConfirmationResponse | id,deliveryId,decision,reviewerId,reviewedAt,disputeId?,goodsTotals,auditEventId,blockchainStatus |
| DisputeRequest | deliveryId?:UUID,reason<=2000; donor endpoints resolve case through own campaign only |
| ResolveDisputeRequest | outcome:UPHOLD/VOID_DELIVERY,reason<=2000,reviewEvidenceVersionIds:UUID[]; void only if dispute has delivery |
| DisputeResponse | id,caseId,deliveryId?,status,reason (internal),resolution?,timestamps; donor version only own dispute summary |
| AuditEventResponse | eventId,type,occurredAt,publicActorRole?,businessStatus,evidenceReviewStatus,blockchain{status,chainId,contractAddress,txHash?,blockNumber?,explorerUrl?,errorCode?}; no private manifest/salt/user email |

Public and internal responses dùng DTO classes khác nhau; public timeline không trả original evidence digest/version keys nếu có thể link file. Commitment response thuộc donor; staff trong org có receipt-related projection. Không trả historical sensitive HTTP request body từ idempotency table.

## Endpoint specification

Abbreviations: FV/CV/OA/DC/PA như Business Workflows; Auth=enabled authenticated account. Common validation/errors/idempotency áp dụng trừ chỗ nêu khác. `local` nghĩa audit không cần chain job, không phải mutation không audit. Các endpoint bổ sung blueprint được đánh dấu `+`.

| ID / method / URL | Purpose / role | Request -> response / success | Business validation & DB effects | Chain effect | Acceptance / required tests |
|---|---|---|---|---|---|
| A01 GET /api/auth/csrf + | Anonymous/Auth, bootstrap CSRF | none -> {token,headerName},200 | No-store, establish/refresh token | none | Token usable for login; token refresh after login/logout |
| A02 POST /api/auth/login | Anonymous | LoginRequest -> MeResponse,200 | CSRF, password hash, enabled account; rotate session; generic failure | none | Valid credentials set secure session; invalid->401; CSRF->403; no Idempotency-Key |
| A03 POST /api/auth/logout + | Auth | none ->204 | CSRF; invalidate session; no body stored | none | Old session unusable; logout UI clears private state |
| A04 GET /api/auth/me + | Auth | none -> MeResponse,200 | Current account/memberships reloaded or invalidated after role changes | none | 401 no session; suspended membership not returned as active |
| O01 POST /api/organizations | PA | CreateOrganizationRequest -> OrganizationResponse,201 | Valid name/address, approved demo org + audit; no public onboarding | Admin registration job if relayer provided | Non-PA403; duplicate idempotent create once |
| O02 GET /api/organizations/{id} | Own staff/PA | none -> OrganizationResponse,200 | Membership/minimal PA projection; no beneficiary data | none | Other org404; admin no automatic evidence access |
| E01 GET /api/evidence-fixtures + | Own staff | organizationId -> fixture metadata list,200 | Allowlist metadata only; no filesystem path or content public | none | Cannot enumerate another org fixture keys |
| C01 POST /api/cases | FV | CreateCaseRequest -> CaseDetailResponse,201 | BR-01, consent, evidence fixtures owned org, need validation; create beneficiary/consent/case/need/evidence links + audit | local | Successful SUBMITTED; invalid org/fixture rejected; atomic rollback |
| C02 GET /api/cases | FV/CV/OA/DC | organizationId,status,page,size -> page CaseSummary,200 | Query scoped to active org; no private notes on summary | none | Only own org results; invalid filter400 |
| C03 GET /api/cases/{id} | Own staff | none -> CaseDetailResponse,200 | Case authorization before joins; PA only dispute-safe projection | none | Cross-org404; public caller401 |
| C04 PATCH /api/cases/{id} | FV creator | PatchCaseRequest -> CaseDetailResponse,200 | SUBMITTED/CHANGES_REQUESTED, version; replace needs before review; audit + version | local | Verified409; stale409; no creator/org reassignment |
| C05 POST /api/cases/{id}/resubmit + | FV creator | {expectedVersion} -> CaseDetailResponse,200 | CHANGES_REQUESTED, required evidence/needs present; SUBMITTED + audit | local | Missing evidence400; invalid state409 |
| E02 POST /api/cases/{id}/evidence-versions + | FV creator | EvidenceVersionRequest -> EvidenceVersionResponse,201 | Editable case, expectedVersion; immutable version, links + audit; supersedes same logical file | local | Previous bytes/hash preserved; reviewed case409; unauthorized fixture404 |
| E03 GET /api/evidence-versions/{id}/content + | Authorized own-org staff reviewing related case | none -> binary MIME,200 | Access audit, no-store, Content-Disposition attachment; allowlisted private fixture resolved server-side | none | Cross-org404; traversal impossible; PA no automatic read |
| V01 POST /api/cases/{id}/verify | CV | DecisionRequest -> DecisionResponse,200 | SUBMITTED; reviewer!=creator; snapshot all current evidence; verification APPROVED + VERIFIED + audit/job | recordCaseVerification | Self403 even multi-role; racing approve/reject one409 |
| V02 POST /api/cases/{id}/reject | CV | DecisionRequest -> DecisionResponse,200 | Same identity guard; reason required; REJECTED + immutable decision | local | Reason missing400; no publish possible |
| V03 POST /api/cases/{id}/request-changes + | CV | DecisionRequest -> DecisionResponse,200 | Reason and reviewed refs; CHANGES_REQUESTED + immutable decision | local | FV can revise/resubmit; not autoapprove |
| P01 POST /api/campaigns | OA | CreateCampaignRequest -> CampaignResponse,201 | VERIFIED case, no dispute/existing campaign; copy exact approved need targets to draft | local | Unverified409; duplicate campaign409 |
| P02 POST /api/campaigns/{id}/publish + | OA | {} -> CampaignResponse,200 | DRAFT; current approved case snapshot matches; public summary allowed/sanitized; IN_PROGRESS | local | No public PII; targets immutable after publish |
| P03 GET /api/campaigns | Public | page,size -> page CampaignResponse,200 | Published/FULFILLED/CLOSED only, no draft; sanitized view | none | Draft not leaked; no internal notes |
| P04 GET /api/campaigns/{id} | Public; draft OA only | none -> CampaignResponse,200 | Public DTO; draft auth scoped | none | Private case ID/details absent; wrong draft404 |
| P05 POST /api/campaigns/{id}/close + | OA | {} -> CampaignResponse,200 | FULFILLED; every item confirmed target; no dispute/review; campaign CLOSED/case COMPLETED + audit | local | Pledged-only409; disputed409; multi-item gap409 |
| G01 POST /api/campaigns/{id}/commitments | Donor | CommitmentRequest -> CommitmentResponse,201 | Published, no dispute, lock items; sum activepledge+qty<=target every line; create once | recordAidCommitment | Atomic multi-line, concurrent bounds, retry no duplicate |
| G02 GET /api/me/commitments + | Donor | page,size -> own page,200 | Only actor's contributions; receipt progress + separately labeled campaign delivery totals | none | Other donor data absent; no lot provenance claim |
| G03 POST /api/commitments/{id}/cancellations + | Same donor | CancellationRequest -> CommitmentResponse,200 | quantity<=outstanding, lock items; increment cancelled + audit/job | recordCommitmentReduction | Received part409; capacity released once |
| R01 POST /api/commitments/{id}/receipts + | OA own org | ReceiptRequest -> ReceiptResponse,201 | Contribution active, same lines; quantity<=active pledge minus received; immutable receipt + audit/job | recordGoodsReceipt | Partial receipt works; over-receive409; receipt not delivery |
| D01 POST /api/cases/{id}/deliveries | FV | DeliveryRequest -> DeliveryResponse,201 | IN_PROGRESS, no dispute, items in campaign, qty<=received-delivered; successful snapshot immutable; failed attempt zero effective | recordDelivery for success, local for FAILED | Overdeliver409; success not confirmed; retries once |
| D02 POST /api/deliveries/{id}/confirm | DC | ConfirmationRequest -> ConfirmationResponse,200 | Success nonvoid delivery, separate user, no open dispute; exact evidence; decision + totals; if requires review create dispute | confirmDelivery OR flagDispute | Self403; duplicate different key409; no completion on require-review |
| S01 POST /api/cases/{id}/disputes + | Own OA/CV/DC | DisputeRequest -> DisputeResponse,201 | Valid relation/reason; OPEN; reopen completed case; block operational writes | flagDispute | New dispute after close reopens; unrelated404 |
| S02 POST /api/campaigns/{id}/disputes + | Donor with own contribution | {reason} -> sanitized DisputeResponse,201 | Resolve case server-side; donor gets no private case/evidence | flagDispute | Donor cannot invent private delivery IDs; unrelated404 |
| S03 POST /api/disputes/{id}/resolve + | PA | ResolveDisputeRequest -> DisputeResponse,200 | OPEN, reason; UPHOLD or VOID_DELIVERY; new audit, recompute projections; no auto-confirm/close | resolveDispute, includes optional delivery void | NonPA403; void absent delivery400; all open disputes must resolve |
| T01 GET /api/cases/{id}/audit-trail | Own staff / dispute-safe PA | page,size -> internal audit DTO page,200 | Case ownership; actor identity for permitted internal view, no secrets/PII in payload | Read confirmed projection | Cross-org404; chain status honest |
| T02 GET /api/campaigns/{id}/audit-trail + | Public | page,size -> public AuditEventResponse page,200 | Published campaign only; role labels instead of account identities; no evidence/consent metadata | Read chain projection | Pending/failed/confirmed different; privacy regression |
| T03 POST /api/audit-events/{eventId}/retry + | PA/operator authenticated internal tooling | {} -> {eventId,blockchainStatus},200 | FAILED only or manual reconcile; preserve ID, snapshot, tx attempts; no user business mutation | Requeue after reconcile | Already confirmed no rebroadcast; missing receipt pending not forced fail |

All endpoint failures: 401 for required auth; 403 role/CSRF; 404 scope; 400 fields; 409 state/quantity/replay conflict as applicable. Dependency downtime trước SQL commit503. GET binary errors vẫn problem+json, không trả arbitrary file bytes.

## End-to-end request example

Donor authenticated with CSRF sends `POST /api/campaigns/{id}/commitments`, `Idempotency-Key: <new UUID>`:

```json
{"items":[{"campaignItemId":"10000000-0000-4000-8000-000000000001","quantity":2},{"campaignItemId":"10000000-0000-4000-8000-000000000002","quantity":2}]}
```

Server validates both lines under locks, returns 201 with commitment ID, per-line totals and PENDING_ON_CHAIN. If second line exceeds remaining target, entire operation409 and neither line is reserved. Network retry cùng key nhận original201; different body cùng key409. IDs trên là ví dụ off-chain, không encode beneficiary và không dùng làm public chain refs.

## API ownership / freeze / verification

B owner DTO/schema/error contract; F review usability/loading/error; C review milestone payloads/IDs/causal ordering. Freeze initial C/V/T slice by05/11; expand goods DTOs without breaking existing fields. Breaking changes ghi decision + update mocks/OpenAPI/tests cùng task trước merge. Generated OpenAPI sau implementation phải match document; không hand-wave endpoint acceptance bằng Swagger screenshot.

First slice: C01 -> V01 (other actor) -> outbox -> receipt/event -> T01/T02 -> FE status. Tests reference IDs tại Test Strategy và Backlog. Future class/output paths là expectations, chưa tồn tại trong workspace.
