package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.UserAiProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MODULE TRUY VẤN HỒ SƠ AI CỦA NGƯỜI DÙNG (UserAiProfileRepository)
 * - Chức năng: Cung cấp các thao tác đọc/ghi dữ liệu trên bảng `user_ai_profiles` thông qua Spring Data JPA.
 * - Ý tưởng logic cốt lõi: Kế thừa `JpaRepository` để có sẵn các hàm CRUD cơ bản và định nghĩa thêm hàm tìm kiếm
 *   hồ sơ AI theo khóa ngoại `userId`.
 * - Đầu vào: Đối tượng `UserAiProfile` hoặc mã định danh người dùng `userId`.
 * - Đầu ra: Đối tượng `Optional<UserAiProfile>` tương ứng trong cơ sở dữ liệu.
 */
@Repository
public interface UserAiProfileRepository extends JpaRepository<UserAiProfile, Long> {

    /**
     * - Chức năng: Tìm kiếm hồ sơ AI của một khách hàng dựa trên ID tài khoản người dùng.
     * - Ý tưởng logic cốt lõi: Truy vấn cột `user_id` trong bảng `user_ai_profiles`. Trả về `Optional` để xử lý an toàn
     *   trường hợp khách hàng mới chưa từng được khởi tạo hồ sơ AI.
     *
     * @param userId ID của người dùng cần tra cứu hồ sơ AI.
     * @return `Optional<UserAiProfile>` chứa hồ sơ AI nếu tìm thấy, hoặc `Optional.empty()` nếu chưa có.
     */
    Optional<UserAiProfile> findByUserId(Long userId);
}
