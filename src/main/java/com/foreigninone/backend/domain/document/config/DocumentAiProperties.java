package com.foreigninone.backend.domain.document.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "paycycle.ocr.documentai")
public class DocumentAiProperties {
    private String projectId;
    private String location = "us";
    private String processorId;
    private String credentialsPath;

    public boolean isConfigured() {
        return projectId != null && !projectId.trim().isEmpty() &&
                processorId != null && !processorId.trim().isEmpty();
    }
}
