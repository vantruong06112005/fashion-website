package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.dto.response.SpecialOfferProductResponse;
import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferProductNotFound;
import iuh.fit.fashionwebsite.mapper.SpecialOfferProductMapper;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SpecialOfferProductService {
    private final SpecialOfferProductRepository specialOfferProductRepository;
    private final SpecialOfferProductMapper specialOfferProductMapper;

    public SpecialOfferProductService(SpecialOfferProductRepository specialOfferProductRepository, SpecialOfferProductMapper specialOfferProductMapper) {
        this.specialOfferProductRepository = specialOfferProductRepository;
        this.specialOfferProductMapper = specialOfferProductMapper;
    }

    // Lấy tất cả sản phẩm thuộc các ưu đãi
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getAll() {
        return specialOfferProductRepository.findAll()
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }

    // Lấy 1 bản ghi theo ID, ném SpecialOfferProductNotFound nếu không tồn tại
    @Transactional(readOnly = true)
    public SpecialOfferProductResponse findById(Long id) {
        return specialOfferProductRepository.findById(id)
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .orElseThrow(SpecialOfferProductNotFound::new);
    }

    // Thêm sản phẩm vào ưu đãi, chặn thêm trùng cùng một sản phẩm trong cùng một ưu đãi
    @Transactional
    public SpecialOfferProductResponse create(SpecialOfferProduct specialOfferProduct) {
        if (specialOfferProductRepository.existsBySpecialOfferIdAndProductId(
                specialOfferProduct.getSpecialOffer().getId(),
                specialOfferProduct.getProduct().getId())) {
            throw new IllegalStateException("Product already belongs to this special offer");
        }
        return specialOfferProductMapper.toSpecialOfferProductResponse(specialOfferProductRepository.save(specialOfferProduct));
    }

    // Cập nhật ưu đãi, sản phẩm và biến thể của một bản ghi
    @Transactional
    public SpecialOfferProductResponse update(Long id, SpecialOfferProduct specialOfferProduct) {
        SpecialOfferProduct sp = specialOfferProductRepository.findById(id).orElseThrow(SpecialOfferProductNotFound::new);
        sp.setProduct(specialOfferProduct.getProduct());
        sp.setProductVariant(specialOfferProduct.getProductVariant());
        sp.setSpecialOffer(specialOfferProduct.getSpecialOffer());
        return specialOfferProductMapper.toSpecialOfferProductResponse(
                specialOfferProductRepository.save(sp));
    }

    // Xóa sản phẩm khỏi ưu đãi theo ID
    @Transactional
    public void delete(Long id) {
        if (!specialOfferProductRepository.existsById(id)) {
            throw new SpecialOfferProductNotFound();
        }
        specialOfferProductRepository.deleteById(id);
    }

    // Lấy các sản phẩm thuộc một ưu đãi (theo ID của ưu đãi)
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getByOffer(Long offerId) {
        return specialOfferProductRepository.findBySpecialOfferId(offerId)
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }

    // Lấy các ưu đãi đang có hiệu lực của một sản phẩm tại thời điểm hiện tại
    @Transactional(readOnly = true)
    public List<SpecialOfferProductResponse> getActiveByProduct(Long productId) {
        return specialOfferProductRepository
                .findActiveByProductId(productId, LocalDateTime.now())
                .stream()
                .map(specialOfferProductMapper::toSpecialOfferProductResponse)
                .toList();
    }
}