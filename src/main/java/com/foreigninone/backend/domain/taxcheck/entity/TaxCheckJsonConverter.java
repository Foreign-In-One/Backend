package com.foreigninone.backend.domain.taxcheck.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/** Preserve decimal money exactly and fail explicitly on a corrupt tax snapshot. */
@Converter
public class TaxCheckJsonConverter implements AttributeConverter<Map<String, Object>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("TaxCheck 기록을 직렬화할 수 없습니다.", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            String json = value.trim();
            // H2 MySQL-mode can return its JSON column as an escaped JSON string.
            if (json.startsWith("\"")) json = MAPPER.readValue(json, String.class);
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("TaxCheck 기록이 손상되어 읽을 수 없습니다.", e);
        }
    }
}
