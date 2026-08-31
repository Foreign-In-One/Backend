package com.foreigninone.backend.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert Map to JSON string", e);
            throw new IllegalArgumentException("Failed to serialize Map to JSON string", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        String json = dbData.trim();
        try {
            if (json.startsWith("\"") && json.endsWith("\"")) {
                try {
                    String unescaped = objectMapper.readValue(json, String.class);
                    if (unescaped != null && (unescaped.startsWith("{") || unescaped.startsWith("["))) {
                        json = unescaped;
                    }
                } catch (Exception ignored) {
                }
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to convert JSON string to Map (length: {})", dbData.length(), e);
            return Collections.emptyMap();
        }
    }
}
