package iuh.fit.fashionwebsite.controller.specialoffer;

import iuh.fit.fashionwebsite.entity.SpecialOffer;
import iuh.fit.fashionwebsite.service.specialoffer.SpecialOfferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/special-offers")
public class SpecialOfferController {
    private final SpecialOfferService specialOfferService;

    public SpecialOfferController(SpecialOfferService specialOfferService) {
        this.specialOfferService = specialOfferService;
    }

    @GetMapping
    public List<SpecialOffer> getAll() {
        return specialOfferService.getAll();
    }

    @GetMapping("/{id}")
    public SpecialOffer getById(@PathVariable Long id) {
        return specialOfferService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialOffer create(@Valid @RequestBody SpecialOffer specialOffer) {
        return specialOfferService.create(specialOffer);
    }

    @PutMapping("/{id}")
    public SpecialOffer update(@PathVariable Long id,
                               @Valid @RequestBody SpecialOffer specialOffer) {
        return specialOfferService.update(id, specialOffer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        specialOfferService.delete(id);
    }
}
