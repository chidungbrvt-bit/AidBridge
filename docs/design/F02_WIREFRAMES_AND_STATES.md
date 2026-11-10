# F02 - Wireframes G01/R01/D01 và shared mock handoff

Ngày: 09/11/2026, Asia/Saigon. Người thực hiện: Khoa/Codex. Status: **In Review**. Phạm vi theo `9_11.pdf`: text wireframes, mock requests/responses, shared fixtures và UI-state contracts để Minh review/triển khai F07/F08. Không tạo frontend application hoặc shared API client; không sửa backend/API specification của Dũng.

## 1. Baseline, deliverables và mức độ kiểm chứng

- Repo `D:/Project/AidBridge`, nhánh `khoa`; baseline F02 `236537853fb829ccc8c778a932e2e1597e8304ab`. Đây là commit C01 được Khoa cho phép lưu; chưa push. `git fetch origin` đã chạy, working tree sạch trước F02.
- Đã đọc AGENTS, Master Plan, MVP Scope, Decision Log, F02 backlog, API Specification, Business Workflows, System Design frontend contracts và E2E07 specification. Coding/Done statements cũ trong planning không thay thế kiểm tra source/runtime.
- G01/R01/D01 chưa có implementation trong baseline đã đọc; mọi scenario trong bộ này là **MOCK_NOT_RUNTIME**. Không có actual HTTP, DB write, blockchain tx, screenshot hoặc keyboard test result.
- Giữ primary owner/estimate/deadline của backlog. Khoa làm phần thiết kế được giao; Minh sở hữu shared API client và UI implementation, Dũng sở hữu DTO/backend/schema.

| Artifact | Mục đích |
|---|---|
| [goods-flow.mock.json](../fixtures/f02/goods-flow.mock.json) | 5 snapshots, 4 fictional identities, hai loại hàng, 10 request/response scenarios và before/after totals |
| [goods-flow.types.ts](../fixtures/f02/goods-flow.types.ts) | DTO type declarations và phần mock-only được đặt tên riêng; chưa compile/freeze với backend |
| [endpoint-ui-states.json](../fixtures/f02/endpoint-ui-states.json) | Mapping G01/R01/D01 -> loading/empty/errors/success/focus/recovery |
| [verify-f02.mjs](../fixtures/f02/verify-f02.mjs) | Node built-in checker cho arithmetic, mappings, DTO keys, authorization scenarios và atomic rejection; không gọi API |

Trong tài liệu này, SOURCE = quy tắc đang được tài liệu repo mô tả; ASSUMPTION = chỉ dùng để thiết kế mock; OPEN = cần owner xác nhận. Không coi mock là quyết định protocol mới đã được duyệt.

## 2. Shared fixture và các trạng thái

Cùng hai loại hàng Rice (20 kg) và Drinking Water (4 box), theo chủ đề C01; campaign items, needs và contribution items có namespace UUID khác nhau. Không đồng nhất item IDs giữa các domain. Organization giả đã APPROVED, case IN_PROGRESS, campaign PUBLISHED, không dispute. Không yêu cầu beneficiary account/wallet, không tiền thật hoặc upload UI.

`S0` có hàng từ các contribution giả trước đó: pledged 6 kg/1 box, received 2 kg/0 box, delivered 1 kg/0 box, confirmed 0/0. Delivery 1 kg đó chưa được reviewer xác nhận. Các arrays receipts/deliveries trong snapshots chỉ liệt kê records focal do bộ này tạo, không phải toàn bộ campaign history.

| State / transition | Item | Target | Pledged | Received | Delivered | Confirmed |
|---|---|---:|---:|---:|---:|---:|
| S0 - initial | Rice (kg) | 20 | 6 | 2 | 1 | 0 |
| S0 - initial | Drinking Water (box) | 4 | 1 | 0 | 0 | 0 |
| S1 - G01 pledge +8/+2 | Rice (kg) | 20 | 14 | 2 | 1 | 0 |
| S1 - G01 pledge +8/+2 | Drinking Water (box) | 4 | 3 | 0 | 0 | 0 |
| S2 - R01 partial receive +5/+1 | Rice (kg) | 20 | 14 | 7 | 1 | 0 |
| S2 - R01 partial receive +5/+1 | Drinking Water (box) | 4 | 3 | 1 | 0 | 0 |
| S3 - D01 deliver +4/+1 | Rice (kg) | 20 | 14 | 7 | 5 | 0 |
| S3 - D01 deliver +4/+1 | Drinking Water (box) | 4 | 3 | 1 | 1 | 0 |
| S2F - alternative FAILED attempt | Both | Same as S2 | Same as S2 | Same as S2 | Same as S2 | Same as S2 |

S0 -> S1 -> S2 -> S3 là success flow. S2 -> S2F là nhánh thay thế, không tự chạy sau S3. Các 403/409 reset về beforeState tương ứng, không chạy nối tiếp sau success. Mỗi scenario có totalsBefore/totalsAfter cụ thể để kiểm tra bằng mắt hoặc code.

Giới hạn từng item: `0 <= confirmed <= delivered <= received <= activePledged <= target`. Không có cancellation trong fixture nên pledgedQty bằng activePledged. Focal contribution của donor ở S1 = 8 kg/2 box, S2 đã nhận 5 kg/1 box, outstanding = 3 kg/1 box. Đây là **partial receipt**, không phải partial delivery hoặc đã hoàn thành hỗ trợ.

Campaign totals là pool chung; không nói 4 kg vừa giao là hàng riêng của donor nào. Donor xem contribution receipt progress và campaign delivery totals với nhãn tách biệt. `confirmedQty` vẫn 0 sau D01; không có independent-confirm API/UI implementation trong F02.

## 3. Wireframe G01 - Pledge goods

Vị trí: section của `/campaigns/[id]`, không tạo dashboard mới. Public đọc sanitized campaign qua P04 `GET /api/campaigns/{id}`; mutation cần authenticated account `donorEnabled=true`. Không tự coi một staff role là donor permission. Context là S0, success thành S1.

```text
Simulated flood relief essentials                       [MOCK DATA]
AidBridge Demo Organization | Simulated District
Campaign status: Published

Campaign progress
Item            Unit Target Pledged Received Delivered Confirmed
Rice            kg       20       6        2         1         0
Drinking Water  box       4       1        0         0         0

Pledge goods
Include  Item            Unit  Remaining to pledge  Quantity
[x]      Rice            kg                     14  [8]
[x]      Drinking Water  box                     3  [2]

Pledged goods have not yet been received or delivered.
                                             [Pledge goods]
```

- Visible: public campaign fields, organization display, five per-item progress columns và remaining capacity. Không beneficiary alias, case UUID/private evidence hoặc account identity trong public view.
- Inputs: include checkbox + integer quantity (min 1, step 1) cho selected rows. Unchecked rows bị loại khỏi request, không gửi quantity 0. Unit chỉ đọc; không nhận amount/money/unit conversion.
- Primary action: shared client gửi G01 CommitmentRequest với campaignItemId, không contributionItemId; user/org lấy từ session ở backend. Button ngăn repeat submit, không tự bảo đảm concurrency bằng frontend arithmetic.
- Loading: skeleton/progress text, disable mutation. Empty: không còn remaining capacity thì `No remaining pledge capacity.` và bảng read-only; campaign chưa có item là context lỗi/empty, không form rỗng submit.
- Success 201: `Goods pledged. Pending on-chain.`; result summary có commitment ID và own pledged lines. Focus `#pledge-result` (`tabIndex=-1`, role=status/aria-live phù hợp). Refetch P04/G02 hợp lệ; không cộng vào received/delivered/confirmed.
- 403: caseVerifier (đọc public campaign được nhưng donorEnabled=false). Hiện safe message, giữ public totals, bỏ action. Không chuyển nhầm thành 404 hoặc CSRF error.
- 409: request rice8/water4, trong khi water remaining3; **cả hai lines không đổi**. Refresh capacity, giữ draft, summary -> error link -> input Water của submitted row `items[1].quantity`; người dùng sửa rồi submit chủ động.

## 4. Wireframe R01 - Record goods receipt

Vị trí: `Goods / Receipts` section trong `/org/cases/[id]`. Actor cần active ORGANIZATION_ADMIN ở owning org. Read case C03 có sẵn trong specification; staff commitment selector/projection chưa có read endpoint cụ thể, dùng **ASSUMPTION F02-A01**. Context S1, success thành S2.

```text
Case goods                                              [MOCK DATA]
Case status: In progress | Campaign: Published
Campaign progress: [Target | Pledged | Received | Delivered | Confirmed]

Record goods receipt
Commitment: [Fictional commitment ...0001 v]
Received at (UTC): [2026-11-09 01:39]
Include  Item            Unit  Pledged  Received  Outstanding  Quantity
[x]      Rice            kg          8         0            8  [5]
[x]      Drinking Water  box         2         0            2  [1]
Note (optional): [Partial receipt for simulated goods.]

This records goods received by the organization.
                                      [Record goods receipt]
```

- Visible: campaign totals ở bảng riêng; selected contribution progress ở bảng form. Staff projection không lộ donor email/private history. Tên người giả trong selector không bắt buộc và không được coi API field; dùng commitment reference là đủ.
- Inputs: selected contribution, receivedAt, selected integer lines theo **contributionItemId**, optional note<=500. CampaignItemId vẫn có ở mock UI mapping nhưng không gửi thay contributionItemId. UTC trên wireframe là lựa chọn mock cho dễ đối chiếu; frontend có thể hiển thị local time có timezone rõ rồi serialize UTC ISO-8601. Không tự dùng fixture clock làm giờ runtime.
- Primary action R01, max từng line = outstanding của commitment đang chọn, không lấy remaining pledge capacity của campaign. Khi chọn commitment khác, reset line mapping/draft/key; tránh dùng stale contributionItemId.
- Loading: đọc context/selector, chưa submit. Empty: `No outstanding commitments.`; không có quyền đọc context thì không hiện fallback JSON giả như data thật. Read error: `Unable to load commitments.` + Retry read.
- Success 201: `Partial receipt recorded. Pending on-chain.`; contribution received5/1, outstanding3/1; campaign received7/1. Focus `#receipt-result`; refetch projection sau khi save. Không tăng delivered hoặc confirmed, không đánh dấu campaign fulfilled.
- 403: FIELD_VOLUNTEER cùng org có thể đọc case/resource context nhưng thiếu OA; backend trả FORBIDDEN. Mock read projection cho actor đó là assumption cần Dũng xác nhận, không thêm read permission bằng frontend.
- 409: request rice5/water3 khi focal contribution outstanding8/2. Water vượt giới hạn; rice không được nhận riêng. Before/after snapshot S1 giống nhau. Focus summary -> Water quantity, refresh selected contribution và campaign, giữ draft để người dùng sửa.

## 5. Wireframe D01 - Record delivery

Vị trí: `Delivery` section của `/org/cases/[id]`, cần active FIELD_VOLUNTEER owning org. Case IN_PROGRESS, không dispute. Context S2, success thành S3. Goods availability do mock projection F02-A02 dựng từ campaign received-delivered; response CaseDetail goodsTotals chưa có exact shape.

```text
Record delivery                                         [MOCK DATA]
Case status: In progress | Open disputes: 0
Outcome: (*) Delivered  ( ) Failed
Handed over at (UTC): [2026-11-09 01:49]
Include  Item            Unit  Available to deliver  Quantity
[x]      Rice            kg                       6  [4]
[x]      Drinking Water  box                      1  [1]
Evidence versions: [x] Fixture version ...5551
                   [x] Fixture version ...5552

Delivery still requires confirmation by a separate reviewer.
                                           [Record delivery]
```

- Visible: internal case/own-org context, versioned allowlisted evidence metadata và per-item availability. Evidence IDs giả trong fixture dùng cho mapping; không có public URL, raw bytes hoặc upload UI.
- DELIVERED inputs: handedOverAt, >=1 selected positive integer campaignItemId line, >=1 authorized evidenceVersionId. Unit chỉ đọc. Không dùng contributionItemId và không claim donor lot provenance.
- FAILED inputs: clear/disable quantities/evidence, items=[], evidenceVersionIds=[], reason required<=2000; omit handedOverAt theo default mock. Không gửi attempted quantities như effective delivery items. Request/response riêng trong D01-failed-attempt.
- Loading/empty: `Loading available goods...`; nếu received-delivered=0 cho mọi line thì disable Delivered submit, giải thích `No received goods available for delivery.`. Failed attempt vẫn có thể ghi theo backend state/role guard; không ép giao hàng giả để qua form.
- Success DELIVERED: `Delivery recorded. Awaiting independent confirmation. Pending on-chain.`. Focus `#delivery-result`; campaign delivered5/1, confirmed0/0. Không expose confirm button cho cùng người ghi hoặc tự đóng case; phần independent confirmation thuộc F09/B10.
- Success FAILED: `Failed attempt recorded. Goods totals are unchanged.`; S2F thêm failed record/audit, không giảm stock, không tạo chain job, không show confirmed/pending-chain badge. reviewStatus=NOT_APPLICABLE là **ASSUMPTION F02-A03**.
- 403: OA đọc own case được nhưng không có FV role. Không cho OA bypass chỉ vì là admin.
- 409: rice4/water2 khi available6/1 -> reject toàn bộ; giữ S2, summary -> Water quantity, refetch rồi cho sửa. Conflict DISPUTE_OPEN/INVALID_STATE khác QUANTITY_EXCEEDED: disable action thay vì gợi ý cứ giảm quantity.

## 6. Endpoint -> UI-state mapping

Các IDs là scenario keys trong JSON; từng scenario có request headers/body, response headers/body, totalsBefore/totalsAfter và effects kỳ vọng. UI spec không phải client implementation.

| Endpoint / case | HTTP / code | UI state / hành động | Focus |
|---|---|---|---|
| G01-success | 201 CommitmentResponse | Show own pledge, Pending on-chain; refresh public campaign / own history | pledge-result |
| G01-forbidden | 403 FORBIDDEN | Giữ public totals, remove mutation; no quantity effect | error-summary |
| G01-quantity-conflict | 409 QUANTITY_EXCEEDED | Refresh capacity, giữ draft; cả hai lines unchanged | summary -> Water quantity |
| R01-success | 201 ReceiptResponse | Partial receipt; refresh contribution outstanding + campaign totals | receipt-result |
| R01-forbidden | 403 FORBIDDEN | Giữ phần staff được phép đọc; remove receipt action | error-summary |
| R01-quantity-conflict | 409 QUANTITY_EXCEEDED | Refresh selected contribution; no receipt/audit/job effect | summary -> Water quantity |
| D01-success | 201 DeliveryResponse | Awaiting independent confirmation; refresh stock; confirmed không đổi | delivery-result |
| D01-forbidden | 403 FORBIDDEN | Giữ permitted case view; remove delivery action | error-summary |
| D01-quantity-conflict | 409 QUANTITY_EXCEEDED | Refresh availability; no delivery/audit/job effect | summary -> Water quantity |
| D01-failed-attempt | 201 DeliveryResponse | Failed record, zero stock effect, NOT_APPLICABLE chain | delivery-result |
| All: initial/refresh read | GET pending | Loading text/skeleton, chưa cho mutation dựa trên stale totals | Giữ focus; aria-live |
| All: no eligible rows | GET success, empty | Empty message đúng form, không tự coi là request failure | Giữ focus |
| All: read error | network/5xx | Retry read; không POST với context chưa tải xong | Error heading sau user-initiated retry |
| All: submitting | POST pending | Disable repeat submit, không reset draft hoặc key | Giữ focus submit |
| All: invalid input | 400 VALIDATION_ERROR / local invalid | Error summary + inline fieldErrors, giữ input | Summary -> first invalid field |
| All: expired session | 401 | Minh session client clear private state và login; không giữ private receipt/evidence trong localStorage | Login heading |
| All: CSRF invalid | 403 CSRF_INVALID | Refresh CSRF qua shared client, khác thiếu role; không loop auto-write | error-summary |
| All: outside scope | 404 RESOURCE_NOT_FOUND | Clear protected context, không tiết lộ org khác | Error heading |
| All: unknown write outcome | timeout after POST | Recovery panel; retry **same key + same payload**, không cộng totals hoặc cấp key mới | Recovery heading |
| All: state conflict | 409 INVALID_STATE / DISPUTE_OPEN | Refresh trạng thái, block action bị vô hiệu | error-summary |
| All: duplicate key changed body | 409 IDEMPOTENCY_CONFLICT | Giữ draft, báo conflict; người dùng tạo lần submit mới có key mới | error-summary |

403 scenarios cố ý dùng **resource có quyền đọc nhưng thiếu quyền action**. Cross-org/not-found dùng 404, không tráo thành 403 để rò tồn tại. Form validation bắt buộc integer/positive/unique item IDs, time không trước case/campaign và không quá tương lai 5 phút, evidence tối thiểu cho delivered, note/reason length. Giới hạn hàng phía UI chỉ là hỗ trợ; backend vẫn kiểm tra atomic/concurrency.

Focus và keyboard: labels gắn inputs bằng htmlFor/id, legend cho outcome/include/evidence groups, caption/scope cho bảng, visible focus, status có text ngoài màu. Error summary có tabIndex=-1 và links trỏ đúng field; focus summary một lần sau submit lỗi, không giật focus mỗi lần background refetch. Mapping fieldErrors dùng row-ID map của **request vừa gửi**, không map theo index của danh sách sau refresh. Không mở dialog/prompt không cần thiết.

## 7. DTO/HTTP boundaries và assumptions cần Dũng review

Request DTOs và top-level response fields giữ đúng API Specification. Không gửi actorId/organizationId overrides, totals, status, expected effects, fixture labels hoặc UI projection trong mutation body. Success 201 là SQL/business success ở thiết kế, không Fuji proof. Fixed X-CSRF-TOKEN trong JSON là dummy, browser-managed session cookie lấy từ shared client thực của Minh; không dùng dummy token gọi real API.

| ID / trạng thái | Khoảng trống | Mock default / ảnh hưởng / owner next action |
|---|---|---|
| F02-A01 ASSUMPTION + OPEN | R01 staff commitment selection/read projection chưa có endpoint và fields đầy đủ. G02 là own donor history, không thể dùng để đọc donor khác. | `mockReadModels.*.receiptContext` chứa selected ID, line mapping, names/units/outstanding; **không phải API response**. Minh nhận qua mock transport adapter; Dũng chốt permitted staff read projection/route, không cho FE gọi G02 bằng identity donor khác. Chặn live receipt form integration, không chặn mock/design. |
| F02-A02 ASSUMPTION + OPEN | CaseDetail goodsTotals/disputeSummary/evidence relation còn generic; campaign public không có private caseId. | `caseContext.deliveryAvailableItems` dựng từ received-delivered trong mocked campaign. Dũng chốt internal case->campaign linkage, per-item availability/dispute/evidence projection và guards. Không thêm field vào public CampaignResponse; không suy private caseId từ campaign URL. |
| F02-A03 ASSUMPTION + OPEN | ReceiptResponse.items / DeliveryResponse.items chưa có line schema; reviewStatus enum FAILED chưa rõ. | Receipt items `{contributionItemId,quantity}`, Delivery items `{campaignItemId,quantity}`, successful reviewStatus=PENDING, failed=NOT_APPLICABLE. Types đặt hậu tố MockAssumption ở phần này. Dũng xác nhận exact wire schema/nullability/status, Minh đổi adapter cùng fixtures trước real integration. |
| F02-A04 ASSUMPTION + OPEN | Success POST yêu cầu Location theo common contract nhưng GET receipt/delivery/commitment detail chưa được đặc tả. | Location sample là resource URI identifier theo REST convention, **không** là lời hứa có GET endpoint. Minh dùng ID/body + documented reads, không navigate/fetch Location cho tới khi Dũng chốt. `auditUrl` trỏ API public audit route trong mock; không coi là frontend screen route hoặc explorer link. |
| F02-A05 ASSUMPTION + OPEN | 403 resource-read permission ở R01 cần endpoint/projection thực; mutation responses không trả campaign totals đầy đủ. | Fixture actor readAccessAssumption chỉ test context, không grant permission. Không cộng local campaign totals từ response; mocked next snapshot đứng ngoài HTTP DTO và dùng adapter refetch. Dũng xác nhận staff projection authorization; Minh dùng refresh/capabilities của shared client, không tạo client thứ hai. |

Không đổi quyết định kiến trúc hoặc thêm feature; không sửa API Specification/Decision Log thuộc owner khác. Những điểm trên là proposal review register và không tự gán decision IDs chính thức. Existing C01 OPEN không được giải quyết ngầm bằng F02 mock. Không cần human product choice để dùng mock defaults; cần Dũng/Minh cross-owner review trước khi freeze/live integration và F02 Done.

## 8. Cách Minh/Codex tái sử dụng ở F07/F08

1. Đọc assumptions trước; load JSON ở mock transport/test harness của shared client F03 do Minh sở hữu. Bộ F02 không tạo hoặc sửa client đó.
2. Chọn scenario bằng `endpointId` + scenario ID trong test harness; reset beforeState cho mỗi test. `response.body` là mock HTTP body; không trả cả fixture file hoặc mockReadModels qua real API.
3. Với F07, dùng G01-success/forbidden/quantity-conflict cho pledge form. P04 campaign view dùng `states[beforeState].campaign`; own contribution chỉ nằm trong authenticated donor state. Sau success/refetch, lấy afterState qua mock adapter. Test same-key replay không nhân đôi quantity/audit/job; fixture có replayExpectation để Minh triển khai.
4. Với F08 receipt form, dùng R01 ở S1 -> S2; chuyển campaignItemId sang contributionItemId bằng focal contribution line mapping. Dùng staff mock projection có nhãn ASSUMPTION, không expose private data vào donor view.
5. Với F08 delivery, dùng D01 S2 -> S3 hoặc S2 -> S2F. Available stock và evidence context là org-private; confirmed vẫn 0. F09 mới thực hiện independent confirmation/disputes UI nếu được giao.
6. Giữ một typed API client, same-origin /api + browser session + CSRF. Với unknown outcome giữ exact body/key; với field correction tạo key mới. Không tự retry POST bằng key mới do network error. Authorization phải được kiểm tra lại khi replay.
7. Gắn nhãn Mock data rõ trong UI dev/demo dùng transport giả. Khi real API có sẵn, thay adapter bằng real transport và ghi separate HTTP/render/E2E results; không gọi static-check PASS là frontend runtime PASS.

Review checklist cho Minh: ba forms đủ selected rows/units/keyboard flow; 9 success/403/409 examples map đúng states; partial receipt hiển thị own outstanding3/1; delivery không tự confirmed; failed attempt zero effect; message/labels English; không beneficiary wallet/upload/signup UI. Dũng review IDs/response items/permissions/read projections trong F02-A01..A05.

## 9. Verification ledger

Fixture checker command (không cần cài package):

```text
node docs/fixtures/f02/verify-f02.mjs
```

Checker kiểm tra syntax/IDs, exact request/top-level DTO keys, type/quantity/time, selected IDs, state arithmetic, response links, roles, 403/409 full-state equality và all-or-nothing, failed attempt record không đổi totals, fixture/state mapping và không có fake tx proof. Đây là kiểm tra dữ liệu thiết kế, không test backend/concurrency hoặc browser accessibility.

Các kết quả thực tế được thêm sau khi chạy ở cuối tài liệu. Backend HTTP, frontend render, keyboard E2E, TypeScript compiler, blockchain runtime: **Not Run**. Không cài dependencies, không scaffold UI, chưa commit/push/PR F02. Không đánh dấu Done trước khi acceptance/review đạt.

### Kết quả đã chạy sau khi ghi vào repo

| Command / check | Observed result |
|---|---|
| `node --check docs/fixtures/f02/verify-f02.mjs` | PASS syntax (chạy trên staging byte-identical trước khi copy) |
| `node docs/fixtures/f02/verify-f02.mjs` | PASS: 5 snapshots, 10 scenarios, integer quantities/IDs, request/response shapes, partial receipt, role examples, atomic 403/409, failed-attempt zero goods effect, UI-state mapping |
| Negative checker tests trong thư mục work tạm | PASS: scenario 403 đổi state và receipt dùng campaignItemId thay contributionItemId đều bị từ chối; repo fixtures không bị sửa |
| JSON parsing / internal Markdown links / fences / new-file whitespace | PASS |
| `node docs/fixtures/c01/verify-c01.mjs` | PASS static C01 regression; không đổi file C01 |
| `git diff --check` | PASS; chỉ cảnh báo line-ending LF -> CRLF theo config |
| Git branch / HEAD / changed paths | khoa / 2365378; đúng 5 file F02 mới và backlog; không có staged changes hoặc application changes |
| Backend HTTP / frontend render / keyboard E2E / TypeScript compiler / blockchain | Not Run |

C01 đã commit riêng `2365378` theo yêu cầu Khoa, không push. F02 để nguyên uncommitted để Khoa và Minh/Dũng review. Không tạo nhánh mới, không PR, không sửa source/API/client của thành viên khác. Chưa có cross-owner acceptance nên F02 vẫn In Review.

### Final quality review - 10/11/2026 (Asia/Saigon)

Khoa đã chấp nhận C01/F02 ở mức sẵn sàng review và cho phép commit F02, push khoa, mở PR vào main; không merge. Các ledger 09/11 phía trên là lịch sử trước authorization này. Cả hai task vẫn In Review: author validation không thay cross-owner acceptance.

- Đã đối chiếu lại AGENTS, phân công 09/11 trong 9_11.pdf, C01/F02 backlog, API Specification, Business Workflows, Blockchain Architecture và Test Strategy. Không phát hiện lỗi nghiêm trọng chặn handoff. Các C01-Oxx/F02-Axx vẫn là OPEN/proposal, không được tự chốt.
- Sửa một thiếu sót F02 trong phạm vi: thêm `idempotencyConflict` vào common UI-state JSON và refs của cả ba endpoints, khớp bảng Markdown; checker bắt buộc state này. Không đổi API/protocol hoặc dependencies.
- PASS: cả hai fixture checkers; six negative checks trên temporary copies (SHA tampering, log tampering, mutation sau 403, receipt sai namespace ID, confirmed tăng ngay sau delivery, thiếu idempotency UI state).
- PASS: Python standard-library tái tính SHA-256 độc lập từ canonical manifest, explicit ABI parameter types/indexed order; JSON parsing và internal handoff links.
- PASS: `node --check` cho cả hai checkers. `node --experimental-strip-types docs/fixtures/f02/goods-flow.types.ts` parse/chạy declarations thành công: chỉ kiểm tra syntax, KHÔNG phải TypeScript typecheck.
- PASS: `git diff --check`; C01 files không đổi so với commit 2365378, không rewrite/amend lịch sử. F02-only stage gồm file design, bốn fixtures/helpers và F02 backlog update.
- Not Run: ethers/Keccak, Java/TypeScript golden agreement, TypeScript compiler/typecheck, smart-contract compile/tests, backend HTTP, frontend render, keyboard E2E, Fuji. Không có dependencies cần thiết trong checkout và không cài thêm; không xem Node type stripping là compile contract/typecheck.
- origin/main sau fetch ở 84f607d đã nhận C02 của Dũng; nhánh khoa không tự merge/cherry-pick code của Dũng. PR diff dùng merge base chỉ gồm docs C01/F02; không xóa hoặc chỉnh contracts từ main.

Codex của Dũng đọc `docs/reviews/C01_BLOCKCHAIN_REVIEW.md`, trả lời C01-O01..O05 trước freeze ABI/worker; `golden-vector.input.json` là input/candidate, chưa là vector cross-language đã accepted. Codex của Minh đọc tài liệu F02 này và F02-A01..A05, dùng mock transport của shared client hiện có; chờ Dũng chốt response items/staff read projection trước live integration. Không copy mock labels/readModels vào real DTO.
