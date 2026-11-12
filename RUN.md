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

Gửi **tất cả thông tin** Android cung cấp về thông báo (ví dụ rút gọn):

```json
{
  "event": "posted",
  "sentAt": 1730700000123,
  "packageName": "com.zing.zalo",
  "appName": "Zalo",
  "title": "Nguyễn Văn A",
  "titleBig": "",
  "text": "Tối nay đi ăn không?",
  "bigText": "",
  "subText": "",
  "summaryText": "",
  "infoText": "",
  "tickerText": "",
  "conversationTitle": "",
  "isGroupConversation": false,
  "textLines": [],
  "messages": [{ "text": "Tối nay đi ăn không?", "time": 1730700000000, "sender": "Nguyễn Văn A" }],
  "template": "android.app.Notification$MessagingStyle",
  "id": 1234,
  "tag": null,
  "key": "0|com.zing.zalo|1234|null|10123",
  "groupKey": "0|com.zing.zalo|g:chat",
  "group": "chat",
  "sortKey": "",
  "isGroupSummary": false,
  "channelId": "message",
  "category": "msg",
  "userId": 0,
  "postTime": 1730700000000,
  "when": 1730700000000,
  "isOngoing": false,
  "isClearable": true,
  "flags": 16,
  "priority": 1,
  "visibility": 0,
  "number": 0,
  "color": "#FF0068FF",
  "progress": { "value": 0, "max": 0, "indeterminate": false },
  "actions": [{ "title": "Trả lời", "hasReply": true }, { "title": "Đánh dấu đã đọc", "hasReply": false }],
  "ranking": { "rank": 0, "isAmbient": false, "matchesInterruptionFilter": true, "importance": 4, "channelName": "Tin nhắn", "canShowBadge": true, "isConversation": true },
  "largeIcon": "data:image/png;base64,iVBORw0KGgo...",
  "picture": null,
  "extras": { "android.title": "Nguyễn Văn A", "android.text": "Tối nay đi ăn không?", "...": "mọi extra gốc app gắn vào" },
  "device": "Xiaomi 2506BPN68G",
  "androidVersion": "16"
}
```

| Trường | Ý nghĩa |
|---|---|
| `packageName` / `appName` | App gửi thông báo, dùng để lọc (ví dụ chỉ lấy app ngân hàng) |
| `title`, `text`, `bigText`, `subText`, `summaryText`, `infoText`, `tickerText` | Các phần chữ của thông báo. `bigText` là nội dung đầy đủ khi mở rộng (tin dài, biến động số dư…) |
| `textLines` | Các dòng của thông báo dạng hộp thư (Gmail gộp nhiều mail…) |
| `messages` | Danh sách tin nhắn (Zalo, Messenger, SMS…), mỗi tin có `text`, `time`, `sender` |
| `id`, `tag`, `key`, `groupKey` | Định danh thông báo trên hệ thống. `key` không đổi khi app cập nhật cùng một thông báo |
| `postTime` / `when` | Thời điểm đăng / thời điểm app ghi trên thông báo (mili giây, Unix epoch) |
| `isGroupSummary` | `true` nếu là thông báo tóm tắt của một nhóm (nội dung thường trùng các thông báo con) |
| `isOngoing` | `true` nếu là thông báo thường trực (nhạc, tải file, định vị…) |
| `progress` | Thanh tiến trình (tải file…) |
| `actions` | Các nút trên thông báo; `hasReply` là có ô trả lời nhanh |
| `ranking` | Mức ưu tiên hệ thống gán: `importance` 0–5, tên kênh thông báo… |
| `largeIcon` / `picture` | Ảnh đại diện (≤128px, PNG) và ảnh lớn của thông báo (≤720px, JPEG) dạng data URI base64; `null` nếu không có |
| `extras` | **Toàn bộ dữ liệu gốc** app gắn vào thông báo. Ảnh trong đây thay bằng chuỗi `[bitmap WxH]` / `[icon]` |

**Cơ chế gửi**
- **Mọi thông báo** đều gửi: kể cả thông báo nhóm, thông báo thường trực, thông báo không có chữ, và mỗi lần app cập nhật thông báo. Tự lọc ở phía webhook bằng các trường ở trên.
- Chỉ bỏ: thông báo của chính app này, và bản đăng lại **giống hệt từng byte** trong vòng 2 giây.
- Lỗi mạng hoặc server trả 5xx/408/429: thử lại tối đa **3 lần** (chờ 1s, rồi 4s).
- Server trả 4xx khác: không thử lại.
- Webhook nên trả về HTTP **2xx** càng nhanh càng tốt. Timeout là 10s khi kết nối, 15s khi đọc.
- Mỗi request có thể nặng vài chục đến vài trăm KB nếu thông báo có ảnh. Server nhận nên cho phép body ≥ 2 MB.

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
