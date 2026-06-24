package com.hrms.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts Java Boolean ↔ Oracle NUMBER(1,0)
 *
 * Oracle stores:
 *   true  → 1  (NUMBER)
 *   false → 0  (NUMBER)
 *   null  → null
 *
 * autoApply = false so we explicitly apply only
 * where needed using @Convert annotation.
 */
@Converter
public class BooleanToNumberConverter
        implements AttributeConverter<Boolean, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? 1 : 0;
    }

    @Override
    public Boolean convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData == 1;
    }
}