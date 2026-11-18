package iuh.fit.fashionwebsite.service.review;

import iuh.fit.fashionwebsite.dto.request.ProductReviewImageCreationRequest;
import iuh.fit.fashionwebsite.dto.request.ProductReviewImageUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.review.ProductReviewImageResponse;
import iuh.fit.fashionwebsite.entity.ProductReview;
import iuh.fit.fashionwebsite.entity.ProductReviewImage;
import iuh.fit.fashionwebsite.entity.User;
import iuh.fit.fashionwebsite.enums.RoleUser;
import iuh.fit.fashionwebsite.exception.AppException;
import iuh.fit.fashionwebsite.exception.ErrorCode;
import iuh.fit.fashionwebsite.exception.offer.ProductRevImageNotFound;
import iuh.fit.fashionwebsite.exception.offer.ProductRevNotFoundException;
import iuh.fit.fashionwebsite.mapper.ProductReviewImageMapper;
import iuh.fit.fashionwebsite.repository.ProductReviewImageRepository;
import iuh.fit.fashionwebsite.repository.ProductReviewRepository;
import iuh.fit.fashionwebsite.service.cloudinary.CloudinaryService;
import iuh.fit.fashionwebsite.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
public class ProductReviewImageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private final ProductReviewImageRepository productReviewImageRepository;
    private final ProductReviewImageMapper productReviewImageMapper;
    private final ProductReviewRepository productReviewRepository;
    private final CloudinaryService cloudinaryService;
    private final UserService userService;

    public ProductReviewImageService(ProductReviewImageRepository productReviewImageRepository,
                                     ProductReviewImageMapper productReviewImageMapper) {
        this(productReviewImageRepository, productReviewImageMapper, null, null, null);
    }

    @Autowired
    public ProductReviewImageService(ProductReviewImageRepository productReviewImageRepository,
                                     ProductReviewImageMapper productReviewImageMapper,
                                     ProductReviewRepository productReviewRepository,
                                     CloudinaryService cloudinaryService,
                                     UserService userService) {
        this.productReviewImageRepository = productReviewImageRepository;
        this.productReviewImageMapper = productReviewImageMapper;
        this.productReviewRepository = productReviewRepository;
        this.cloudinaryService = cloudinaryService;
        this.userService = userService;
    }

    // Lấy tất cả ảnh đánh giá
    @Transactional(readOnly = true)
    public List<ProductReviewImageResponse> getAll() {
        return productReviewImageRepository.findAll()
                .stream()
                .map(productReviewImageMapper::toProductReviewImageResponse)
                .toList();
    }

    // Lấy 1 ảnh theo ID, ném ProductRevImageNotFound nếu không tồn tại
    @Transactional(readOnly = true)
    public ProductReviewImageResponse findById(Long id) {
        return productReviewImageRepository.findById(id)
                .map(productReviewImageMapper::toProductReviewImageResponse)
                .orElseThrow(ProductRevImageNotFound::new);
    }

    // Thêm ảnh vào đánh giá từ DTO ProductReviewImageCreationRequest, giới hạn tối đa 5 ảnh mỗi đánh giá
    @Transactional
    public ProductReviewImageResponse create(ProductReviewImageCreationRequest request) {
        Long reviewId = request.getProductReviewId();
        ProductReview review = resolveReviewOrThrow(reviewId);
        ensureCanModifyReviewImage(review);

        long currentCount = reviewId != null ? productReviewImageRepository.countByProductReviewId(reviewId) : 0;
        if (currentCount >= 5) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        ProductReviewImage entity = productReviewImageMapper.toProductReviewImage(request);
        entity.setProductReview(review);
        if (entity.getMediaType() == null || entity.getMediaType().isBlank()) {
            entity.setMediaType("IMAGE");
        }
        if (entity.getDisplayOrder() == null) {
            entity.setDisplayOrder((int) currentCount + 1);
        }

        return productReviewImageMapper.toProductReviewImageResponse(productReviewImageRepository.save(entity));
    }

    // Tải file ảnh thực tế lên cho một đánh giá (tối đa 5 ảnh, mỗi ảnh không quá 5MB)
    @Transactional
    public ProductReviewImageResponse upload(Long reviewId, MultipartFile file, Integer displayOrder) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        ProductReview review = resolveReviewOrThrow(reviewId);
        ensureCanModifyReviewImage(review);

        long currentCount = productReviewImageRepository.countByProductReviewId(reviewId);
        if (currentCount >= 5) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        String imageUrl;
        try {
            if (cloudinaryService != null) {
                imageUrl = cloudinaryService.uploadImage(file, "fashion_website/reviews");
            } else {
                imageUrl = toDataUrl(file);
            }
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.FILE_UPLOAD_FAILED) {
                imageUrl = toDataUrl(file);
            } else {
                throw ex;
            }
        }

        ProductReviewImage entity = ProductReviewImage.builder()
                .productReview(review)
                .imageUrl(imageUrl)
                .mediaType("IMAGE")
                .displayOrder(displayOrder != null ? displayOrder : (int) currentCount + 1)
                .build();
        return productReviewImageMapper.toProductReviewImageResponse(productReviewImageRepository.save(entity));
    }

    // Cập nhật thông tin ảnh từ DTO ProductReviewImageUpdateRequest
    @Transactional
    public ProductReviewImageResponse update(Long id, ProductReviewImageUpdateRequest request) {
        ProductReviewImage productReviewImg = productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
        ensureCanModifyReviewImage(productReviewImg.getProductReview());

        productReviewImageMapper.updateProductReviewImage(productReviewImg, request);
        if (request.getProductReviewId() != null) {
            ProductReview newReview = resolveReviewOrThrow(request.getProductReviewId());
            ensureCanModifyReviewImage(newReview);
            productReviewImg.setProductReview(newReview);
        }

        return productReviewImageMapper
                .toProductReviewImageResponse(productReviewImageRepository.save(productReviewImg));
    }

    // Xóa ảnh theo ID (chỉ chủ sở hữu đánh giá hoặc ADMIN)
    @Transactional
    public void delete(Long id) {
        ProductReviewImage img = productReviewImageRepository.findById(id).orElseThrow(ProductRevImageNotFound::new);
        ensureCanModifyReviewImage(img.getProductReview());
        productReviewImageRepository.delete(img);
    }

    // Lấy tất cả ảnh thuộc một đánh giá (theo ID của đánh giá)
    @Transactional(readOnly = true)
    public List<ProductReviewImageResponse> getByReview(Long reviewId) {
        return productReviewImageRepository.findByProductReviewId(reviewId)
                .stream()
                .map(productReviewImageMapper::toProductReviewImageResponse)
                .toList();
    }

    private ProductReview resolveReviewOrThrow(Long reviewId) {
        if (reviewId == null) {
            throw new ProductRevNotFoundException();
        }
        return productReviewRepository != null
                ? productReviewRepository.findById(reviewId).orElseThrow(ProductRevNotFoundException::new)
                : ProductReview.builder().id(reviewId).build();
    }

    private void ensureCanModifyReviewImage(ProductReview review) {
        if (userService == null || review == null || review.getUser() == null) {
            return;
        }
        User currentUser;
        try {
            currentUser = userService.getCurrentUserEntity();
        } catch (Exception ex) {
            currentUser = null;
        }
        if (currentUser == null || currentUser.getRole() == RoleUser.ADMIN) {
            return;
        }
        if (!currentUser.getId().equals(review.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String toDataUrl(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/jpeg";
            }
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
}
