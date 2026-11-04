# Hướng dẫn chạy Noti Webhook

App Android chuyển **mọi thông báo mới** trên điện thoại thành request `POST` JSON tới một webhook URL.

> App có code native (Kotlin) nên **không chạy được bằng Expo Go**. Phải build ra file APK rồi cài vào máy.

---

## 1. Chuẩn bị (làm 1 lần)

Cần có:
- Node.js 20 trở lên
- Tài khoản Expo (https://expo.dev, miễn phí)
- Điện thoại Android 7.0 trở lên

Mở **PowerShell**, chạy từng lệnh một. PowerShell 5.1 không hiểu `&&`, nên đừng gộp lệnh.

```powershell
npm install -g eas-cli
```

```powershell
cd C:\source\Project\NotiWebhookExpo
```

```powershell
npm install
```

### Đăng nhập Expo

Có 2 cách:

**Cách A – Đăng nhập qua trình duyệt**
```powershell
eas login
```
Trình duyệt sẽ mở ra. Anh đăng nhập và **cho phép tạo/build project** khi được hỏi quyền.

**Cách B – Dùng token** (khi tài khoản tạo bằng Google/GitHub và không có mật khẩu)
1. Vào expo.dev → Account settings → **Access tokens** → Create token (chọn **personal token**, đừng chọn robot token).
2. Chạy lệnh sau. Biến này chỉ có tác dụng trong cửa sổ PowerShell hiện tại:
   ```powershell
   $env:EXPO_TOKEN="dán-token-vào-đây"
   ```

Project đã được gắn sẵn với EAS: `app.json` → `extra.eas.projectId`, owner là `tvinhthanh`, nên **không cần** chạy `eas init`.

---

## 2. Build APK

```powershell
eas build -p android --profile preview
```

- Nếu được hỏi *"Generate a new Android Keystore?"* → chọn **Yes**.
- Build chạy trên cloud của Expo, mất khoảng 10–20 phút. Gói miễn phí có thể phải xếp hàng.
- Build xong, terminal in ra **link tải APK** kèm mã QR. Anh cũng xem được ở expo.dev → project `noti-webhook` → Builds.

Profile `preview` ra file `.apk` để cài trực tiếp. Profile `production` ra `.aab` để đưa lên Google Play.

---

## 3. Cài và cấu hình trên điện thoại

1. Mở link APK trên điện thoại, tải về rồi cài. Nếu Android chặn, cho phép *"Cài ứng dụng không rõ nguồn gốc"*.
2. Mở app **Noti Webhook**.
3. Bấm **Cấp quyền đọc thông báo**, tìm **Noti Webhook** trong danh sách và bật lên, rồi quay lại app.
4. Bấm **Tắt tối ưu pin**, chọn **Cho phép**. Nếu không làm bước này, Android có thể tắt app khi chạy nền.
5. Nhập **Webhook URL** (bắt đầu bằng `https://` hoặc `http://`) và bấm **Lưu**.
6. Bấm **Gửi thử**. Nếu hiện *"Gửi thành công · HTTP 200"* là OK.

### Riêng máy Xiaomi / Oppo / Vivo / Realme / Huawei

Mấy hãng này kill app chạy nền rất mạnh, nên cần làm thêm:
- Cài đặt → Ứng dụng → Noti Webhook → bật **Tự khởi chạy (Autostart)**.
- Tiết kiệm pin → Noti Webhook → chọn **Không giới hạn**.
- Mở màn hình đa nhiệm, kéo app xuống để **khoá (lock)** app, tránh bị dọn khi bấm "xoá tất cả".

---

## 4. Kiểm tra app chạy đúng

1. Vào https://webhook.site và copy **Your unique URL**.
2. Dán URL đó vào app, bấm Lưu rồi bấm Gửi thử. Trên webhook.site sẽ hiện 1 request.
3. Nhờ ai nhắn Zalo/Messenger/SMS cho mình. Mỗi thông báo hiện lên điện thoại sẽ:
   - hiện thêm 1 request trên webhook.site,
   - hiện thêm 1 dòng trong mục **Lịch sử gửi** của app (chấm xanh là thành công, chấm đỏ là lỗi, kèm mã HTTP hoặc lỗi cụ thể).

---

## 5. Dữ liệu gửi lên webhook

Mỗi thông báo gửi 1 request `POST`, header `Content-Type: application/json; charset=utf-8`:

```json
{
  "packageName": "com.zing.zalo",
  "appName": "Zalo",
  "title": "Nguyễn Văn A",
  "text": "Tối nay đi ăn không?",
  "bigText": "",
  "subText": "",
  "category": "msg",
  "postTime": 1730700000000,
  "key": "0|com.zing.zalo|1234|null|10123",
  "isOngoing": false,
  "device": "samsung SM-S918B"
}
```

| Trường | Ý nghĩa |
|---|---|
| `packageName` | Mã app gửi thông báo, dùng để lọc (ví dụ chỉ lấy app ngân hàng) |
| `appName` | Tên app hiển thị |
| `title` / `text` | Tiêu đề và nội dung ngắn |
| `bigText` | Nội dung đầy đủ khi thông báo dạng mở rộng (tin nhắn dài, biến động số dư…) |
| `postTime` | Thời điểm thông báo, tính bằng mili giây (Unix epoch) |
| `key` | ID của thông báo trên hệ thống |
| `isOngoing` | `true` nếu là thông báo thường trực (nhạc, tải file…) |

**Cơ chế gửi**
- Lỗi mạng hoặc server trả 5xx/408/429: thử lại tối đa **3 lần** (chờ 1s, rồi 4s).
- Server trả 4xx khác: không thử lại.
- Thông báo trùng hệt trong 10 giây bị bỏ qua (app hay đăng lại cùng thông báo khi cập nhật).
- Không gửi: thông báo nhóm (group summary), thông báo không có nội dung, thông báo của chính app này.
- Webhook nên trả về HTTP **2xx** càng nhanh càng tốt. Timeout là 10s khi kết nối, 15s khi đọc.

> Lưu ý: nội dung thông báo có thể chứa tin nhắn riêng, OTP, số dư tài khoản… Anh nên dùng URL `https://` và để webhook ở server do mình kiểm soát.

---

## 6. Sửa code và build lại

| Sửa gì | File |
|---|---|
| Giao diện | `App.tsx` |
| Bắt thông báo, lọc, nội dung JSON | `modules/noti-webhook/android/src/main/java/expo/modules/notiwebhook/NotiListener.kt` |
| Gửi HTTP, retry | `modules/noti-webhook/android/src/main/java/expo/modules/notiwebhook/WebhookSender.kt` |
| Hàm gọi từ JS | `modules/noti-webhook/android/src/main/java/expo/modules/notiwebhook/NotiWebhookModule.kt` + `modules/noti-webhook/index.ts` |
| Quyền, khai báo service | `modules/noti-webhook/android/src/main/AndroidManifest.xml` |

Sau khi sửa code, phải **build lại APK** (`eas build -p android --profile preview`) rồi cài đè lên bản cũ. Cấu hình và lịch sử trong app vẫn được giữ.

Muốn kiểm tra lỗi TypeScript trước khi build:
```powershell
npx tsc --noEmit
```

---

## 7. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `The token '&&' is not a valid statement separator` | PowerShell 5.1 không hỗ trợ `&&`. Chạy từng lệnh riêng. |
| `you can't create one... permissions granted during authentication` | Token thiếu quyền. Chạy lại `eas login` và cấp quyền, hoặc tạo personal access token mới. |
| `eas login` bắt nhập mật khẩu nhưng không có | Dùng Cách B (token) ở mục 1. |
| Build lỗi trên EAS | Mở link build, xem log ở bước bị lỗi (thường là *Run gradlew*), rồi gửi log để sửa. |
| Gửi thử báo `Chưa cấu hình webhook URL` | Nhập URL rồi bấm **Lưu** trước. |
| Gửi thử báo `UnknownHostException` / `ConnectException` | Điện thoại mất mạng, hoặc URL sai hay không truy cập được từ điện thoại. Nếu dùng IP LAN (`192.168.x.x`) thì điện thoại phải chung Wi-Fi với server. |
| Gửi thử OK nhưng thông báo thật không gửi | Kiểm tra lại quyền đọc thông báo: vào app xem còn cảnh báo vàng không. Kiểm tra công tắc *"Đang chuyển tiếp thông báo"* có bật không. |
| Một lúc sau thì ngừng gửi | Android đã kill app. Làm bước **Tắt tối ưu pin** và phần riêng cho Xiaomi/Oppo… ở mục 3. Mở lại app một lần cũng giúp hệ thống kết nối lại service. |
| Thiếu nội dung tin nhắn | Một số app ẩn nội dung khi màn hình khoá. Bật hiển thị nội dung thông báo trong cài đặt của app đó. |
