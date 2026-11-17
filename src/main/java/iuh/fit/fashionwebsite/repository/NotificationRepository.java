package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    long countByUsernameAndSeenFalse(String username);

    @Modifying
    @Query("update Notification n set n.seen = true where n.id = :id and n.username = :u")
    int markSeen(@Param("id") Long id, @Param("u") String username);

    @Modifying
    @Query("update Notification n set n.seen = true where n.username = :u and n.seen = false")
    int markAllSeen(@Param("u") String username);
}
