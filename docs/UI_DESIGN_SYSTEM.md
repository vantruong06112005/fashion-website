# BỘ QUY TẮC THIẾT KẾ GIAO DIỆN (UI DESIGN SYSTEM)
**Dự án:** Hệ thống Website Thương mại Điện tử Thời trang (Fashion E-Commerce)  
**Nền tảng mục tiêu:** Desktop Web Application (React JS)  
**Phong cách chủ đạo:** Hiện đại, Tối giản, Sắc nét (Minimalist & Sharp Editorial)

---

## 1. Mục đích & Nguyên tắc Cốt lõi (Core Principles)

Tài liệu này là tiêu chuẩn thiết kế giao diện (Single Source of Truth) cho toàn bộ thành viên trong nhóm phát triển. Mọi màn hình chức năng (Trang chủ, Danh mục, Chi tiết sản phẩm, Giỏ hàng, Thanh toán, Tài khoản...) khi code mới đều **bắt buộc** tuân thủ bộ quy tắc này nhằm đảm bảo:
1. **Tính đồng bộ tuyệt đối:** Mọi nút bấm, ô nhập liệu, thẻ sản phẩm, cỡ chữ và màu sắc trên tất cả các màn hình đều nhất quán.
2. **Quản lý tập trung bằng biến (Design Tokens):** Tuyệt đối **KHÔNG** gõ trực tiếp mã màu cứng (ví dụ `#111111`, `#D0021B`) hay kích thước tự do vào từng file component. Toàn bộ thông số phải gọi qua biến CSS trong file `src/styles/tokens.css`.
3. **Tối ưu thị giác thời trang:** Ưu tiên không gian trắng (`#FFFFFF`), đường nét vuông vức nam tính/hiện đại (`border-radius: 0px - 2px`), giúp hình ảnh sản phẩm quần áo trở thành tâm điểm.

---

## 2. Hệ thống Biến Toàn cục (`src/styles/tokens.css`)

Trước khi code bất kỳ màn hình nào, đảm bảo file `src/styles/tokens.css` đã được import vào `main.jsx` (hoặc `index.jsx`). Khi nhóm muốn đổi tông màu chủ đạo hoặc độ bo góc của toàn website, chỉ cần sửa giá trị tại file này.

```css
/* src/styles/tokens.css */
@import url('https://fonts.googleapis.com/css2?family=Montserrat:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap');

:root {
  /* ================= 1. BẢNG MÀU (COLOR PALETTE) ================= */
  /* Màu chủ đạo & Hành động chính (Đen Carbon) */
  --color-primary: #111111;
  --color-primary-hover: #333333;
  --color-primary-active: #000000;

  /* Màu điểm nhấn thương mại (Đỏ Khuyến mãi / Vàng Đồng Hạng Thành viên) */
  --color-accent-red: #D0021B;
  --color-accent-red-hover: #A80015;
  --color-accent-gold: #C5A059;

  /* Màu nền (Backgrounds) */
  --bg-body: #FFFFFF;                /* Nền chính toàn trang */
  --bg-surface: #F6F6F6;             /* Nền khối phụ, ô tìm kiếm, hover phụ */
  --bg-elevated: #FFFFFF;            /* Nền Modal, Dropdown, Drawer, Card */
  --bg-dark: #111111;                /* Nền Topbar, Footer */
  --bg-overlay: rgba(0, 0, 0, 0.55); /* Lớp phủ mờ khi mở Modal/Drawer */
  --bg-disabled: #EAEAEA;            /* Nền nút/input khi bị vô hiệu hóa */

  /* Màu chữ (Typography Colors) */
  --text-primary: #111111;           /* Chữ chính, tiêu đề, tên sản phẩm */
  --text-secondary: #555555;         /* Chữ mô tả phụ, thuộc tính */
  --text-muted: #888888;             /* Giá gốc gạch ngang, placeholder, ngày tháng */
  --text-inverse: #FFFFFF;           /* Chữ trên nền tối (Button chính, Footer) */
  --text-disabled: #AAAAAA;          /* Chữ khi bị khóa (Disabled) */

  /* Màu viền (Borders) */
  --border-light: #EBEBEB;           /* Viền chia ô nhạt, đường kẻ ngang */
  --border-default: #CCCCCC;         /* Viền Input, nút Secondary, ô chọn Size */
  --border-focus: #111111;           /* Viền khi Focus/Active */
  --border-error: #D0021B;           /* Viền khi nhập sai dữ liệu */

  /* Màu trạng thái hệ thống (Feedback States) */
  --status-success: #1F8B4C;         /* Còn hàng, Đặt hàng thành công */
  --status-warning: #E67E22;         /* Sắp hết hàng */
  --status-error: #D0021B;           /* Hết hàng, Lỗi biểu mẫu */

  /* ================= 2. PHÔNG CHỮ & CỠ CHỮ (TYPOGRAPHY) ================= */
  --font-primary: 'Montserrat', -apple-system, BlinkMacSystemFont, sans-serif;

  /* Cỡ chữ chuẩn Desktop */
  --fs-xs: 12px;     /* Badge, chú thích nhỏ, breadcrumb, báo lỗi */
  --fs-sm: 13px;     /* Menu phụ, giá gốc, nhãn thuộc tính, bảng size */
  --fs-base: 14px;   /* Chữ mặc định toàn trang, tên SP trên Card, nút bấm, input */
  --fs-md: 16px;     /* Giá bán trên Card, tiêu đề khối nhỏ, tiêu đề Drawer */
  --fs-lg: 20px;     /* Tên SP ở trang Chi tiết, tiêu đề Modal, tổng tiền */
  --fs-xl: 24px;     /* Tiêu đề phân khu lớn (Section Heading) */
  --fs-2xl: 32px;    /* Tiêu đề Banner / Trang lớn */

  /* Độ đậm chữ (Font Weight) */
  --fw-regular: 400;
  --fw-medium: 500;
  --fw-semibold: 600;
  --fw-bold: 700;

  /* Chiều cao dòng & Giãn chữ */
  --lh-tight: 1.25;
  --lh-base: 1.5;
  --tracking-wide: 0.05em;   /* Bắt buộc dùng khi chữ IN HOA (Menu, Button) */
  --tracking-wider: 0.08em;  /* Dùng cho Section Heading IN HOA */

  /* ================= 3. ĐỘ BO GÓC (BORDER RADIUS) ================= */
  --radius-none: 0px;
  --radius-sm: 2px;          /* Ô chọn size, badge, phân trang */
  --radius-btn: 0px;         /* Bo góc chuẩn cho mọi Button */
  --radius-input: 0px;       /* Bo góc chuẩn cho mọi Input / Select */
  --radius-card: 0px;        /* Bo góc cho Card sản phẩm */
  --radius-modal: 4px;       /* Bo góc cho Popup / Modal */
  --radius-full: 9999px;     /* Hình tròn tuyệt đối (chấm màu, badge số lượng) */

  /* ================= 4. BỐ CỤC & KHOẢNG CÁCH (LAYOUT & SPACING) ================= */
  --container-max-width: 1320px;
  --container-padding: 24px;
  --grid-gap: 24px;          /* Khoảng cách giữa các cột sản phẩm */
  --header-height: 76px;
  --topbar-height: 36px;

  /* ================= 5. ĐỔ BÓNG & HIỆU ỨNG (SHADOWS & TRANSITIONS) ================= */
  --shadow-header: 0 2px 10px rgba(0, 0, 0, 0.06);
  --shadow-dropdown: 0 8px 24px rgba(0, 0, 0, 0.12);
  --shadow-modal: 0 16px 40px rgba(0, 0, 0, 0.2);
  --transition-fast: all 0.2s ease-in-out;
  --transition-smooth: all 0.35s cubic-bezier(0.25, 0.8, 0.25, 1);
}
```

---

## 3. Quy tắc Định dạng Văn bản (Typography Rules)

Để tránh việc mỗi thành viên dùng chữ đậm, chữ nghiêng, chữ hoa tùy ý trên các màn hình khác nhau, toàn nhóm thống nhất 4 quy tắc hiển thị chữ sau:

1. **Chữ IN HOA (`text-transform: uppercase`) + In đậm (`600` - `700`) + Giãn chữ (`letter-spacing`):**
   * **Áp dụng cho:** Menu điều hướng chính, tất cả các Nút bấm (`Primary`/`Secondary`), Tiêu đề các phân khu lớn (`Section Heading`), Tiêu đề cột Bộ lọc/Footer, và Nhãn trạng thái (`NEW`, `SALE`, `BEST SELLER`).
   * **Tuyệt đối không dùng** chữ IN HOA cho đoạn văn mô tả dài hoặc tên sản phẩm dài.
2. **Chữ In đậm (`600` - `700`) nhưng KHÔNG viết hoa toàn bộ:**
   * **Áp dụng cho:** Giá bán hiện tại của sản phẩm, Tổng tiền thanh toán, Tên sản phẩm tại trang Chi tiết sản phẩm, và Nhãn (`Label`) đứng trên các ô nhập liệu.
3. **Chữ Thường (`400` - `500`):**
   * **Áp dụng cho:** Tên sản phẩm trên Card (`500`), Nội dung mô tả chất liệu/hướng dẫn bảo quản (`400`), Các mục con trong Mega Menu/Footer (`400`), và Nội dung người dùng nhập vào ô Input (`400`).
4. **Chữ In nghiêng (`font-style: italic`):**
   * **Hạn chế tối đa** để giữ nét vuông vức, hiện đại. Chỉ dùng cho: Gợi ý thông số người mẫu (*Ví dụ: Mẫu cao 1m78, nặng 70kg mặc size L*), Link phụ (*Hướng dẫn chọn size*), hoặc dòng thông báo lỗi nhỏ nằm ngay dưới ô Input.

---

## 4. Bảng Quy chuẩn 26 Thành phần Giao diện Dùng chung (Component Specs)

Mọi thành viên khi xây dựng hoặc tái sử dụng Component phải đối chiếu đúng các thông số kích thước, màu sắc và 4 trạng thái tương tác (**Default / Hover / Active / Disabled**) dưới đây:

| STT | Thành phần (Component) | Bố cục & Kích thước (Desktop) | Chữ & Bo góc (Typography & Radius) | Màu sắc & Trạng thái (Default / Hover / Active / Disabled) | Ghi chú kỹ thuật (React / UX) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Topbar** (Thanh thông báo đỉnh trang) | Rộng `100%`, cao `var(--topbar-height)` (`36px`). Chia 2 hoặc 3 cột trong khung `container` (Hotline trái, Thông điệp khuyến mãi giữa, Link phụ phải). | Cỡ chữ `var(--fs-xs)` (`12px`), `var(--fw-medium)` (`500`). Không bo góc (`0px`). | **Default:** Nền `var(--bg-dark)`, chữ `var(--text-inverse)` (`#FFF`).<br>**Hover (Link):** Chữ chuyển sang `var(--color-accent-gold)` (`#C5A059`). | Dùng hiển thị ưu đãi miễn phí vận chuyển hoặc hotline. |
| **2** | **Header chính** (Main Navbar) | Rộng `100%`, nội dung gói trong `var(--container-max-width)` (`1320px`), cao `var(--header-height)` (`76px`). Flexbox 3 khối: Logo (`160px`) - Menu chính (giữa, gap `32px`) - Cụm tìm kiếm & Icon (phải). | Menu chính: Cỡ `var(--fs-base)` (`14px`), **IN HOA**, đậm `var(--fw-semibold)` (`600`), giãn chữ `var(--tracking-wide)`. Không bo góc. | **Default:** Nền `var(--bg-body)`, chữ `var(--text-primary)`, viền dưới `1px solid var(--border-light)`.<br>**Hover (Menu item):** Hiện gạch chân `2px solid var(--color-primary)` chạy từ trái sang phải.<br>**Active (Trang hiện tại):** Gạch chân cố định `2px solid var(--color-primary)`.<br>**Mục Sale:** Chữ màu `var(--color-accent-red)`. | Đặt `position: sticky; top: 0; z-index: 1000`. Thêm `box-shadow: var(--shadow-header)` khi cuộn trang. |
| **3** | **Mega Menu** (Menu đa cấp xổ xuống) | Rộng `100%` hoặc `1100px` căn giữa dưới Header, padding `32px`, chia 4-5 cột (3 cột danh mục chữ + 1-2 cột ảnh bộ sưu tập). | Tiêu đề cột: `var(--fs-base)` (`14px`), **IN HOA**, **In đậm** (`700`).<br>Mục con: `var(--fs-base)` (`14px`), chữ thường (`400`), line-height `2.2`. Bo góc dưới `var(--radius-sm)`. | **Default:** Nền `var(--bg-elevated)`, bóng đổ `var(--shadow-dropdown)`, chữ mục con `var(--text-secondary)`.<br>**Hover (Mục con):** Chữ đổi sang `var(--text-primary)`, **In đậm** (`500`), dịch sang phải `4px` (`translateX(4px)`). | Kết hợp `opacity`, `visibility` và `var(--transition-fast)` để mượt mà khi di chuột. |
| **4** | **Thanh tìm kiếm** (Header Search Input) | Rộng `220px` (mở rộng `280px` khi Focus), cao `38px`, padding trái `14px`, phải `36px` (chừa icon kính lúp). | Cỡ chữ `var(--fs-sm)` (`13px`), chữ thường (`400`). Bo góc `var(--radius-input)` (`0px`). | **Default:** Nền `var(--bg-surface)` (`#F6F6F6`), viền `1px solid transparent`, chữ `var(--text-primary)`, placeholder `var(--text-muted)`.<br>**Hover:** Viền `1px solid var(--border-default)`.<br>**Focus:** Nền `#FFFFFF`, viền `1px solid var(--border-focus)`. | Áp dụng `debounce` `300ms` khi gõ để hiển thị popup gợi ý sản phẩm. |
| **5** | **Breadcrumb** (Thanh đường dẫn trang) | Nằm dưới Header, cao `44px`, căn trái theo `container`. Các mục cách nhau dấu `/` (gap `8px`). | Cỡ chữ `var(--fs-xs)` (`12px`), chữ thường (`400`). Mục cuối cùng `var(--fw-medium)` (`500`). | **Default (Cấp trước):** Chữ `var(--text-muted)`.<br>**Hover (Cấp trước):** Chữ `var(--text-primary)`, có gạch chân.<br>**Active (Trang hiện tại):** Chữ `var(--text-primary)`, không click được. | Tự động render theo đường dẫn của `react-router-dom`. |
| **6** | **Button Chính** (Primary Button - "MUA NGAY", "THANH TOÁN") | Cao chuẩn `44px` (cỡ lớn `48px` ở trang Chi tiết SP), padding ngang `24px`, căn giữa chữ & icon (gap `8px`). | Cỡ chữ `var(--fs-base)` (`14px`), **IN HOA**, **In đậm** (`var(--fw-semibold)` `600`), giãn chữ `var(--tracking-wide)`. Bo góc `var(--radius-btn)` (`0px`). | **Default:** Nền `var(--color-primary)` (`#111`), chữ `var(--text-inverse)` (`#FFF`), viền `1px solid var(--color-primary)`.<br>**Hover:** Nền `var(--color-primary-hover)` (`#333`), `cursor: pointer`.<br>**Active:** Nền `var(--color-primary-active)` (`#000`), `scale(0.99)`.<br>**Disabled:** Nền `var(--bg-disabled)`, chữ `var(--text-disabled)`, viền `transparent`, `cursor: not-allowed`. | Sử dụng chung component `<Button variant="primary">`. |
| **7** | **Button Phụ** (Secondary / Outline Button - "THÊM VÀO GIỎ", "XEM THÊM") | Cao chuẩn `44px` (hoặc `48px`), padding ngang `24px`, viền `1px solid`. | Cỡ chữ `var(--fs-base)` (`14px`), **IN HOA**, **In đậm** (`var(--fw-semibold)` `600`), giãn chữ `var(--tracking-wide)`. Bo góc `var(--radius-btn)` (`0px`). | **Default:** Nền `#FFFFFF`, chữ `var(--text-primary)`, viền `1px solid var(--color-primary)`.<br>**Hover:** Đảo màu — Nền chuyển sang `var(--color-primary)` (`#111`), chữ chuyển sang `var(--text-inverse)` (`#FFF`).<br>**Active:** Nền `#000`, chữ `#FFF`.<br>**Disabled:** Nền `#FFF`, viền `var(--border-light)`, chữ `var(--text-disabled)`. | Sử dụng chung component `<Button variant="secondary">`. |
| **8** | **Button Văn bản** (Text / Ghost Button - "Xóa bộ lọc", "Xem bảng size") | Chiều cao tự động theo dòng chữ, padding `4px 0`, có gạch chân dưới chữ. | Cỡ chữ `var(--fs-sm)` (`13px`), `var(--fw-medium)` (`500`), chữ thường hoặc *in nghiêng* (với link "Hướng dẫn chọn size"). | **Default:** Nền trong suốt, chữ `var(--text-secondary)`, gạch chân `1px solid var(--border-default)`.<br>**Hover:** Chữ `var(--text-primary)`, gạch chân `1px solid var(--text-primary)`.<br>**Disabled:** Chữ `var(--text-disabled)`, bỏ gạch chân. | Dùng cho các hành động phụ để không tranh chấp với nút Primary. |
| **9** | **Icon Button & Badge đếm** (Giỏ hàng, Yêu thích) | Khung vuông `40px x 40px`, icon `20px x 20px` căn giữa. Badge số lượng nằm góc trên phải (`18px x 18px`). | Số trên Badge: Cỡ `10px`, **In đậm** (`700`). Bo góc Badge `var(--radius-full)` (tròn tuyệt đối). | **Default:** Icon màu `var(--text-primary)`, Badge nền `var(--color-primary)` (hoặc `var(--color-accent-red)`), chữ `#FFF`.<br>**Hover:** Nền nút hiện tròn mờ `var(--bg-surface)`, icon phóng nhẹ `scale(1.08)`. | Khi số lượng giỏ hàng tăng, thêm hiệu ứng nảy nhẹ (`scale`). |
| **10** | **Tiêu đề Phân khu** (Section Heading - VD: "SẢN PHẨM MỚI") | Căn giữa toàn khối hoặc căn trái kèm nút "Xem tất cả" bên phải. Margin dưới `32px`. | Cỡ chữ `var(--fs-xl)` (`24px`), **IN HOA**, **In đậm** (`var(--fw-bold)` `700`), giãn chữ `var(--tracking-wider)`. Không bo góc. | **Default:** Chữ `var(--text-primary)`. Có gạch ngang ngắn `40px x 2px` màu `var(--color-primary)` dưới tiêu đề. | Dùng chung component `<SectionHeader title="..." />`. |
| **11** | **Khung Card Sản phẩm** (Product Card Container) | Bố cục Grid 4 cột trên Desktop (`grid-template-columns: repeat(4, 1fr)`), gap `var(--grid-gap)` (`24px`). Nội dung xếp dọc: Khung ảnh → Chấm màu → Tên SP → Giá. | Bo góc toàn khung `var(--radius-card)` (`0px`). Khoảng cách từ ảnh xuống thông tin là `12px`. | **Default:** Nền `transparent`, không viền.<br>**Hover (Toàn Card):** Ảnh sản phẩm zoom nhẹ hoặc đổi sang ảnh góc chụp thứ 2, nút "Thêm nhanh" trượt từ đáy ảnh lên. | Tỷ lệ khung ảnh bắt buộc là **3:4** (`aspect-ratio: 3 / 4`) trên mọi màn hình. |
| **12** | **Ảnh Sản phẩm & Nút Mua nhanh** (Product Thumbnail) | Khung `aspect-ratio: 3 / 4`, `overflow: hidden`, `position: relative`. Nút "MUA NHANH" cao `40px`, rộng `100%` nằm sát đáy ảnh. | Chữ nút Mua nhanh: `var(--fs-xs)` (`12px`), **IN HOA**, **In đậm** (`600`). Không bo góc. | **Default:** Hiển thị ảnh chính, nút Mua nhanh ẩn (`translateY(100%)`).<br>**Hover Card:** Ảnh phóng nhẹ hoặc đổi sang ảnh phụ (`var(--transition-smooth)`), nút Mua nhanh trượt lên (`translateY(0)`), nền `rgba(17, 17, 17, 0.85)`, chữ `#FFF`.<br>**Hover Nút Mua nhanh:** Nền đặc `#111111`. | Luôn gắn `loading="lazy"` và `object-fit: cover` cho thẻ `<img>`. |
| **13** | **Nhãn Sản phẩm** (Badge: `-30%`, `NEW`, `BEST SELLER`) | Đặt `position: absolute; top: 10px; left: 10px` trên khung ảnh. Cao `22px`, padding ngang `8px`. Các nhãn cách nhau `4px`. | Cỡ chữ `11px`, **IN HOA**, **In đậm** (`var(--fw-bold)` `700`). Bo góc `var(--radius-sm)` (`2px`). | **Badge Sale (`-30%`):** Nền `var(--color-accent-red)` (`#D0021B`), chữ `#FFF`.<br>**Badge New:** Nền `var(--color-primary)` (`#111`), chữ `#FFF`.<br>**Badge Best Seller / VIP:** Nền `var(--color-accent-gold)` (`#C5A059`), chữ `#FFF`. | Đặt `pointer-events: none` để không cản trở việc click vào ảnh. |
| **14** | **Tên Sản phẩm trên Card** (Product Title) | Giới hạn tối đa 1 dòng (`line-clamp: 1`, `overflow: hidden`, `text-overflow: ellipsis`). Margin dưới `6px`. | Cỡ chữ `var(--fs-base)` (`14px`), chữ thường, độ đậm `var(--fw-medium)` (`500`). | **Default:** Chữ `var(--text-primary)`.<br>**Hover:** Chữ chuyển sang `var(--text-secondary)`. | Bọc trong thẻ `<Link>` dẫn tới trang Chi tiết sản phẩm. |
| **15** | **Cụm Giá Sản phẩm** (Price Block) | Flexbox ngang, căn dòng `align-items: baseline`, gap `8px`. | **Giá bán hiện tại:** Cỡ `var(--fs-md)` (`16px` trên Card, `22px` ở Chi tiết SP), **In đậm** (`var(--fw-bold)` `700`).<br>**Giá gốc:** Cỡ `var(--fs-sm)` (`13px`), chữ thường (`400`), gạch ngang (`line-through`). | **Không giảm giá:** Giá bán màu `var(--text-primary)` (`#111`).<br>**Có giảm giá:** Giá bán màu `var(--color-accent-red)` (`#D0021B`), giá gốc màu `var(--text-muted)` (`#888`). | Sử dụng hàm `formatCurrencyVND(price)` chung toàn dự án (ví dụ: `450.000₫`). |
| **16** | **Bộ chọn Màu sắc** (Color Swatches) | Flexbox ngang, gap `8px`. Trên Card: ô tròn `16px x 16px`. Ở trang Chi tiết SP: ô tròn `28px x 28px`. | Bo góc `var(--radius-full)` (hình tròn `50%`). | **Default:** Nền là mã màu thực của sản phẩm, viền `1px solid var(--border-default)`.<br>**Hover:** Phóng nhẹ `scale(1.1)`.<br>**Active (Đang chọn):** Viền kép — khoảng trắng `2px` bên trong và viền ngoài `1.5px solid var(--border-focus)` (`#111`).<br>**Disabled (Hết màu):** `opacity: 0.35`, có gạch chéo. | Khi chọn màu trên Card, cập nhật luôn ảnh sản phẩm tương ứng. |
| **17** | **Bộ chọn Kích cỡ** (Size Selector - `S`, `M`, `L`, `XL`, `2XL`) | Flexbox ngang, gap `10px`. Mỗi ô rộng tối thiểu `44px`, cao `38px`, căn giữa chữ. | Cỡ chữ `var(--fs-sm)` (`13px`), **IN HOA**, độ đậm `var(--fw-medium)` (`500`). Bo góc `var(--radius-sm)` (`2px`). | **Default:** Nền `#FFF`, chữ `var(--text-primary)`, viền `1px solid var(--border-default)`.<br>**Hover:** Viền đổi sang `var(--color-primary)` (`#111`).<br>**Active (Đang chọn):** Nền `var(--color-primary)` (`#111`), chữ `var(--text-inverse)` (`#FFF`), viền `#111`, **In đậm** (`600`).<br>**Disabled (Hết size):** Nền `var(--bg-surface)`, chữ `var(--text-disabled)`, gạch chéo qua ô, `cursor: not-allowed`. | Kiểm tra tồn kho theo cặp `(colorId, sizeId)` để khóa chính xác size đã hết. |
| **18** | **Bộ chọn Số lượng** (Quantity Stepper `-` `1` `+`) | Khung ngang gồm 3 phần ghép liền: Nút `-` (`38px x 42px`) + Ô số (`50px x 42px`) + Nút `+` (`38px x 42px`). Tổng rộng `126px`. | Số ở giữa: Cỡ `var(--fs-base)` (`14px`), **In đậm** (`600`), căn giữa. Dấu `-` `+` cỡ `16px`. Bo góc ngoài `var(--radius-input)` (`0px`). | **Default:** Nền `#FFF`, viền chung `1px solid var(--border-default)`, chữ `var(--text-primary)`.<br>**Hover (Nút `-` / `+`):** Nền ô nút chuyển sang `var(--bg-surface)`.<br>**Disabled (Khi số lượng = 1 hoặc chạm mức tồn kho tối đa):** Dấu mờ đi (`var(--text-disabled)`), `cursor: not-allowed`. | Chặn nhập ký tự chữ hoặc số nhỏ hơn 1. |
| **19** | **Ô nhập liệu Biểu mẫu** (Text Input / Textarea) | Rộng `100%` theo cột form, cao `44px`, padding ngang `14px`. Nhãn (`Label`) nằm phía trên, cách input `6px`. | Label: `var(--fs-sm)` (`13px`), **In đậm** (`600`).<br>Input: `var(--fs-base)` (`14px`), chữ thường (`400`).<br>Thông báo lỗi: `var(--fs-xs)` (`12px`), *in nghiêng*. Bo góc `var(--radius-input)` (`0px`). | **Default:** Nền `#FFF`, viền `1px solid var(--border-default)`, chữ `var(--text-primary)`, placeholder `var(--text-muted)`.<br>**Hover:** Viền `#888888`.<br>**Focus:** Viền `1px solid var(--border-focus)` (`#111`), `outline: none`.<br>**Error:** Viền `1px solid var(--border-error)`, chữ báo lỗi màu `var(--status-error)`.<br>**Disabled:** Nền `var(--bg-disabled)`, chữ `var(--text-disabled)`. | Dùng chung component `<Input label="..." error="..." />` cho Đăng nhập, Đăng ký, Thanh toán. |
| **20** | **Hộp chọn & Sắp xếp** (Select / Sort Dropdown) | Rộng `200px` - `240px`, cao `40px`, padding trái `12px`, phải `32px` (chừa icon mũi tên `▾`). | Cỡ chữ `var(--fs-base)` (`14px`), chữ thường (`400`). Bo góc `var(--radius-input)` (`0px`). | **Default:** Nền `#FFF`, viền `1px solid var(--border-default)`, chữ `var(--text-primary)`.<br>**Hover / Focus:** Viền `1px solid var(--border-focus)`.<br>**Menu xổ xuống:** Nền `#FFF`, bóng đổ `var(--shadow-dropdown)`, mục đang chọn nền `var(--bg-surface)`, chữ **In đậm** (`600`). | Dùng cho bộ sắp xếp danh mục và chọn Tỉnh/Thành phố khi thanh toán. |
| **21** | **Checkbox & Radio** (Bộ lọc & Phương thức thanh toán) | Khung vuông (`18px x 18px` cho Checkbox) hoặc tròn (`18px x 18px` cho Radio), cách nhãn chữ `10px`. Mỗi dòng cách nhau `12px`. | Nhãn đi kèm: Cỡ `var(--fs-base)` (`14px`), chữ thường (`400`). Bo góc Checkbox `var(--radius-sm)` (`2px`), Radio `var(--radius-full)`. | **Default:** Nền `#FFF`, viền `1.5px solid var(--border-default)`.<br>**Hover:** Viền `var(--color-primary)`.<br>**Checked (Active):** Nền `var(--color-primary)` (`#111`), dấu tích trắng `#FFF` bên trong.<br>**Disabled:** Nền `var(--bg-disabled)`, chữ `var(--text-disabled)`. | Dùng `accent-color: var(--color-primary)` hoặc custom UI để không bị màu xanh mặc định của trình duyệt. |
| **22** | **Sidebar Bộ lọc Danh mục** (Category Filter Accordion) | Cột trái rộng `260px` - `280px`. Chia thành từng khối (Khoảng giá, Kích cỡ, Màu sắc, Kiểu dáng) ngăn cách bởi đường kẻ `1px solid var(--border-light)`, padding dọc `20px`. | Tiêu đề nhóm lọc: `var(--fs-base)` (`14px`), **IN HOA**, **In đậm** (`var(--fw-bold)` `700`), kèm dấu `+`/`-` bên phải. | **Default:** Nền `transparent`, chữ `var(--text-primary)`.<br>**Hover (Tiêu đề nhóm):** Chữ đổi sang `var(--text-secondary)`. | Cho phép đóng/mở (Collapse/Expand) từng nhóm tiêu chí. |
| **23** | **Ngăn kéo Giỏ hàng nhanh** (Mini Cart Drawer) | Trượt từ cạnh phải màn hình (`width: 420px`, `height: 100vh`, `position: fixed; right: 0; top: 0; z-index: 1100`). Chia 3 phần: Header (`60px`) - Danh sách SP cuộn dọc (`flex: 1`) - Footer tính tổng & Nút thanh toán (`160px`). | Tiêu đề Drawer: `var(--fs-md)` (`16px`), **IN HOA**, **In đậm** (`700`).<br>Tổng tiền: `var(--fs-lg)` (`20px`), **In đậm** (`700`). Không bo góc. | **Lớp nền mờ (Backdrop):** `var(--bg-overlay)`.<br>**Khung Drawer:** Nền `var(--bg-elevated)` (`#FFF`), bóng đổ `var(--shadow-modal)`. Tổng tiền màu `var(--color-accent-red)`. | Khóa cuộn trang chính (`document.body.style.overflow = 'hidden'`) khi Drawer đang mở. |
| **24** | **Popup / Modal** (Bảng quy đổi Size, Xem nhanh SP) | Căn giữa màn hình (`max-width: 720px` cho Bảng size, `920px` cho Quick View), padding `32px`, nút đóng `✕` (`36px x 36px`) ở góc trên phải. | Tiêu đề Modal: `var(--fs-lg)` (`20px`), **IN HOA**, **In đậm** (`700`), căn giữa. Bo góc khung `var(--radius-modal)` (`4px`). | **Default:** Nền `#FFF`, bóng đổ `var(--shadow-modal)`, lớp phủ sau lưng `var(--bg-overlay)`.<br>**Nút đóng `✕` Hover:** Nền `var(--bg-surface)`, chữ `#111`. | Hỗ trợ đóng Modal khi nhấn phím `ESC` hoặc click ra vùng nền mờ bên ngoài. |
| **25** | **Phân trang** (Pagination) | Căn giữa bên dưới lưới sản phẩm, margin trên `48px`. Flexbox ngang, gap `8px`. Mỗi ô vuông `38px x 38px`. | Cỡ chữ `var(--fs-base)` (`14px`), `var(--fw-medium)` (`500`). Bo góc `var(--radius-sm)` (`2px`). | **Default:** Nền `#FFF`, chữ `var(--text-primary)`, viền `1px solid var(--border-light)`.<br>**Hover:** Viền `1px solid var(--color-primary)`.<br>**Active (Trang hiện tại):** Nền `var(--color-primary)` (`#111`), chữ `#FFF`, viền `#111`, **In đậm** (`600`).<br>**Disabled:** Chữ `var(--text-disabled)`, `cursor: not-allowed`. | Tự động cuộn mượt lên đầu danh sách sản phẩm khi chuyển trang. |
| **26** | **Footer** (Chân trang) | Rộng `100%`, padding trên `64px`, dưới `32px`. Nội dung trong `container` chia 4 cột (Giới thiệu, Chính sách, Hỗ trợ khách hàng, Đăng ký nhận tin). | Tiêu đề cột: `var(--fs-base)` (`14px`), **IN HOA**, **In đậm** (`600`), margin dưới `20px`.<br>Link mục con: `var(--fs-sm)` (`13px`), chữ thường (`400`), line-height `2.2`. | **Default:** Nền `var(--bg-dark)` (`#111111`), tiêu đề cột màu `var(--text-inverse)` (`#FFF`), chữ mục con màu `#AAAAAA`.<br>**Hover (Link):** Chữ sáng lên `#FFFFFF` và dịch nhẹ sang phải (`translateX(4px)`). | Giữ độ tương phản cao với phần thân trang nền trắng. |

---

## 5. Quy ước Cấu trúc Thư mục & Sử dụng Component trong Nhóm

Để tránh việc mỗi thành viên tự tạo lại nút bấm hay ô nhập liệu riêng ở từng trang, nhóm thống nhất cấu trúc thư mục UI dùng chung như sau:

```text
src/
├── styles/
│   ├── tokens.css          # Chứa toàn bộ biến :root (Tuyệt đối không sửa lẻ tẻ ở file khác)
│   └── global.css          # Reset CSS, áp dụng font-family và màu nền mặc định
├── components/
│   ├── ui/                 # Các thành phần cơ bản (Atomic UI Components)
│   │   ├── Button.jsx
│   │   ├── Input.jsx
│   │   ├── Select.jsx
│   │   ├── Badge.jsx
│   │   ├── Modal.jsx
│   │   └── Pagination.jsx
│   ├── product/            # Các thành phần nghiệp vụ Sản phẩm
│   │   ├── ProductCard.jsx
│   │   ├── ColorSwatches.jsx
│   │   ├── SizeSelector.jsx
│   │   └── QuantityStepper.jsx
│   └── layout/             # Khung bố cục chung toàn trang
│       ├── Topbar.jsx
│       ├── Header.jsx
│       ├── MegaMenu.jsx
│       ├── MiniCartDrawer.jsx
│       ├── Breadcrumb.jsx
│       └── Footer.jsx
```

### Checklist trước khi tạo Pull Request (PR) giao diện:
- [ ] Không có mã màu HEX cứng (`#...`) nằm ngoài file `tokens.css`.
- [ ] Tất cả các nút bấm đều có đủ trạng thái `Hover`, `Active` và `Disabled`.
- [ ] Khung ảnh sản phẩm tuân thủ đúng tỷ lệ `aspect-ratio: 3 / 4`, không bị méo ảnh khi kích thước ảnh gốc khác nhau.
- [ ] Các tiêu đề phân khu và nút bấm chính đã áp dụng `text-transform: uppercase` kèm `letter-spacing: var(--tracking-wide)`.
- [ ] Khoảng cách hai bên lề trang nằm gọn trong khung `max-width: var(--container-max-width)` (`1320px`).
