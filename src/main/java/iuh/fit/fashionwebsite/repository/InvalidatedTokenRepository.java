package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {

    @Modifying
    @Query("DELETE FROM InvalidatedToken t WHERE t.expiryTime < :now")
    int deleteAllExpired(@Param("now") Instant now);

    /**
     * Thu hồi token một cách nguyên tử (atomic).
     * Trả về 1 nếu thu hồi thành công, 0 nếu jti đã nằm trong blacklist
     * → dùng để chặn 2 request refresh song song với cùng một refresh token.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO invalidated_tokens (id, expiry_time) VALUES (:id, :expiryTime)",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("expiryTime") Instant expiryTime);
}
