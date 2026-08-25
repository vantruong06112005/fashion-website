/*
 * @ (#) ProductRepository.java     1.0    8/25/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.repository;

import iuh.fit.fashionwebsite.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/*
 * @description
 * @author:NguyenTruong
 * @date:  8/25/2026
 * @version:    1.0
 */
public interface ProductRepository extends JpaRepository<Product,Long> {
}
