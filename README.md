# FacebookExcelApp

Android app Kotlin + Jetpack Compose + Room.

## Chức năng

- Bảng kiểu Excel.
- Cột Link / Tiêu đề / Thời gian / Ảnh / Load.
- Nhập link Facebook ở dòng cuối.
- SQLite Room lưu dữ liệu.
- LOAD gọi Facebook Graph API.
- SAVE ghi toàn bộ bảng vào SQLite.
- Có thể sửa trực tiếp từng ô.
- Xóa dòng.
- Cuộn ngang/dọc.

## Facebook

Vào nút Settings trong app và nhập Facebook Graph API Access Token.

App không lưu token vào SQLite.

Graph API permissions và dữ liệu trả về phụ thuộc loại URL, object, token và chính sách/quyền hiện hành của Facebook.

## Kiểm tra trùng link

Khi nhấn LOAD, app kiểm tra SQLite trước. Nếu link đã có ở dòng khác, app báo `Link đã tồn tại trong CSDL` và không gọi Facebook Graph API.
