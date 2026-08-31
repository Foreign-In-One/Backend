package com.foreigninone.backend.domain.document.service;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.document.dto.DocumentOcrResponse;
import com.foreigninone.backend.domain.document.dto.DocumentUploadResponse;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.entity.OcrStatus;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OcrService ocrService;

    @Value("${paycycle.upload.dir:uploads}")
    private String uploadDir;

    @Transactional
    public DocumentUploadResponse uploadDocument(Long userId, MultipartFile file, DocumentType documentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String originalFilename = file != null ? file.getOriginalFilename() : "sample_document.pdf";
        String mimeType = file != null ? file.getContentType() : "application/pdf";
        long fileSize = file != null ? file.getSize() : 0L;
        String savedFilePath = null;

        if (file != null && !file.isEmpty()) {
            try {
                Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String cleanName = (originalFilename != null && !originalFilename.isBlank())
                        ? Paths.get(originalFilename).getFileName().toString()
                        : "upload_document.bin";
                String storedFileName = UUID.randomUUID() + "_" + cleanName;
                Path targetLocation = uploadPath.resolve(storedFileName);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                savedFilePath = targetLocation.toString();
            } catch (Exception e) {
                log.error("Failed to save uploaded file: filename={}, uploadDir={}", originalFilename, uploadDir, e);
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "파일 저장에 실패했습니다: " + e.getMessage());
            }
        }

        Document document = Document.builder()
                .user(user)
                .documentType(documentType != null ? documentType : DocumentType.OTHER)
                .originalFilename(originalFilename)
                .filePath(savedFilePath)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .ocrStatus(OcrStatus.PENDING)
                .build();

        Document savedDocument = documentRepository.save(document);

        return DocumentUploadResponse.builder()
                .documentId(savedDocument.getDocumentId())
                .documentType(savedDocument.getDocumentType())
                .ocrStatus(savedDocument.getOcrStatus())
                .build();
    }

    @Transactional
    public DocumentOcrResponse processOcr(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        document.setOcrStatus(OcrStatus.PROCESSING);
        Map<String, Object> extractedData = ocrService.processDocument(document);
        document.updateExtractedData(extractedData);

        return DocumentOcrResponse.builder()
                .documentId(document.getDocumentId())
                .ocrStatus(document.getOcrStatus())
                .extractedData(document.getExtractedData())
                .build();
    }

    @Transactional
    public DocumentOcrResponse updateExtractedData(Long documentId, Map<String, Object> extractedData) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        document.updateExtractedData(extractedData);

        return DocumentOcrResponse.builder()
                .documentId(document.getDocumentId())
                .ocrStatus(document.getOcrStatus())
                .extractedData(document.getExtractedData())
                .build();
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }
}
