package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.dto.response.SpecialOfferResponse;
import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferNotFoundException;
import iuh.fit.fashionwebsite.mapper.SpecialOfferMapper;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SpecialOfferService {
    private final SpecialOfferRepository specialOfferRepository;
    private final SpecialOfferMapper specialOfferMapper;

    public SpecialOfferService(SpecialOfferRepository specialOfferRepository, SpecialOfferMapper specialOfferMapper) {
        this.specialOfferRepository = specialOfferRepository;
        this.specialOfferMapper = specialOfferMapper;
    }

    // Lấy tất cả ưu đãi
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> getAll() {
        return specialOfferRepository
                .findAll()
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Lấy 1 ưu đãi theo ID, ném SpecialOfferNotFoundException nếu không tồn tại
    @Transactional(readOnly = true)
    public SpecialOfferResponse findById(Long id) {
        return specialOfferRepository.findById(id).map(specialOfferMapper::toSpecialOfferResponse)
                .orElseThrow(SpecialOfferNotFoundException::new);
    }

    // Tạo ưu đãi mới
    @Transactional
    public SpecialOfferResponse create(SpecialOffer specialOffer) {
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(specialOffer));
    }

    // Cập nhật toàn bộ thông tin của một ưu đãi (không đụng đến createdAt, updatedAt)
    @Transactional
    public SpecialOfferResponse update(Long id, SpecialOffer specialOffer) {
        SpecialOffer sp = specialOfferRepository.findById(id).orElseThrow(SpecialOfferNotFoundException::new);
        sp.setTitle(specialOffer.getTitle());
        sp.setDiscountPercent(specialOffer.getDiscountPercent());
        sp.setDiscountAmount(specialOffer.getDiscountAmount());
        sp.setOfferType(specialOffer.getOfferType());
        sp.setMinOrderAmount(specialOffer.getMinOrderAmount());
        sp.setMaxDiscountAmount(specialOffer.getMaxDiscountAmount());
        sp.setStartDate(specialOffer.getStartDate());
        sp.setEndDate(specialOffer.getEndDate());
        sp.setIsActive(specialOffer.getIsActive());
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(sp));
    }

    // Lấy các ưu đãi đang có hiệu lực: đang bật và thời điểm hiện tại nằm trong khoảng startDate đến endDate
    @Transactional(readOnly = true)
    public List<SpecialOfferResponse> getActiveOffers() {
        return specialOfferRepository.findActiveOffers(LocalDateTime.now())
                .stream()
                .map(specialOfferMapper::toSpecialOfferResponse)
                .toList();
    }

    // Bật hoặc tắt một ưu đãi mà không cần gửi lại toàn bộ thông tin
    @Transactional
    public SpecialOfferResponse updateActive(Long id, boolean active) {
        SpecialOffer offer = specialOfferRepository.findById(id)
                .orElseThrow(SpecialOfferNotFoundException::new);
        offer.setIsActive(active);
        return specialOfferMapper.toSpecialOfferResponse(specialOfferRepository.save(offer));
    }

//    // Xóa ưu đãi theo ID
//    @Transactional
//    public void delete(Long id) {
//        if (!specialOfferRepository.existsById(id)) {
//            throw new SpecialOfferNotFoundException();
//        }
//        specialOfferRepository.deleteById(id);
//    }
}