package com.foreigninone.backend.domain.document.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedDataUpdateRequest {
    private Map<String, Object> extractedData;

    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();

    public Map<String, Object> getEffectiveExtractedData() {
        if (extractedData != null && !extractedData.isEmpty()) {
            return extractedData;
        }
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            return additionalProperties;
        }
        return Map.of();
    }
}
