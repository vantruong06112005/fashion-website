package iuh.fit.fashionwebsite.service.offer;

import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.exception.review.SpecialOfferProductNotFound;
import iuh.fit.fashionwebsite.repository.SpecialOfferProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpecialOfferProductService {
    private final SpecialOfferProductRepository specialOfferProductRepository;

    public SpecialOfferProductService(SpecialOfferProductRepository specialOfferProductRepository) {
        this.specialOfferProductRepository = specialOfferProductRepository;
    }

    public List<SpecialOfferProduct> getAll(){
        return specialOfferProductRepository.findAll();
    }
    public SpecialOfferProduct findById(Long id){
        return specialOfferProductRepository.findById(id).orElseThrow(SpecialOfferProductNotFound::new);
    }
    public SpecialOfferProduct create(SpecialOfferProduct specialOfferProduct){
        return specialOfferProductRepository.save(specialOfferProduct);
    }
    public SpecialOfferProduct update(Long id, SpecialOfferProduct specialOfferProduct){
        SpecialOfferProduct sp = specialOfferProductRepository.findById(id).orElseThrow(SpecialOfferProductNotFound::new);
        sp.setProduct(specialOfferProduct.getProduct());
        sp.setProductVariant(specialOfferProduct.getProductVariant());
        sp.setSpecialOffer(specialOfferProduct.getSpecialOffer());
        sp.setCreatedAt(specialOfferProduct.getCreatedAt());
        sp.setUpdatedAt(specialOfferProduct.getUpdatedAt());
        return specialOfferProductRepository.save(sp);

    }
    public void delete(Long id){
        if(!specialOfferProductRepository.existsById(id)){
            throw new SpecialOfferProductNotFound();
        }
        specialOfferProductRepository.deleteById(id);
    }


}
