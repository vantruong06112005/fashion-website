package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferNotFoundException;
import iuh.fit.fashionwebsite.repository.SpecialOfferRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpecialOfferService {
    private final SpecialOfferRepository specialOfferRepository;

    public SpecialOfferService(SpecialOfferRepository specialOfferRepository) {
        this.specialOfferRepository = specialOfferRepository;
    }

    public List<SpecialOffer> getAll(){
        return specialOfferRepository.findAll();
    }
    public SpecialOffer findById(Long id){
        return specialOfferRepository.findById(id).orElseThrow(SpecialOfferNotFoundException::new);
    }
    public SpecialOffer create(SpecialOffer specialOffer){
        return specialOfferRepository.save(specialOffer);
    }
    public SpecialOffer update(Long id, SpecialOffer specialOffer){
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
        return specialOfferRepository.save(sp);
    }
//    public void delete(Long id){
//        if(!specialOfferRepository.existsById(id)){
//            throw new SpecialOfferNotFoundException();
//        }
//        specialOfferRepository.deleteById(id);
//    }
}
