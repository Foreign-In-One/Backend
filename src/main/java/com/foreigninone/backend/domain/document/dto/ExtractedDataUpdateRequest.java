package com.foreigninone.backend.domain.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedDataUpdateRequest {
    @NotNull(message = "추출 데이터는 필수입니다.")
    private Map<String, Object> extractedData;
}
