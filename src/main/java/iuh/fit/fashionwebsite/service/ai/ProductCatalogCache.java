package iuh.fit.fashionwebsite.service.ai;

import iuh.fit.fashionwebsite.entity.Product;
import iuh.fit.fashionwebsite.entity.ProductVariant;
import iuh.fit.fashionwebsite.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCatalogCache {

    private final ProductRepository productRepository;
    private volatile String catalog;

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh();
    }

    public String getCatalog() {
        String currentCatalog = catalog;
        if (currentCatalog == null) {
            throw new IllegalStateException("Product catalog cache has not been loaded");
        }
        return currentCatalog;
    }

    public void refreshAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refresh();
                } catch (RuntimeException e) {
                    log.error("Không thể cập nhật cache catalog sau khi sản phẩm thay đổi", e);
                }
            }
        });
    }

    private void refresh() {
        List<Product> products = productRepository.findAllActiveForAiCatalog();
        String refreshedCatalog = products.isEmpty()
                ? "Hiện không có sản phẩm đang bán."
                : products.stream()
                        .map(this::formatProduct)
                        .collect(Collectors.joining("\n"));
        catalog = refreshedCatalog;
        log.info("Đã nạp cache catalog AI với {} sản phẩm", products.size());
    }

    private String formatProduct(Product product) {
        StringBuilder text = new StringBuilder("- ")
                .append(product.getName())
                .append(" | Mã: ").append(product.getId())
                .append(" | Giá niêm yết: ")
                .append(product.getBasePrice() == null ? "Chưa có giá" : product.getBasePrice().toPlainString())
                .append(" VNĐ");

        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            text.append(" | Mô tả: ").append(product.getDescription().trim());
        }
        if (product.getCategory() != null) {
            text.append(" | Danh mục: ").append(product.getCategory().getName());
        }

        List<ProductVariant> activeVariants = product.getVariants() == null
                ? List.of()
                : product.getVariants().stream()
                        .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                        .toList();
        if (activeVariants.isEmpty()) {
            return text.append(" | Không có biến thể đang hoạt động.").toString();
        }

        String variantDetails = activeVariants.stream()
                .map(variant -> String.format("%s, size %s, giá %s VNĐ, tồn kho %d",
                        variant.getColor(),
                        variant.getSize(),
                        variant.getPrice() == null ? "chưa có giá" : variant.getPrice().toPlainString(),
                        variant.getStockQuantity() == null ? 0 : variant.getStockQuantity()))
                .collect(Collectors.joining("; "));
        return text.append(" | Biến thể: ").append(variantDetails).toString();
    }
}
