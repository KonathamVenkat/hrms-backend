package com.hrms.common.util;

import com.hrms.common.enums.BloodGroup;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for BloodGroup enum.
 *
 * <p>Oracle stores blood groups as their label strings (e.g., "A+", "O-")
 * rather than enum names. This converter bridges the gap between
 * the Java enum and the Oracle VARCHAR2 column value.</p>
 *
 * <p>Applied automatically to all BloodGroup fields via {@code autoApply = true}.</p>
 */
@Converter(autoApply = true)
public class BloodGroupConverter implements AttributeConverter<BloodGroup, String> {

    @Override
    public String convertToDatabaseColumn(BloodGroup attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getLabel();   // stores "A+", "B-", "AB+", etc.
    }

    @Override
    public BloodGroup convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return BloodGroup.fromValue(dbData.trim());
    }
}
