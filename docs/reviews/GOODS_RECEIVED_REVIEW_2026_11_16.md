# Task #3 — GOODS_RECEIVED, 16/11/2026

**PASS WITH NOTES.** Không phát hiện lỗi B08 cần sửa trong phạm vi payload/refs/scope/sequence. Worker đa operation, manifest/parity và proof goods runtime là **PENDING Task #7**, không phải kết quả đã PASS của review này. Owner triển khai: Dũng; reviewer: Khoa.

## Snapshot và bằng chứng

Fetch: main `15bcba4ec5cce8bf07b929353e68d26b006a0668` (PR30 merged), dung `c288f4409023e9f2352082ff91428f324d7445bb`; khoa giữ `cda5360` trước commit báo cáo. So main với khoa: GoodsReceiptService, ContributionOutboxService, receipt entity/DTO và blockchain worker/repository không thay đổi; ContributionRepository chỉ thêm query donor-dispute B10, không đổi query R01. Tái sử dụng review [B08 ngày14/11](B08_GOODS_CHAIN_REVIEW_2026_11_14.md), [C07](C07_WORKER_SAFETY_REVIEW.md), [C08](C08_RECEIPT_LOG_REVIEW.md), [PR30](PR30_HIGH_CODE_REVIEW_2026_11_16.md) và D49/D53/D55/D56. Không mở lại review C08/proof writer hoặc C10 fixture đầy đủ; ghi chú phân công cũ trong báo cáo14/11 đã được D56 thay thế: Dũng làm worker, Khoa review semantics.

Đường dẫn Java dưới đây tương đối `backend/aidbridge-backend/src/main/java/com/aidbridge/`; dòng thuộc main15bcba4.

## Payload, refs và nghiệp vụ

- `contribution/service/GoodsReceiptService.java:45–133`: active ORG_ADMIN trong org sở hữu commitment; kiểm scope/role trước replay; khóa case → campaign → items → contribution/lines. Campaign PUBLISHED, org APPROVED, không dispute; receivedAt trong thời gian publish/legacy start đến now+5m. DTO ReceiptRequest bắt buộc items không rỗng, ID không null/trùng, quantity số nguyên dương (không nhận string/fraction), note tối đa500 và plain text.
- Mỗi dòng thuộc chính contribution; outstanding = pledged − cancelled − received. Tất cả dòng được kiểm trước insert. Một receipt nhiều hàng hoặc nhận nhiều lần hợp lệ; mỗi lần mới có receipt riêng, không tăng delivered/confirmed. V14 composite FK buộc goods_receipt_items cùng commitment với receipt và contribution item; receipt/items immutable. V11 bảo vệ campaign/org của các dòng contribution.
- `ContributionOutboxService.java:66–85`: `entity_type=GOODS_RECEIPT`, entity_id/event_key = receipt UUID; payload gồm `payloadVersion:1`, receiptId, contributionId, receiptRef, commitmentRef, receivedAt, lines[{contributionItemId,campaignItemId,quantity}]. Payload không có campaignId/orgRef/caseRef hoặc evidenceVersionIds: worker phải scoped join để resolve, không tự mặc định thiếu field là receipt có evidence file.
- receiptRef là nonzero random bytes32, tạo một lần khi enqueue và **chỉ lưu trong immutable audit payload**; GoodsReceipt không có cột receipt_ref. Đây là nguồn gốc ổn định qua restart, không cần thêm cột cho Task #3. commitmentRef lấy contributions.public_ref đã lưu. Nhiều receipts của cùng commitment có refs/eventIds riêng nhưng commitmentRef giống nhau.
- `BlockchainJob.java:90–100`: logical_event_id random bytes32 riêng, schema_version1, case lấy từ audit; chain mặc định43113. Không dùng audit UUID/event_key/receipt UUID làm ABI eventId. `payloadVersion` và `schemaVersion` là hai lớp phiên bản khác nhau.

## ABI và commitment

`GOODS_RECEIVED → recordGoodsReceipt → GoodsReceived` (AidRegistry.sol:603–676).

Function: `recordGoodsReceipt(bytes32 orgRef,bytes32 caseRef,bytes32 eventId,uint64 chainSequence,bytes32 evidenceCommitment,uint16 schemaVersion,bytes32 receiptRef,bytes32 commitmentRef)`; selector `0x73a5f8b9`. Event có indexed orgRef/caseRef/eventId; data theo thứ tự chainSequence, evidenceCommitment, schemaVersion, uint64 block timestamp, receiptRef, commitmentRef (192 bytes). Không đảo receiptRef/commitmentRef.

Contract kiểm authorized relayer, envelope/nonzero refs, duplicate eventId/digest, verified case/org, receiptRef chưa dùng và commitment thuộc đúng org/case, rồi next sequence. Contract cho phép receipt khi dispute mở nhưng B08 chặn sớm hơn theo workflow; không phải bypass. Quantities/campaign/physical goods không được contract kiểm, không tuyên bố blockchain chứng minh giao nhận thực tế.

**evidenceCommitment bắt buộc nhưng B08 chưa tính:** Task #7 chuẩn bị canonical manifest từ immutable receipt audit + receipt/items/commitment đã scoped; chốt vector Java/JS theo D49. Không giả tạo evidenceVersionIds, không lấy current case evidence thay receipt snapshot, không dùng JSON serialization tùy ý hoặc placeholder fixture làm commitment thực. Domain/chain/registry/case/eventId/manifest SHA256/salt theo D49; persist preparation bất biến. Note, actor/donor identifiers, raw UUID/quantities/evidence/manifest/salt không vào calldata/event; on-chain chỉ envelope/opaque refs/commitment. Refs không phải evidence hash.

## Scope, transaction và sequence

`findReceiptTarget` scoped org → campaign/case; `lockScoped` bảo vệ org của campaign/case; requested line IDs phải thuộc contribution. HTTP không nhận case/org/ref tùy ý để enqueue. Outbox nhận caseId từ campaign đã khóa. **Task #7 phải kiểm lại toàn bộ graph**, vì chữ ký enqueueReceipt nhận đối tượng/UUID nội bộ chứ không tự chứng thực mọi tham số: job.case = audit.case = campaign.case; audit.org = owning case/campaign/contribution.org; receipt.contribution = contribution.id; lines thuộc đúng receipt/contribution/campaign và quantities khớp snapshot; commitmentRef khớp persisted public_ref và pledge gốc. Sai/missing ref/scope/version phải fail closed trước sign.

Receive `@Transactional`; outbox `MANDATORY`; idempotency yêu cầu transaction đang active. Receipt/items/status/audit/job/journal commit hoặc rollback cùng nhau. Replay cùng actor/org/operation/key/body trả response cũ, không enqueue lại; payload khác cùng key409. Rollback trước commit có thể tạo random IDs/refs khác khi retry nhưng không để lại record đã commit hay nghĩa vụ signer; sau commit tuyệt đối không tạo lại refs.

Outbox MAX(chain_sequence)+1 dưới case lock; V12 UNIQUE(case,sequence), một job/audit. R01 và cancellation chung lock order nên cạnh tranh outstanding được serialize. Không dùng sequence cố định3/4. **Chuỗi bắt buộc Task #7:** CASE_VERIFIED → AID_PLEDGED → COMMITMENT_REDUCED → GOODS_RECEIVED → DELIVERY_RECORDED. Trong case tối giản đó sequences1–5; thêm pledge/reduction/receipt khác thì dùng sequence thực tế, không hardcode. Reduction payload có reason private: không đưa reason lên chain.

## Handoff Task #7 — invariants bắt buộc

1. Hỗ trợ cả pledge/reduction trước receipt; claim **và poll** theo operation được hỗ trợ. Với N>1 cần predecessor N−1 tồn tại và CONFIRMED trong đúng case/chain/registry, đồng thời không có predecessor cũ hơn chưa confirmed. Thiếu/hỏng/out-of-scope predecessor giữ chờ/fail có reason, không nhảy sequence; case khác vẫn tiến.
2. Resolve orgRef/caseRef từ public_ref scoped; lấy receiptRef từ audit đã lưu và commitmentRef từ contribution đối chiếu audit/pledge. Không sinh refs/logical eventId khi retry, không dùng current aggregate thay các receipt lines gốc.
3. Giữ signer lock, nonce journal, lease/fencing C07; persist signed bytes/hash/attempt trước broadcast; restart/timeout poll hoặc rebroadcast cùng bytes đúng deployment. Không tạo receipt/audit/job/sequence mới từ worker. Case C03 cũ không tự chuyển sang registry C06.
4. Verifier so pinned chain/registry/signer/hash/canonical block; status chỉ0/1, status0 phải đúng context; status1 cần đúng GoodsReceived, bốn topics, data192 bytes, refs và toàn bộ envelope/schema/commitment đúng expected độc lập từ snapshot. Reject missing/negative logIndex, removed/duplicate/missing/wrong events hoặc swapped refs. Persist proof/job/attempt atomically; timeout giữ pending, không CONFIRMED chỉ vì tx status1. C10 độc lập đối chiếu getEventRecord/digest khi triển khai.
5. Test negative scope từng cấp, missing/malformed/zero ref, đổi receiptRef sau restart, nhiều partial receipts, concurrent receipt/cancel và same-key replay, reduction xen trước receipt, missing N−1, wrong chain/registry, duplicate receipt log, late proof/rollback. D55 VOID ngoài operation receipt: hai expected logs chung eventId/sequence là một job; không áp quy tắc một GoodsReceived log cho VOID.

## Tests và giới hạn

- Đã đọc ReceiptApiIT **5 tests**: partial/multi-item + replay và audit/job/sequence counts; failure atomic khi quá lượng/foreign line; role/scope trước replay; duplicate lines/time; race receipt/cancel và received+cancelled không vượt pledged. Kịch bản partial → cancel → receipt tiếp theo có thật; chưa phải worker chain chạy toàn chuỗi mới.
- ReceiptSchemaIT có assertions migrate V14 giữ legacy receipt, ownership FK/immutable triggers và reject legacy vượt lượng/sai commitment. Bằng chứng runtime gần nhất: PR30 description Dũng báo verify tại ef30487, ReceiptApiIT5 PASS, tổng146 unit/205 IT/2 skipped. Không coi đó là run trên merge SHA15bcba4 hoặc tests do Khoa chạy lại.
- Review này chỉ đọc source/diff/assertions, **không chạy Java/DB/CI**. ABI offline4/4 từ review PR30 được tái sử dụng; không chứng minh reduction-inclusive chain E2E. Goods worker/verifier, manifest parity, PostgreSQL retry worker, Hardhat đầy đủ5operations và Fuji goods **PENDING Dũng Task #7 / các deployment gates riêng**. Không phát hiện finding mới cần sửa B08; không đánh PENDING của Task #7 thành lỗi Task #3.

Chỉ commit local báo cáo, giữ khoa/cda5360, không sửa code/backlog hoặc thực hiện Task4/5/8/review6; không push/PR/merge/deploy/transaction.
