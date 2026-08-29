/*
 * @ (#) IntrospectRequest.java     1.0    7/8/2026
 *
 * Copyright (c) 2026 IUH. All rights reserved.
 */
package iuh.fit.fashionwebsite.dto.request;


import lombok.*;

/*
 * @description
 * @author:NguyenTruong
 * @date:  7/8/2026
 * @version:    1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class IntrospectRequest {
    private String token;
}
