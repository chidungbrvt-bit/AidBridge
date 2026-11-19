# Task #8 — C09 audit source / Fuji proof acceptance preparation, 17/11/2026

**C09 SOURCE REVIEW: PASS WITH NOTES. FUJI PROOF VERIFICATION: PENDING. TASK #8: PREPARED.** Phần review độc lập và checklist hoàn tất; phần nghiệm thu Fuji mới còn phụ thuộc pilot. Không có finding source mới cần sửa, không gọi chuẩn bị checklist là Fuji E2E PASS. Không làm lại Task3/4 hoặc chạy Task6 pilot.

## Phiên bản / phương pháp

Fetch main `627be561bc224286a3dc62d5d14898dc1b438141`, dung/PR33 `f85d3902b7c2549987c78cd4438c6366a752d23a`; khoa `672a4b9` trước báo cáo. Giữ dd77cfb/672a4b9; không merge code remote. Đã đọc main controller/service/repository/DTO/security, frontend audit boundary, các assertions tests và đối chiếu review15–16/11. Không review lại worker; PR33 không thay audit query/mapper/security, chỉ thêm getter eventType trong entity. Patch proof writer PR33 chưa merge vào main; local gate vẫn như báo cáo Task3, không tự đóng gate từ lượt này.

## C09 source / API boundary

Đường dẫn backend dưới đây tương đối backend/aidbridge-backend/src/main/java/com/aidbridge/.

- `audit/controller/AuditTrailController.java:23–41`: T01 `/api/cases/{id}/audit-trail` authenticated/scoped, T02 `GET /api/public/cases/{publicCaseRef}/audit`; `auth/security/SecurityConfig.java:92–99` permitAll đúng D49 GET, không dùng URI T02 campaign cũ. Response no-store.
- `audit/service/AuditTrailService.java:57–112`: T01 active org member có role; ngoài scope/PA không membership404. T02 publicCaseRef lowercase bytes32, campaign PUBLISHED/FULFILLED/CLOSED + org APPROVED; draft/unknown404. Cardinality0..1campaign/case đã được schema enforce, giữ D15 PROPOSED như Task5 ngày16/11, không đổi quyết định.
- Public event allowlist chỉ CASE_VERIFIED/CAMPAIGN_PUBLISHED/AID_PLEDGED/COMMITMENT_REDUCED/GOODS_RECEIVED/DELIVERY_RECORDED; role label theo type, không lookup account identity. Public DTO không có actor/account/entity/case/org UUID, payload/manifest/evidence/salt/signed bytes/errorCode. `publicItem` trả audit_ + SHA256 UTF8(public-audit:+UUID), không raw audit UUID. Internal T01 có UUID/actor cho staff được scope, không nhầm privacy public với internal.
- `audit/repository/AuditEventRepository.java:42–75,94,118`: một row/audit, một selected attempt qua lateral LIMIT1, không join mỗi log thành timeline event. Local-only events không sequence đứng trước, sau đó chain_sequence tăng, rồi occurred_at và audit UUID làm tie-breaker. Public không trả UUID dùng để sort. D55 VOID sau này không được tạo hai logical rows từ hai logs; hiện public allowlist chưa gồm dispute/confirm/void, không tuyên bố public timeline B10 đầy đủ.
- `validatePage`: page>=0,size1..100, default20. list/count dùng cùng scope/allowlist. Empty hoặc page vượt cuối trả items[], metadata/count thực tế; không fake audit event, không suy missing campaign thành empty200 (campaign không public vẫn404). LIMIT/OFFSET ổn định theo tập dữ liệu không đổi; live inserts/status changes có thể khiến các lần fetch/page khác nhau, không phải snapshot/cursor guarantee. Không đưa scanner/cursor vào acceptance lượt này.

## Blockchain status / trust boundary

SQL receiptVerified so receipt_status1, attempt.chain=job.chain, txHash, registry, eventId,sequence,eventType,schema,orgRef,caseRef,status,blockNumber; kiểm blockHash shape và logIndex không âm, thiếu/mismatch cho false. Mapper còn đòi job và attempt đều CONFIRMED, hash/registry/block hợp lệ, chain khớp. PENDING/PROCESSING/SUBMITTED/unknown không confirmed; hash không đồng nghĩa đã mine. Failed/reverted biểu diễn FAILED_ON_CHAIN, mismatch public ẩn hash/link/errorCode; NOT_APPLICABLE khi không có job. Không suy business approved/received/delivered hay review CONFIRMED thành blockchain confirmed.

**Giới hạn có chủ đích:** C09 chỉ đọc persisted proof/context được trusted verifier ghi, không gọi RPC/canonicality mới ở mỗi GET, không chứng minh DB chưa bị sửa hoặc reorg chưa được phát hiện. SQL blockHash shape check không thay canonical receipt verification; wrong registry nhưng tự ghi context đồng bộ trong DB không thể được C09 tự phát hiện qua RPC. C10 độc lập phải đối chiếu deployment allowlist và raw receipt/log. Không nới guard hoặc coi boolean receiptVerified là cryptographic verification phía client.

Main writer cũ có thể thiếu7context nên proof C03 đã confirm vẫn được API biểu diễn pending/unverified; receipt fix nằm PR33, chưa được merge/nghiệm thu runtime. Không sửa lịch sử C03 hoặc giả enrichment để UI xanh. C03 CASE_VERIFIED evidence lịch sử giữ đúng deployment, không dùng nó chứng minh goods/C06.

## F09/F10 trực tiếp liên quan

`frontend/src/lib/audit.ts` và AuditFeed trên main dùng D49, kiểm publicCaseRef/URL shape. UI giữ blockchain sync riêng, không thay business/review status; confirmed Fuji cần status đúng + chain43113/hash/block/contract hợp shape, explorer đúng host/path. Invalid URL hoặc load lỗi không bật share link; share link chỉ sau result thành công; empty/loading/error có nhãn riêng. Không phát hiện sai lệch frontend mới cần Minh sửa.

DTO có status,chainId,contractAddress,logicalEventId,sequence,txHash,blockNumber đủ trình bày verified/unverified **theo quyết định backend**; không có blockHash/logIndex/orgRef/caseRef/raw event topics/data/evidenceCommitment hoặc operation selector cho độc lập verify. Không đòi thêm public fields hoặc expose payload trong Task8; Dũng cung cấp proof bundle riêng cho reviewer. Biến verified trong frontend chỉ kiểm đủ fields trình bày, không phải C10 verification. Minh cần tiêu thụ response/status đúng deployment đã được nghiệm thu, không bật link từ hash/fixture hoặc suy Hardhat thành Fuji.

## Checklist dùng khi Dũng bàn giao pilot Fuji — hiện tất cả PENDING

Không điền registry C06/txHash/block giả. Điền từng hàng theo proof thật tại thời điểm review; nếu thiếu thì đánh PENDING/MISMATCH, không lấy giá trị C03 hoặc Hardhat31337 lấp chỗ trống.

| Gate / dữ liệu cần nhận | Cách đối chiếu và điều kiện đạt |
|---|---|
| Provenance | Source/head+tree tested/runner/verifier/API, clean/dirty state, lệnh/môi trường/schema test hoặc pilot và artifact/build fingerprint; phân biệt nguồn report với reviewer chạy |
| Approved deployment | Chain43113 từ RPC; manifest C06 được phê duyệt, address thực tế, canonical deploy receipt, source/compiler/settings/ABI/artifact và getCode runtime khớp fingerprint release; C06 address/tx chưa có ở review này |
| Per-operation ledger | Secure scoped export tối thiểu audit/job/attempt mapping, type,eventId,case/org public refs,chain_sequence,chainId,registry,status và hash; public eventId audit_ khác logical bytes32 eventId và internal audit UUID |
| Scoped snapshot / refs | Pledge gốc → commitmentRef ổn định; reduction đúng pledge; receiptRef khác nhau giữa các receipts, cùng commitmentRef khi cùng pledge; deliveryRef đúng persisted delivery/evidence snapshot. Không gửi manifest/salt/private evidence/PII/raw signed bytes trong public handoff |
| Sequence / prefix | Đọc thứ tự actual operations theo case, không hardcode1–5; full required prefix gồm reduction. Mỗi predecessor cùng case/chain/registry,CONFIRMED và proof phù hợp; không ghép C03 prefix sang C06 mới |
| Transaction / canonical block | Receipt.txHash đúng attempt; from signer/to registry đúng approved binding, status1, blockNumber/hash và receipt block canonical qua RPC; tx input selector/envelope/refs đúng. Canonicality là tại thời điểm kiểm, không tuyên bố finality vô hạn |
| Raw log / operation | Address/topic0 đúng ABI; orgRef/caseRef/eventId topics, sequence/commitment/schema/timestamp/refs data; logIndex non-null/nonnegative, removed=false, tx/block context khớp; không nhận tx status1 thiếu event |
| Digest / record | Đối chiếu calldata và DB preparation với getEventRecord tại block receipt: exists,selector/org/case/sequence/commitment/schema/digest đúng operation+refs, không lấy log làm expected duy nhất |
| Worker proof / DB | receipt_private7context + tx/block/log fields khớp observation; job+attempt CONFIRMED nhất quán, một job/logical sequence; retry/restart giữ eventId/nonce/hash/bytes và không tăng inventory/job/attempt vô cớ |
| D55 VOID nếu có trong pilot | DeliveryVoided rồi DisputeResolved chung eventId/sequence, đúng deliveryRef/disputeRef/resolution; hai logIndex khác; record selector resolveDispute; một job, hai observations. Không coi thiếu VOID ngoài pilot scope là thiếu proof năm mốc, không đếm đôi |
| API roundtrip | Capture T01 authorized và T02 anonymous JSON trên đúng pilot case, paginate mọi row; match logicalEventId/sequence/type/chain/registry/txHash/blockNumber với ledger và guard. Unknown/missing/mismatched proof không CONFIRMED; duplicates/retry không thêm row |
| Privacy / UI | T02 không raw IDs/account/private payload/evidence/errorCode; internal scope đúng403/404/auth conventions. Check frontend status/link dựa response thật, testnet label, không bật link mismatch; không coi UI badge là independent evidence |
| Historical isolation | C03 proof/attempt/manifest/address giữ nguyên; không relabel thành C06 hoặc replace registry config khi replay. C06 case mới từ prefix riêng; read projection theo đúng deployment từng attempt |

**Status expectations khi đối chiếu:** thiếu context → pending/unverified theo guard; RPC timeout/job pending không confirmed; reverted/failed giữ failed; wrong chain/registry/log → không chấp nhận confirmed trong nghiệm thu dù API/DB hiện báo gì. Ghi mismatch và owner thay vì tự sửa proof/SQL. Canonical raw log/deployment evidence phải lấy ngoài public DTO như trên.

## Tests/evidence đã đọc, không chạy lại

AuditChainStatusMapperTest có13methods (missing proof, chain mismatch, missing block/registry, malformed hash, pending/failure mapping). AuditTrailApiIT3methods có scoped permission/state/pagination; PublicAuditTrailIT4methods có privacy markers trên toàn JSON, visibility, bounded pages và sequence overriding timestamp/opaque IDs. Pagination tests đi đến empty page; không suy đã test fresh case không có business audit (case creation thường tạo audit). Negative mapper tests dùng boolean proof flag không thay exhaustive SQL tamper matrix hoặc live canonicality tests.

Tái dùng PR30 reports của Dũng (đúng ef30487), không gọi chúng là runtime run trên main627be56 hoặc PR33f85d390. Không chạy Java/JUnit/DB/CI/Hardhat/RPC mới. Source mới proof writer PR33 và thiếu chain patch/evidence đã được Task3 ghi, không re-review ở Task8.

## Handoff / trạng thái

- **Dũng:** sau local gate/deployment/cutover được duyệt, bàn giao scoped proof bundle + actual ledger/canonical receipt/log + T01/T02 captures và provenance. Sửa backend nếu delta evidence thật mismatch; không tạo Fuji evidence thay bằng snapshot/local report.
- **Minh:** giữ D49/status/link boundary; nhận response pilot đã nghiệm thu để kiểm UI/F09/F10 khi đến task tương ứng. Không yêu cầu thêm UI hoặc sửa nhánh Minh trong lượt này.
- **Khoa:** checklist PREPARED; khi bundle đến chỉ đối chiếu SHA/operation/proof/API delta và ghi verdict từng mốc; thiếu deployment/pilot hiện tại thì FUJI PROOF VERIFICATION PENDING. Không tự thông qua C10/Fuji E2E từ source review.

Task8 độc lập đã làm tối đa; chưa có pilot để nghiệm thu. Không có finding source mới cần sửa, các limitations nêu trên là trust/scope boundary đã có, không mở scanner/hosting. Chỉ commit tài liệu local trên khoa, giữ dd77cfb/672a4b9; không push/PR/merge/code/config/wallet/blockchain.
