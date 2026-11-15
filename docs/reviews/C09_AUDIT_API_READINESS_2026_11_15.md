# Task8 — C09/T01/T02 readiness và blockchain evidence

15/11/2026, Khoa/Codex. **BLOCKED BY IMPLEMENTATION.** Đã review tối đa source hiện có; chưa có audit read API để nghiệm thu mapping/privacy/proof. Không viết thay API, không thực hiện Task12.

## Phiên bản và mâu thuẫn bàn giao

Fetch main9c24630/dung0fc3e3d/minh0595149/minh-d010595149; giữ khoa và commits2a0a58e/7ac567b/8a69ce7. Đọc remote tree trực tiếp trên main và dung, tìm route trên cả bốn refs và fetched history AuditTrail/AuditChainStatus/T01_T02. Chưa thấy implementation/read DTO/test/handoff tương ứng. GitHub hiện chỉ có PR23 frontend OPEN; không có PR audit riêng đang mở trong danh sách đã kiểm tra.

[PR25](https://github.com/chidungbrvt-bit/AidBridge/pull/25) đã MERGED và description khẳng định T01/T02/AuditChainStatusMapper/tests/báo cáo301 tests. Tuy nhiên GitHub files API liệt kê31 files, **không có audit read controller/service/DTO/tests hoặc T01_T02_AUDIT_TRAIL_HANDOFF.md**. Không lấy description/khẳng định test làm source evidence; có thể implementation chưa push/bàn giao, không suy đoán vị trí local của Dũng. Không kết luận từ main..dung diff rỗng.

## Những gì thực sự có

| Source | Quan sát và giới hạn |
|---|---|
| `backend/aidbridge-backend/src/main/java/com/aidbridge/audit/service/LocalAuditService.java:19–58` | append business audit/case revision trong transaction MANDATORY, không controller hoặc read projection. Không có pagination/sanitization/chain-status mapper ở đây để đánh giá API public. |
| `.../audit/repository/AuditEventRepository.java:7–8` | JpaRepository cơ bản; chưa query audit timeline join job/attempt/receipt theo sequence, chưa public/internal DTO. Entity AuditEvent không được serialize trực tiếp vì chứa private payload/identity. Đây là yêu cầu cho API tương lai, chưa phát hiện exposure endpoint thực tế. |
| `.../auth/security/SecurityConfig.java:94–99` | Permit GET route campaign audit-trail cũ, nhưng không có controller; URI canonical D49 `/api/public/cases/{publicCaseRef}/audit` chưa được permit, rơi vào authenticated. Khi triển khai Dũng phải đồng bộ matcher/guards đúng public contract, không chỉ thêm controller rồi coi public access đã đạt. Chưa gọi HTTP để khẳng định response runtime. |
| `frontend/src/components/audit/audit-timeline.tsx` | Fixture rendering/proof examples, không C09 live API evidence. URI trong CampaignMapper/DeliveryResponse chỉ là metadata, không chứng minh route tồn tại. |

Do chưa có read implementation: empty/pending/reverted/mismatch/duplicate/privacy/tenant guards, event order chain_sequence và API fields **NOT VERIFIED**, không PASS hoặc suy FAIL một logic chưa viết. Chưa có AuditTrailApiIT/PublicAuditTrailIT/AuditChainStatusMapperTest trên refs đã kiểm tra để đối chiếu assertions.

## Blockchain evidence và boundary

[B05 real Fuji report](B05_REAL_FUJI_RUN_2026_11_13.md) đã ghi hai CaseVerified receipts trên C03 chain43113/registry0x015D941c679b0c402c8262BE237adAD36cEf6726, calldata/event/readback match; các cột DB/B2/restart-time còn NOT_VERIFIED được giữ nguyên. Không gọi RPC/DB hoặc xác minh lại report hôm nay. **Chưa có C09 HTTP response để ghép proof với API/case/job độc lập.** Goods/delivery chưa có worker/receipts Fuji đầy đủ, không gán proof C03 cho operation C06 hoặc biến job PENDING thành confirmed.

C09 cung cấp sanitized read projection: cần internal expected linkage audit→job→attempt theo deployment, eventId=logical_event_id, orgRef/caseRef/sequence/operation và txHash/chain/registry/blockHash/blockNumber/logIndex/verification state hoặc boundary chứng minh tương đương. Public fields cần chốt DTO/privacy, không mặc định phải expose mọi internal ID. Thiếu API khiến toàn bộ field availability/mapping chưa xác minh; C10 đối chiếu expected DB với receipt/log, C08 scanner coverage/reorg/replay vẫn chưa triển khai. Không đánh đồng ba phần.

Khi bàn giao, Dũng cần tests pending/hash-only không confirmed, đúng receipt context/canonical policy, reverted0 khác malformed/mismatch, sequence order/deterministic pagination, retry/replay không duplicate timeline; business VERIFIED độc lập chain status. D55 VOID hai logs là một logical operation/job/sequence, không thành hai business rows. Public DTO không private payload/actor identity/recipient/internal IDs/salt/private evidence; cross-org/internal authorization và published-case public access phải có tests. Đây là checklist tiếp nhận, không test đã chạy hoặc triển khai C09/C10 mới.

## Dependency / điều kiện tiếp tục

**Owner Dũng:** push/bàn giao đúng controller/service/DTO/queries/mapping/security/tests T01/T02/C09 và commit SHA; đối chiếu PR25 description với files thực tế. URI theo [auditUrl decision/alignment](AUDIT_URL_ALIGNMENT_2026_11_15.md), không thêm hai endpoints để dung hòa D57 chỉ được tham chiếu. Sau đó cung cấp test environment/actual responses và DB expected có thể đối chiếu CaseVerified proof. Không cần gửi secret.

**Khoa:** tiếp tục review C09/C10 đúng implementation khi có; giữ boundaries của [C08 scanner](C08_RECEIPT_LOG_REVIEW.md). **Minh:** không bật link giả hoặc dùng fixture như API real; F10 cần F09/C10, API/proof và hosting/security/recovery gates theo [F10 checklist](F10_HOSTING_AUDIT_CHECKLIST_2026_11_15.md). Thiếu V16 của Task3 là blocker schema riêng cho lifecycle B09, không dùng thay lý do audit controller chưa có.

Runtime tests/HTTP/DB/Fuji **NOT RUN** vì chưa có implementation đủ để review; không cài môi trường/Actions, không sửa backend/nhánh người khác. Tài liệu/diff/reference check không được tính là API PASS. Dừng Task8 tại readiness blocker, không mở rộng xây API hoặc thực hiện Task12.
