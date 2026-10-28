package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findAllByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndProductVariantId(Long cartId, Long variantId);
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
    void deleteAllByCartId(Long cartId);
}
