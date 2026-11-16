# PR #30 — High code review, 16/11/2026

**Verdict: READY WITH NOTES.** Không phát hiện Critical/High hoặc lỗi logic mới chặn merge trong phạm vi C10 design, B09/B10 và tương thích C09 đã kiểm. Đây là review tĩnh; không nghiệm thu C10 runtime/deployment. Owner review: Khoa.

## Phiên bản và phương pháp

- PR30 OPEN, head `ef30487957a0dfd354aae15fbf91f9236cc69cec`; base SHA GitHub trả `d65b0176927afdddb28b7fe5770f31fafd1a72c5` (merge PR28, cũng là merge-base). `origin/main` mới nhất `ed19bdf27d45e5cc4e5ecabceacc28266d1cec39` đã merge PR29. GitHub báo `mergeable=true`, `mergeable_state=clean` tại lúc đọc.
- Diff PR: 55 files, +5366/-42; bỏ ML-2 khỏi phạm vi chuyên môn review này. Đã đọc commit list, code service/repository/DTO/migrations liên quan, assertions integration tests và thiết kế/fixture C10. Không merge code vào khoa.
- PR30 đã chứa PR28 qua merge28fe580; chưa chứa PR29. Phía main bổ sung frontend/docs và DonorUiE2EIT; không thấy thay đổi production backend mới đối nghịch B10. Kết quả runtime Dũng báo không bao phủ tổ hợp PR29 + PR30. Không suy clean merge thành runtime PASS.

## C10 / refs / sequence / ABI

- Mapping đúng source Solidity: CASE_VERIFIED → recordCaseVerification → CaseVerified; AID_PLEDGED → recordAidCommitment → AidCommitted; COMMITMENT_REDUCED → recordCommitmentReduction → CommitmentReduced; GOODS_RECEIVED → recordGoodsReceipt → GoodsReceived; DELIVERY_RECORDED → recordDelivery → DeliveryRecorded. Receipt refs đúng thứ tự receiptRef, commitmentRef; delivery dùng deliveryRef. Envelope/event layout đúng.
- Đã chạy kiểm tra Node/ethers offline với fixture **lấy từ origin/dung** và dependencies/artifact hiện có: encode/decode calldata + topics/data/refs **4/4 PASS**; đối chiếu riêng bốn function signatures và bốn event signatures với source Solidity remote **PASS**. Không compile, Java parity hoặc deploy.
- Fixture sequence1–4 là kịch bản không có operation xen giữa, không phải quy tắc production. Thiết kế dòng36 yêu cầu predecessor N−1 tồn tại/cùng case-chain-registry/CONFIRMED; reduction ngoài allowlist phải chặn case, không bỏ qua hoặc đánh số lại. B09/D02 dùng MAX+1 dưới khóa case, không hardcode sequence4/5. DELIVERY_CONFIRMED là operation bổ sung, chưa nằm trong bốn mốc bắt buộc.
- V16 có cùng Git blob trên main/dung (`a28f370b5df3f597f0f2593fc3de89f113c3f38d`); PR30 không sửa lại V16. DeliveryService lấy ref từ entity cho audit; ConfirmationService.java:361–378 lấy đúng ref gốc. V17/V18 không thay refs/evidence links. Evidence IDs của D02 phải bằng chính xác immutable delivery snapshot.
- D55: AidRegistry.sol:970–993 emit DeliveryVoided trước DisputeResolved, chung eventId/sequence, hai logIndex riêng; event record dùng resolveDispute. Scanner/verifier tương lai phải ghép một logical operation/job và hai observations, không áp rule đúng một log của bốn mốc này cho VOID. Dispute/resolve/VOID backend hiện local audit, không có chain job/ref payload đầy đủ cho D55; không nghiệm thu chúng on-chain.
- Matrix dòng47–49 có wrong chain/registry/signer/hash/refs, missing/duplicate/removed logs, revert context, malformed ABI/schema, predecessor missing/out-of-order, retry/restart và DB uniqueness. Các suite GoodsMilestone* chỉ là tên dự kiến; worker hiện vẫn CASE_VERIFIED. Evidence commitments trong fixture là placeholders, không chứng minh manifest/salt/Java parity.

## B09/B10 và C09

- Confirm khóa case → campaign → items → delivery; role DELIVERY_CONFIRMER và actor khác recorder trước idempotency replay; fresh write kiểm trạng thái/dispute/void, exact evidence set. CONFIRMED tạo một audit + một PENDING job MAX+1 cùng transaction; REQUIRES_REVIEW giữ reservation và tạo dispute/local audit. FAILED không được confirm.
- VOID giữ immutable delivery/items/confirmations; unique delivery_voids.delivery_id + dispute_id và FK case/delivery, trigger chống update/delete. Queries deliveredTotals/confirmedTotals/progressTotals loại void; EXISTS confirmation tránh nhân số lượng khi join. Multi-delivery totals, fulfillment, replacement và close/dispute race có assertions cụ thể (DisputeResolveIT:86–186; DeliveryConfirmationIT:51–95,166–214,358–388; CampaignCloseIT:118–150). Service mutations chung khóa case nên serialize các thao tác đó.
- AuditTrailService chỉ sửa lỗi compile Hashing.sha256 bằng UTF-8 bytes (b976a29), không nới visibility hoặc proof guard. D49 controller/security, hashed public IDs, ordering chain_sequence/timestamp/id và SQL receiptVerified từ PR28 được giữ. Public allowlist chưa gồm confirm/dispute/void: không tuyên bố public timeline hỗ trợ đầy đủ các operation B10.
- Worker không đổi; receipt_private writer cũ vẫn thiếu context mà SQL guard yêu cầu. API tiếp tục fail closed/pending dù có proof C05 cũ; không sửa/nới guard, không coi runtime B10 PASS là C10/audit Fuji PASS. Public DTO không cung cấp raw blockHash/logIndex/receipt để verifier độc lập thay C10.

## Findings và lưu ý bàn giao

1. **Medium, docs/integration — owner Dũng:** docs/reviews/C10_DUNG_WORKER_DESIGN_2026_11_15.md:123 hướng T02 tới `/api/campaigns/{campaignId}/audit-trail` theo D57; dòng3,5,29,129 cũng dựa vào D57. Decision Log remote không có quyết định D57; D49/D56 và controller/security thực tế dùng `/api/public/cases/{publicCaseRef}/audit`. Nếu làm theo runbook sẽ gọi route cũ không public/không có handler. Sửa dòng123 và các tham chiếu approval/fallback chưa có căn cứ; chỉ giữ fallback như đề xuất. Không tạo endpoint thứ hai. Không chặn logic REST/SQL đang dùng đúng D49, nhưng cần sửa trước khi dùng tài liệu để triển khai/handoff.
2. **Artifact limitation, không kết luận bytecode lỗi:** artifact local có SHA256 `4ea70150abe967dfef9b31906a8304488aadac0738da1d435f1ae5036c3e623f`, runtime keccak `0xfac13b94b0b8e937c00a237f276943a48110dcd1d9b25b365d3b6cbaf3a926ad`; khác metadata fixture/dòng121 (`976825c3…`, `0x525314…`). Do đó lệnh nguyên bản assert artifact hash chưa tái lập PASS ở máy Khoa. ABI signatures/roundtrip vẫn PASS như trên. Không suy khác hash là lỗi source hoặc đã verify artifact của Dũng; trước deployment Khoa/Dũng phải chốt chính artifact/build input-bytecode và hash. Không build lại/không cấp phép redeploy trong review này.

## Bằng chứng tests và merge gate

- **Dũng báo trong PR description**, tại đúng head ef30487: Windows Java26.0.1, Maven wrapper, PostgreSQL aidbridge_test/schema test riêng, `mvnw.cmd -B -ntp verify`: Surefire146/0/0/0, Failsafe205/0/0/2, BUILD SUCCESS. Không có raw Surefire/Failsafe XML/log được commit trong diff để tự chứng thực counts; review này không gọi đó là tests Khoa đã chạy.
- Assertions đã đối chiếu: ReceiptApiIT5, DeliveryRefMigrationIT9, DeliveryApiIT7, DeliveryConcurrencyIT1, AuditTrailApiIT3, PublicAuditTrailIT4; B10 Confirmation12/Open4/Resolve6/Donor4/Close5/StaffRead7 =38. Các assertions có stock/DB/audit/job/journal counts, rollback rồi cùng-key retry, role/scope/CSRF, exact snapshots, simultaneous confirmations/resolutions và close-versus-dispute.
- V16 tests273454e dùng TEMP schema dạng V15, chưa thay validation upgrade DB V15 có dữ liệu thật. Dũng báo V1–V18 + Hibernate validation trên DB sạch; DB demo có V16 draft checksum1206533238 phải được kiểm lịch sử trước dùng, không repair/xóa dữ liệu theo review này.
- Skipped CaseVerifiedHardhatE2EIT và DonorUiE2EIT là opt-in, không tự động chặn merge phần REST/SQL/design; chúng chặn tuyên bố E2E/full lifecycle PASS. Java C10 parity, PostgreSQL C10 retry, Hardhat C06 và Fuji goods đều NOT RUN.
- **Có thể merge phần đã bàn giao với các notes trên**, theo branch protection và review nhóm; không cần mở rộng thành worker/scanner. Nếu yêu cầu runtime trên tổ hợp mới nhất hoặc phải cập nhật branch, Dũng chạy targeted/runtime gate trên SHA mới, không gắn kết quả ef30487 cho merge result. Không tự merge trong lượt này.

Chỉ thêm báo cáo review trên khoa; không sửa code Dũng/Minh, không push/PR/merge, không Java/DB/CI, không blockchain/wallet.
