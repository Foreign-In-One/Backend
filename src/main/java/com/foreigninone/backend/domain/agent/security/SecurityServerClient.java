package com.foreigninone.backend.domain.agent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Python 보안 서버(security-server: Presidio + NeMo Guardrails) 호출 클라이언트
 */
@Component
public class SecurityServerClient {

    private final RestClient restClient;

    public SecurityServerClient(@Value("${paycycle.security-server.base-url:http://localhost:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    public PreResult pre(String question, String context, String userName, String companyName) {
        return restClient.post()
                .uri("/guard/pre")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PreRequest(question, context, userName, companyName))
                .retrieve()
                .body(PreResult.class);
    }

    public PostResult post(String responseText, String canaryToken) {
        return restClient.post()
                .uri("/guard/post")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PostRequest(responseText, canaryToken))
                .retrieve()
                .body(PostResult.class);
    }

    public record PreRequest(String question, String context, String userName, String companyName) {}

    public record PreResult(boolean blocked, String reason, String sanitizedQuestion, String sanitizedContext) {}

    public record PostRequest(String responseText, String canaryToken) {}

    public record PostResult(boolean blocked, String safeText) {}
}
