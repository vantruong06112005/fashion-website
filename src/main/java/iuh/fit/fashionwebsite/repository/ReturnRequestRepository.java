package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    // Lấy danh sách phiếu trả hàng của một khách, có phân trang.
    Page<ReturnRequest> findByUserId(Long userId, Pageable pageable);

    // Tìm phiếu trả hàng thuộc đúng khách hàng.
    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    // Kiểm tra mã phiếu trả hàng đã tồn tại hay chưa.
    boolean existsByReturnCode(String returnCode);
}
