package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop100ByCustomerUsernameOrderByCreatedAtDesc(String customerUsername);

    @Query("select m.customerUsername from ChatMessage m "
            + "group by m.customerUsername order by max(m.createdAt) desc")
    List<String> findConversationUsernames();
}
