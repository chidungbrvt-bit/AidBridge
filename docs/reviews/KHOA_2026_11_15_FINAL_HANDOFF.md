# Final high audit và bàn giao Khoa — 15/11/2026

**PASS WITH NOTES — sẵn sàng PR để review, chưa sẵn sàng merge/deploy khi runtime validation chưa hoàn tất.** Audit source/design/docs, không chạy Java/JUnit/Flyway/PostgreSQL/Actions hoặc blockchain. Không phát hiện thêm Critical/High có bằng chứng trong phạm vi source đã đọc; không khẳng định hệ thống không có bug. Tài liệu này là trạng thái hiện hành, các findings “chưa có V16/API” trong báo cáo trước là lịch sử đã được supersede.

## Năm task và phạm vi thực tế

| Task | Kết quả phần Khoa | Giới hạn/gate còn lại |
|---|---|---|
| #2 D56/owner/auditUrl | DONE phần Decision/phối hợp; a4752a1,2a0a58e | Dũng worker/verifier, Khoa semantics/C10/deploy khi được phép; deploy tách cutover. D57 chưa có evidence phê duyệt; giữ D49. Minh0595149 cần align URI, không sửa nhánh đó. Slot/rollout/deploy permission còn riêng. |
| #3 B09/ref/V16 | Source fixes + focused test source đã bàn giao; runtime PENDING | 7ac567b/0025e58 review,7946be1 V16,206d2f4 guard backfill. Chưa nghiệm thu DB upgrade/Hibernate/transaction runtime. |
| #8 C09 | Source fixes + regression source đã bàn giao; runtime PENDING | b90ab5a readiness cũ,0025e58 re-review,206d2f4 URI/proof/order/privacy fixes. API hiện đã có trên khoa; chưa có proof API→Fuji độc lập mới. |
| #10 C08 scanner/cursor | DONE WITH PENDING DECISIONS cho thiết kế;8a69ce7 | Scanner/adapter/schema/finality/completeness/caps/lease-CAS vẫn PROPOSED/NOT IMPLEMENTED, cần Dũng/Khoa thống nhất trước viết code. |
| #12 F10 phối hợp | DONE WITH DEPENDENCIES cho handoff;f87cb98 | Minh có thể chuẩn bị fixture/UI; F09/C09/C10/runtime/security/cost/recovery và deploy permission chưa đủ để public hosting. F10 chưa implemented/deployed. |

## Candidate và Git

main baseline9c246304a5b11e8eb0fc567bde4cc407f19a8519. PR27 head7946be19fde64d5dcc13fcc066b53c8284225615 đã merge **local vào khoa** bằng aee0a78, sau đó206d2f4 sửa bốn nhóm findings. Các commits/docs local được bảo toàn; PR27 chưa merge main. Diff từ main chỉ là audit API/V16/focused tests và docs năm task; B09/ML baseline đã ở main, không có diff ML/frontend/workflow/contract/dependencies/build/cache. Không cherry-pick lại PR27 khi bàn giao candidate này.

**PR tổng hợp bao gồm implementation của PR27 cùng fixes206d2f4. Không merge PR27 riêng rồi merge lại candidate một cách mù.** Review/validate exact head PR tổng hợp trước; khi được phép merge sau này, thống nhất thứ tự/retire PR27 thủ công. Lượt này không tự merge hoặc đóng PR27, không chỉnh nhánh Dũng/Minh.

## Source/design cross-task audit

- **V16/B09/D55:** ref tạo một lần, column=payload, guard exactly-one audit đúng delivery/event_key/case/org, canonical nonzero bytes32, uniqueness và trigger/transaction. FAILED null/no job/quantity effect. D55 sau này phải reuse ref gốc, hai logs VOID chỉ một logical operation. Không coi V16 đã áp DB thật hoặc backend dispute/resolve đã triển khai.
- **C09 URI/privacy/order:** public route/security/mapper theo D49 `/api/public/cases/{publicCaseRef}/audit`; no legacy public alias. Case-ref lookup chỉ owning public campaign hợp lệ; internal staff tenant guard giữ nguyên. Public DTO không raw audit UUID, dùng stable one-way namespaced presentation ID, logicalEventId tách riêng. Timeline local-only events trước, milestones chain_sequence tăng dần; occurredAt/id chỉ tie-break, không hứa toàn timeline time order. Include reduction; DTO có chainSequence.
- **C07/C08→C09:** API đọc persisted proof/context, không RPC mỗi GET. C09 không biến hai DB flags/hash thành confirmed thiếu proof. C07 receipt writer hiện lưu format ngắn hơn fields C09 mới required: **proof lịch sử thiếu context bị pending/unverified**, không tự enrich hoặc claim hash/checks hiện tại là finality mới. Không thay worker trong scope fixes. C10/owner có thể enrich proof trong task được phép sau khi đối chiếu độc lập; không patch journal để làm badge xanh. Reviewer phải cân nhắc degradation này trước merge, tests phải chứng minh thiếu/wrong proof không false confirmed.
- **C03/C06:** bằng chứng Fuji hiện có CaseVerified trên C03, không goods receipts C06; fresh case từ1, queue inventory cả unprepared jobs và signed attempts trước config switch. DB status/job không proof; deployment/nonce/sequence/permissions không tự migrate. Không đổi C03 proofs hoặc dùng registry mới cho bytes cũ.
- **C08 scanner design:** range/cursor riêng deployment/filterVersion, bounded batch/coverage và atomic cursor+observations; no advance on timeout/truncation/unresolved mismatch. Observation identity chain/registry/txHash/logIndex và append occurrence history theo blockHash; reorg rewind không replay quantities/jobs. VOID giữ đủ2 logs/one operation. Completeness/finality/provider policy và deep-reorg handling chưa được kiểm chứng/triển khai; getLogs rỗng không chứng minh không có logs.
- **F09/F10:** id/recorderId/evidenceVersionIds theo DELIVERED response, không lấy receiptId thay deliveryId hoặc đồng nhất review/chain status. UI fixture không live proof; chưa bật audit link chỉ vì URI đúng. Minh cần đồng bộ D49 consumer với backend candidate. HTTPS/cookie/session/CSRF/same-origin proxy/private DB/files, health và rollback đều còn gate target-runtime; budget19USD là ceiling đề xuất, không giá/eligibility hoặc authorization.

## Runtime validation handoff — NOT RUN — DELEGATED

**Owner Dũng hoặc người được nhóm giao backend runtime; Minh frontend integration; Khoa review evidence, không cài/chạy môi trường trong lượt final audit.** Lệnh chỉ là hướng dẫn cho môi trường test đã được cấp quyền, không chứa secrets và chưa thực thi:

```text
cd backend/aidbridge-backend
./mvnw -B -ntp -DskipTests compile
./mvnw -B -ntp -Dtest=AuditChainStatusMapperTest test
./mvnw -B -ntp -Dtest=AuditChainStatusMapperTest -Dit.test=DeliveryRefMigrationIT,DeliveryApiIT,DeliveryConcurrencyIT,AuditTrailApiIT,PublicAuditTrailIT verify
```

Kiểm tra cấu hình Surefire/Failsafe và reports thực tế trước kết luận; không tính skipped/no-tests như PASS. Dùng schema test riêng theo guards hiện có AIDBRIDGE_TEST_DATABASE_* / DB_PASSWORD, không production DB. Cần ghi exact candidate SHA, số tests/errors/skips và XML reports. Không chạy QA toàn repo chỉ để hoàn thành checklist.

1. **Compile/focused JUnit:** verify record constructors/projection/native SQL/HQL/ref lookup, mapper missing/malformed/mismatched chain/registry/proof; synthetic positive proof không được gọi Fuji verification.
2. **Fresh install:** Flyway V1–V16 schema trống rồi Hibernate validate. Tests migration dùng temporary V15-shaped tables hỗ trợ kiểm guard, **không thay thế full Flyway upgrade evidence**.
3. **Populated V15→V16:** DELIVERED valid payload copy chính xác; FAILED null; zero/bad prefix/length/duplicates/ambiguous/missing audit/wrong case/org fail-closed. Failed migration rollback cả column/trigger state. Test schema drift/column đã thêm thủ công bằng policy reconcile, không overwrite refs lịch sử.
4. **B09 replay/concurrency:** one delivery/audit/job; ref/immutable evidence ổn định; errors rollback lines/reservation/outbox; FAILED không stock/job/sequence; backend migrations tracked đủ để tái lập.
5. **C09 public/internal HTTP:** canonical route anonymous/published guard, malformed/unknown ref404, role/tenant guards, no payload/PII/internal audit UUID, stable opaque IDs, seq timestamps inversion/ties, bounded pagination/count, reduction không mất, no duplicate rows do attempts.
6. **Proof regression:** DB-only CONFIRMED thiếu proof vẫn pending/no public success link; context chain/registry/event/refs/schema/sequence/hash/block/index lệch không confirmed. Probe with legitimate persisted verified context; đối chiếu CaseVerified C03 từ DB expected tới receipt/report, không lấy calldata làm expected duy nhất. API không tự chứng minh canonical hiện tại nếu chỉ đọc proof lịch sử; ghi verification scope/time.
7. **Minh integration:** D49 URI/client validator/types/fixtures theo [alignment guide](AUDIT_URL_ALIGNMENT_2026_11_15.md); unknown/pending/error/missing proof không anchored; không live link trước endpoint readiness. PR23/26 chưa được test bằng candidate này; frontend code không được sửa/merge ở final audit.
8. **Scanner/worker/Fuji future:** chỉ chạy scanner replay/reorg/VOID/dual-deployment hoặc goods Fuji khi implementation/artifact/permission đã sẵn; không phải yêu cầu cài scanner/C10/F10 để review riêng C09. Hosting smoke/HTTPS/CSRF/restore/cost chỉ khi target và deploy approval riêng.

**Kết quả lượt final:** static diff/references/history/scope/secret-pattern review, không runtime test mới. CI26+32 của C08 tại9a9f7fb là evidence lịch sử cho worker không đổi, không chứng minh fixes206d2f4 compile hoặc migration/C09 mới PASS. Tests được thêm chỉ là NOT RUN — DELEGATED.

## Merge/triển khai gates và owner

PR để nhóm review tổng thể, **không tự merge**. Trước merge: Dũng/owner chạy focused runtime matrix trên exact final head, xử lý failure có bằng chứng; Minh phối hợp URI/DTO/status integration. Không coi thiếu runtime evidence là tests FAIL hoặc tự bỏ tests để vượt gate. D56 ownership/capacity policy chưa cho phép deploy/cutover; Khoa chỉ deploy C06 khi có reviewed artifact và lệnh riêng. F10/scanner vẫn phụ thuộc các gate đã ghi, không đổi task khác thành Done.
