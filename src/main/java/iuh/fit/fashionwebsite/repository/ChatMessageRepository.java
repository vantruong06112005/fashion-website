package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop100ByCustomerUsernameOrderByCreatedAtDesc(String customerUsername);

    List<ChatMessage> findAllByCreatedAtBefore(LocalDateTime cutoff);

    List<ChatMessage> findAllByCustomerUsername(String customerUsername);

    @Query("select m.customerUsername from ChatMessage m "
            + "group by m.customerUsername order by max(m.createdAt) desc")
    List<String> findConversationUsernames();

    @Modifying
    @Query("delete from ChatMessage m where m.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from ChatMessage m where m.customerUsername = :customerUsername")
    int deleteByCustomerUsername(@Param("customerUsername") String customerUsername);
}
