package com.farm2home.inventory.mapper;

import com.farm2home.inventory.domain.entity.Product;
import com.farm2home.inventory.domain.enums.ProductStockStatus;
import com.farm2home.inventory.dto.request.CreateProductRequest;
import com.farm2home.inventory.dto.request.UpdateProductRequest;
import com.farm2home.inventory.dto.response.ProductResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** category and stockQuantity/minimumStockQuantity are deliberately NOT mapped by MapStruct on the
 *  way in (toEntity/updateEntityFromRequest) - resolving a categoryId to a real, active
 *  ProductCategory requires a repository lookup (and a 400 if it doesn't resolve), which belongs
 *  in ProductServiceImpl, not a pure mapper. See ProductServiceImpl.create()/update(). */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "unit", expression = "java(product.getUnit() != null ? product.getUnit().name() : null)")
    @Mapping(target = "stockStatus", ignore = true)
    @Mapping(target = "availability", ignore = true)
    ProductResponse toResponse(Product product);

    @AfterMapping
    default void computeDerivedFields(Product product, @MappingTarget ProductResponse.ProductResponseBuilder response) {
        int stockQty = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int minStockQty = product.getMinimumStockQuantity() != null ? product.getMinimumStockQuantity() : 0;
        ProductStockStatus status;
        if (stockQty <= 0) {
            status = ProductStockStatus.OUT_OF_STOCK;
        } else if (stockQty <= minStockQty) {
            status = ProductStockStatus.LOW_STOCK;
        } else {
            status = ProductStockStatus.IN_STOCK;
        }
        response.stockStatus(status.name());
        response.availability(product.isActive() && stockQty > 0);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "stockQuantity", ignore = true)
    @Mapping(target = "minimumStockQuantity", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Product toEntity(CreateProductRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "stockQuantity", ignore = true)
    @Mapping(target = "minimumStockQuantity", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromRequest(UpdateProductRequest request, @MappingTarget Product product);
}
