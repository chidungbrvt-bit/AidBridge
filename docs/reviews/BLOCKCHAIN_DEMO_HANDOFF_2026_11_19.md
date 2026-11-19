# Blockchain proof — video/slide handoff, 19/11/2026

**Kết luận:** đã đọc trực tiếp Fuji và xác minh **4 giao dịch C03: 1 deployment + 3 CaseVerified**. Không có bằng chứng C06 deploy hoặc pilot hàng hóa Fuji mới trong các refs/PR đã kiểm tra. C06 vẫn **FREEZE CANDIDATE trong hồ sơ repo**; không thể dùng candidate/Hardhat để khẳng định đã deploy. Không loại trừ một deployment ngoài repo chưa được bàn giao.

## Link sử dụng ngay

Chain **Avalanche Fuji C-Chain 43113**; registry **C03** `0x015D941c679b0c402c8262BE237adAD36cEf6726`.

Contract/code: https://testnet.snowtrace.io/address/0x015D941c679b0c402c8262BE237adAD36cEf6726#code

| Loại / trạng thái | Explorer transaction | Block / receipt / event |
|---|---|---|
| C03 deploy — VERIFIED ON FUJI | https://testnet.snowtrace.io/tx/0xf5a11026f7bea424b6ce538ab74f7618e619264e2be86d5a7407e92505b49d24 | 60885528; status1; contractAddress đúng C03, canonical block |
| Independent contract smoke 11/11 — VERIFIED ON FUJI, không phải job B05 | https://testnet.snowtrace.io/tx/0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f | 60893112; status1; CaseVerified, seq1/schema1/logIndex0 |
| B05 historical #1 — VERIFIED ON FUJI; **link nên dùng trong slide/video** | https://testnet.snowtrace.io/tx/0x465140011495f85743088ada113097204a891e41bc0b985b15e653366cb8566a | 60959263; status1; CaseVerified, seq1/schema1/logIndex0 |
| B05 historical #2 — VERIFIED ON FUJI, link dự phòng | https://testnet.snowtrace.io/tx/0x97104a33730c05210da67a21c96b271876d744fcb61fbab46a5d87cff925f98e | 60959653; status1; CaseVerified, seq1/schema1/logIndex0 |

**Source verification:** explorer API hiện trả AidRegistry, compiler `v0.8.28+commit.7893614a`, source và ABI có công bố, proxy0. Trạng thái **EXPLORER VERIFIED** theo API; không compile lại source trong lượt này, không coi là verify source C06. URL kiểm tra:
https://api.routescan.io/v2/network/testnet/evm/43113/etherscan/api?module=contract&action=getsourcecode&address=0x015D941c679b0c402c8262BE237adAD36cEf6726

Runtime C03 đọc từ RPC khớp hash lịch sử `0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad`. Đã kiểm transaction.chainId, destination/emitter, receipt status, blockHash so block canonical, txHash/logIndex/removed và decode đúng event signature `CaseVerified(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64)`. Không chỉ dựa vào explorer link hoặc status1.

[Snapshot RPC công khai](evidence/fuji-proof-2026-11-19.json) lưu thời điểm UTC, block hashes, raw topics/data và đầy đủ eventId/orgRef/caseRef/sequence/evidenceCommitment, cùng source metadata/hash. Kiểm tra lúc **15:25 ngày19/11 UTC+07**; không đọc signer/key hay gửi transaction. Explorer UI/link click trong browser chưa smoke-test; receipt/source đã kiểm qua RPC/API, không coi UI đã quay được.

## Proof thuộc hồ sơ nào?

Cả ba CaseVerified có orgRef `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46` nhưng **ba caseRef khác nhau**:

| Proof | caseRef | eventId |
|---|---|---|
| Smoke | `0x9667486a2b4613986fab8955798abe2ac28f337549e622c29d2058d4c6b64494` | `0x13c87e35c29891a05f4daf06fca618b2647cb486fe4edc7a7ec336f6d84addc9` |
| B05 #1 | `0x8d5c62bea7acd49a2e54e29efadc01b56f39fa0e55a40d6c9d33217c2b3dd9d6` | `0xb015509dd7c0f529e05293bc96fc263115a1b524a139a5ddf70e7d0ffe389a78` |
| B05 #2 | `0x3429fa203bf357eb91aba6f367e70180da2563ab9e564c9d5ee345461ae7929c` | `0x72c71be20cc1f8f0120839dff5fd781b8ce6d1cd7f761520b1afe0ace0adb91f` |

- **DB/audit mapping B05: MATCH với CSV đã cung cấp**, không phải kiểm DB live. Hai dòng CONFIRMED trong `origin/main@32a0984:docs/reviews/evidence/task5/b1-jobs-aidbridge_demo.csv` khớp orgRef/caseRef/logical_event_id/sequence, C03 registry, signer D54, nonce0/1, block và txHash của B05 #1/#2. Tài liệu B05 ngày13/11 hỗ trợ cùng mapping. Chưa đọc API audit live hôm nay.
- **Không thuộc pilot staging trong plan hiện có.** Task5 plan trên main dùng caseRef `0x87db7e09856e03b7d8465936543a1821ff91e48e7b226254b0fe9a67ed37de2e`, orgRef `0x26b3e1feb12244348aae40a3eb2db479daa4562b627049eba9d6710b92b7299f`: khác cả case và org của proof lịch sử. Không gắn hash B05 vào hồ sơ này.
- **Hồ sơ mới do Minh tạo xuyên suốt E2E ngày18/11: UNCONFIRMED về mapping Fuji.** `flow-evidence.json` trên main khai báo `blockchainEnabled:false`; không cung cấp publicCaseRef/orgRef để gắn với Fuji. Backend-real E2E không phải proof on-chain. Dũng/Minh cần chốt hồ sơ quay thực tế và cung cấp public refs nếu khác plan staging.
- C06 multi-operation/full-chain: **LOCAL ONLY** theo evidence Hardhat31337 đã review trước; không có receipt C06/Fuji goods mới được bàn giao. C03 không đại diện proof C06. Manifest FREEZE CANDIDATE không có address/receipt C06 thật.

## Minh — cảnh quay 2–3 phút

| Thời lượng | Cảnh / thông điệp |
|---|---|
| 0:00–0:25 | UI hồ sơ demo giả lập: nhu cầu hai mặt hàng, người tạo và người duyệt độc lập; không quay credentials/evidence riêng tư |
| 0:25–1:05 | Campaign → cam kết → nhận từng phần hai đợt; chỉ rõ received và outstanding thay đổi từ backend thật |
| 1:05–1:40 | Delivery → tài khoản khác xác nhận; hiện đúng quantities/review status; có thể lướt guard tự xác nhận/tranh chấp nếu thời lượng cho phép |
| 1:40–2:00 | Timeline/audit của **chính hồ sơ UI**: status thực tế PENDING/unverified phải giữ nguyên; không sửa fixture/status để bật explorer link |
| 2:00–2:35 | Chuyển cảnh có caption **“Bằng chứng Fuji lịch sử — hồ sơ khác”**. Mở link B05 #1 trong bảng; chỉ network Fuji, status success, to=C03, block, event CaseVerified/seq1. Mở contract code để thấy verified source; nếu explorer UI không tải, dùng API snapshot công khai có ghi nguồn, không giả màn hình explorer |
| 2:35–2:50 | Kết: nghiệp vụ backend thật đã demo; CASE_VERIFIED có proof Fuji lịch sử; hàng hóa/full-chain chỉ có local evidence, C06 chưa có proof deploy bàn giao |

D49 public audit là `/api/public/cases/{publicCaseRef}/audit`; lấy URL thật từ UI/API của hồ sơ quay, không thay public ref bằng UUID. Khi chưa có confirmed proof đúng hồ sơ, không ép bật link. Có thể mở lịch sử B05 bằng URL trực tiếp như cảnh riêng, không dựng thành một hành trình on-chain liền mạch của case mới.

## Dũng — nội dung slide/pitch có thể dùng trực tiếp

**Slide “Avalanche audit trail”**

- AidBridge neo bằng chứng xác minh hồ sơ lên smart contract AidRegistry trên Avalanche Fuji C-Chain (43113).
- Đã xác minh trực tiếp ba giao dịch CaseVerified trên registry C03; hai giao dịch khớp ledger B05 lịch sử. Mỗi event có orgRef, caseRef, eventId, sequence và evidence commitment; không đưa hồ sơ cá nhân/evidence gốc lên chain.
- Demo nghiệp vụ nhận/giao/xác nhận chạy backend thật. Chuỗi hàng hóa mới có bằng chứng local; chưa tuyên bố full-chain Fuji hoặc C06 deployment.
- Proof chính/QR: dùng **B05 #1**; code/source: dùng contract link trong bảng. Link dự phòng B05 #2. Caption: **Historical Fuji proof — CASE_VERIFIED / C03**, không “pilot goods confirmed”.

**Lời nói gợi ý:** “Chúng tôi dùng Avalanche để tạo dấu vết có thể kiểm chứng cho việc xác minh hồ sơ. Giao dịch Fuji này ghi lại định danh công khai và cam kết bằng chứng trên AidRegistry. Luồng nhận, giao và xác nhận được demo với backend thật; phần blockchain hàng hóa hiện được kiểm chứng ở local, chưa tuyên bố đã chạy trọn chuỗi trên Fuji.”

Giới hạn: relayer ghi kết quả nghiệp vụ do hệ thống chấp thuận; blockchain không tự chứng minh hàng đã đến tay người nhận, tính đúng đắn của tài liệu gốc hay loại bỏ thông đồng. Commitment không phải công khai evidence hoặc chữ ký độc lập của từng actor.

## Cần xác nhận thêm / provenance

1. Dũng/Minh chốt hồ sơ quay hôm nay: publicCaseRef, orgRef, campaign public URL và API audit response đã khử thông tin riêng tư. Hiện mapping proof lịch sử sang case mới không được chứng minh.
2. Nếu C06 đã được deploy ngoài repo: Dũng bàn giao approved deployment record, address/tx/block, artifact/runtime và quyền readback; trước đó giữ NOT VERIFIED, không tự tìm địa chỉ dự đoán để gọi là deployment.
3. Minh kiểm tra explorer UI tải được trước khi quay và giữ caption phân biệt lịch sử/current/local. Hosting/link UI public chưa được kiểm tra hôm nay.
4. Source/refs đọc: main `32a0984`, dung `3b219f1`, khoa `2f08279`; PR35 đã MERGED, PR38/40 đã MERGED. Không pull/merge vào khoa trong lượt này. Task5 report/CSV và Minh day18 handoff trên main được đọc trực tiếp bằng Git; local C10 gate không review lại.

Chỉ bổ sung tài liệu và snapshot công khai; không gửi tin nhắn cho thành viên, không push/PR/merge, không tests full suite, deploy/cutover/DB changes hoặc giao dịch blockchain.


## Fee clarification — 19/11/2026, PR41

Đã lấy lại receipt chỉ đọc từ Fuji RPC chính thức, đối chiếu PublicNode và Routescan receipt proxy + transaction API. B05 #1 và #2 đều trả **gasUsed `0x3edc1` = 257473**, **effectiveGasPrice `0xc0` = 192 wei = 0.000000192 gwei**. Phép nhân BigInt: **49434816 wei = 0.000000000049434816 AVAX mỗi giao dịch**. Routescan transaction API cũng trả gasPrice192 và burnedFees49434816; transaction gasPrice đọc trước đó cũng là0xc0. Smoke trả `0x3b9aca00` = 1000000000 wei = 1 gwei, phí **0.000257473 AVAX**. Raw hex/các nguồn và phép tính chính xác được bổ sung vào feeVerification của snapshot; proof fields cũ không thay đổi.

Dũng báo Snowtrace hiển thị khoảng **0.0104943 AVAX/B05 tx**. Số này **không khớp receipt và ba nguồn đã đối chiếu**, không được thay vào snapshot như phí đã xác minh. Trang Snowtrace trực tiếp trả HTTP403 nên chưa tái hiện số UI hoặc xác định nguyên nhân hiển thị. Không có bằng chứng nhầm hex/decimal hay wei/gwei trong phép tính: 0xc0 thực sự bằng192; không hardcode thành giá trị UI. Snapshot trước bổ sung này chưa có trường gas/fee; số192 nằm trong báo cáo B05 ngày13/11, và nay được RPC/API tái xác nhận.

**Slide:** nếu cần phí, dùng số receipt chính xác ở trên, hoặc bỏ số phí khỏi pitch trong khi chờ đối chiếu UI. Không xác nhận0.0104943AVAX là phí gas. Dũng cung cấp ảnh/URL đúng tx với nhãn trường/đơn vị đang xem để giải thích chênh lệch UI; không cần chạy lại worker/tests.
