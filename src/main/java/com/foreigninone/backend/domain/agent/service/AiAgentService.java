package com.foreigninone.backend.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.agent.config.OpenAiProperties;
import com.foreigninone.backend.domain.agent.dto.AgentChatResponse;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckResponse;
import com.foreigninone.backend.domain.agent.dto.EmployerQuestionCard;
import com.foreigninone.backend.domain.exitcheck.entity.ExitCheck;
import com.foreigninone.backend.domain.exitcheck.repository.ExitCheckRepository;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final OpenAiProperties openAiProperties;
    private final PaycheckRepository paycheckRepository;
    private final UserRepository userRepository;
    private final ExitCheckRepository exitCheckRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AgentPaycheckResponse analyzePaycheckCase(Long paycheckId, PaycheckCaseType inputCaseType) {
        return analyzePaycheckCase(paycheckId, inputCaseType, null, null);
    }

    @Transactional(readOnly = true)
    public AgentPaycheckResponse analyzePaycheckCase(Long paycheckId, PaycheckCaseType inputCaseType, String requestLocale, String requestWorkplace) {
        return analyzePaycheckCase(paycheckId, inputCaseType, requestLocale, requestWorkplace, null);
    }

    @Transactional(readOnly = true)
    public AgentPaycheckResponse analyzePaycheckCase(Long paycheckId, PaycheckCaseType inputCaseType, String requestLocale, String requestWorkplace, Object finding) {
        Paycheck paycheck = paycheckRepository.findById(paycheckId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYCHECK_NOT_FOUND));

        User user = paycheck.getUser();
        FindingInfo findingInfo = extractFindingInfo(finding);
        PaycheckCaseType caseType = inputCaseType != null ? inputCaseType : determineCaseType(paycheck, findingInfo);

        String effectiveLocale = (requestLocale != null && !requestLocale.isBlank())
                ? requestLocale.trim().toLowerCase()
                : ((user != null && user.getLanguage() != null && !user.getLanguage().isBlank())
                ? user.getLanguage().trim().toLowerCase()
                : "ko");

        String effectiveWorkplace = (requestWorkplace != null && !requestWorkplace.isBlank())
                ? requestWorkplace.trim()
                : ((user != null && user.getCompanyName() != null && !user.getCompanyName().isBlank())
                ? user.getCompanyName().trim()
                : "사업장");

        if (openAiProperties.isConfigured()) {
            try {
                log.info("Calling OpenAI API for paycheckId: {}, caseType: {}, locale: {}, workplace: {}",
                        paycheckId, caseType, effectiveLocale, effectiveWorkplace);
                return callOpenAi(paycheck, user, caseType, effectiveLocale, effectiveWorkplace, findingInfo);
            } catch (Exception e) {
                log.warn("OpenAI API call failed or timed out, falling back to mock AI agent response: {}", e.getMessage());
            }
        } else {
            log.info("OpenAI API key not configured, using mock AI agent for paycheckId: {}", paycheckId);
        }

        return generateMockAgentResponse(paycheck, user, caseType, effectiveLocale, effectiveWorkplace, findingInfo);
    }

    @Getter
    @Builder
    private static class FindingInfo {
        private final String id;
        private final String title;
        private final String fact;
        private final Long difference;
        private final String status;
    }

    private FindingInfo extractFindingInfo(Object finding) {
        if (finding == null) return null;
        try {
            JsonNode node = (finding instanceof JsonNode)
                    ? (JsonNode) finding
                    : objectMapper.valueToTree(finding);
            String id = node.has("id") ? node.path("id").asText(null) : null;
            String title = node.has("title") ? node.path("title").asText(null) : null;
            String fact = node.has("fact") ? node.path("fact").asText(null) : null;
            String status = node.has("status") ? node.path("status").asText(null) : null;
            Long diff = null;
            if (node.has("difference") && !node.path("difference").isNull()) {
                diff = Math.abs(node.path("difference").asLong());
            }
            return FindingInfo.builder()
                    .id(id)
                    .title(title)
                    .fact(fact)
                    .difference(diff)
                    .status(status)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private PaycheckCaseType determineCaseType(Paycheck paycheck, FindingInfo findingInfo) {
        if (paycheck.getStatus() == com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus.NOT_RECEIVED) {
            return PaycheckCaseType.NOT_RECEIVED;
        }
        if (paycheck.getStatus() == com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus.INSUFFICIENT_DATA) {
            return PaycheckCaseType.UNKNOWN;
        }

        // 1. 차액이 존재하는 경우 (금액 이상징후 최우선 판정)
        if (paycheck.getDifferenceAmount() != null && paycheck.getDifferenceAmount().compareTo(BigDecimal.ZERO) < 0) {
            return PaycheckCaseType.SALARY_DECREASE;
        }
        if (paycheck.getDifferenceAmount() != null && paycheck.getDifferenceAmount().compareTo(BigDecimal.ZERO) > 0) {
            return PaycheckCaseType.LARGE_DEVIATION;
        }

        // 2. findingInfo 기반 이상징후 세부 매핑
        if (findingInfo != null && findingInfo.getId() != null) {
            String fid = findingInfo.getId();
            if ("base".equals(fid) || "net".equals(fid) || "contract-deposit".equals(fid)) {
                return PaycheckCaseType.SALARY_DECREASE;
            }
            if ("deduction".equals(fid)) {
                return PaycheckCaseType.LARGE_DEVIATION;
            }
            if ("paydate".equals(fid)) {
                return PaycheckCaseType.PAYMENT_DELAY;
            }
        }

        // 3. 금액 차이가 없을 때, 입금일 지연 여부 확인
        if (paycheck.getExpectedPaymentDate() != null && paycheck.getPaymentDate() != null
                && paycheck.getPaymentDate().toLocalDate().isAfter(paycheck.getExpectedPaymentDate())) {
            return PaycheckCaseType.PAYMENT_DELAY;
        }

        // 4. 상태 기반 fallback
        if (paycheck.getStatus() == com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus.EXPLANATION_REQUIRED) {
            return PaycheckCaseType.SALARY_DECREASE;
        }
        if (paycheck.getStatus() == com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus.CONFIRMATION_REQUIRED) {
            return PaycheckCaseType.LARGE_DEVIATION;
        }

        return PaycheckCaseType.NORMAL;
    }

    private PaycheckCaseType determineCaseType(Paycheck paycheck) {
        return determineCaseType(paycheck, null);
    }

    private AgentPaycheckResponse callOpenAi(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace, FindingInfo findingInfo) throws Exception {
        String prompt = buildPrompt(paycheck, user, caseType, effectiveLocale, effectiveWorkplace, findingInfo);
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "외국인";

        long diff = calculateEffectiveDiff(paycheck, findingInfo);

        Map<String, Object> requestBody = Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 외국인 근로자의 금융권리를 돕는 PayCycle AI 어시스턴트입니다.\n" +
                                        "규칙:\n" +
                                        "1. '임금체불', '불법공제', '위반' 같은 단정적인 법적 용어를 절대 사용하지 마세요. 대신 '설명이 필요한 차이', '추가 확인 필요' 표현을 사용하세요.\n" +
                                        "2. [환각 방지] 제시된 차액(" + String.format("%,d", diff) + "원)과 명시된 급여 금액만을 사용하며, 임의로 다른 금액이나 없는 숫자를 추론/계산하여 지어내지 마세요.\n" +
                                        "3. [다국어 지원 지침 - 필수 준수]\n" +
                                        "   - 사용자의 인터페이스 언어: " + effectiveLocale + " (국적: " + nationality + ")\n" +
                                        "   - 만약 effectiveLocale이 'ko'가 아니라면(예: vi(베트남어), en(영어), zh(중국어), th(태국어), km(캄보디아어) 등), 사용자가 앱에서 직접 읽는 필드인 'headline', 'summary', 'documentCheckGuide', 'reasons', 'requiredEvidence', 'nextActions' 및 질문카드의 'title'은 반드시 사용자가 선택한 언어(" + effectiveLocale + ")로 번역하여 자연스럽게 작성하세요.\n" +
                                        "   - [중요: koreanScript 작성 원칙] 절대 '급여 차이에 대한 문의', '확인 요청' 같은 단순 제목이나 1줄 단문으로 끝내지 마세요. 외국인 근로자가 한국인 사장님/대표님께 문자나 카카오톡으로 복사하여 정중하게 보낼 수 있도록 ① 첫인사 및 평소 배려에 대한 감사, ② 체결한 근로계약서 제4조 기본급, 교부받은 임금명세서 실지급액/기본급, 통장 실제 입금액, 그리고 발생한 차액의 구체적 금액(" + String.format("%,d", diff) + "원)을 명시하는 구체적 근거 대조, ③ 추가 공제(기숙사비, 식대, 4대보험 등) 여부나 산정 기준 변동에 대한 정중한 확인 질문, ④ 바쁘신 중에 번거롭게 해드려 죄송하다는 양해와 편하신 시간에 확인 부탁드린다는 맺음말을 모두 갖춘 3~4문장의 장문 완성형 존댓말 비즈니스 메시지로 작성하세요.\n" +
                                        "   - [중요: nativeScript 작성 원칙] 위 한국어 장문 전체를 사용자의 언어(" + effectiveLocale + ")로 정중하고 완전한 비즈니스 격식체로 번역하여 작성하세요.\n" +
                                        "4. [중복 방지 및 구체적 진단] headline은 한 줄 요약 제목(예: '2026-08 실지급액 40,000원 부족 차액 감지'), summary는 사실 관계와 배경을 친절하게 풀어서 설명하는 상세 본문으로 작성하여, headline과 summary 문장이 서로 겹치거나 단순히 문장을 복사하지 않도록 하세요.\n" +
                                        "5. [동적 문서 대조 가이드] documentCheckGuide 항목에는 사용자가 지금 바로 대조하고 확인해야 할 구체적인 문서(예: '임금명세서의 공제 내역(4대보험, 식대, 숙소비 등)과 통장 거래내역서를 대조하여...', '근로계약서 제4조(기본급)와 임금명세서 기본급 항목을 대조하여...')와 확인 포인트를 상황에 맞추어 매우 구체적으로 작성하세요.\n" +
                                        "6. 반드시 아래 JSON 형식으로만 응답하세요.\n" +
                                        "{\n" +
                                        "  \"headline\": \"핵심 한 줄 요약 헤드라인\",\n" +
                                        "  \"summary\": \"상세 분석 및 상황 설명\",\n" +
                                        "  \"documentCheckGuide\": \"확인해야 할 구체적 문서 및 대조 포인트 안내\",\n" +
                                        "  \"reasons\": [\"추정 원인 1\", \"추정 원인 2\"],\n" +
                                        "  \"requiredEvidence\": [\"...\"],\n" +
                                        "  \"nextActions\": [\"...\"],\n" +
                                        "  \"messageForEmployer\": \"...\",\n" +
                                        "  \"employerQuestionCards\": [\n" +
                                        "    {\n" +
                                        "      \"language\": \"" + effectiveLocale + "\",\n" +
                                        "      \"title\": \"질문 카드 제목\",\n" +
                                        "      \"koreanScript\": \"한국어 사장님 문의 정중한 장문 완성형 문장 (존댓말 3~4문장)\",\n" +
                                        "      \"nativeScript\": \"사용자 모국어 번역 비즈니스 장문\"\n" +
                                        "    }\n" +
                                        "  ]\n" +
                                        "}"),
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );

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
            log.error("OpenAI API returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        } catch (Exception ex) {
            log.error("OpenAI API request failed: {}", ex.getMessage());
            throw ex;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode resultNode = objectMapper.readTree(content);

        String headline = resultNode.path("headline").asText(null);
        String summary = resultNode.path("summary").asText(null);
        String documentCheckGuide = resultNode.path("documentCheckGuide").asText(null);

        if (headline == null || headline.isBlank()) {
            headline = String.format("%s 급여 분석 결과", paycheck.getPayPeriod());
        }
        if (summary == null || summary.isBlank()) {
            summary = "급여 분석이 완료되었습니다.";
        }
        if (documentCheckGuide == null || documentCheckGuide.isBlank()) {
            documentCheckGuide = "임금명세서의 세부 지급 및 공제 항목과 통장 거래내역서를 대조해보세요.";
        }

        List<String> reasons = new ArrayList<>();
        if (resultNode.path("reasons").isArray()) {
            for (JsonNode node : resultNode.path("reasons")) {
                reasons.add(node.asText());
            }
        }

        List<String> requiredEvidence = new ArrayList<>();
        if (resultNode.path("requiredEvidence").isArray()) {
            for (JsonNode node : resultNode.path("requiredEvidence")) {
                requiredEvidence.add(node.asText());
            }
        }

        List<String> nextActions = new ArrayList<>();
        if (resultNode.path("nextActions").isArray()) {
            for (JsonNode node : resultNode.path("nextActions")) {
                nextActions.add(node.asText());
            }
        }

        List<EmployerQuestionCard> employerQuestionCards = new ArrayList<>();
        if (resultNode.path("employerQuestionCards").isArray()) {
            for (JsonNode node : resultNode.path("employerQuestionCards")) {
                String kor = node.path("koreanScript").asText("");
                String nat = node.path("nativeScript").asText("");
                String title = node.path("title").asText("");
                String cardLang = node.has("language") && !node.path("language").asText().isBlank() ? node.path("language").asText() : effectiveLocale;

                if (kor.isBlank() || kor.trim().length() < 35 || kor.trim().equals("급여 차이에 대한 문의") || kor.trim().equals("급여 차액에 대한 문의") || (kor.trim().startsWith("급여 차이") && kor.trim().length() < 30)) {
                    AgentPaycheckResponse mockFallback = generateMockAgentResponse(paycheck, user, caseType, effectiveLocale, effectiveWorkplace, findingInfo);
                    if (!mockFallback.getEmployerQuestionCards().isEmpty()) {
                        EmployerQuestionCard fallbackCard = mockFallback.getEmployerQuestionCards().get(0);
                        kor = fallbackCard.getKoreanScript();
                        if (nat.isBlank() || nat.trim().length() < 35 || nat.trim().contains("discrepancy in my salary")) {
                            nat = fallbackCard.getNativeScript();
                        }
                        if (title.isBlank()) {
                            title = fallbackCard.getTitle();
                        }
                    }
                }

                employerQuestionCards.add(EmployerQuestionCard.builder()
                        .language(cardLang)
                        .title(title)
                        .koreanScript(kor)
                        .nativeScript(nat)
                        .build());
            }
        }

        String messageForEmployer = resultNode.path("messageForEmployer").asText("");

        if (employerQuestionCards.isEmpty()) {
            AgentPaycheckResponse mockFallback = generateMockAgentResponse(paycheck, user, caseType, effectiveLocale, effectiveWorkplace, findingInfo);
            if (!mockFallback.getEmployerQuestionCards().isEmpty()) {
                employerQuestionCards.addAll(mockFallback.getEmployerQuestionCards());
            } else if (!messageForEmployer.isBlank()) {
                employerQuestionCards.add(EmployerQuestionCard.builder()
                        .language(effectiveLocale)
                        .title("급여 확인 요청")
                        .koreanScript(messageForEmployer)
                        .nativeScript(messageForEmployer)
                        .build());
            }
        }

        return AgentPaycheckResponse.builder()
                .caseType(caseType.name())
                .headline(headline)
                .summary(summary)
                .documentCheckGuide(documentCheckGuide)
                .reasons(reasons)
                .requiredEvidence(requiredEvidence)
                .nextActions(nextActions)
                .messageForEmployer(messageForEmployer)
                .employerQuestionCards(employerQuestionCards)
                .build();
    }

    private long calculateEffectiveDiff(Paycheck paycheck, FindingInfo findingInfo) {
        if (paycheck.getDifferenceAmount() != null && paycheck.getDifferenceAmount().compareTo(BigDecimal.ZERO) != 0) {
            return paycheck.getDifferenceAmount().abs().longValue();
        }
        if (findingInfo != null && findingInfo.getId() != null) {
            String fid = findingInfo.getId();
            if ("base".equals(fid) || "net".equals(fid) || "deduction".equals(fid) || "contract-deposit".equals(fid)) {
                if (findingInfo.getDifference() != null && findingInfo.getDifference() > 0) {
                    return findingInfo.getDifference();
                }
            }
        }
        return 0L;
    }

    private String buildPrompt(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace, FindingInfo findingInfo) {
        String userName = (user != null && user.getName() != null) ? user.getName() : "근로자";
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "외국인";
        long diff = calculateEffectiveDiff(paycheck, findingInfo);
        String findingDetail = (findingInfo != null && findingInfo.getFact() != null)
                ? "\n- 핵심 불일치 팩트: " + findingInfo.getFact()
                : "";

        String caseInstruction;
        if (caseType == PaycheckCaseType.NORMAL) {
            caseInstruction = "[정상 지급 안내 지침]\n" +
                    "- 계약상 기본급과 명세서 기본급이 일치하고, 명세서 실지급액과 통장 입금액이 완벽히 일치하여 부족 차액이 없습니다 (차액: 0원).\n" +
                    "- 계약상 급여와 명세서 실지급액 간의 차이는 근로기준법 및 4대보험/소득세 관련 정상적인 공제액(세금 등)이며, 임금 차액이나 삭감이 아닙니다.\n" +
                    "- summary에는 급여가 정상적으로 전액 입금되었음을 명확히 안내하세요.\n" +
                    "- messageForEmployer 및 employerQuestionCards에는 사장님께 급여 입금에 대해 정중히 감사 인사를 전하는 내용으로 작성하세요.\n";
        } else {
            caseInstruction = String.format(
                    "[이상징후 안내 지침]\n" +
                    "- 차액 %,d원이 감지되었습니다. 원인 추정과 함께 사장님께 정중하게 확인을 요청할 수 있는 질문 카드를 작성하세요.\n",
                    diff
            );
        }

        return String.format(
                "사용자 정보:\n- 이름: %s\n- 국적: %s\n- 사업장: %s\n- 요청 언어: %s\n\n" +
                        "급여 분석 정보:\n- 급여월: %s\n- 계약상 급여: %s원\n- 명세서 실지급액: %s원\n- 실제 입금액: %s원\n- 차액: %,d원\n- 판정 케이스: %s%s\n" +
                        "명세서 등록 여부: %s\n\n%s\n위 사실을 바탕으로 사용자에게 친절하고 객관적인 설명(headline, summary), 대조 가이드(documentCheckGuide), 원인(reasons), 필요한 서류(requiredEvidence), 권장 행동(nextActions)을 반드시 사용자가 요청한 언어(%s)로 작성하고, 사장님께 정중하게 문의할 수 있는 한국어(koreanScript) 및 모국어(%s, nativeScript) 질문 카드를 작성해주세요.",
                userName,
                nationality,
                effectiveWorkplace,
                effectiveLocale,
                paycheck.getPayPeriod(),
                paycheck.getContractAmount() != null ? paycheck.getContractAmount().toPlainString() : "미등록",
                paycheck.getPayslipAmount() != null ? paycheck.getPayslipAmount().toPlainString() : "미등록",
                paycheck.getActualAmount() != null ? paycheck.getActualAmount().toPlainString() : "0",
                diff,
                caseType.name(),
                findingDetail,
                paycheck.getPayslipDocument() != null ? "등록됨" : "미등록",
                caseInstruction,
                effectiveLocale,
                effectiveLocale
        );
    }

    private AgentPaycheckResponse generateMockAgentResponse(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace, FindingInfo findingInfo) {
        String company = effectiveWorkplace;
        long diff = calculateEffectiveDiff(paycheck, findingInfo);
        String payPeriod = paycheck.getPayPeriod();
        String lang = effectiveLocale;
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "";

        String nativeDecreaseScript;
        if ("vi".equals(lang)) {
            nativeDecreaseScript = String.format("Xin chào giám đốc, em xin chân thành cảm ơn giám đốc đã vất vả và chuyển lương %s cho em. Khi kiểm tra tài khoản, em thấy số tiền thực lĩnh ghi trên phiếu lương và số tiền thực tế nhận vào tài khoản ngân hàng có khoản chênh lệch khoảng %,d won. Không biết công ty có khấu trừ thêm khoản nào ngoài phiếu lương như tiền ký túc xá, tiền ăn, bảo hiểm truy thu hay có nhầm lẫn trong quá trình chuyển khoản không ạ? Khi nào thuận tiện, nhờ giám đốc xem lại giúp em với ạ. Em xin lỗi vì đã làm phiền giám đốc trong lúc bận rộn. Em cảm ơn giám đốc rất nhiều!", payPeriod, diff);
        } else if ("zh".equals(lang)) {
            nativeDecreaseScript = String.format("老板您好，辛苦您了，非常感谢您按时发放%s的工资。我在核对实到账目时注意到，工资条上载明的实发金额与我的银行账户实际到账金额之间存在约 %,d 韩元的差额，因此想向您礼貌地咨询一下。想请问是否有未在明细中列出的扣款项目（如宿舍费、餐费、四大保险补扣等），或者是转账过程中出现了小差错？百忙之中给您添麻烦了，方便时请您帮忙查验一下。非常感谢老板一直以来的关照！", payPeriod, diff);
        } else if ("en".equals(lang)) {
            nativeDecreaseScript = String.format("Hello sir, thank you very much for all your hard work and for sending my salary for %s. While checking my account, I noticed a discrepancy of about %,d KRW between the net pay stated on my payslip and the actual amount deposited into my bank account, so I am reaching out politely. Could you please check when you have a moment whether there were additional unlisted deductions—such as dormitory, meal expenses, or retroactive insurance adjustments—or perhaps a minor discrepancy during the bank transfer? I apologize for taking up your time during your busy schedule. Thank you sincerely for your continuous support and care!", payPeriod, diff);
        } else if ("th".equals(lang)) {
            nativeDecreaseScript = String.format("สวัสดีครับหัวหน้า ขอบคุณมากครับสำหรับเงินเดือนเดือน %s ในการตรวจสอบยอดเงิน พบว่ายอดเงินที่ได้รับจริงในบัญชีมีส่วนต่างประมาณ %,d วอน เมื่อเทียบกับใบแจ้งยอดเงินเดือน ไม่ทราบว่ามีการหักค่าใช้จ่ายอื่นเพิ่มเติม หรือมีข้อผิดพลาดในการโอนหรือไม่ครับ รบกวนหัวหน้าช่วยตรวจสอบเมื่อสะดวกด้วยครับ ขอบคุณมากครับ", payPeriod, diff);
        } else if ("ko".equals(lang)) {
            nativeDecreaseScript = String.format("안녕하세요 사장님, 이번 달에도 노고 많으셨고 %s 급여 챙겨주셔서 진심으로 감사드립니다. 다름이 아니라 급여 내역을 확인하던 중, 교부받은 임금명세서 상의 실지급액과 실제 제 통장에 입금된 금액 사이에 약 %,d원의 차액이 확인되어 조심스럽게 문의드립니다. 혹시 기숙사비나 식대 등 명세서에 기재되지 않은 추가 공제 항목이 있었는지, 아니면 계좌 송금 과정에서 착오가 있었는지 시간 되실 때 확인해 주시면 감사하겠습니다. 바쁘신 업무 중에 번거롭게 해드려 죄송합니다. 늘 배려해 주셔서 감사합니다!", payPeriod, diff);
        } else if (nationality.contains("베트남")) {
            nativeDecreaseScript = String.format("Xin chào giám đốc, em xin chân thành cảm ơn giám đốc đã vất vả và chuyển lương %s cho em. Khi kiểm tra tài khoản, em thấy số tiền thực lĩnh ghi trên phiếu lương và số tiền thực tế nhận vào tài khoản ngân hàng có khoản chênh lệch khoảng %,d won. Không biết công ty có khấu trừ thêm khoản nào ngoài phiếu lương như tiền ký túc xá, tiền ăn, bảo hiểm truy thu hay có nhầm lẫn trong quá trình chuyển khoản không ạ? Khi nào thuận tiện, nhờ giám đốc xem lại giúp em với ạ. Em xin lỗi vì đã làm phiền giám đốc trong lúc bận rộn. Em cảm ơn giám đốc rất nhiều!", payPeriod, diff);
        } else if (nationality.contains("중국")) {
            nativeDecreaseScript = String.format("老板您好，辛苦您了，非常感谢您按时发放%s的工资。我在核对实到账目时注意到，工资条上载明的实发金额与我的银行账户实际到账金额之间存在约 %,d 韩元的差额，因此想向您礼貌地咨询一下。想请问是否有未在明细中列出的扣款项目（如宿舍费、餐费、四大保险补扣等），或者是转账过程中出现了小差错？百忙之中给您添麻烦了，方便时请您帮忙查验一下。非常感谢老板一直以来的关照！", payPeriod, diff);
        } else if (nationality.contains("태국")) {
            nativeDecreaseScript = String.format("สวัสดีครับหัวหน้า ขอบคุณมากครับสำหรับเงินเดือนเดือน %s ในการตรวจสอบยอดเงิน พบว่ายอดเงินที่ได้รับจริงในบัญชีมีส่วนต่างประมาณ %,d วอน เมื่อเทียบกับใบแจ้งยอดเงินเดือน ไม่ทราบว่ามีการหักค่าใช้จ่ายอื่นเพิ่มเติม หรือมีข้อผิดพลาดในการโอนหรือไม่ครับ รบกวนหัวหน้าช่วยตรวจสอบเมื่อสะดวกด้วยครับ ขอบคุณมากครับ", payPeriod, diff);
        } else {
            nativeDecreaseScript = String.format("안녕하세요 사장님, 이번 달에도 노고 많으셨고 %s 급여 챙겨주셔서 진심으로 감사드립니다. 다름이 아니라 급여 내역을 확인하던 중, 교부받은 임금명세서 상의 실지급액과 실제 제 통장에 입금된 금액 사이에 약 %,d원의 차액이 확인되어 조심스럽게 문의드립니다. 혹시 기숙사비나 식대 등 명세서에 기재되지 않은 추가 공제 항목이 있었는지, 아니면 계좌 송금 과정에서 착오가 있었는지 시간 되실 때 확인해 주시면 감사하겠습니다. 바쁘신 업무 중에 번거롭게 해드려 죄송합니다. 늘 배려해 주셔서 감사합니다!", payPeriod, diff);
        }

        switch (caseType) {
            case SALARY_DECREASE -> {
                String factDetail = (findingInfo != null && findingInfo.getFact() != null) ? findingInfo.getFact() : "";
                String korScript;
                if (!factDetail.isBlank() && factDetail.contains("기본급")) {
                    korScript = String.format("안녕하세요 사장님, %s에서 항상 따뜻하게 배려해 주시고 챙겨주셔서 진심으로 감사드립니다. 다름이 아니라 %s 급여 내역을 확인하던 중, 체결한 근로계약서 제4조 상의 기본급과 교부받은 임금명세서 기본급 사이에 약 %,d원의 차액이 확인되어 조심스럽게 연락드렸습니다. 혹시 소정근로시간 계산이나 기본급 산정 기준에 변동 사항이 있었는지, 바쁘시겠지만 편하신 시간에 확인해 주실 수 있으실까요? 늘 감사드리며, 항상 건강 유의하시기 바랍니다!",
                            company, payPeriod, diff);
                } else {
                    korScript = String.format("안녕하세요 사장님, 이번 달에도 노고 많으셨고 %s 급여 챙겨주셔서 진심으로 감사드립니다. 다름이 아니라 급여 내역을 확인하던 중, 교부받은 임금명세서 상의 실지급액과 실제 제 통장에 입금된 금액(%,d원) 사이에 약 %,d원의 차액이 확인되어 조심스럽게 문의드립니다. 혹시 기숙사비나 식대 등 명세서에 기재되지 않은 추가 공제 항목이 있었는지, 아니면 계좌 송금 과정에서 착오가 있었는지 시간 되실 때 확인해 주시면 감사하겠습니다. 바쁘신 업무 중에 번거롭게 해드려 죄송합니다. 늘 배려해 주셔서 감사합니다!",
                            company, paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L, diff);
                }

                String cardTitle;
                if ("vi".equals(lang)) {
                    cardTitle = String.format("Yêu cầu xác nhận chênh lệch lương %s %,d won", payPeriod, diff);
                } else if ("en".equals(lang)) {
                    cardTitle = String.format("Inquiry for %s salary difference %,d KRW", payPeriod, diff);
                } else if ("zh".equals(lang)) {
                    cardTitle = String.format("关于%s工资差额 %,d韩元的确认请求", payPeriod, diff);
                } else {
                    cardTitle = String.format("%s 급여 차액 %,d원 확인 요청", payPeriod, diff);
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(cardTitle)
                        .koreanScript(korScript)
                        .nativeScript(nativeDecreaseScript)
                        .build();

                String headlineText;
                String summaryText;
                String docGuideText;
                List<String> reasonsList;
                List<String> evidenceList;
                List<String> nextActionsList;

                if ("vi".equals(lang)) {
                    if (!factDetail.isBlank() && factDetail.contains("기본급")) {
                        headlineText = String.format("Phát hiện chênh lệch %,d won lương cơ bản %s", diff, payPeriod);
                        summaryText = String.format("Kết quả phân tích lương tháng %s cho thấy lương cơ bản trên phiếu lương thấp hơn %,d won so với hợp đồng lao động. Theo Điều 43 Luật Tiêu chuẩn Lao động, việc giảm lương cơ bản mà không có sự đồng ý bằng văn bản của người lao động là bị hạn chế.", payPeriod, diff);
                        docGuideText = "Vui lòng đối chiếu mục lương cơ bản trên hợp đồng lao động với phiếu lương để xác nhận lý do phát sinh chênh lệch.";
                    } else {
                        headlineText = String.format("Phát hiện thiếu %,d won tiền vào tài khoản %s", diff, payPeriod);
                        summaryText = String.format("Khoản tiền nhận vào tài khoản tháng %s có chênh lệch thiếu %,d won so với thực nhận trên phiếu lương. Cần kiểm tra xem có khoản khấu trừ bổ sung nào chưa được thông báo hay không.", payPeriod, diff);
                        docGuideText = String.format("Vui lòng đối chiếu mục các khoản khấu trừ trên phiếu lương với sao kê tài khoản ngân hàng để làm rõ khoản %,d won chưa ghi rõ.", diff);
                    }
                    reasonsList = List.of(
                            "Khả năng có khoản khấu trừ chưa ghi trên phiếu lương (ký túc xá, điện nước, ăn uống...)",
                            "Bỏ sót tính tiền làm thêm giờ hoặc phụ cấp chuyên cần",
                            "Lỗi nhập liệu hoặc chuyển tiền chia nhỏ của kế toán"
                    );
                    evidenceList = List.of(
                            String.format("Bản sao phiếu lương tháng %s (chi tiết thanh toán và khấu trừ)", payPeriod),
                            "Sao kê giao dịch nhận lương từ tài khoản ngân hàng",
                            "Bản sao hợp đồng lao động tiêu chuẩn",
                            "Bảng chấm công hoặc nhật ký làm việc"
                    );
                    nextActionsList = List.of(
                            "Bước 1: Chụp màn hình lưu trữ bằng chứng (phiếu lương, sao kê ngân hàng)",
                            "Bước 2: Sao chép Thẻ câu hỏi gửi chủ sử dụng để nhắn tin hỏi lý do lịch sự",
                            "Bước 3: Yêu cầu chuyển khoản bổ sung nếu do tính nhầm và lưu phiếu lương sửa đổi",
                            "Bước 4: Liên hệ Bộ Lao động (1350) hoặc Trung tâm Hỗ trợ nếu không được giải quyết thỏa đáng"
                    );
                } else if ("en".equals(lang)) {
                    if (!factDetail.isBlank() && factDetail.contains("기본급")) {
                        headlineText = String.format("Base salary difference of %,d KRW detected for %s", diff, payPeriod);
                        summaryText = String.format("Analysis for %s shows a %,d KRW difference in base salary between the employment contract and payslip. Under Article 43 of the Labor Standards Act, unauthorized base pay reduction is restricted.", payPeriod, diff);
                        docGuideText = "Please compare the base pay in your employment contract with your payslip to check the reason for the difference.";
                    } else {
                        headlineText = String.format("Deposit shortage of %,d KRW detected for %s", diff, payPeriod);
                        summaryText = String.format("There is a shortage of %,d KRW between your bank deposit and the net pay on your payslip for %s. Please verify any additional deductions.", payPeriod, diff);
                        docGuideText = String.format("Please compare the deduction details on your payslip with your bank transaction record for the %,d KRW difference.", diff);
                    }
                    reasonsList = List.of(
                            "Possible unlisted deductions (dormitory, utilities, meals, etc.)",
                            "Omission or calculation error in overtime or weekly holiday allowances",
                            "Clerical mistake or split transfer by the employer"
                    );
                    evidenceList = List.of(
                            String.format("Copy of %s payslip (payment and deduction details)", payPeriod),
                            "Bank salary transaction statement",
                            "Standard employment contract copy",
                            "Timecard or work attendance log"
                    );
                    nextActionsList = List.of(
                            "Step 1: Save evidence (screenshots of payslip and bank statement)",
                            "Step 2: Copy the Employer Question Card to inquire politely via message",
                            "Step 3: Request adjustment payment and revised payslip if it was an error",
                            "Step 4: Contact the Ministry of Employment and Labor (1350) if unresolved"
                    );
                } else {
                    if (!factDetail.isBlank() && factDetail.contains("기본급")) {
                        headlineText = String.format("%s 기본급 %,d원 삭감 차액 확인 필요", payPeriod, diff);
                        summaryText = String.format("%s 급여 분석 결과, 근로계약서 기본급 대비 임금명세서 기본급에서 %,d원의 차액이 확인되었습니다. 근로기준법 제43조(전액 지급의 원칙)에 따라 근로자의 사전 서면 동의 없는 기본급 삭감은 제한되므로, 산정 기준 변경 여부에 대한 구체적 확인이 필요합니다.",
                                payPeriod, diff);
                        docGuideText = "근로계약서 제4조(임금 구성)의 기본급 명시액과 임금명세서의 기본급 항목을 대조해보세요. 계약 체결 시 약정한 금액보다 적게 책정되었다면, 산정 기준 변경에 대한 사전 동의서가 존재하는지 사업장에 확인해야 합니다.";
                    } else {
                        headlineText = String.format("%s 통장 실입금액 %,d원 부족 차액 감지", payPeriod, diff);
                        summaryText = String.format("%s 급여 입금액(%,d원)과 임금명세서 실지급액 사이에 %,d원의 부족 차액이 감지되었습니다. 근로기준법 제43조(전액 지급의 원칙)에 따라 근로자의 사전 서면 동의 없는 공제는 제한되므로, 추가 공제 항목 여부 및 계산 착오에 대한 구체적 확인이 필요합니다.",
                                payPeriod, paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L, diff);
                        docGuideText = String.format("임금명세서의 '공제 내역(4대보험 소급 정산, 숙소비, 식대 등)'과 실제 통장 입금 거래내역서를 대조해보세요. 명세서에 기재되지 않은 %,d원의 별도 공제나 송금 착오가 있었는지 급여 담당자에게 확인해야 합니다.", diff);
                    }
                    reasonsList = List.of(
                            "임금명세서 미기재 추가 공제 가능성 (기숙사비, 수도광열비, 식대, 유니폼 비용 또는 4대보험 소급 정산 등 사전 미동의 공제)",
                            "가산수당(연장·야간·휴일근로 1.5배 가산) 또는 주휴수당 산정 누락/오차",
                            "사업장 급여 담당자의 단순 송금 입력 착오 또는 분할 이체"
                    );
                    evidenceList = List.of(
                            String.format("%s 귀속월 임금명세서 사본 (지급 및 공제 세부 항목)", payPeriod),
                            "급여 통장 입금 거래내역서 (입금 일시, 금액, 송금인 명의)",
                            "표준근로계약서 사본 (소정근로시간, 기본급, 숙식비 공제 약정서)",
                            "출퇴근 기록부 또는 근무일지 (연장·야간 근로시간 증빙)"
                    );
                    nextActionsList = List.of(
                            "1단계: 팩트 확인 및 증빙 자료(명세서, 통장 거래내역) 캡처 확보",
                            "2단계: 제공된 '사장님 질문 카드'를 복사하여 메신저(문자/카카오톡)로 공제 사유 정중히 서면 문의",
                            "3단계: 계산 착오 시 차액 입금 요청 및 공제 사유가 명시된 수정 임금명세서 수령·보관",
                            "4단계: 정당한 이유 없이 미해결 시 고용노동부(1350) 또는 관할 외국인노동자지원센터 권리구제 상담"
                    );
                }

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .headline(headlineText)
                        .summary(summaryText)
                        .documentCheckGuide(docGuideText)
                        .reasons(reasonsList)
                        .requiredEvidence(evidenceList)
                        .nextActions(nextActionsList)
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case PAYMENT_DELAY -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 지급 일정과 관련하여 평소와 달라 확인차 연락드렸습니다. 혹시 이번 달 급여 입금 일정이 언제쯤 진행되는지 알려주시면 감사하겠습니다!", company, payPeriod);
                String nativeDelayScript;
                String cardTitle;
                if ("vi".equals(lang)) {
                    nativeDelayScript = "Xin chào giám đốc, lịch thanh toán lương tháng này có chút thay đổi nên tôi xin phép hỏi thăm ạ.";
                    cardTitle = String.format("Hỏi về việc chậm trả lương %s", payPeriod);
                } else if ("zh".equals(lang)) {
                    nativeDelayScript = "老板您好，本月发薪日期与合同约定有所差异，想向您确认一下情况，谢谢！";
                    cardTitle = String.format("%s发薪延迟咨询", payPeriod);
                } else if ("en".equals(lang)) {
                    nativeDelayScript = "Hello sir, there seems to be a difference between the contractual payday and actual payment date. Could you please check this?";
                    cardTitle = String.format("Inquiry about %s payment delay", payPeriod);
                } else {
                    nativeDelayScript = "안녕하세요 대표님, 급여 입금 일정이 지연되어 확인차 문의드립니다.";
                    cardTitle = String.format("%s 급여 입금일 지연 문의", payPeriod);
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(cardTitle)
                        .koreanScript(korScript)
                        .nativeScript(nativeDelayScript)
                        .build();

                String expectedDateStr = paycheck.getExpectedPaymentDate() != null ? paycheck.getExpectedPaymentDate().toString() : "정기 급여일";

                String headlineText;
                String summaryText;
                String docGuideText;
                List<String> reasonsList;
                List<String> evidenceList;
                List<String> nextActionsList;

                if ("vi".equals(lang)) {
                    headlineText = String.format("Cần kiểm tra việc chậm trả lương %s", payPeriod);
                    summaryText = String.format("Tiền lương tháng %s bị chuyển chậm hơn ngày trả lương theo hợp đồng (%s). Theo Khoản 2 Điều 43 Luật Tiêu chuẩn Lao động, tiền lương phải được trả vào ngày cố định hàng tháng.", payPeriod, expectedDateStr);
                    docGuideText = "Vui lòng đối chiếu điều khoản 'Ngày trả lương' trên hợp đồng với thời gian nhận tiền thực tế trong sao kê ngân hàng để kiểm tra lý do chậm trễ.";
                    reasonsList = List.of(
                            "Chậm trễ trong quy trình quyết toán lương hoặc quá giờ giao dịch ngân hàng",
                            "Trùng vào ngày nghỉ/ngày lễ dẫn đến dời sang ngày làm việc tiếp theo",
                            "Tình hình tài chính hoặc thủ tục phê duyệt nội bộ của công ty"
                    );
                    evidenceList = List.of("Bản sao hợp đồng lao động (điều khoản ngày trả lương)", "Sao kê nhận tiền tài khoản ngân hàng", "Thông báo đổi ngày trả lương của công ty (nếu có)");
                    nextActionsList = List.of(
                            "Bước 1: Kiểm tra lại quy định ngày trả lương và thỏa thuận khi trùng ngày lễ",
                            "Bước 2: Hỏi người sử dụng lao động/kế toán lý do chậm và ngày dự kiến thanh toán",
                            "Bước 3: Yêu cầu cam kết bằng văn bản nếu tình trạng chậm trễ lặp lại nhiều lần"
                    );
                } else if ("en".equals(lang)) {
                    headlineText = String.format("Payment delay verification required for %s", payPeriod);
                    summaryText = String.format("The salary for %s was deposited later than the contractual payday (%s). Under Article 43(2) of the Labor Standards Act, wages must be paid on a fixed date each month.", payPeriod, expectedDateStr);
                    docGuideText = "Please compare the payday clause in your employment contract with your bank transaction time to check if it was shifted due to weekends or holidays.";
                    reasonsList = List.of(
                            "Company payroll settlement delay or bank cutoff time passed",
                            "Shifted to the next business day due to weekend/public holidays",
                            "Company cash flow or internal approval procedure delay"
                    );
                    evidenceList = List.of("Standard employment contract copy", "Bank salary transaction statement", "Company payday change notice (if any)");
                    nextActionsList = List.of(
                            "Step 1: Check contractual payday and holiday payment agreements",
                            "Step 2: Inquire politely about the delay reason and expected payment date",
                            "Step 3: Request written assurance if delays occur repeatedly"
                    );
                } else {
                    headlineText = String.format("%s 급여 입금 지연 확인 필요", payPeriod);
                    summaryText = String.format("%s 급여가 계약상 정해진 정기 급여일(%s)보다 늦게 입금되었거나 지연되고 있습니다. 근로기준법 제43조 제2항(정기일 지급의 원칙)에 따라 임금은 매월 정해진 날짜에 지급되어야 합니다.", payPeriod, expectedDateStr);
                    docGuideText = "근로계약서에 명시된 '임금 지급일' 조항과 통장 거래내역서의 실제 입금 일시를 대조해보세요. 지급일이 주말이나 공휴일이어서 은행 영업일로 순연된 것인지 사업장에 확인해보세요.";
                    reasonsList = List.of(
                            "사업장 급여 정산 일정 지연 또는 금융기관 이체 마감 시간 초과",
                            "급여일이 주말/공휴일인 경우 사전 약정된 지급일(직전 영업일 또는 익영업일) 차이",
                            "회사 자금 사정 또는 내부 행정 결재 절차 지연"
                    );
                    evidenceList = List.of("표준근로계약서 사본 (임금 지급일 명시 조항)", "급여 입금 통장 거래내역서", "회사 급여 지급일 변경 공지(있는 경우)");
                    nextActionsList = List.of(
                            "1단계: 근로계약서 상 정기 급여일 및 주말/공휴일 지급 특약 확인",
                            "2단계: 사업주/급여 담당자에게 지연 사유 및 지급 예정일 정중히 확인",
                            "3단계: 반복 지연 발생 시 향후 정기 지급 일정에 대한 서면 확약 요청"
                    );
                }

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .headline(headlineText)
                        .summary(summaryText)
                        .documentCheckGuide(docGuideText)
                        .reasons(reasonsList)
                        .requiredEvidence(evidenceList)
                        .nextActions(nextActionsList)
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case NOT_RECEIVED -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여일인데 아직 통장에 급여 입금 내역이 확인되지 않아 연락드렸습니다. 혹시 제 계좌번호에 이상이 있거나 확인이 필요한 사항이 있는지 점검 부탁드립니다!", company, payPeriod);
                String nativeNotReceivedScript;
                String cardTitle;
                if ("vi".equals(lang)) {
                    nativeNotReceivedScript = "Xin chào giám đốc, hiện tại tôi chưa thấy tiền lương tháng này vào tài khoản, nhờ giám đốc kiểm tra giúp ạ.";
                    cardTitle = String.format("Yêu cầu xác nhận chưa nhận lương %s", payPeriod);
                } else if ("zh".equals(lang)) {
                    nativeNotReceivedScript = "老板您好，目前未查到本月工资入账记录，麻烦您确认一下，谢谢！";
                    cardTitle = String.format("%s未收到工资确认请求", payPeriod);
                } else if ("en".equals(lang)) {
                    nativeNotReceivedScript = "Hello sir, I have not seen my salary deposit for this month yet. Could you please check the status?";
                    cardTitle = String.format("Inquiry on missing %s salary deposit", payPeriod);
                } else {
                    nativeNotReceivedScript = "안녕하세요 대표님, 이번 달 급여 입금 내역이 확인되지 않아 확인 부탁드립니다.";
                    cardTitle = String.format("%s 급여 미입금 확인 요청", payPeriod);
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(cardTitle)
                        .koreanScript(korScript)
                        .nativeScript(nativeNotReceivedScript)
                        .build();

                String headlineText;
                String summaryText;
                String docGuideText;
                List<String> reasonsList;
                List<String> evidenceList;
                List<String> nextActionsList;

                if ("vi".equals(lang)) {
                    headlineText = String.format("Cần kiểm tra việc chưa nhận lương %s", payPeriod);
                    summaryText = String.format("Đã qua ngày trả lương định kỳ tháng %s nhưng chưa thấy ghi nhận khoản tiền lương vào tài khoản. Cần nhanh chóng kiểm tra xem có sai sót số tài khoản hay lỗi chuyển khoản không.", payPeriod);
                    docGuideText = "Vui lòng kiểm tra lại thông tin số tài khoản và ngân hàng đã đăng ký với công ty, đồng thời xuất sao kê 3 tháng gần nhất để hỏi bộ phận kế toán.";
                    reasonsList = List.of(
                            "Sai số tài khoản nhận lương hoặc ngân hàng xử lý chậm",
                            "Bỏ sót tên trong danh sách trả lương của công ty",
                            "Công ty điều chỉnh ngày trả lương mà chưa thông báo"
                    );
                    evidenceList = List.of("Bản sao hợp đồng lao động tiêu chuẩn", "Sao kê giao dịch ngân hàng 3 tháng gần nhất", "Bản sao sổ tài khoản (xác nhận lại số tài khoản)");
                    nextActionsList = List.of(
                            "Bước 1: Xác nhận lại số tài khoản ngân hàng đã đăng ký với công ty",
                            "Bước 2: Thông báo cho chủ sử dụng về việc chưa nhận lương và hỏi ngày dự kiến",
                            "Bước 3: Nếu tiếp tục không được trả, liên hệ Bộ Lao động (1350) để được hướng dẫn"
                    );
                } else if ("en".equals(lang)) {
                    headlineText = String.format("Missing salary deposit for %s", payPeriod);
                    summaryText = String.format("The scheduled payday for %s has passed, but no salary deposit has been verified in your account. Prompt check is needed for account errors or transfer omissions.", payPeriod);
                    docGuideText = "Please double check your bank account number registered with the employer and review recent transactions before contacting the payroll manager.";
                    reasonsList = List.of(
                            "Bank account number error or bank system processing delay",
                            "Omission from the employer's payroll transfer list",
                            "Unannounced payday adjustment or temporary cash flow delay"
                    );
                    evidenceList = List.of("Standard employment contract copy", "Bank transaction statement for past 3 months", "Bank passbook copy (account number verification)");
                    nextActionsList = List.of(
                            "Step 1: Verify registered bank account details with your employer",
                            "Step 2: Inform employer of missing deposit and request status update",
                            "Step 3: Consult Ministry of Employment and Labor (1350) if persistent"
                    );
                } else {
                    headlineText = String.format("%s 급여 미입금 확인 필요", payPeriod);
                    summaryText = String.format("%s 정기 급여일이 경과하였으나 통장으로 입금된 급여 내역이 전혀 확인되지 않았습니다. 계좌번호 착오 여부, 급여 처리 누락, 또는 일시적 송금 오류인지 신속한 확인이 필요합니다.", payPeriod);
                    docGuideText = "회사에 등록된 본인의 급여 통장 사본(은행명 및 계좌번호)을 재확인하고, 최근 3개월 통장 입출금 내역서를 출력하여 사업장 급여 담당자에게 이체 여부를 문의하세요.";
                    reasonsList = List.of(
                            "급여 입금 통장 계좌번호 오류 또는 은행 전산 처리 지연",
                            "사업장 급여 지급 명단 누락 또는 담당자 송금 누락",
                            "사업장 지급일 조정 미공지 또는 일시적 자금 집행 지연"
                    );
                    evidenceList = List.of("표준근로계약서 사본", "최근 3개월 급여 통장 입출금 내역서", "급여 수령용 통장 사본 (계좌번호 재확인)");
                    nextActionsList = List.of(
                            "1단계: 회사에 등록된 급여 통장 계좌번호 및 은행명 재확인",
                            "2단계: 사업주/담당자에게 급여 미입금 사실 알리고 입금 상태 및 예정일 확인 요청",
                            "3단계: 지속적 미지급 시 고용노동부 무료 상담(1350)을 통한 권리 구제 절차 안내"
                    );
                }

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .headline(headlineText)
                        .summary(summaryText)
                        .documentCheckGuide(docGuideText)
                        .reasons(reasonsList)
                        .requiredEvidence(evidenceList)
                        .nextActions(nextActionsList)
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case LARGE_DEVIATION -> {
                long actualAmt = paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L;
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금액(%,d원)에 변동 내역이 있어 확인차 연락드렸습니다. 혹시 이번 달 급여 명세서 항목 중 변동된 수당이나 공제 항목에 대해 간략히 설명해 주실 수 있는지 부탁드립니다!",
                        company, payPeriod, actualAmt);

                String nativeLargeDevScript;
                String cardTitle;
                if ("vi".equals(lang)) {
                    nativeLargeDevScript = String.format("Xin chào giám đốc, tiền lương tháng %s vào tài khoản (%,d won) có sự thay đổi, nhờ giám đốc kiểm tra giúp tôi ạ.", payPeriod, actualAmt);
                    cardTitle = String.format("Yêu cầu xác nhận biến động lương %s", payPeriod);
                } else if ("zh".equals(lang)) {
                    nativeLargeDevScript = String.format("老板您好，%s月份工资实发金额（%,d韩元）存在变动，想向您确认一下具体明细，谢谢！", payPeriod, actualAmt);
                    cardTitle = String.format("%s工资变动确认请求", payPeriod);
                } else if ("en".equals(lang)) {
                    nativeLargeDevScript = String.format("Hello sir, there is a variation in my %s salary deposit (%,d KRW). Could you please verify the details?", payPeriod, actualAmt);
                    cardTitle = String.format("Inquiry on %s salary variation", payPeriod);
                } else {
                    nativeLargeDevScript = korScript;
                    cardTitle = String.format("%s 급여 변동 확인 요청", payPeriod);
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(cardTitle)
                        .koreanScript(korScript)
                        .nativeScript(nativeLargeDevScript)
                        .build();

                String headlineText;
                String summaryText;
                String docGuideText;
                List<String> reasonsList;
                List<String> evidenceList;
                List<String> nextActionsList;

                if ("vi".equals(lang)) {
                    headlineText = String.format("Xác nhận chi tiết biến động lương %s", payPeriod);
                    summaryText = String.format("Số tiền lương thực tế nhận vào tài khoản tháng %s (%,d won) có sự chênh lệch đáng kể so với mức lương hợp đồng hoặc các tháng trước. Cần đối chiếu từng mục về phụ cấp làm thêm, tiền thưởng hoặc khấu trừ.", payPeriod, actualAmt);
                    docGuideText = "Vui lòng đối chiếu các mục 'Phụ cấp làm thêm giờ/ngày nghỉ' và 'Tiền thưởng' trên phiếu lương với bảng chấm công của bạn.";
                    reasonsList = List.of(
                            "Tăng giảm phụ cấp làm thêm giờ do thay đổi thời gian làm ngoài giờ",
                            "Chi trả tiền thưởng dịp lễ tết hoặc đánh giá năng suất",
                            "Khấu trừ quyết toán thuế cuối năm hoặc bảo hiểm 4 bên"
                    );
                    evidenceList = List.of(String.format("Bản sao phiếu lương tháng %s", payPeriod), "Sao kê tài khoản nhận lương", "Nhật ký làm việc và xác nhận làm thêm giờ");
                    nextActionsList = List.of(
                            "Bước 1: Đối chiếu chi tiết thanh toán và khấu trừ trên phiếu lương",
                            "Bước 2: So sánh các mục biến động so với tháng trước",
                            "Bước 3: Yêu cầu kế toán giải thích bằng văn bản nếu có mục bất thường"
                    );
                } else if ("en".equals(lang)) {
                    headlineText = String.format("Salary variation verification for %s", payPeriod);
                    summaryText = String.format("The actual salary deposit for %s (%,d KRW) shows a notable variation from your contractual salary. Itemized comparison is recommended for overtime allowances, bonuses, or deductions.", payPeriod, actualAmt);
                    docGuideText = "Please compare the overtime allowance and bonus items on your payslip with your attendance log.";
                    reasonsList = List.of(
                            "Changes in overtime/night work hours affecting allowances",
                            "One-time holiday bonus or incentive payment",
                            "Retroactive tax or 4 major social insurances settlement"
                    );
                    evidenceList = List.of(String.format("Copy of %s payslip", payPeriod), "Bank salary transaction statement", "Attendance log and overtime approval records");
                    nextActionsList = List.of(
                            "Step 1: Compare detailed payment and deduction items on payslip",
                            "Step 2: Review changes compared to the previous month",
                            "Step 3: Request payroll calculation breakdown from employer"
                    );
                } else {
                    headlineText = String.format("%s 급여 변동 내역 확인", payPeriod);
                    summaryText = String.format("%s 급여 실입금액(%,d원)이 평소 또는 계약 급여와 상당한 차이를 보이고 있습니다. 연장근로수당 정산, 상여금 지급, 또는 공제액 변동 여부에 대해 항목별 대조가 필요합니다.", payPeriod, actualAmt);
                    docGuideText = "임금명세서의 '연장·야간·휴일 근로수당' 및 '상여금' 항목과 본인의 출퇴근 기록부(근무일지)를 대조해보세요. 특별 수당이나 정산금이 정상 반영된 것인지 확인이 필요합니다.";
                    reasonsList = List.of(
                            "연장·야간·휴일 근로시간 변동에 따른 시간외근로수당 증감",
                            "분기/명절 상여금 또는 성과급 일시 지급",
                            "연말정산 소급 공제 또는 4대보험 정산금 반영"
                    );
                    evidenceList = List.of(String.format("%s 귀속월 임금명세서 사본", payPeriod), "급여 입금 통장 거래내역서", "근무일지 및 연장근로 승인 내역");
                    nextActionsList = List.of(
                            "1단계: 임금명세서 항목별 세부 지급 및 공제 내역 대조",
                            "2단계: 전월 대비 변동 항목(수당, 공제 등) 비교 점검",
                            "3단계: 이상 항목에 대해 사업장 급여 담당자에게 산출 근거 서면 요청"
                    );
                }

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .headline(headlineText)
                        .summary(summaryText)
                        .documentCheckGuide(docGuideText)
                        .reasons(reasonsList)
                        .requiredEvidence(evidenceList)
                        .nextActions(nextActionsList)
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            default -> {
                String headlineText;
                String summaryText;
                String docGuideText;
                List<String> reasonsList;
                List<String> evidenceList;
                List<String> nextActionsList;

                if ("vi".equals(lang)) {
                    headlineText = String.format("Đối chiếu 3 bên tháng %s: Khớp hoàn toàn (Bình thường)", payPeriod);
                    summaryText = String.format("Kết quả đối chiếu 3 bên lương tháng %s cho thấy tất cả các mục (hợp đồng, thực nhận phiếu lương, tiền vào tài khoản) đều hoàn toàn chính xác và khớp nhau.", payPeriod);
                    docGuideText = "Vui lòng lưu giữ an toàn phiếu lương và lịch sử giao dịch ngân hàng để làm bằng chứng tính trợ cấp thôi việc và gia hạn visa sau này.";
                    reasonsList = List.of("Không có bất kỳ sự sai lệch nào giữa hợp đồng, phiếu lương và sao kê ngân hàng");
                    evidenceList = List.of(String.format("Lưu trữ phiếu lương tháng %s", payPeriod));
                    nextActionsList = List.of("Lưu trữ file phiếu lương lâu dài (dùng cho visa và thuế)", "Định kỳ kiểm tra các khoản đóng bảo hiểm 4 bên");
                } else if ("en".equals(lang)) {
                    headlineText = String.format("3-way comparison match for %s (Normal)", payPeriod);
                    summaryText = String.format("All items (contract amount, payslip net amount, bank deposit) matched perfectly for %s.", payPeriod);
                    docGuideText = "Please keep your payslip and bank records safe for future severance calculation and visa extensions.";
                    reasonsList = List.of("No discrepancy found among contract, payslip, and bank deposit");
                    evidenceList = List.of(String.format("Keep %s payslip record", payPeriod));
                    nextActionsList = List.of("Store payslip file securely for visa and tax purposes", "Regularly verify social insurance and tax deductions");
                } else {
                    headlineText = String.format("%s 급여 3중 대조 일치 (정상)", payPeriod);
                    summaryText = String.format("%s 급여 3중 대조 결과 모든 항목(계약금액, 명세서 실지급액, 통장 실입금액)이 정상적으로 일치합니다.", payPeriod);
                    docGuideText = "교부받은 임금명세서와 은행 입금 내역은 향후 퇴직금 산정 및 비자 연장 시 중요한 증빙이 되므로 안전하게 보관하세요.";
                    reasonsList = List.of("계약서와 임금명세서, 은행 실입금액 간 불일치 사항 없음");
                    evidenceList = List.of("해당 귀속월 임금명세서 보관");
                    nextActionsList = List.of("급여 명세서 파일 영구 보관 (향후 비자 연장 및 세무 정산용)", "4대보험 및 세금 공제 내역 정기 확인");
                }

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .headline(headlineText)
                        .summary(summaryText)
                        .documentCheckGuide(docGuideText)
                        .reasons(reasonsList)
                        .requiredEvidence(evidenceList)
                        .nextActions(nextActionsList)
                        .messageForEmployer("")
                        .employerQuestionCards(List.of())
                        .build();
            }
        }
    }

    /**
     * 금융권리 챗봇 질문 응답.
     * OpenAI가 설정돼 있으면 서버가 직접 조회한 사용자 데이터를 컨텍스트로 실제 답변을 생성하고,
     * 미설정이거나 호출 실패 시 text=null 을 반환해 클라이언트가 localAnswer() 규칙 엔진으로 폴백하게 한다.
     */
    @Transactional(readOnly = true)
    public AgentChatResponse answerChatQuestion(Long userId, String question, String requestLocale) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String effectiveLocale = (requestLocale != null && !requestLocale.isBlank())
                ? requestLocale.trim().toLowerCase()
                : ((user.getLanguage() != null && !user.getLanguage().isBlank()) ? user.getLanguage().trim().toLowerCase() : "ko");

        if (!openAiProperties.isConfigured()) {
            log.info("OpenAI API key not configured, chat question will fall back to client rule engine for userId: {}", userId);
            return AgentChatResponse.builder().ok(false).text(null).error(null).build();
        }

        try {
            String context = buildChatContext(user);
            String content = callOpenAiChat(context, question, effectiveLocale);
            return AgentChatResponse.builder().ok(true).text(content).error(null).build();
        } catch (Exception e) {
            log.warn("OpenAI chat call failed, falling back to client rule engine: {}", e.getMessage());
            return AgentChatResponse.builder().ok(false).text(null).error(null).build();
        }
    }

    private String buildChatContext(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("사용자: %s / 국적 %s / 체류자격 %s%n",
                user.getName(), nullToDash(user.getNationality()), nullToDash(user.getVisaType())));
        sb.append(String.format("근로 상태: %s / 사업장 %s / 계약 급여일 %s%n",
                nullToDash(user.getEmploymentStatus()), nullToDash(user.getCompanyName()),
                user.getPayday() != null ? "매월 " + user.getPayday() + "일" : "모름"));
        sb.append(String.format("입국일 %s / 근무 시작일 %s / 예상 출국일 %s%n",
                dateOrDash(user.getEntryDate()), dateOrDash(user.getWorkStartDate()), dateOrDash(user.getExpectedExitDate())));

        Integer months = monthsWorked(user.getWorkStartDate());
        if (months != null) sb.append(String.format("총 근속 개월수: 약 %d개월%n", months));

        List<Paycheck> paychecks = paycheckRepository.findByUser_UserIdOrderByPayPeriodDesc(user.getUserId());
        if (paychecks.isEmpty()) {
            sb.append("급여 확인 기록: 없음\n");
        } else {
            Paycheck latest = paychecks.get(0);
            sb.append(String.format("최근 급여(%s) 판정: %s / 실입금액 %s원%n",
                    latest.getPayPeriod(), latest.getStatus(),
                    latest.getActualAmount() != null ? latest.getActualAmount().toPlainString() : "확인 불가"));
        }

        exitCheckRepository.findFirstByUser_UserIdOrderByAnalyzedAtDesc(user.getUserId()).ifPresentOrElse(
                (ExitCheck ec) -> sb.append(String.format("출국 정산 상태: %s / 준비도 %s%%%n",
                        ec.getStatus(), ec.getReadinessScore() != null ? ec.getReadinessScore() : 0)),
                () -> sb.append("출국 정산 확인 기록: 없음\n")
        );

        return sb.toString();
    }

    private Integer monthsWorked(java.time.LocalDate workStartDate) {
        if (workStartDate == null) return null;
        java.time.LocalDate now = java.time.LocalDate.now();
        if (now.isBefore(workStartDate)) return 0;
        Period period = Period.between(workStartDate, now);
        return Math.max(period.getYears() * 12 + period.getMonths(), 0);
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "미입력" : value;
    }

    private String dateOrDash(java.time.LocalDate date) {
        return date == null ? "미입력" : date.toString();
    }

    private String callOpenAiChat(String context, String question, String locale) {
        String systemPrompt =
                "당신은 한국에서 일하는 외국인 근로자를 돕는 금융권리 AI 어시스턴트입니다.\n" +
                        "규칙:\n" +
                        "1. 아래 [확인된 사용자 정보]에 없는 사실은 절대로 지어내지 마세요. 모르면 모른다고 답하세요.\n" +
                        "2. 반드시 요청 언어로, 2~4문장 이내로 간결하게 답변하세요.\n" +
                        "3. 순수 텍스트로만 답변하고 마크다운이나 JSON을 쓰지 마세요.";

        String userPrompt = String.format("[확인된 사용자 정보]%n%s%n[질문]%n%s%n%n요청 언어: %s", context, question, locale);

        Map<String, Object> requestBody = Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2
        );

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openAiProperties.getApiKey())
                .build();

        String responseJson = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root;
        try {
            root = objectMapper.readTree(responseJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI chat response", e);
        }
        String content = root.path("choices").get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("OpenAI chat response had no content");
        }
        return content.trim();
    }
}
