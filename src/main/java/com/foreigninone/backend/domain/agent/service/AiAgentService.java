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
                                        "  \"reasons\": [\"추정 원인 1\", \"추정 원인 2\"],\n" +
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
                .reasons(reasons)
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
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금해 주셔서 감사합니다. 통장 입금액(%,d원)과 명세서 실지급액 사이에 %,d원의 차액이 확인되어 연락드렸습니다. 혹시 추가로 공제된 항목이나 확인이 필요한 부분이 있는지 알려주시면 감사하겠습니다!",
                        company, payPeriod, paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L, diff);

                EmployerQuestionCard card = EmployerQuestionCard.builder()
                        .language(lang)
                        .title(String.format("%s 급여 차액 %,d원 확인 요청", payPeriod, diff))
                        .koreanScript(korScript)
                        .nativeScript(nativeDecreaseScript)
                        .build();

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여 입금액(%,d원)과 임금명세서 실지급액 사이에 %,d원의 부족 차액이 감지되었습니다. 근로기준법 제43조(전액 지급의 원칙)에 따라 근로자의 사전 서면 동의 없는 공제는 제한되므로, 추가 공제 항목 여부 및 계산 착오에 대한 구체적 확인이 필요합니다.",
                                payPeriod, paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L, diff))
                        .reasons(List.of(
                                "임금명세서 미기재 추가 공제 가능성 (기숙사비, 수도광열비, 식대, 유니폼 비용 또는 4대보험 소급 정산 등 사전 미동의 공제)",
                                "가산수당(연장·야간·휴일근로 1.5배 가산) 또는 주휴수당 산정 누락/오차",
                                "사업장 급여 담당자의 단순 송금 입력 착오 또는 분할 이체"
                        ))
                        .requiredEvidence(List.of(
                                String.format("%s 귀속월 임금명세서 사본 (지급 및 공제 세부 항목)", payPeriod),
                                "급여 통장 입금 거래내역서 (입금 일시, 금액, 송금인 명의)",
                                "표준근로계약서 사본 (소정근로시간, 기본급, 숙식비 공제 약정서)",
                                "출퇴근 기록부 또는 근무일지 (연장·야간 근로시간 증빙)"
                        ))
                        .nextActions(List.of(
                                "1단계: 팩트 확인 및 증빙 자료(명세서, 통장 거래내역) 캡처 확보",
                                "2단계: 제공된 '사장님 질문 카드'를 복사하여 메신저(문자/카카오톡)로 공제 사유 정중히 서면 문의",
                                "3단계: 계산 착오 시 차액 입금 요청 및 공제 사유가 명시된 수정 임금명세서 수령·보관",
                                "4단계: 정당한 이유 없이 미해결 시 고용노동부(1350) 또는 관할 외국인노동자지원센터 권리구제 상담"
                        ))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case PAYMENT_DELAY -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 지급 일정과 관련하여 평소와 달라 확인차 연락드렸습니다. 혹시 이번 달 급여 입금 일정이 언제쯤 진행되는지 알려주시면 감사하겠습니다!", company, payPeriod);
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

                String expectedDateStr = paycheck.getExpectedPaymentDate() != null ? paycheck.getExpectedPaymentDate().toString() : "정기 급여일";

                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여가 계약상 정해진 정기 급여일(%s)보다 늦게 입금되었거나 지연되고 있습니다. 근로기준법 제43조 제2항(정기일 지급의 원칙)에 따라 임금은 매월 정해진 날짜에 지급되어야 합니다.", payPeriod, expectedDateStr))
                        .reasons(List.of(
                                "사업장 급여 정산 일정 지연 또는 금융기관 이체 마감 시간 초과",
                                "급여일이 주말/공휴일인 경우 사전 약정된 지급일(직전 영업일 또는 익영업일) 차이",
                                "회사 자금 사정 또는 내부 행정 결재 절차 지연"
                        ))
                        .requiredEvidence(List.of("표준근로계약서 사본 (임금 지급일 명시 조항)", "급여 입금 통장 거래내역서", "회사 급여 지급일 변경 공지(있는 경우)"))
                        .nextActions(List.of(
                                "1단계: 근로계약서 상 정기 급여일 및 주말/공휴일 지급 특약 확인",
                                "2단계: 사업주/급여 담당자에게 지연 사유 및 지급 예정일 정중히 확인",
                                "3단계: 반복 지연 발생 시 향후 정기 지급 일정에 대한 서면 확약 요청"
                        ))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case NOT_RECEIVED -> {
                String korScript = String.format("안녕하세요 사장님, %s %s 급여일인데 아직 통장에 급여 입금 내역이 확인되지 않아 연락드렸습니다. 혹시 제 계좌번호에 이상이 있거나 확인이 필요한 사항이 있는지 점검 부탁드립니다!", company, payPeriod);
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
                        .summary(String.format("%s 정기 급여일이 경과하였으나 통장으로 입금된 급여 내역이 전혀 확인되지 않았습니다. 계좌번호 착오 여부, 급여 처리 누락, 또는 일시적 송금 오류인지 신속한 확인이 필요합니다.", payPeriod))
                        .reasons(List.of(
                                "급여 입금 통장 계좌번호 오류 또는 은행 전산 처리 지연",
                                "사업장 급여 지급 명단 누락 또는 담당자 송금 누락",
                                "사업장 지급일 조정 미공지 또는 일시적 자금 집행 지연"
                        ))
                        .requiredEvidence(List.of("표준근로계약서 사본", "최근 3개월 급여 통장 입출금 내역서", "급여 수령용 통장 사본 (계좌번호 재확인)"))
                        .nextActions(List.of(
                                "1단계: 회사에 등록된 급여 통장 계좌번호 및 은행명 재확인",
                                "2단계: 사업주/담당자에게 급여 미입금 사실 알리고 입금 상태 및 예정일 확인 요청",
                                "3단계: 지속적 미지급 시 고용노동부 무료 상담(1350)을 통한 권리 구제 절차 안내"
                        ))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            case LARGE_DEVIATION -> {
                long actualAmt = paycheck.getActualAmount() != null ? paycheck.getActualAmount().longValue() : 0L;
                String korScript = String.format("안녕하세요 사장님, %s %s 급여 입금액(%,d원)에 변동 내역이 있어 확인차 연락드렸습니다. 혹시 이번 달 급여 명세서 항목 중 변동된 수당이나 공제 항목에 대해 간략히 설명해 주실 수 있는지 부탁드립니다!",
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
                        .summary(String.format("%s 급여 실입금액(%,d원)이 평소 또는 계약 급여와 상당한 차이를 보이고 있습니다. 연장근로수당 정산, 상여금 지급, 또는 공제액 변동 여부에 대해 항목별 대조가 필요합니다.", payPeriod, actualAmt))
                        .reasons(List.of(
                                "연장·야간·휴일 근로시간 변동에 따른 시간외근로수당 증감",
                                "분기/명절 상여금 또는 성과급 일시 지급",
                                "연말정산 소급 공제 또는 4대보험 정산금 반영"
                        ))
                        .requiredEvidence(List.of(String.format("%s 귀속월 임금명세서 사본", payPeriod), "급여 입금 통장 거래내역서", "근무일지 및 연장근로 승인 내역"))
                        .nextActions(List.of(
                                "1단계: 임금명세서 항목별 세부 지급 및 공제 내역 대조",
                                "2단계: 전월 대비 변동 항목(수당, 공제 등) 비교 점검",
                                "3단계: 이상 항목에 대해 사업장 급여 담당자에게 산출 근거 서면 요청"
                        ))
                        .messageForEmployer(korScript)
                        .employerQuestionCards(List.of(card))
                        .build();
            }
            default -> {
                return AgentPaycheckResponse.builder()
                        .caseType(caseType.name())
                        .summary(String.format("%s 급여 3중 대조 결과 모든 항목(계약금액, 명세서 실지급액, 통장 실입금액)이 정상적으로 일치합니다.", payPeriod))
                        .reasons(List.of("계약서와 임금명세서, 은행 실입금액 간 불일치 사항 없음"))
                        .requiredEvidence(List.of("해당 귀속월 임금명세서 보관"))
                        .nextActions(List.of("급여 명세서 파일 영구 보관 (향후 비자 연장 및 세무 정산용)", "4대보험 및 세금 공제 내역 정기 확인"))
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
