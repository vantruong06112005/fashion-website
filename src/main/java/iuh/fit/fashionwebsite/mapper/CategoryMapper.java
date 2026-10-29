package iuh.fit.fashionwebsite.mapper;

import iuh.fit.fashionwebsite.dto.request.CategoryCreationRequest;
import iuh.fit.fashionwebsite.dto.request.CategoryUpdateRequest;
import iuh.fit.fashionwebsite.dto.response.CategoryResponse;
import iuh.fit.fashionwebsite.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parentCategory", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Category toEntity(CategoryCreationRequest request);

    @Mapping(target = "parentId", source = "parentCategory.id")
    CategoryResponse toResponse(Category category);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parentCategory", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void update(@MappingTarget Category category, CategoryUpdateRequest request);
}
