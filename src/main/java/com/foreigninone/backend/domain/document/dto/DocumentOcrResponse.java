package com.foreigninone.backend.domain.document.dto;

import com.foreigninone.backend.domain.document.entity.OcrStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentOcrResponse {
    private Long documentId;
    private OcrStatus ocrStatus;
    private Map<String, Object> extractedData;
}
