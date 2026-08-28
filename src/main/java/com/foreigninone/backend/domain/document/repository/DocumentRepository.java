package com.foreigninone.backend.domain.document.repository;

import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUser_UserId(Long userId);
    List<Document> findByUser_UserIdAndDocumentType(Long userId, DocumentType documentType);
    List<Document> findByUser_UserIdAndDocumentTypeOrderByUploadedAtDesc(Long userId, DocumentType documentType);
}
