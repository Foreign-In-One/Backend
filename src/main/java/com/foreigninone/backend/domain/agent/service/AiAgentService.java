package com.foreigninone.backend.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.common.exception.ErrorCode;
import com.foreigninone.backend.domain.agent.config.OpenAiProperties;
import com.foreigninone.backend.domain.agent.dto.AgentPaycheckResponse;
import com.foreigninone.backend.domain.agent.dto.EmployerQuestionCard;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckCaseType;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final OpenAiProperties openAiProperties;
    private final PaycheckRepository paycheckRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AgentPaycheckResponse analyzePaycheckCase(Long paycheckId, PaycheckCaseType inputCaseType) {
        return analyzePaycheckCase(paycheckId, inputCaseType, null, null);
    }

    @Transactional(readOnly = true)
    public AgentPaycheckResponse analyzePaycheckCase(Long paycheckId, PaycheckCaseType inputCaseType, String requestLocale, String requestWorkplace) {
        Paycheck paycheck = paycheckRepository.findById(paycheckId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYCHECK_NOT_FOUND));

        User user = paycheck.getUser();
        PaycheckCaseType caseType = inputCaseType != null ? inputCaseType : determineCaseType(paycheck);

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
                return callOpenAi(paycheck, user, caseType, effectiveLocale, effectiveWorkplace);
            } catch (Exception e) {
                log.warn("OpenAI API call failed or timed out, falling back to mock AI agent response: {}", e.getMessage());
            }
        } else {
            log.info("OpenAI API key not configured, using mock AI agent for paycheckId: {}", paycheckId);
        }

        return generateMockAgentResponse(paycheck, user, caseType, effectiveLocale, effectiveWorkplace);
    }

    private PaycheckCaseType determineCaseType(Paycheck paycheck) {
        if (paycheck.getStatus() == com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus.NOT_RECEIVED) {
            return PaycheckCaseType.NOT_RECEIVED;
        }
        if (paycheck.getExpectedPaymentDate() != null && paycheck.getPaymentDate() != null
                && paycheck.getPaymentDate().toLocalDate().isAfter(paycheck.getExpectedPaymentDate())) {
            return PaycheckCaseType.PAYMENT_DELAY;
        }
        if (paycheck.getDifferenceAmount() != null && paycheck.getDifferenceAmount().compareTo(BigDecimal.ZERO) < 0) {
            return PaycheckCaseType.SALARY_DECREASE;
        }
        if (paycheck.getDifferenceAmount() != null && paycheck.getDifferenceAmount().compareTo(BigDecimal.ZERO) > 0) {
            return PaycheckCaseType.LARGE_DEVIATION;
        }
        return PaycheckCaseType.NORMAL;
    }

    private AgentPaycheckResponse callOpenAi(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace) throws Exception {
        String prompt = buildPrompt(paycheck, user, caseType, effectiveLocale, effectiveWorkplace);
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "외국인";

        long diff = paycheck.getDifferenceAmount() != null ? paycheck.getDifferenceAmount().abs().longValue() : 0L;

        Map<String, Object> requestBody = Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 외국인 근로자의 금융권리를 돕는 PayCycle AI 어시스턴트입니다.\n" +
                                        "규칙:\n" +
                                        "1. '임금체불', '불법공제', '위반' 같은 단정적인 법적 용어를 절대 사용하지 마세요. 대신 '설명이 필요한 차이', '추가 확인 필요' 표현을 사용하세요.\n" +
                                        "2. [환각 방지] 제시된 차액(" + String.format("%,d", diff) + "원)과 명시된 급여 금액만을 사용하며, 임의로 다른 금액이나 없는 숫자를 추론/계산하여 지어내지 마세요.\n" +
                                        "3. [언어 분기] koreanScript는 한국인 사업주가 읽을 공손하고 격식 있는 존댓말로 작성하고, nativeScript는 사용자가 요청한 언어/국적(" + nationality + ", " + effectiveLocale + ")에 맞추어 해당 모국어로 정확히 번역하여 작성하세요.\n" +
                                        "4. 반드시 아래 JSON 형식으로만 응답하세요.\n" +
                                        "{\n" +
                                        "  \"summary\": \"...\",\n" +
                                        "  \"requiredEvidence\": [\"...\"],\n" +
                                        "  \"nextActions\": [\"...\"],\n" +
                                        "  \"messageForEmployer\": \"...\",\n" +
                                        "  \"employerQuestionCards\": [\n" +
                                        "    {\n" +
                                        "      \"language\": \"" + effectiveLocale + "\",\n" +
                                        "      \"title\": \"질문 카드 제목\",\n" +
                                        "      \"koreanScript\": \"한국어 사장님 문의 문구\",\n" +
                                        "      \"nativeScript\": \"사용자 모국어 번역 문구\"\n" +
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
                employerQuestionCards.add(EmployerQuestionCard.builder()
                        .language(node.has("language") && !node.path("language").asText().isBlank() ? node.path("language").asText() : effectiveLocale)
                        .title(node.path("title").asText())
                        .koreanScript(node.path("koreanScript").asText())
                        .nativeScript(node.path("nativeScript").asText())
                        .build());
            }
        }

        String messageForEmployer = resultNode.path("messageForEmployer").asText();

        if (employerQuestionCards.isEmpty() && !messageForEmployer.isBlank()) {
            employerQuestionCards.add(EmployerQuestionCard.builder()
                    .language(effectiveLocale)
                    .title("급여 차액 확인 요청")
                    .koreanScript(messageForEmployer)
                    .nativeScript(messageForEmployer)
                    .build());
        }

        return AgentPaycheckResponse.builder()
                .caseType(caseType.name())
                .summary(resultNode.path("summary").asText())
                .requiredEvidence(requiredEvidence)
                .nextActions(nextActions)
                .messageForEmployer(messageForEmployer)
                .employerQuestionCards(employerQuestionCards)
                .build();
    }

    private String buildPrompt(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace) {
        String userName = (user != null && user.getName() != null) ? user.getName() : "근로자";
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "외국인";

        return String.format(
                "사용자 정보:\n- 이름: %s\n- 국적: %s\n- 사업장: %s\n- 요청 언어: %s\n\n" +
                        "급여 분석 정보:\n- 급여월: %s\n- 계약상 급여: %s원\n- 명세서 실지급액: %s원\n- 실제 입금액: %s원\n- 차액: %s원\n- 판정 케이스: %s\n" +
                        "명세서 등록 여부: %s\n\n위 사실을 바탕으로 사용자에게 친절하고 객관적인 설명, 필요한 서류, 권장 행동, 그리고 사장님께 정중하게 문의할 수 있는 한국어 및 모국어(%s) 질문 카드를 작성해주세요.",
                userName,
                nationality,
                effectiveWorkplace,
                effectiveLocale,
                paycheck.getPayPeriod(),
                paycheck.getContractAmount() != null ? paycheck.getContractAmount().toPlainString() : "미등록",
                paycheck.getPayslipAmount() != null ? paycheck.getPayslipAmount().toPlainString() : "미등록",
                paycheck.getActualAmount() != null ? paycheck.getActualAmount().toPlainString() : "0",
                paycheck.getDifferenceAmount() != null ? paycheck.getDifferenceAmount().toPlainString() : "0",
                caseType.name(),
                paycheck.getPayslipDocument() != null ? "등록됨" : "미등록",
                effectiveLocale
        );
    }

    private AgentPaycheckResponse generateMockAgentResponse(Paycheck paycheck, User user, PaycheckCaseType caseType, String effectiveLocale, String effectiveWorkplace) {
        String company = effectiveWorkplace;
        long diff = paycheck.getDifferenceAmount() != null ? paycheck.getDifferenceAmount().abs().longValue() : 0L;
        String payPeriod = paycheck.getPayPeriod();
        String lang = effectiveLocale;
        String nationality = (user != null && user.getNationality() != null) ? user.getNationality() : "";

        String nativeDecreaseScript;
        if ("vi".equals(lang)) {
            nativeDecreaseScript = String.format("Xin chào giám đốc, lương tháng %s có chênh lệch %,d won giữa phiếu lương và tiền vào tài khoản, nhờ giám đốc kiểm tra giúp tôi.", payPeriod, diff);
        } else if ("zh".equals(lang)) {
            nativeDecreaseScript = String.format("老板您好，%s月份工资实发金额与工资条有%,d韩元的差额，麻烦您确认一下扣除明细。", payPeriod, diff);
        } else if ("en".equals(lang)) {
            nativeDecreaseScript = String.format("Hello sir, there is a difference of %,d KRW in my %s salary deposit compared to the payslip. Could you please check the deduction details?", diff, payPeriod);
        } else if ("th".equals(lang)) {
            nativeDecreaseScript = String.format("สวัสดีครับหัวหน้า เงินเดือนเดือน %s มีส่วนต่าง %,d วอน รบกวนช่วยตรวจสอบให้หน่อยครับ", payPeriod, diff);
        } else if ("ko".equals(lang)) {
            nativeDecreaseScript = String.format("안녕하세요 대표님, %s 급여 입금액과 명세서에 %,d원 차이가 있어 공제 내역 확인 부탁드립니다.", payPeriod, diff);
        } else if (nationality.contains("베트남")) {
            nativeDecreaseScript = String.format("Xin chào giám đốc, lương tháng %s có chênh lệch %,d won giữa phiếu lương và tiền vào tài khoản, nhờ giám đốc kiểm tra giúp tôi.", payPeriod, diff);
        } else if (nationality.contains("중국")) {
            nativeDecreaseScript = String.format("老板您好，%s月份工资实发金额与工资条有%,d韩元的差额，麻烦您确认一下扣除明细。", payPeriod, diff);
        } else if (nationality.contains("태국")) {
            nativeDecreaseScript = String.format("สวัสดีครับหัวหน้า เงินเดือนเดือน %s มีส่วนต่าง %,d วอน รบกวนช่วยตรวจสอบให้หน่อยครับ", payPeriod, diff);
        } else {
            nativeDecreaseScript = String.format("안녕하세요 대표님, %s 급여 입금액과 명세서에 %,d원 차이가 있어 공제 내역 확인 부탁드립니다.", payPeriod, diff);
        }

        switch (caseType) {
            case SALARY_DECREASE -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금액(%,d원)과 명세서 금액에 %,d원의 차이가 있어 확인 부탁드립니다.",
                        company, payPeriod, paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L, diff);

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(String.format("%s 급여 차액 %,d원 확인 요청", payPeriod, diff))
                        .koreanScript(korScript)
                        .nativeScript(nativeDecreaseScript)
                        .build();

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여의 실입금액이 명세서 금액보다 %,d원 적게 입금되어 확인이 필요합니다.", payPeriod, diff))
                        .requiredEvidence(List.of(String.format("%s 임금명세서", payPeriod), "급여 입금 통장 거래내역"))
                        .nextActions(List.of("임금명세서 상세 공제 내역 확인", "추가 공제 항목(세금, 4대보험, 가불금 등) 여부 확인", "사업주 사실 확인 문의"))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case PAYMENT_DELAY -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금 일정이 평소와 달라 확인차 연락드렸습니다.", company, payPeriod);
                String nativeDelayScript;
                if ("vi".equals(lang)) {
                    nativeDelayScript = "Xin chào giám đốc, lịch thanh toán lương tháng này có chút thay đổi nên tôi xin phép hỏi thăm ạ.";
                } else if ("zh".equals(lang)) {
                    nativeDelayScript = "老板您好，本月发薪日期与合同约定有所差异，想向您确认一下情况，谢谢！";
                } else if ("en".equals(lang)) {
                    nativeDelayScript = "Hello sir, there seems to be a difference between the contractual payday and actual payment date. Could you please check this?";
                } else if ("th".equals(lang)) {
                    nativeDelayScript = "สวัสดีครับหัวหน้า กำหนดการจ่ายเงินเดือนมีความล่าช้า จึงขอสอบถามครับ";
                } else if (nationality.contains("베트남")) {
                    nativeDelayScript = "Xin chào giám đốc, lịch thanh toán lương tháng này có chút thay đổi nên tôi xin phép hỏi thăm ạ.";
                } else if (nationality.contains("중국")) {
                    nativeDelayScript = "老板您好，本月发薪日期与合同约定有所差异，想向您确认一下情况，谢谢！";
                } else if (nationality.contains("태국")) {
                    nativeDelayScript = "สวัสดีครับหัวหน้า กำหนดการจ่ายเงินเดือนมีความล่าช้า จึงขอสอบถามครับ";
                } else {
                    nativeDelayScript = "안녕하세요 대표님, 급여 입금 일정이 지연되어 확인차 문의드립니다.";
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(String.format("%s 급여 입금일 지연 문의", payPeriod))
                        .koreanScript(korScript)
                        .nativeScript(nativeDelayScript)
                        .build();

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여가 계약상 정해진 급여일보다 늦게 입금되었습니다.", payPeriod))
                        .requiredEvidence(List.of("표준근로계약서", "급여 입금 통장 거래내역"))
                        .nextActions(List.of("급여 지급 지연 사유 확인", "향후 정기 지급 일정 확인"))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case NOT_RECEIVED -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금 내역이 확인되지 않아 확인 부탁드립니다.", company, payPeriod);
                String nativeNotReceivedScript;
                if ("vi".equals(lang)) {
                    nativeNotReceivedScript = "Xin chào giám đốc, hiện tại tôi chưa thấy tiền lương tháng này vào tài khoản, nhờ giám đốc kiểm tra giúp ạ.";
                } else if ("zh".equals(lang)) {
                    nativeNotReceivedScript = "老板您好，目前未查到本月工资入账记录，麻烦您确认一下，谢谢！";
                } else if ("en".equals(lang)) {
                    nativeNotReceivedScript = "Hello sir, I have not seen my salary deposit for this month yet. Could you please check the status?";
                } else if ("th".equals(lang)) {
                    nativeNotReceivedScript = "สวัสดีครับหัวหน้า ยังไม่พบยอดเงินเดือนเข้าบัญชี จึงขอสอบถามครับ";
                } else if (nationality.contains("베트남")) {
                    nativeNotReceivedScript = "Xin chào giám đốc, hiện tại tôi chưa thấy tiền lương tháng này vào tài khoản, nhờ giám đốc kiểm tra giúp ạ.";
                } else if (nationality.contains("중국")) {
                    nativeNotReceivedScript = "老板您好，目前未查到本月工资入账记录，麻烦您确认一下，谢谢！";
                } else if (nationality.contains("태국")) {
                    nativeNotReceivedScript = "สวัสดีครับหัวหน้า ยังไม่พบยอดเงินเดือนเข้าบัญชี จึงขอสอบถามครับ";
                } else {
                    nativeNotReceivedScript = "안녕하세요 대표님, 이번 달 급여 입금 내역이 확인되지 않아 확인 부탁드립니다.";
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(String.format("%s 급여 미입금 확인 요청", payPeriod))
                        .koreanScript(korScript)
                        .nativeScript(nativeNotReceivedScript)
                        .build();

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여일에 입금 내역이 확인되지 않았습니다.", payPeriod))
                        .requiredEvidence(List.of("표준근로계약서", "급여 통장 입출금 내역"))
                        .nextActions(List.of("통장 계좌번호 재확인", "사업장 급여 지급 일정 확인"))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case LARGE_DEVIATION -> {
                long actualAmt = paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L;
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금액(%,d원)에 변동 내역이 있어 확인 부탁드립니다.",
                        company, payPeriod, actualAmt);

                String nativeLargeDevScript;
                if ("vi".equals(lang)) {
                    nativeLargeDevScript = String.format("Xin chào giám đốc, tiền lương tháng %s vào tài khoản (%,d won) có sự thay đổi, nhờ giám đốc kiểm tra giúp tôi ạ.",
                            payPeriod, actualAmt);
                } else if ("zh".equals(lang)) {
                    nativeLargeDevScript = String.format("老板您好，%s月份工资实发金额（%,d韩元）存在变动，想向您确认一下具体明细，谢谢！",
                            payPeriod, actualAmt);
                } else if ("en".equals(lang)) {
                    nativeLargeDevScript = String.format("Hello sir, there is a variation in my %s salary deposit (%,d KRW). Could you please verify the details?",
                            payPeriod, actualAmt);
                } else if ("th".equals(lang)) {
                    nativeLargeDevScript = String.format("สวัสดีครับหัวหน้า ยอดเงินเดือนเดือน %s (%,d วอน) มีความเปลี่ยนแปลง จึงขอสอบถามรายละเอียดครับ",
                            payPeriod, actualAmt);
                } else if (nationality.contains("베트남")) {
                    nativeLargeDevScript = String.format("Xin chào giám đốc, tiền lương tháng %s vào tài khoản (%,d won) có sự thay đổi, nhờ giám đốc kiểm tra giúp tôi ạ.",
                            payPeriod, actualAmt);
                } else if (nationality.contains("중국")) {
                    nativeLargeDevScript = String.format("老板您好，%s月份工资实发金额（%,d韩元）存在变动，想向您确认一下具体明细，谢谢！",
                            payPeriod, actualAmt);
                } else if (nationality.contains("태국")) {
                    nativeLargeDevScript = String.format("สวัสดีครับหัวหน้า ยอดเงินเดือนเดือน %s (%,d วอน) มีความเปลี่ยนแปลง จึงขอสอบถามรายละเอียดครับ",
                            payPeriod, actualAmt);
                } else {
                    nativeLargeDevScript = korScript;
                }

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(String.format("%s 급여 변동 확인 요청", payPeriod))
                        .koreanScript(korScript)
                        .nativeScript(nativeLargeDevScript)
                        .build();

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여 입금액에 예상과 다른 변동이 확인되었습니다.", payPeriod))
                        .requiredEvidence(List.of(String.format("%s 임금명세서", payPeriod), "급여 입금 통장 거래내역"))
                        .nextActions(List.of("임금명세서 상세 항목 확인", "사업주 사실 확인 문의"))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            default -> {
                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여 내역이 정상적으로 확인되었습니다.", payPeriod))
                        .requiredEvidence(List.of())
                        .nextActions(List.of("급여 내역 보관", "세금 및 공제 내역 정기 확인"))
                        .messageForEmployer("")
                        .employerQuestionCards(List.of())
                        .build();
            }
        }
    }
}
