# LAN CopyPaste

MVP chia se text copy/paste giua laptop va dien thoai Android trong cung mang LAN.

## Chay thu

```powershell
npm install
npm start
```

Mo link LAN ma terminal in ra tren cac thiet bi cung Wi-Fi, vi du:

```text
http://192.168.1.12:3000
```

## Cach dung

1. Tren thiet bi gui, paste text vao o `Noi dung gui`.
2. Bam `Send`.
3. Tren thiet bi nhan, bam `Copy`.

## Desktop auto agent

Mo them terminal thu hai tren laptop va chay:

```powershell
npm run agent
```

Agent se tu doc clipboard Windows. Khi ban copy text tren laptop, no tu gui sang cac thiet bi dang mo web. Khi dien thoai gui text ve, agent tu ghi text do vao clipboard laptop.

Co the ket noi agent den server khac bang:

```powershell
$env:SERVER_URL="ws://192.168.1.101:3000"; npm run agent
```

Ban web/PWA van la cach tot nhat cho Android vi Android gioi han viec doc clipboard nen.

## Lich su ma hoa local

Moi doan text duoc gui qua server se duoc luu vao:

```text
.data/history.json
```

Noi dung trong file nay duoc ma hoa bang `AES-256-GCM`. Key nam rieng tai:

```text
.data/history.key
```

Web co panel `Lich su da ma hoa` de xem lai, copy lai, tai lai, hoac xoa toan bo lich su. Neu mat file key thi khong giai ma duoc lich su cu.

## Android companion / PWA

Tren Android, mo URL LAN trong Chrome:

```text
http://192.168.1.101:3000
```

Sau do mo menu Chrome va chon `Add to Home screen` hoac bam nut `Install` neu Chrome hien nut nay. Ban se co mot shortcut/app nhe tren man hinh chinh.

Ban nay co them Web Share Target:

1. Chon text/link trong app Android bat ky.
2. Bam `Share`.
3. Chon `LAN CopyPaste` neu Android/Chrome da dang ky PWA.
4. Noi dung se mo trong trang share va tu gui sang laptop.

Luu y: Web Share Target tren Chrome Android co the yeu cau PWA duoc cai dat va trong mot so ban Chrome co the khong kich hoat day du tren HTTP LAN. Neu muc Share khong hien, van dung cach mo web, paste text, roi bam `Gui ngay`.

## Android APK Stable mode

APK debug nam tai:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Tinh nang:

- Mo app, nhap server `http://192.168.1.101:3000`, bam `Luu server`.
- Trong app bat ky tren Android: chon text/link -> `Share` -> `LAN CopyPaste`.
- Them Quick Settings Tile `Send Clipboard`; sau khi copy text, keo Quick Settings va bam tile de gui clipboard sang laptop.

Server can dang chay `npm start`. APK gui ve endpoint:

```text
POST /api/clip
```
