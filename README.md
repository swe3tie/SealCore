# SealCore

Plugin lõi (All-in-One Core) cho máy chủ Minecraft **Economy SMP**, tối ưu hóa và tương thích hoàn toàn với cả **Paper** và **Folia** (hỗ trợ đa luồng theo vùng - Regionized Multithreading).

SealCore tập trung vào việc kiến tạo trải nghiệm gameplay sinh tồn kinh tế cân bằng, tương tác giao diện (GUI) mượt mà, cùng hệ thống tính năng phong phú phục vụ cộng đồng người chơi.

---

## Tính năng Gameplay nổi bật

### 1. Hệ thống Kinh Tế Toàn Diện (Economy)
- **Giao diện Bán Hàng Trực Quan (`/sell`)**:
  - Kéo thả tự do vật phẩm vào GUI, hệ thống tự động quét và tính toán tổng số tiền theo thời gian thực.
  - Nhận tiền ngay lập tức khi đóng giao diện. Các vật phẩm không có giá bán được hoàn trả an toàn về túi đồ.
- **Hệ số nhân Bán Hàng (Sell Multiplier / Progression)**:
  - Cày cuốc để nâng cấp các mốc bán hàng theo từng nhóm danh mục (Nông sản, Khoáng sản, Mob drops...).
  - Mở khóa cấp độ để gia tăng vĩnh viễn % tiền thưởng khi bán đồ (ví dụ: +5%, +10%, +25%).
- **Cửa Hàng Máy Chủ Đa Dạng (`/shop`)**:
  - Giao diện mua sắm phân loại theo danh mục: Lương thực, Trang bị, Nether, The End, Shards...
  - Menu chọn số lượng (`/shop amount`) linh hoạt: hỗ trợ tăng/giảm số lượng nhanh, mua tối đa theo sức chứa túi đồ hoặc theo số dư hiện có.
- **Lịch Sử Giao Dịch Bán (`/sell history`)**:
  - Lưu trữ và hiển thị trực quan các đợt bán đồ gần nhất giúp người chơi dễ dàng theo dõi thu nhập.
- **Soi Giá Trị Vật Phẩm (Packet Worth Lore)**:
  - Xem trực tiếp giá bán của vật phẩm ngay trên dòng thông tin (lore) trong túi đồ mà không làm thay đổi dữ liệu (NBT) gốc của item.
  - Lệnh `/worth` hỗ trợ tra cứu nhanh giá bán của vật phẩm đang cầm trên tay.
- **Giao Dịch Linh Hoạt (`/pay`, `/balance`)**:
  - Hỗ trợ nhập số tiền viết tắt thông minh và tiện lợi (ví dụ: `10k`, `1.5m`, `2b`, `5t`).
  - Tích hợp thời gian chờ (cooldown) và hạn mức giao dịch để ngăn chặn lạm dụng và gian lận.

### 2. Trải Nghiệm Người Chơi & Hiệu Năng
- **Giao diện & Tin nhắn chuẩn MiniMessage**: Hỗ trợ màu sắc gradient, hex color hiện đại, định dạng trực quan.
- **Đa ngôn ngữ**: Đi kèm sẵn gói ngôn ngữ Tiếng Việt (`vi.yml`) và Tiếng Anh (`en.yml`).
- **Tối ưu hóa Folia**: Toàn bộ tác vụ gameplay được xử lý qua scheduler chuyên biệt, vận hành mượt mà trên môi trường máy chủ phân vùng đa luồng mà không gây xung đột dữ liệu.

---

## Lộ trình Phát triển (Roadmap)

Kế hoạch xây dựng và hoàn thiện các module tính năng gameplay cho SealMC:

- [x] **economy** *(Đã hoàn thành)*: Hệ thống kinh tế hoàn chỉnh — `/sell` GUI thông minh, hệ số nhân cấp độ bán, `/shop` danh mục, lịch sử bán hàng, packet worth lore, chuyển tiền `/pay` & số dư `/balance` (hỗ trợ `10k`, `1.5m`, `2b`...).
- [ ] **shards**: Khu vực AFK nhận thưởng tài nguyên theo thời gian; bộ công cụ và trang bị Amethyst custom với chỉ số và hiệu ứng đặc biệt.
- [ ] **auction**: Hệ thống chợ đen / sàn đấu giá giữa người chơi, cho phép tự do niêm yết, đấu giá và trao đổi vật phẩm quý hiếm.
- [ ] **bounty**: Hệ thống truy nã người chơi — đặt tiền thưởng săn đầu người, kích thích giao tranh và nhận thưởng khi tiêu diệt mục tiêu.
- [ ] **combat**: Rơi shards khi hạ gục đối thủ; hệ thống Combat Log chống đăng xuất khi đang giao tranh (combat tag); tùy biến thông báo hạ gục (kill messages).
- [ ] **duel**: Đấu trường PvP 1v1 tại map riêng biệt; hỗ trợ chế độ thách đấu cược đồ / mất đồ khi tử trận (high-stakes pvp).
- [ ] **home**: Hệ thống quản lý điểm nhà cá nhân (`/sethome`, `/home`), cho phép cấu hình giới hạn số lượng home linh hoạt theo cấp bậc / rank.
- [ ] **stats**: Thống kê chi tiết chỉ số sinh tồn của người chơi (kills, deaths, thời gian chơi, số quặng đã đào...); hệ thống bảng xếp hạng (Leaderboards) vinh danh top server.
- [ ] **spawner**: Hệ thống lồng quái ảo (Virtual Spawner) — tích lũy và rơi thẳng vật phẩm/kinh nghiệm vào kho hoặc túi đồ, tối ưu hóa hiệu năng máy chủ khi không phải sinh thực thể quái vật.
- [ ] **team**: Hệ thống tổ đội / bang hội — liên minh cùng đồng đội, chia sẻ tài nguyên và các quyền lợi nhóm.
- [ ] **teleport**: Hệ thống dịch chuyển người chơi toàn diện (`/warp`, `/tpa`, `/tpahere`, `/tpaccept`, `/spawn`, `/back`...).

---

## Danh Sách Lệnh Người Chơi

| Lệnh | Mô tả | Quyền hạn mặc định |
| --- | --- | --- |
| `/sell` | Mở giao diện bán đồ tự động | `sealcore.economy.sell` |
| `/sell history` | Xem lại lịch sử các lần bán hàng gần nhất | `sealcore.economy.sell` |
| `/shop` | Mở cửa hàng mua sắm vật phẩm của máy chủ | `sealcore.economy.shop` |
| `/balance`, `/bal` | Kiểm tra số dư tài khoản của bản thân | `sealcore.economy.balance` |
| `/balance <player>` | Xem số dư của người chơi khác | `sealcore.economy.balance.others` |
| `/pay <player> <số_tiền>` | Chuyển tiền cho người chơi khác (hỗ trợ `10k`, `1.5m`...) | `sealcore.economy.pay` |
| `/worth` | Tra cứu giá bán của vật phẩm đang cầm | `sealcore.economy.worth` |
| `/sealcore reload` | Tải lại cấu hình và ngôn ngữ của plugin (Admin) | `sealcore.admin` |

---

## Cài Đặt & Cấu Hình

### Yêu cầu hệ thống
- Máy chủ Minecraft chạy **Paper** hoặc **Folia** (phiên bản 1.21.x trở lên).
- Môi trường chạy **Java 21** trở lên.
- Các plugin liên kết (khuyên dùng):
  - **ExcellentEconomy**: Bắt buộc để quản lý các nguồn tiền tệ của module kinh tế.
  - **PacketEvents**: Bắt buộc để kích hoạt tính năng Packet Worth Lore mà không chỉnh sửa NBT của item.
  - **PlaceholderAPI**: Để sử dụng placeholder trong tin nhắn và giao diện.

### Cài đặt nhanh
1. Tải bản build `SealCore.jar` và đặt vào thư mục `plugins/` của máy chủ.
2. Cài đặt các plugin phụ trợ cần thiết (`ExcellentEconomy`, `PacketEvents`).
3. Khởi động máy chủ để SealCore tự động khởi tạo thư mục và các tệp cấu hình mặc định tại `plugins/SealCore/`.
4. Điều chỉnh giá bán và tùy biến giao diện trong thư mục `plugins/SealCore/modules/economy/`.
5. Sử dụng lệnh `/sealcore reload` để áp dụng các thay đổi cấu hình mà không cần khởi động lại máy chủ (ngoại trừ các thiết lập kết nối cơ sở dữ liệu và tên lệnh).
