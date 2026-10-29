package iuh.fit.fashionwebsite.service.product;

import iuh.fit.fashionwebsite.dto.request.ProductCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductUpdateRequest;
import iuh.fit.fashionwebsite.dto.request.ProductFilterRequest;
import iuh.fit.fashionwebsite.dto.response.ProductResponse;
import java.util.List;

public interface ProductService {
    ProductResponse create(ProductCreationRequest request);
    List<ProductResponse> getAll(Long categoryId);
    List<ProductResponse> search(ProductFilterRequest filter);
    List<ProductResponse> getNew(ProductFilterRequest filter, int days);
    ProductResponse getById(Long id);
    ProductResponse update(Long id, ProductUpdateRequest request);
    void delete(Long id);
}
