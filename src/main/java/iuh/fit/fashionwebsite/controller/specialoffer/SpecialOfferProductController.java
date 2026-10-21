package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.entity.SpecialOfferProduct;
import iuh.fit.fashionwebsite.service.specialoffer.SpecialOfferProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/special-offer-products")
public class SpecialOfferProductController {
    private final SpecialOfferProductService specialOfferProductService;

    public SpecialOfferProductController(SpecialOfferProductService specialOfferProductService) {
        this.specialOfferProductService = specialOfferProductService;
    }

    @GetMapping
    public List<SpecialOfferProduct> getAll() {
        return specialOfferProductService.getAll();
    }

    @GetMapping("/{id}")
    public SpecialOfferProduct getById(@PathVariable Long id) {
        return specialOfferProductService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialOfferProduct create(@Valid @RequestBody SpecialOfferProduct specialOfferProduct) {
        return specialOfferProductService.create(specialOfferProduct);
    }

    @PutMapping("/{id}")
    public SpecialOfferProduct update(@PathVariable Long id, @Valid @RequestBody SpecialOfferProduct specialOfferProduct) {
        return specialOfferProductService.update(id, specialOfferProduct);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        specialOfferProductService.delete(id);
    }
}
