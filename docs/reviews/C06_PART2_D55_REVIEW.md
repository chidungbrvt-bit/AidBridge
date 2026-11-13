# Task 4 — Review C06 phần 2 và D55, 13/11/2026

**Verdict: PASS; Task 4 hoàn thành.** Lưu lại kết luận review đã thực hiện, không chạy lại review trong Task 5.

Phiên bản: `6944fffd9858565f36c909ae9e5f83804afbe36f` (contract/tests/ABI) và `e8d8278dbd0315d309d934ae1881b4aa243dfaf2` (D55/docs), đều đã ở main. Source trên origin/dung tại `76321e6` không đổi sau bản này.

- `confirmDelivery`, `flagDispute`, `resolveDispute`: authorization trước lookup; validation trước event replay; digest gắn operation/refs; cùng org/case; đúng sequence; mutation nguyên tử. Không phát hiện lỗi đáng kể cần sửa.
- D55 đúng implementation: UPHOLD=1/VOID=2 dạng uint8; deliveryRef luôn khớp dispute; dispute mức case cho phép zero deliveryRef; VOID phát DeliveryVoided rồi DisputeResolved dưới cùng eventId/sequence, event record lưu operation resolveDispute.
- BR-06 và quyền PA thực hiện VOID vẫn do backend kiểm tra vì contract chỉ thấy Relayer. C10 cần đối chiếu cả hai log VOID, không tạo hai job/sequence.
- D53/B07 liên quan phù hợp: khóa case trước MAX+1, UNIQUE(case,sequence), outbox cùng transaction. Không review toàn bộ backend.
- Bằng chứng đã kiểm tra độc lập: ABI C03 functions/events không đổi; ba selector C06 mới không có ở C03. RPC Fuji43113 xác nhận runtime registry `0x015D941c679b0c402c8262BE237adAD36cEf6726` khớp snapshot C03, hash `0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad`; eth_call confirmDelivery trên registry cũ revert rỗng. Không ký/gửi giao dịch.
- Hardhat 194 passing/1 pending là kết quả Dũng ghi trong backlog; đã đọc tests nhưng không tự chạy lại compile/Hardhat và không nhận đó là test run mới của Khoa.

**Fuji redeploy YES khi đưa delivery/dispute C06 lên Fuji; không cần chỉ để chạy CASE_VERIFIED C05 trên registry C03.** Registry không upgradeable, thay ABI không bổ sung runtime code. Registry mới có address/config mới, cần đăng ký lại org/Relayer (có thể giữ cùng ví và orgRef); trạng thái case/event/sequence không tự chuyển. Phải có kế hoạch chuyển trạng thái trước khi trỏ job cũ tới registry mới; không tái sử dụng signed bytes cũ. Giữ bằng chứng C04/C05 gắn đúng deployment cũ, không ghi đè/đổi nhãn proof. Task 4 không cấp phép redeploy và không phải gate nghiệm thu C08.
