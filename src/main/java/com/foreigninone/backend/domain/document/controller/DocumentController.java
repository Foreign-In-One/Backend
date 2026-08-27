package com.foreigninone.backend.domain.document.controller;

import com.foreigninone.backend.common.dto.ApiResponse;
import com.foreigninone.backend.domain.document.dto.DocumentOcrResponse;
import com.foreigninone.backend.domain.document.dto.DocumentUploadResponse;
import com.foreigninone.backend.domain.document.dto.ExtractedDataUpdateRequest;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> uploadDocument(
            @RequestHeader(value = "X-User-Id", required = false) Long xUserId,
            @RequestHeader(value = "X-Demo-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentType", required = false, defaultValue = "PAYSLIP") DocumentType documentType
    ) {
        Long userId = paramUserId != null ? paramUserId : (xUserId != null ? xUserId : (headerUserId != null ? headerUserId : 1L));
        DocumentUploadResponse response = documentService.uploadDocument(userId, file, documentType);
        return ResponseEntity.ok(ApiResponse.ok(response, "문서가 성공적으로 업로드되었습니다."));
    }

    @PostMapping("/{documentId}/ocr")
    public ResponseEntity<ApiResponse<DocumentOcrResponse>> processOcr(
            @PathVariable("documentId") Long documentId
    ) {
        DocumentOcrResponse response = documentService.processOcr(documentId);
        return ResponseEntity.ok(ApiResponse.ok(response, "OCR 처리가 완료되었습니다."));
    }

    @PatchMapping("/{documentId}/extracted-data")
    public ResponseEntity<ApiResponse<DocumentOcrResponse>> updateExtractedData(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ExtractedDataUpdateRequest request
    ) {
        DocumentOcrResponse response = documentService.updateExtractedData(documentId, request.getExtractedData());
        return ResponseEntity.ok(ApiResponse.ok(response, "추출 데이터가 성공적으로 수정되었습니다."));
    }
}
