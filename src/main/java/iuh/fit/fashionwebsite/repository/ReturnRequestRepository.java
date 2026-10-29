package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ReturnRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    // Lấy danh sách phiếu trả hàng của một khách, có phân trang.
    Page<ReturnRequest> findByUserId(Long userId, Pageable pageable);

    // Tìm phiếu trả hàng thuộc đúng khách hàng.
    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    // Tìm và khóa phiếu trả hàng thuộc đúng khách hàng để tránh thao tác chỉnh sửa/hủy đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r join fetch r.order where r.id = :id and r.user.id = :userId")
    Optional<ReturnRequest> findOwnedReturnRequestForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // Kiểm tra mã phiếu trả hàng đã tồn tại hay chưa.
    boolean existsByReturnCode(String returnCode);
}
