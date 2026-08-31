package com.foreigninone.backend.domain.document.dto;

import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.entity.OcrStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    private Long documentId;
    private DocumentType documentType;
    private OcrStatus ocrStatus;
}
