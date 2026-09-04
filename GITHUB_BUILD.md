# Build APK bằng GitHub — chỉ cần điện thoại

## 1. Tạo repository

Trên GitHub:
- Bấm **New repository**
- Đặt tên: `FacebookExcelApp`
- Chọn **Private** nếu không muốn project công khai
- Tạo repository.

## 2. Upload project

Giải nén ZIP này trên điện thoại.

Upload **toàn bộ nội dung bên trong thư mục FacebookExcelApp** lên repository.

Quan trọng: thư mục `.github/workflows/build-apk.yml` phải được upload đúng vị trí.

Cấu trúc cần có:

```text
FacebookExcelApp/
├── .github/
│   └── workflows/
│       └── build-apk.yml
├── app/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 3. Chạy build

Sau khi upload:
- Mở tab **Actions**
- Chọn workflow **Build Android APK**
- Nếu GitHub hỏi cho phép workflow, bấm **Enable workflows**
- Bấm **Run workflow**
- Chọn branch `main` hoặc `master`
- Bấm **Run workflow**

## 4. Tải APK

Chờ workflow chạy xong, thường vài phút.

Mở lần chạy vừa hoàn thành → kéo xuống **Artifacts** → chọn:

`FacebookExcelApp-debug`

GitHub sẽ tải về một file ZIP. Giải nén ZIP đó và lấy:

`app-debug.apk`

Sau đó cài APK trên điện thoại Android.

## Lưu ý

Bản debug APK dùng để cài/test. Không cần Android Studio trên điện thoại.

Nếu GitHub không cho phép workflow chạy, vào:

**Settings → Actions → General**

và bật quyền cho phép Actions chạy.
