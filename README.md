# LAN CopyPaste

LAN CopyPaste là bộ công cụ chia sẻ văn bản copy/paste giữa laptop và điện thoại Android trong cùng mạng LAN. Dự án ưu tiên chạy nội bộ, không cần tài khoản cloud, có web app, desktop agent và Android companion app.

## Tính năng

- Gửi/nhận văn bản realtime qua WebSocket trong mạng LAN.
- Web/PWA dùng được trên laptop, điện thoại và tablet.
- Desktop agent cho Windows: tự theo dõi clipboard, tự gửi khi clipboard đổi, tự ghi clipboard khi nhận từ thiết bị khác.
- Android app Stable mode:
  - Nhận text/link từ Android Share menu.
  - Gửi clipboard bằng Quick Settings Tile.
  - Tải nội dung mới nhất từ server và sao chép lại.
- Lịch sử local được mã hóa bằng `AES-256-GCM`.
- Tìm kiếm lịch sử, lọc theo thiết bị gửi và giới hạn số mục hiển thị.

## Chạy server

```powershell
npm install
npm start
```

Terminal sẽ in ra URL LAN, ví dụ:

```text
http://192.168.1.101:3000
```

Mở URL này trên các thiết bị cùng Wi-Fi/LAN.

## Desktop Agent

Mở thêm một terminal và chạy:

```powershell
npm run agent
```

Agent sẽ theo dõi clipboard Windows. Khi bạn copy text trên laptop, nội dung sẽ được gửi sang các thiết bị đang kết nối. Khi thiết bị khác gửi text về, agent sẽ ghi text đó vào clipboard laptop.

Kết nối agent tới server khác:

```powershell
$env:SERVER_URL="ws://192.168.1.101:3000"; npm run agent
```

## Android App

APK debug sau khi build nằm tại:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Cách dùng:

- Mở app, nhập server, ví dụ `http://192.168.1.101:3000`, rồi bấm `Lưu máy chủ`.
- Trong app bất kỳ trên Android: chọn text/link -> `Share` -> `LAN CopyPaste`.
- Thêm Quick Settings Tile `Gửi clipboard`; sau khi copy text, kéo Quick Settings và bấm tile để gửi sang laptop.
- Trong app, bấm `Tải mới nhất` để lấy nội dung mới nhất từ server rồi bấm `Sao chép`.

Build APK:

```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

## Lịch Sử Mã Hóa Local

Mọi đoạn text gửi qua server được lưu local tại:

```text
.data/history.json
```

Nội dung trong file này được mã hóa bằng `AES-256-GCM`. Key giải mã nằm riêng tại:

```text
.data/history.key
```

Quan trọng:

- `.data/` đã được đưa vào `.gitignore`.
- Nếu mất `history.key`, lịch sử cũ không thể giải mã.
- Nếu người khác lấy được cả `history.json` và `history.key`, họ có thể giải mã lịch sử.

## Bảo Mật

LAN CopyPaste được thiết kế cho mạng nội bộ tin cậy. Bản hiện tại chưa có đăng nhập, phân quyền người dùng hoặc mã hóa end-to-end giữa các thiết bị.

Khuyến nghị:

- Chỉ chạy trong mạng Wi-Fi/LAN bạn tin tưởng.
- Không mở port server ra Internet.
- Không commit hoặc chia sẻ thư mục `.data/`.
- Cẩn thận khi copy mật khẩu, OTP, token API hoặc dữ liệu nhạy cảm.

Xem thêm [SECURITY.md](SECURITY.md) và [PRIVACY.md](PRIVACY.md).

## Giấy Phép

Dự án phát hành theo giấy phép MIT. Xem [LICENSE](LICENSE).
