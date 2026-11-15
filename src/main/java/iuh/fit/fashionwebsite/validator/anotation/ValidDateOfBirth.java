package iuh.fit.fashionwebsite.validator.anotation;

import iuh.fit.fashionwebsite.validator.validator.ValidDateOfBirthValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDateOfBirthValidator.class)
public @interface ValidDateOfBirth {

    String message() default "Ngày sinh không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    boolean nullable() default true;

    int minAge() default 10;

    int maxAge() default 110;
}
