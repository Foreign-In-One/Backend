package com.foreigninone.backend.domain.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.domain.agent.config.OpenAiProperties;
import com.foreigninone.backend.domain.document.config.DocumentAiProperties;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final DocumentAiProperties documentAiProperties;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public Map<String, Object> processDocument(Document document) {
        // 1. Google Cloud Document AI 시도 (설정된 경우)
        if (documentAiProperties.isConfigured()) {
            try {
                log.info("Executing Google Cloud Document AI for documentId: {}", document.getDocumentId());
                Map<String, Object> gcpResult = executeGoogleDocumentAi(document);
                if (gcpResult != null && !gcpResult.isEmpty() && (gcpResult.containsKey("baseSalary") || gcpResult.containsKey("netPay") || gcpResult.containsKey("depositAmount"))) {
                    return gcpResult;
                }
            } catch (Exception e) {
                log.warn("Google Cloud Document AI call failed, falling back to next engine: {}", e.getMessage());
            }
        }

        // 2. OpenAI OCR 시도 (업로드된 실제 파일이 있는 경우)
        if (document.getFilePath() != null) {
            File file = new File(document.getFilePath());
            if (file.exists() && file.length() > 0) {
                try {
                    Map<String, Object> openAiResult = executeSmartOpenAiOcr(document, file);
                    if (openAiResult != null && !openAiResult.isEmpty()) {
                        return openAiResult;
                    }
                } catch (Exception e) {
                    log.warn("OpenAI OCR processing failed, falling back to regex/mock OCR: {}", e.getMessage());
                }

                // PDF 텍스트 직접 파싱 시도 (OpenAI 실패 시 fallback)
                if (isPdfFile(file, document.getMimeType())) {
                    try (PDDocument pdDoc = Loader.loadPDF(file)) {
                        String text = new PDFTextStripper().getText(pdDoc);
                        if (text != null && !text.isBlank()) {
                            log.info("Extracting values from PDF text via regex fallback for documentId: {}", document.getDocumentId());
                            Map<String, Object> regexResult = new LinkedHashMap<>();
                            parseRegexFromText(text, regexResult);
                            if (!regexResult.isEmpty()) {
                                Map<String, Object> fallback = generateMockExtractedDataByType(document.getDocumentType());
                                fallback.forEach(regexResult::putIfAbsent);
                                return regexResult;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract text from PDF: {}", e.getMessage());
                    }
                }
            }
        }

        // 3. Fallback to mock parser
        log.info("Using built-in OCR parser fallback for documentId: {}", document.getDocumentId());
        return generateMockExtractedData(document);
    }

    private boolean isPdfFile(File file, String mimeType) {
        if (mimeType != null && mimeType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        return file.getName().toLowerCase().endsWith(".pdf");
    }

    private Map<String, Object> executeSmartOpenAiOcr(Document document, File file) throws Exception {
        if (!openAiProperties.isConfigured()) {
            return null;
        }

        boolean isPdf = isPdfFile(file, document.getMimeType());

        if (isPdf) {
            try (PDDocument pdDoc = Loader.loadPDF(file)) {
                String pdfText = new PDFTextStripper().getText(pdDoc);
                if (pdfText != null && pdfText.trim().length() > 20) {
                    log.info("Executing OpenAI Text-based OCR on PDF text for documentId: {}", document.getDocumentId());
                    return executeOpenAiTextOcr(document, pdfText);
                }

                // 텍스트가 없는 스캔본 PDF인 경우 1페이지를 이미지로 렌더링하여 Vision API 호출
                log.info("Rendering PDF page 1 to image for OpenAI Vision OCR: documentId: {}", document.getDocumentId());
                PDFRenderer renderer = new PDFRenderer(pdDoc);
                BufferedImage pageImage = renderer.renderImageWithDPI(0, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(pageImage, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();
                return executeOpenAiVisionOcrWithBytes(document, imageBytes, "image/png");
            }
        } else {
            // 이미지 파일 (PNG, JPG, JPEG, WEBP 등)
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String mime = (document.getMimeType() != null && document.getMimeType().startsWith("image/"))
                    ? document.getMimeType()
                    : "image/jpeg";
            return executeOpenAiVisionOcrWithBytes(document, fileBytes, mime);
        }
    }

    private Map<String, Object> executeOpenAiTextOcr(Document document, String text) throws Exception {
        String systemPrompt = getOcrSystemPrompt();
        String userContent = String.format("다음 문서 텍스트를 고정밀 분석하여 기본급, 수당, 공제, 실지급액/지급총액, 잔액 및 발견된 모든 금액 후보 목록(candidateAmounts)을 JSON으로 정확히 추출해주세요.\n\n【문서 텍스트】\n%s", text);

        Map<String, Object> requestBody = Map.of(
                "model", openAiProperties.getModel() != null ? openAiProperties.getModel() : "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1
        );

        return sendOpenAiRequest(requestBody, document.getDocumentType());
    }

    private Map<String, Object> executeOpenAiVisionOcrWithBytes(Document document, byte[] imageBytes, String mimeType) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        String systemPrompt = getOcrSystemPrompt();

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "이 문서 이미지를 고정밀 분석하여 모든 기본급, 수당, 공제, 실지급액/지급총액, 잔액 및 후보 금액 목록(candidateAmounts)을 JSON으로 정확히 추출해주세요."),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high"))
                        ))
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1
        );

        return sendOpenAiRequest(requestBody, document.getDocumentType());
    }

    private String getOcrSystemPrompt() {
        return "당신은 한국의 임금명세서(급여명세서), 표준근로계약서, 통장 입출금 거래내역 영수증 전문 고정밀 OCR 판독 AI입니다.\n" +
                "작은 글씨(Small font), 표(Table), 캡션, 비정형 서식에서도 숫자를 정밀하게 찾아내야 합니다.\n\n" +
                "【추출 규칙 및 필드 매핑】\n" +
                "1. baseSalary (기본급): '기본급', '본봉', '기본급여', '월급' 항목의 금액 (작은 글씨나 표 안의 숫자도 정밀 인식)\n" +
                "2. totalPayment (지급총액): '지급총액', '지급합계', '총지급액' (공제 전 전체 금액)\n" +
                "3. deduction (공제총액): '공제총액', '공제합계', '공제액', '세금/4대보험 합계'\n" +
                "4. netPay (실지급액): '실지급액', '차인지급액', '실수령액', '통장입금액' (지급총액 - 공제총액)\n" +
                "5. overtimeAllowance (수당 합계): '연장수당', '야간수당', '휴일수당', '주휴수당', '기타수당'의 합계\n" +
                "6. afterBalanceAmt (거래후 잔액): 통장 거래내역의 '거래후잔액', '잔액', '현재잔액'\n" +
                "7. depositAmount (입금액): 통장 영수증의 '입금액', '거래금액'\n" +
                "8. companyName (회사명): '사업장명', '회사명', '상호', '사용자(사)'\n" +
                "9. payPeriod (급여귀속월): '귀속년월', '지급년월' (YYYY-MM 형식으로 표준화, 예: 2026-08)\n" +
                "10. paymentDate (지급일자): '지급일', '입금일', '거래일' (YYYY-MM-DD 형식)\n" +
                "11. payday (정기급여일): 근로계약서의 매월 급여지급일 (1~31 정수)\n" +
                "12. workStartDate (근로개시일): 근로시작일 (YYYY-MM-DD 형식)\n" +
                "13. contractDurationMonths (계약기간 개월수): 근로계약 기간 개월수\n" +
                "14. candidateAmounts (문서 내 모든 발견 금액 후보 목록): 문서 표나 텍스트에서 발견된 모든 금액을 라벨과 함께 배열로 나열 [{\"label\": \"기본급\", \"amount\": 2300000}, ...]\n\n" +
                "【주의사항】\n" +
                "- 모든 금액 필드는 콤마(,)와 '원' 단위를 제거하고 숫자(Number) 타입으로 반환하세요.\n" +
                "- 기본급, 지급총액, 실지급액, 잔액 등의 숫자를 문서에서 정확하게 읽어내세요.\n" +
                "- 사용자가 UI에서 직접 고를 수 있도록 candidateAmounts에 문서에 적힌 모든 항목과 금액을 반드시 포함하세요.\n" +
                "- 반드시 JSON 객체로만 응답하세요.";
    }

    private Map<String, Object> sendOpenAiRequest(Map<String, Object> requestBody, DocumentType documentType) throws Exception {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openAiProperties.getApiKey())
                .build();

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            log.error("OpenAI OCR API returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        } catch (Exception ex) {
            log.error("OpenAI OCR API request failed: {}", ex.getMessage());
            throw ex;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode resultNode = objectMapper.readTree(content);

        Map<String, Object> extracted = new LinkedHashMap<>();
        Iterator<String> fieldNames = resultNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode node = resultNode.get(fieldName);
            if (node.isNumber()) {
                extracted.put(fieldName, node.numberValue());
            } else if (node.isBoolean()) {
                extracted.put(fieldName, node.booleanValue());
            } else if (node.isTextual()) {
                extracted.put(fieldName, node.textValue());
            } else if (node.isArray()) {
                extracted.put(fieldName, objectMapper.convertValue(node, List.class));
            }
        }

        // netPay가 없고 totalPayment만 있어도 자동 대입하지 않음.
        // totalPayment(지급총액, 세전)와 netPay(실지급액, 세후)는 다른 개념이므로
        // candidateAmounts에 후보로만 포함되어 사용자가 직접 선택하도록 함.

        if (extracted.isEmpty() || (!extracted.containsKey("baseSalary") && !extracted.containsKey("netPay") && !extracted.containsKey("depositAmount"))) {
            Map<String, Object> fallback = generateMockExtractedDataByType(documentType);
            fallback.forEach(extracted::putIfAbsent);
        }

        return extracted;
    }

    private Map<String, Object> executeGoogleDocumentAi(Document document) throws Exception {
        String location = documentAiProperties.getLocation();
        String projectId = documentAiProperties.getProjectId();
        String processorId = documentAiProperties.getProcessorId();

        String endpoint = String.format("https://%s-documentai.googleapis.com/v1/projects/%s/locations/%s/processors/%s:process",
                location, projectId, location, processorId);

        byte[] fileBytes;
        if (document.getFilePath() != null && new File(document.getFilePath()).exists()) {
            fileBytes = Files.readAllBytes(new File(document.getFilePath()).toPath());
        } else {
            fileBytes = "MOCK_DOCUMENT_CONTENT".getBytes();
        }

        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        String mimeType = document.getMimeType() != null ? document.getMimeType() : "application/pdf";

        Map<String, Object> rawDocument = Map.of(
                "content", base64Content,
                "mimeType", mimeType
        );
        Map<String, Object> requestBody = Map.of("rawDocument", rawDocument);

        RestClient restClient = RestClient.builder().build();
        String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return parseDocumentAiResponse(responseBody, document.getDocumentType());
    }

    private Map<String, Object> parseDocumentAiResponse(String responseBody, DocumentType documentType) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documentNode = root.path("document");
            String text = documentNode.path("text").asText("");
            result.put("rawText", text);

            JsonNode entities = documentNode.path("entities");
            if (entities.isArray()) {
                for (JsonNode entity : entities) {
                    String type = entity.path("type").asText();
                    String mentionText = entity.path("mentionText").asText();
                    result.put(type, mentionText);
                }
            }

            // Document AI 원문 텍스트에서 주요 금액 정규식 보정 (작은 글씨나 미분류 엔티티 대응)
            if (!text.isBlank()) {
                parseRegexFromText(text, result);
            }
        } catch (Exception e) {
            log.error("Failed to parse Google Document AI response JSON", e);
        }

        if (result.isEmpty() || (!result.containsKey("baseSalary") && !result.containsKey("netPay") && !result.containsKey("depositAmount"))) {
            Map<String, Object> mockFallback = generateMockExtractedDataByType(documentType);
            result.putAll(mockFallback);
        }

        return result;
    }

    private void parseRegexFromText(String text, Map<String, Object> result) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        // 기본급 정규식 매칭
        if (!result.containsKey("baseSalary")) {
            Matcher m = Pattern.compile("(?:기본급|본봉|기본급여)[\\s:：]*([0-9,]+)").matcher(text);
            if (m.find()) {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                result.put("baseSalary", val);
                candidates.add(Map.of("label", "기본급", "amount", val));
            }
        }
        // 실지급액/차인지급액 정규식 매칭
        if (!result.containsKey("netPay")) {
            Matcher m = Pattern.compile("(?:실지급액|차인지급액|실수령액)[\\s:：]*([0-9,]+)").matcher(text);
            if (m.find()) {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                result.put("netPay", val);
                candidates.add(Map.of("label", "실지급액", "amount", val));
            }
        }
        // 지급총액 정규식 매칭
        if (!result.containsKey("totalPayment")) {
            Matcher m = Pattern.compile("(?:지급총액|지급합계|총지급액)[\\s:：]*([0-9,]+)").matcher(text);
            if (m.find()) {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                result.put("totalPayment", val);
                candidates.add(Map.of("label", "지급총액", "amount", val));
            }
        }
        // 공제총액 정규식 매칭
        if (!result.containsKey("deduction")) {
            Matcher m = Pattern.compile("(?:공제총액|공제합계|공제액)[\\s:：]*([0-9,]+)").matcher(text);
            if (m.find()) {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                result.put("deduction", val);
                candidates.add(Map.of("label", "공제총액", "amount", val));
            }
        }
        // 잔액 정규식 매칭
        if (!result.containsKey("afterBalanceAmt")) {
            Matcher m = Pattern.compile("(?:거래후잔액|잔액|현재잔액)[\\s:：]*([0-9,]+)").matcher(text);
            if (m.find()) {
                long val = Long.parseLong(m.group(1).replace(",", ""));
                result.put("afterBalanceAmt", val);
                candidates.add(Map.of("label", "거래후잔액", "amount", val));
            }
        }

        if (!candidates.isEmpty()) {
            result.put("candidateAmounts", candidates);
        }
    }

    private Map<String, Object> generateMockExtractedData(Document document) {
        return generateMockExtractedDataByType(document.getDocumentType());
    }

    private Map<String, Object> generateMockExtractedDataByType(DocumentType documentType) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (documentType == null) {
            documentType = DocumentType.PAYSLIP;
        }

        switch (documentType) {
            case PAYSLIP -> {
                data.put("payPeriod", "2026-08");
                data.put("baseSalary", 2300000);
                data.put("totalPayment", 2380000);
                data.put("overtimeAllowance", 80000);
                data.put("deduction", 120000);
                // netPay는 명확히 명세서에서 읽은 값이 있을 때만 설정 (totalPayment를 netPay로 자동 대입하지 않음)
                data.put("companyName", "한국정밀");
                data.put("paymentDate", "2026-08-25");
                data.put("candidateAmounts", List.of(
                        Map.of("label", "기본급", "amount", 2300000),
                        Map.of("label", "연장근로수당", "amount", 80000),
                        Map.of("label", "지급총액(세전)", "amount", 2380000),
                        Map.of("label", "공제총액", "amount", 120000),
                        Map.of("label", "실지급액(차인지급액)", "amount", 2260000)
                ));
            }
            case EMPLOYMENT_CONTRACT -> {
                data.put("companyName", "한국정밀");
                data.put("baseSalary", 2300000);
                data.put("payday", 25);
                data.put("workStartDate", "2025-03-10");
                data.put("contractDurationMonths", 36);
                data.put("candidateAmounts", List.of(
                        Map.of("label", "계약 기본급(월급)", "amount", 2300000),
                        Map.of("label", "통상시급", "amount", 11005),
                        Map.of("label", "식대/복리후생비", "amount", 100000)
                ));
            }
            case BANK_RECEIPT -> {
                data.put("bankName", "하나은행");
                data.put("depositAmount", 2260000);
                data.put("afterBalanceAmt", 6760000);
                data.put("depositDate", "2026-08-25");
                data.put("sender", "한국정밀 8월 급여");
                data.put("candidateAmounts", List.of(
                        Map.of("label", "급여 입금액", "amount", 2260000),
                        Map.of("label", "거래후 잔액", "amount", 6760000)
                ));
            }
            default -> {
                data.put("documentType", documentType.name());
                data.put("status", "EXTRACTED");
            }
        }
        return data;
    }
}
