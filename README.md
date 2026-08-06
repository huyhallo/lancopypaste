# LAN CopyPaste

LAN CopyPaste là công cụ chia sẻ nội dung copy/paste giữa máy tính và điện thoại trong cùng mạng Wi-Fi/LAN. Dữ liệu chạy trong mạng nội bộ của bạn, không cần tài khoản cloud và không gửi clipboard lên server bên ngoài.

> Android APK đã được phát hành.

## Dùng Nhanh Trên Windows

1. Cài [Node.js](https://nodejs.org/) nếu máy chưa có.
2. Tải/copy project về máy.
3. Mở thư mục project và chạy:

```powershell
npm install
```

4. Bấm đúp file:

```text
LAN CopyPaste Launcher.bat
```

Sau khi chạy, LAN CopyPaste sẽ hiện icon ở khay hệ thống Windows. Bấm đúp icon để mở web, hoặc chuột phải icon để mở menu.

Menu khay hệ thống có:

- `Mở web`
- `Copy địa chỉ LAN`
- `Khởi động server`
- `Dừng server`
- `Khởi động lại server`
- `Chạy cùng Windows`
- `Mở log server`
- `Thoát launcher`

Nếu muốn tạo lại shortcut ngoài Desktop, chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\create-desktop-shortcut.ps1
```

## Cách Kết Nối Điện Thoại

1. Trên máy tính, mở web bằng icon khay hoặc vào:

```text
http://localhost:3000
```

2. Vào tab `Ghép nối`.
3. Web sẽ hiển thị mã 6 số và QR.
4. Trên điện thoại, mở app Android LAN CopyPaste.
5. Vào `Cài đặt` -> `Ghép nối`.
6. Nhập mã 6 số hoặc dùng `Quét QR ghép nối`.

Sau khi ghép nối, điện thoại có thể gửi văn bản, ảnh và xem lịch sử đã nhận/gửi.

## Tính Năng Chính

- Gửi và nhận văn bản realtime trong mạng LAN.
- Gửi ảnh giữa web và Android.
- Web chia thành các tab dễ dùng: `Gửi nhận`, `Ghép nối`, `Lịch sử`, `Thiết bị`.
- Ghép nối bằng mã 6 số có thời hạn hoặc QR.
- Danh sách thiết bị đã ghép nối, có chấm xanh/đỏ cho trạng thái kết nối.
- Android có kiểm tra kết nối/ngắt kết nối để cập nhật trạng thái lên web.
- Lịch sử lưu local trên máy chạy server và được mã hóa.
- Tìm kiếm/lọc lịch sử theo nội dung, loại dữ liệu và thiết bị.
- Chuyển lịch sử vào kho lưu trữ thay vì xóa hẳn.
- Xem lại từng lần lưu trữ và mở lại nội dung bên trong.
- Android có log nội bộ, chia sẻ log và xuất file log.
- Windows launcher có icon khay hệ thống để người mới dễ biết server đang chạy.

## Dữ Liệu Được Lưu Ở Đâu?

Dữ liệu local nằm trong thư mục:

```text
.data/
```

Trong đó:

- `history.json`: lịch sử đang dùng, đã mã hóa.
- `history-trash.json`: kho lưu trữ, đã mã hóa.
- `history.key`: khóa giải mã local.
- `auth.json`: thiết bị đã ghép nối và trạng thái kết nối.
- `blobs/`: dữ liệu ảnh đã mã hóa.

Quan trọng:

- Không commit hoặc chia sẻ thư mục `.data/`.
- Nếu mất `history.key`, lịch sử cũ không thể giải mã.
- Nếu ai đó có cả dữ liệu mã hóa và `history.key`, họ có thể đọc lại lịch sử.

## Chạy Server Thủ Công

Nếu không dùng launcher, có thể chạy bằng terminal:

```powershell
npm install
npm start
```

Server mặc định chạy ở:

```text
http://localhost:3000
```

Thiết bị khác trong cùng mạng có thể mở bằng địa chỉ LAN, ví dụ:

```text
http://192.168.1.101:3000
```

## Android

Mã nguồn Android nằm trong:

```text
android/
```

Build APK debug:

```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

APK debug sau khi build:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

APK phát hành cho người dùng sẽ được đóng gói và tải lên sau.

## Lưu Ý Bảo Mật

LAN CopyPaste được thiết kế cho mạng nội bộ tin cậy.

Nên làm:

- Chỉ chạy trong Wi-Fi/LAN bạn tin tưởng.
- Không mở port `3000` ra Internet.
- Không chia sẻ thư mục `.data/`.
- Cẩn thận khi gửi mật khẩu, OTP, token API hoặc dữ liệu nhạy cảm.

Không nên dùng trong mạng công cộng nếu chưa hiểu rõ rủi ro.

Xem thêm:

- [SECURITY.md](SECURITY.md)
- [PRIVACY.md](PRIVACY.md)

## Phát Triển

Cài dependency:

```powershell
npm install
```

Chạy server:

```powershell
npm start
```

Chạy desktop clipboard agent cũ:

```powershell
npm run agent
```

Agent cũ vẫn còn trong repo, nhưng trải nghiệm khuyến nghị hiện tại là dùng `LAN CopyPaste Launcher.bat`.

## Giấy Phép

MIT. Xem [LICENSE](LICENSE).
