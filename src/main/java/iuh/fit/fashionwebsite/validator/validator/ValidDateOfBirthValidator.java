package iuh.fit.fashionwebsite.validator.validator;

import iuh.fit.fashionwebsite.validator.anotation.ValidDateOfBirth;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class ValidDateOfBirthValidator implements ConstraintValidator<ValidDateOfBirth, LocalDate> {

    private boolean nullable;
    private int minAge;
    private int maxAge;

    @Override
    public void initialize(ValidDateOfBirth constraintAnnotation) {
        this.nullable = constraintAnnotation.nullable();
        this.minAge = constraintAnnotation.minAge();
        this.maxAge = constraintAnnotation.maxAge();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return nullable;
        }

        context.disableDefaultConstraintViolation();
        LocalDate today = LocalDate.now();

        if (!value.isBefore(today)) {
            context.buildConstraintViolationWithTemplate("Ngày sinh phải là một ngày trong quá khứ")
                    .addConstraintViolation();
            return false;
        }

        int age = Period.between(value, today).getYears();

        if (age < minAge) {
            context.buildConstraintViolationWithTemplate("Người dùng phải đủ từ " + minAge + " tuổi trở lên")
                    .addConstraintViolation();
            return false;
        }

        if (age > maxAge) {
            context.buildConstraintViolationWithTemplate("Ngày sinh không hợp lệ (không được vượt quá " + maxAge + " tuổi)")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
