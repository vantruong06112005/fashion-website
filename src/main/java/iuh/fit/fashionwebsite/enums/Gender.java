package iuh.fit.fashionwebsite.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    @JsonCreator
    public static Gender fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "MALE", "NAM" -> MALE;
            case "FEMALE", "NU", "NỮ" -> FEMALE;
            case "OTHER", "KHAC", "KHÁC" -> OTHER;
            default -> throw new IllegalArgumentException("Invalid gender value: " + value);
        };
    }
}
