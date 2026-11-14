# Task3 — B08 GOODS_RECEIVED và C08, 14/11/2026

**PASS WITH NOTES. B08 payload/refs/sequence PASS qua review code; không phát hiện lỗi cần sửa B08 trong phạm vi này.** Không đồng nghĩa worker goods hoặc Fuji goods E2E đã chạy. Reviewer Khoa/Codex.

## Phiên bản và phương pháp

Fetch: main `5122281` (PR22 đã merge), dung `9c441eb` (B09 mới chỉ có FAILED). B08 tại `fe7414a`, C08 `36108eb` + fixes `9a9f7fb` đã merge PR21 `7b7ab6e`. Diff main..dung không thay contribution/blockchain; B09/V15 còn ở dung, không nghiệm thu B09. Giữ local khoa `6478d27`, không merge/cherry-pick code. Đọc B08_R01_HANDOFF, D49/D53/D55, architecture, ABI C06, service/outbox/job/repository, idempotency và ReceiptApiIT; không chạy Java/DB/Actions hoặc RPC mới.

## B08 mapping và invariants đã kiểm tra

Nguồn đường dẫn Java bên dưới tương đối với `backend/aidbridge-backend/src/main/java/com/aidbridge/`.

| Field/operation | Expected → actual và nguồn |
|---|---|
| GOODS_RECEIVED | `contribution/service/ContributionOutboxService.java:66–85`: entity_type GOODS_RECEIPT, entity_id/event_key = receipt UUID, payloadVersion1; function `recordGoodsReceipt` → event `GoodsReceived` (Solidity:603–675). |
| Private snapshot | receiptId, contributionId, random receiptRef, contribution.publicRef làm commitmentRef, receivedAt, lines[{contributionItemId,campaignItemId,quantity}]. Note không đưa lên chain; actor UUID/private IDs/lines chỉ vào private manifest, không calldata/log. |
| Stable refs | receiptRef sinh một lần khi audit được lưu, commitmentRef lấy bytes32 đã lưu của pledge. Retry HTTP đọc journal, không enqueue lại; rollback hủy cả receipt/audit/job/journal. Worker tương lai phải lấy ref đã lưu, không sinh lại khi retry. |
| Envelope | orgRef = owning organization.public_ref, caseRef = case.public_ref (repository BlockchainJobRepository:56,59); không dùng UUID thô. eventId = job.logical_event_id bytes32, **không phải** auditEventId/event_key/receiptId/receiptRef. Job constructor giữ case từ audit, random logical_event_id và schema_version1 (BlockchainJob:93–105). PayloadVersion1 không thay thế schema_version của job. |
| Quantity | GoodsReceiptService:56–64 khóa case→campaign trước idempotency; :90–133 khóa items/contribution/lines, đọc aggregate sau khóa, validate tất cả dòng trước insert. Outstanding = pledged−cancelled−received; nhiều receipt có refs/eventIds riêng, cùng commitmentRef. Receipt không tăng delivered/confirmed. |
| Sequence/atomicity | Cùng case lock như ContributionService:79–80,162–163. Outbox MAX+1 dưới khóa, V12 UNIQUE(case,sequence); MANDATORY tham gia transaction receive. Pledge1 lần/contribution; mỗi reduction/receipt là milestone mới; retry không tăng sequence. Không mặc định GOODS_RECEIVED luôn sequence3: cancellation/pledge khác đã commit có thể đứng trước. |

ABI C06: `recordGoodsReceipt(bytes32 orgRef,bytes32 caseRef,bytes32 eventId,uint64 chainSequence,bytes32 evidenceCommitment,uint16 schemaVersion,bytes32 receiptRef,bytes32 commitmentRef)`. GoodsReceived có ba indexed orgRef/caseRef/eventId; data sequence/commitment/schema/timestamp/receiptRef/commitmentRef (6 words). Contract yêu cầu case verified, commitment cùng org/case, receiptRef mới và next sequence. Quantities được SQL kiểm, không có trên chain; receipt contract cho phép open dispute nhưng B08 chặn sớm hơn theo workflow, không phải bypass.

Pledge phải được gửi trước receipt/reduction để tạo commitmentRef. `AID_PLEDGED → recordAidCommitment → AidPledged`; `COMMITMENT_REDUCED → recordCommitmentReduction → CommitmentReduced`. Pledge/reduction payload lấy contributionId, worker tương lai phải resolve đúng immutable publicRef, không giả định hai payload này đã chứa commitmentRef như receipt. Không dùng serialization JSON mặc định làm evidenceCommitment/payloadDigest: chuẩn bị manifest canonical/salt bất biến theo D48/D49, digest operation-bound gồm refs đúng thứ tự theo ABI. C10 phải đối chiếu các expected từ DB snapshot với receipt và getEventRecord, không lấy calldata làm expected độc lập.

ReceiptApiIT:24–96 assert partial nhiều hàng, replay cùng body/Location, một audit/job, sequence3 ở fixture verify→pledge→receipt; cancellation sau partial và receipt cuối đạt RECEIVED. :100–117 assert atomic failure; :149 trở đi receipt cạnh tranh cancellation, tổng received+cancelled không vượt pledged. Đây là assertions đã đọc, **không phải test chạy mới**. Báo cáo B08 phân biệt Maven5 validation PASS/DB skips với Dũng xác nhận IntelliJ PASS chưa có XML; chưa đủ chứng minh goods Fuji E2E. Không phát hiện lỗi code B08 bắt buộc sửa; cần bổ sung integration assertions refs/payload/digest khi mở worker goods, không thay B08 chỉ để lấp phần adapter chưa có.

## Hai thay đổi C08 — PASS

- CaseVerifiedWorker:322–325 gọi readChainId khi poll, sai chain defer; không chỉ check startup.
- CaseVerifiedReceiptVerifier:54–66 kiểm destination/signer trước status0→Reverted; status khác0/1 là mismatch. :114–117 reject logIndex thiếu/âm/overflow; worker:347 lưu index thật, không clamp.
- Web3jBlockchainAdapter:103–110 chỉ nhận canonical raw0x0/0x1; missing/malformed không bị biến thành revert.
- Tái sử dụng bằng chứng đã xác minh: [run43473786126](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43473786126) SUCCESS trên9a9f7fb, compile +26 unit +32 IT, 0 fail/error/skip. Không chạy lại; xem [C08 review](C08_RECEIPT_LOG_REVIEW.md). Receipt recovery PASS, scanner/replay PENDING, không tạo task mới.

## Chốt mapping cho B09 và auditUrl

**Mapping thiết kế đã có:** DELIVERY_RECORDED (DELIVERED) → `recordDelivery(envelope,deliveryRef)` → `DeliveryRecorded` (AidRegistry:679–749). Common indexed fields như trên; data sequence/commitment/schema/block timestamp/deliveryRef, không receiptRef hoặc commitmentRef. deliveryRef random bytes32 lưu ổn định với record/audit; eventId vẫn logical job ID; cấp MAX+1 dưới case lock. FAILED là DELIVERY_ATTEMPT_FAILED private audit, không job/sequence on-chain, NOT_APPLICABLE và zero stock effect. Trên dung9c441eb mới có FAILED; DELIVERED trả501, chưa chứng minh mapping/outbox DELIVERED đã implement. Dũng tiếp tục B09, không gọi đây là lỗi của phần chưa bàn giao.

**auditUrl còn OPEN thật:** D49 (`DECISION_LOG.md:102`) chốt `/api/public/cases/{publicCaseRef}/audit`; T02 (`API_SPECIFICATION.md:125`) và CampaignMapper:55 dùng `/api/campaigns/{id}/audit-trail`. Không thấy endpoint T02 thực thi trong phạm vi hiện tại. Đề xuất giữ D49 làm chuẩn cho DeliveryResponse/C10, T02 là public campaign projection riêng nếu nhóm cần; Dũng/Minh xác nhận boundary và cập nhật API docs trước bật link. Không tự sửa D49 thành URI T02; không bịa publicCaseRef từ UUID/campaignId hoặc bật link endpoint chưa có. FAILED auditUrl luôn null; DELIVERED link phải chờ endpoint thật/contract thống nhất, không coi txHash là confirmed.

## Đề xuất owner và chuyển deployment — cần nhóm duyệt

- TEAM_RESPONSIBILITIES:23,26 giao Khoa worker phía chain và goods→contract/refs/causal order; Dũng hỗ trợ SQL/outbox/Java. **Đề xuất Khoa primary mở rộng worker goods**, Dũng support query/snapshot/DB assertions; chưa nhận triển khai hoặc tự thêm việc vào lịch. Phải thống nhất slot trước C10 ngày17, không hứa lịch deploy chắc chắn.
- GOODS_RECEIVED cần registry C06: C03 Fuji không có function này. Chỉ deploy sau artifact C06 được review/test, worker hỗ trợ đủ AID_PLEDGED/COMMITMENT_REDUCED/GOODS_RECEIVED theo thứ tự và plan chuyển deployment được duyệt, rồi người phụ trách cho phép deploy. Không chỉ bật receipt worker và bỏ qua sequence đang pending trước nó.
- Đề xuất MVP đơn giản: giữ case/job/proof cũ gắn C03, dùng **case demo mới** cho C06 và tạo lifecycle từ CASE_VERIFIED sequence1 → pledge → receipt → delivery. Pin deployment/ABI/source trong attempt, không trỏ mọi job cũ sang address mới, không rewrite confirmed hoặc signed bytes. Tạm dừng/đối soát in-flight trước cutover.
- Nếu nhóm muốn tiếp tục case cũ trên registry mới: cần kế hoạch replay toàn bộ prefix bằng immutable IDs/refs/commitments đúng sequence và ledger theo deployment; không reset DB sequence hoặc gửi receipt sequenceN vào registry rỗng. Hiện chưa có scanner/migration này; cần quyết định riêng, không tự triển khai. Proof C04/C05 cũ giữ nguyên deployment, D55 VOID hai logs chung eventId/sequence vẫn chỉ một logical operation.

Task3 hoàn thành review; B09 không cần chờ redeploy để viết REST/SQL. Goods worker/redeploy/audit URI là dependency tích hợp cần thống nhất, không phải B08 payload FAIL. Không sửa code backend, không đổi trạng thái task khác, không push/PR/blockchain.
