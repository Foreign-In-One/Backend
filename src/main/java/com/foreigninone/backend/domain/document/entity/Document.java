package com.foreigninone.backend.domain.document.entity;

import com.foreigninone.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30, nullable = false)
    private DocumentType documentType;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", length = 20, nullable = false)
    private OcrStatus ocrStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "JSON")
    private Map<String, Object> extractedData;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateExtractedData(Map<String, Object> extractedData) {
        this.extractedData = extractedData;
        this.ocrStatus = OcrStatus.SUCCESS;
    }
}
