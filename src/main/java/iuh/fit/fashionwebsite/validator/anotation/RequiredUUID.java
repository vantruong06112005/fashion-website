package iuh.fit.fashionwebsite.validator.anotation;


import iuh.fit.fashionwebsite.validator.validator.RequiredUUIDValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RequiredUUIDValidator.class)
public @interface RequiredUUID {

    String message() default "error.invalid_uuid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Cho phép null hoặc không. Mặc định false — bắt buộc phải có giá trị.
     * Set true nếu field optional nhưng khi có giá trị vẫn phải đúng format UUID.
     */
    boolean nullable() default false;
}