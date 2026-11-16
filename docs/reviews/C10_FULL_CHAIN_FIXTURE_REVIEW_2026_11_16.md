# Task #4 — C10 design / multi-item fixture / full chain, 16/11/2026

**NEEDS FIX (thiết kế/fixture cho gate Task #7 mới).** Không phát hiện lỗi ABI/contract hoặc Critical/High mới. Bản bốn mốc hợp lệ trong phạm vi cũ nhưng chưa đáp ứng chuỗi bắt buộc có COMMITMENT_REDUCED và coverage nhiều commitments/receipts hôm nay. Dũng có thể bắt đầu triển khai phần nền đã rõ; phải điều chỉnh thiết kế và acceptance trước khi nghiệm thu Task #7. Không kết luận worker/runtime FAIL khi chưa triển khai.

## Snapshot / phạm vi

Fetch main `15bcba4ec5cce8bf07b929353e68d26b006a0668`, dung `c288f4409023e9f2352082ff91428f324d7445bb`, local khoa `e98ac9d`. Giữ review PR30 `cda5360` và Task3 `e98ac9d`; không merge remote vào khoa. Đọc fixture và thiết kế C10 từ **origin/main** (không lấy bản local cũ thiếu sửa D49). Căn cứ D49/D53/D55/D56; tái sử dụng [PR30 review](PR30_HIGH_CODE_REVIEW_2026_11_16.md) và [Task3](GOODS_RECEIVED_REVIEW_2026_11_16.md), không review lại B08 hoặc proof-writer Task6. Phân công hiện hành: Dũng worker/verifier, Khoa semantics/parity/C10 review. Không triển khai hoặc sửa tài liệu của Dũng trong lượt này.

Các file/line dưới đây thuộc main15bcba4.

## Findings thực tế — Dũng tiếp nhận

### F1 · Medium · Chuỗi acceptance bỏ reduction, không đạt gate hôm nay

- `docs/reviews/C10_DUNG_WORKER_DESIGN_2026_11_15.md:9–11,29,35–36,49,54,127`; `docs/fixtures/c10-goods-milestones.design.json:70–274` chỉ có verify/pledge/receipt/delivery. Dòng29 loại reduction khỏi demo; dòng36 yêu cầu dừng case khi gặp reduction chưa hỗ trợ. Matrix Hardhat chốt sequence4 và harness chỉ chọn bốn jobs.
- Tái hiện: tạo G01, rồi G03 giảm một phần trước R01. Outbox có reduction N=3; theo thiết kế hiện tại worker không hỗ trợ reduction và phải dừng receipt/delivery. Bỏ qua N=3 hoặc renumber sẽ sai D53/contract; giữ guard thì demo bắt buộc không hoàn thành. Đây là thiếu scope thiết kế so với yêu cầu16/11, không phải lỗi của guard fail closed.
- Sửa: thêm operation reduction vào scoped loader/manifest/encoder/expected-log, allowlist **claim và poll**, fixture và matrix. Giữ nguyên sequence đã lưu. Hardhat phải so sequence cuối với tập logical operations thực tạo, không cố định4/5; harness chọn mọi job cần thiết qua audit IDs, không lọc bỏ reduction. Basic fixture năm mốc có thể là1–5; thêm operations phải tăng tương ứng. DELIVERY_CONFIRMED nếu bật là optional sau delivery thực tế, không mặc định luôn5 như dòng29.

### F2 · Medium · Fixture chưa thử phân biệt refs giữa commitments và receipt lặp

- JSON `referenceRows:43–59` chỉ một contribution/receipt/delivery; milestones pledge `113–160`, receipt `162–218` chỉ một lần nhận. Hai item rows không đồng nghĩa hai commitments hay nhiều lần nhận. Không có reduction quantities, cancelled totals, expected transitions hoặc mutation vectors negative/retry trong JSON.
- Hậu quả: encode/decode4/4 vẫn PASS dù worker chọn nhầm commitment khi có nhiều pledge, gộp receiptRef theo item hoặc tái dùng ref lần nhận trước. Những lỗi loader/state này không bị single-positive ABI vector bắt được.
- Sửa: bổ sung ít nhất hai commitments khác refs, một reduction, hai receipts của cùng commitment và một receipt của commitment khác; mapping contributionItemId → contribution/campaignItem rõ, expected quantities theo từng bước và ref identity checks. Thêm negative variants trỏ đúng shape nhưng sai case/org/commitment/registry, duplicate receiptRef, malformed/version/zero refs, vượt outstanding/available. Test retry/restart phải assert cardinality và identity không đổi, không chỉ decode thành công.

## Mapping ABI — đúng, gồm operation cần bổ sung

Common calldata: orgRef, caseRef, logical eventId, uint64 chainSequence, evidenceCommitment, uint16 schemaVersion. Topic1–3 = orgRef/caseRef/eventId; data bắt đầu sequence/commitment/schema/uint64 block timestamp. Không đưa items/quantities/private IDs lên chain.

| Logical operation | Solidity function → event | Extra refs theo thứ tự | Selector / data bytes |
|---|---|---|---|
| CASE_VERIFIED | recordCaseVerification → CaseVerified | không | 0x1bd1fadf /128 |
| AID_PLEDGED | recordAidCommitment → AidCommitted | commitmentRef | 0x89f8809e /160 |
| COMMITMENT_REDUCED | recordCommitmentReduction → CommitmentReduced | commitmentRef | 0x0612a2a5 /160 |
| GOODS_RECEIVED | recordGoodsReceipt → GoodsReceived | receiptRef, commitmentRef | 0x73a5f8b9 /192 |
| DELIVERY_RECORDED | recordDelivery → DeliveryRecorded | deliveryRef | 0x2df45447 /160 |

Reduction full signature: `recordCommitmentReduction(bytes32,bytes32,bytes32,uint64,bytes32,uint16,bytes32)`; event `CommitmentReduced(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64,bytes32)`, topic0 `0xa5826f78c498bce3d2ac824d3d313cc7e6890af56421f8e4ea55a827a12bfb32`. So source Solidity:538–600, operation-bound digest và next-sequence như các mốc khác; không sửa commitmentRef gốc khi giảm lượng.

Refs là cấp record, không phải cấp item: một commitmentRef cho pledge và mọi reduction/receipt của commitment đó; mỗi receipt mới có receiptRef mới dùng chung cho các lines của receipt; một deliveryRef cho delivery nhiều items. Delivery refs không suy từ một receipt đơn lẻ: source snapshot delivery/campaign/items và evidence links của chính delivery. Mỗi logical operation có eventId riêng; retry giữ ID/ref/nonce/hash/bytes cũ. Org/case chung xuyên chuỗi, registry pin theo deployment của attempt và prefix. D55 VOID (Solidity:970–993) là một resolve operation/job/sequence, emit DeliveryVoided rồi DisputeResolved với hai logIndex riêng; không áp quy tắc đúng một log của năm operation trên cho VOID.

## Coverage hiện có và ví dụ acceptance cần bổ sung

Kiểm tra nhẹ thực chạy bằng Python trên JSON remote: Rice pledged8/received5/delivered4 → outstanding3, available1; Water5/3/2 → outstanding2, available1. Đúng target10/6, confirmed0, unique audit/event keys/logical IDs. Fixture block timestamp03:00Z sau receivedAt02:20/handedOverAt02:30. Tất cả observed=NOT_RUN; chain31337, commitmentKind=placeholder, không pin registry cụ thể trong positive fixture. Bốn refs/ABI signatures đã PASS offline trước đó; không suy quantity hoặc retry correctness từ ABI roundtrip.

**Đề xuất acceptance vector bổ sung, chưa tạo fixture/chưa chạy:** cùng hai items Rice/Water, C1 pledge8/5, reduce1/1 → net7/4; C2 pledge2/1 → tổng active9/5. Nhận C1 lần1=3/2, lần2=2/1; C2=1/1 → received6/4, outstanding tổng3/1. Delivery4/2 → available2/2, confirmed0. C1/C2 refs khác; receipt1/2/3 refs khác, receipt1/2 commitmentRef=C1, receipt3=C2. Sequence tối giản của vector này: verify, pledgeC1, reductionC1, pledgeC2, receiptC1a, receiptC1b, receiptC2, delivery (8 operations). Expected sequence lấy từ outbox runtime, không nhúng giả định1–8 vào worker. Negative vượt một dòng phải rollback toàn bộ multi-item receipt/delivery, counts/stock/sequence không đổi; replay từng mutation cùng key/body không thêm operation. Đây chỉ là hướng điều chỉnh test, không quyết định Task5 về mô hình campaign/case.

## Handoff chất lượng / các phần PENDING đúng phạm vi

- Thiết kế dòng36–39 có boundary phù hợp: loader scoped immutable audit/snapshot → preparation lưu cố định → signer/nonce lock/fencing → persisted PREPARED before broadcast → verifier theo operation → atomic attempt/job proof. Kiểm predecessor N−1 tồn tại/cùng case-chain-registry/CONFIRMED và không có predecessor cũ hơn chưa confirmed; registry C03/C06 không tự thay thế nhau. Thiếu predecessor block case, không bỏ sequence và không chặn mọi case khác.
- C07 invariants đã rõ, không thay; C08 chain guard khi poll, context trước revert, status chỉ0/1, logIndex hợp lệ, timeout pending và proof context fail closed phải giữ. Expected lấy từ persisted preparation và scoped source, không copy receipt log làm expected. Proof fields/context cần persist theo guard đã có; chưa nghiệm thu Task6 writer trong review này.
- Manifest hàng hóa mới ở mức đề xuất dòng37; fixture commitment placeholder dòng68 và các expected rows. Dũng/Khoa cần chốt canonical manifest/vector Java/JS trước runtime acceptance: snapshot v1 từng operation, ordering lines, exact refs/domain/chain/registry/eventId/schema và encrypted salt reuse. R01 không có evidenceVersionIds riêng; không tự lấy current case evidence. Delivery lấy immutable evidence links; snapshot không đổi khi current aggregate/evidence thay đổi. Đây là dependency thiết kế/parity PENDING, không báo commitment đã được tính/kiểm chứng.
- Matrix dòng47–49 đã có wrong chain/registry/signer/hash/ref, swapped receipt refs, missing/duplicate/removed logs, malformed ABI/schema, predecessor/retry/restart/fencing. Cần gắn các ca này với expanded fixtures và assert DB/job/attempt/sequence/stock cardinalities, bổ sung explicit insufficient quantity/duplicate business refs/multi-commitment mapping (F2). Scanner/log replay và Fuji không thuộc nghiệm thu thiết kế; không đòi triển khai ở Task4.
- Artifact hash local khác metadata Dũng đã được ghi ở PR30 review, không lặp build hoặc tuyên bố đã verify bytecode deploy. Registry pinning phải rõ trong runtime harness (capture address deploy C06) và negative variant; chưa có observed registry không phải proof sai.

## Kiểm chứng và kết luận

Mới thực chạy: Python fixture quantity/unique-ID/NOT_RUN checks PASS; Node/ethers đọc trực tiếp source remote và kiểm chữ ký/types/indexed fields/selector/topic0 **reduction ABI PASS**. Tái sử dụng4/4 ABI offline của PR30; **không chạy Java/JUnit/DB/Hardhat E2E/CI**. Các GoodsMilestone* tests vẫn chỉ là tên dự kiến ở dòng52, chưa có class trên remote; Java parity/worker retry/full-chain local/Fuji PENDING. Không tính tests B09/B10 đã báo PASS thành C10 full-chain PASS.

Task4 hoàn tất review, kết luận **NEEDS FIX F1/F2 trước khi chốt acceptance Task7**; Dũng primary chỉnh design/fixture/test matrix. Không sửa worker, fixture hoặc docs của Dũng, không đổi backlog/status task khác. Báo cáo này là phần bổ sung ngày16/11 cho baseline bốn mốc đã review ngày trước, không phủ nhận kết quả ABI bốn mốc. Chỉ commit local trên khoa; giữ e98ac9d/cda5360; không push/PR/merge/blockchain. Chưa làm Task5/8 hoặc review6.
