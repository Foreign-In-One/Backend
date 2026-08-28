package com.foreigninone.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.domain.calendar.dto.CalendarEventCreateRequest;
import com.foreigninone.backend.domain.paycheck.dto.PaycheckAnalyzeRequest;
import com.foreigninone.backend.domain.user.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PaycheckApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("시드 사용자 프로필 조회 (GET /api/profile)")
    void testGetProfile() throws Exception {
        mockMvc.perform(get("/api/profile")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("민수"))
                .andExpect(jsonPath("$.data.nationality").value("베트남"))
                .andExpect(jsonPath("$.data.companyName").value("한국정밀"));
    }

    @Test
    @DisplayName("프로필 수정 및 사이드이펙트 검증 (PATCH /api/profile)")
    void testUpdateProfile() throws Exception {
        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .companyName("한국정밀(수정)")
                .payday(20)
                .expectedExitDate(LocalDate.of(2027, 4, 1))
                .language("ko")
                .build();

        mockMvc.perform(patch("/api/profile")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("한국정밀(수정)"))
                .andExpect(jsonPath("$.data.payday").value(20));
    }

    @Test
    @DisplayName("Mock Bank 거래내역 조회 (GET /api/mock/bank/transactions)")
    void testGetMockBankTransactions() throws Exception {
        mockMvc.perform(get("/api/mock/bank/transactions")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rspCode").value("A0000"))
                .andExpect(jsonPath("$.resList").isArray());
    }

    @Test
    @DisplayName("PayCheck 목록 조회 (GET /api/paychecks)")
    void testGetPaychecks() throws Exception {
        mockMvc.perform(get("/api/paychecks")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PayCheck 분석 실행 및 paycheckId 및 CalendarEvent 투영 검증 (POST /api/paychecks/analyze)")
    void testAnalyzePaycheck() throws Exception {
        PaycheckAnalyzeRequest request = PaycheckAnalyzeRequest.builder()
                .payPeriod("2026-08")
                .build();

        mockMvc.perform(post("/api/paychecks/analyze")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paycheckId").isNumber())
                .andExpect(jsonPath("$.data.payPeriod").value("2026-08"))
                .andExpect(jsonPath("$.data.status").value("EXPLANATION_REQUIRED"))
                .andExpect(jsonPath("$.data.differenceAmount").value(-120000));

        // 캘린더 이벤트에 PAYCHECK 및 PAYDAY가 투영되었는지 확인
        mockMvc.perform(get("/api/calendar/events")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.eventType == 'PAYCHECK')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.eventType == 'PAYDAY')]").isNotEmpty());
    }

    @Test
    @DisplayName("PayCheck 수기 입력/수정 필드를 포함한 분석 저장 검증 (POST /api/paychecks/analyze)")
    void testAnalyzePaycheckWithManualInputs() throws Exception {
        PaycheckAnalyzeRequest request = PaycheckAnalyzeRequest.builder()
                .payPeriod("2026-08")
                .contractAmount(java.math.BigDecimal.valueOf(2500000))
                .payslipAmount(java.math.BigDecimal.valueOf(2380000))
                .actualAmount(java.math.BigDecimal.valueOf(2260000))
                .differenceAmount(java.math.BigDecimal.valueOf(-120000))
                .expectedPaymentDate(LocalDate.of(2026, 8, 25))
                .paymentDate(LocalDateTime.of(2026, 8, 25, 9, 14, 0))
                .build();

        mockMvc.perform(post("/api/paychecks/analyze")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paycheckId").isNumber())
                .andExpect(jsonPath("$.data.payPeriod").value("2026-08"))
                .andExpect(jsonPath("$.data.contractAmount").value(2500000))
                .andExpect(jsonPath("$.data.payslipAmount").value(2380000))
                .andExpect(jsonPath("$.data.actualAmount").value(2260000))
                .andExpect(jsonPath("$.data.differenceAmount").value(-120000))
                .andExpect(jsonPath("$.data.status").value("EXPLANATION_REQUIRED"));
    }

    @Test
    @DisplayName("PayCheck AI 설명 API - URL Path & Body & Header & 다국어 질문카드 (POST /api/paychecks/{paycheckId}/explain)")
    void testExplainPaycheckWithBodyAndLocale() throws Exception {
        // 1. 먼저 8월 급여 분석 실행하여 실제 paycheckId 확보
        PaycheckAnalyzeRequest request = PaycheckAnalyzeRequest.builder()
                .payPeriod("2026-08")
                .build();

        String analyzeResponse = mockMvc.perform(post("/api/paychecks/analyze")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(analyzeResponse);
        long paycheckId = rootNode.path("data").path("paycheckId").asLong();

        // 2. 베트남어 (vi) locale로 explain 요청
        String explainBodyVi = """
                {
                    "finding": { "id": "net", "difference": -120000 },
                    "period": "2026-08",
                    "workplace": "한국정밀",
                    "locale": "vi"
                }
                """;

        mockMvc.perform(post("/api/paychecks/" + paycheckId + "/explain")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(explainBodyVi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.reasons").isArray())
                .andExpect(jsonPath("$.data.nextActions").isArray())
                .andExpect(jsonPath("$.data.employerQuestionCards").isArray())
                .andExpect(jsonPath("$.data.employerQuestionCards[0].koreanScript").isNotEmpty())
                .andExpect(jsonPath("$.data.employerQuestionCards[0].nativeScript").value(org.hamcrest.Matchers.containsString("Xin chào")));

        // 3. 중국어 (zh) locale로 explain 요청
        String explainBodyZh = """
                {
                    "finding": { "id": "net", "difference": -120000 },
                    "period": "2026-08",
                    "workplace": "한국정밀",
                    "locale": "zh"
                }
                """;

        mockMvc.perform(post("/api/paychecks/" + paycheckId + "/explain")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(explainBodyZh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employerQuestionCards[0].nativeScript").value(org.hamcrest.Matchers.containsString("老板您好")));
    }

    @Test
    @DisplayName("AI Agent PayCheck 설명 API (POST /api/agent/paycheck)")
    void testAgentPaycheck() throws Exception {
        mockMvc.perform(post("/api/agent/paycheck")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paycheckId\": 1, \"caseType\": \"SALARY_DECREASE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.caseType").value("SALARY_DECREASE"))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.nextActions").isArray());
    }

    @Test
    @DisplayName("캘린더 일정 조회 및 등록 (GET/POST /api/calendar/events)")
    void testCalendarEvents() throws Exception {
        mockMvc.perform(get("/api/calendar/events")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        CalendarEventCreateRequest createRequest = CalendarEventCreateRequest.builder()
                .title("외국인등록증 갱신 방문")
                .description("출입국관리사무소 방문")
                .startAt(LocalDateTime.of(2026, 9, 15, 14, 0))
                .endAt(LocalDateTime.of(2026, 9, 15, 16, 0))
                .build();

        mockMvc.perform(post("/api/calendar/events")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("외국인등록증 갱신 방문"));
    }

    @Test
    @DisplayName("급여 모니터링 배치 수동 실행 및 캘린더 동기화 검증 (POST /api/batch/salary-monitoring)")
    void testBatchMonitoring() throws Exception {
        mockMvc.perform(post("/api/batch/salary-monitoring?userId=1")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").isNumber())
                .andExpect(jsonPath("$.data.paychecks").isArray())
                .andExpect(jsonPath("$.data.paychecks[0].paycheckId").isNumber());

        // 배치 후 캘린더 이벤트 갱신 확인
        mockMvc.perform(get("/api/calendar/events")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'PAYCHECK')]").isNotEmpty());
    }

    @Test
    @DisplayName("문서 파일 업로드 (POST /api/documents)")
    void testUploadDocument() throws Exception {
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test_payslip.pdf",
                "application/pdf",
                "PDF_CONTENT_SAMPLE".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(mockFile)
                        .param("documentType", "PAYSLIP")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.documentType").value("PAYSLIP"));
    }

    @Test
    @DisplayName("급여 모니터링 배치 멱등성 및 CalendarEvent 테이블 매핑 규격 검증")
    void testBatchMonitoringIdempotencyAndCalendarMapping() throws Exception {
        // 1회차 배치 실행 (생성)
        mockMvc.perform(post("/api/batch/salary-monitoring?userId=1")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paychecks[0].paycheckId").isNumber());

        // 2회차 배치 중복 실행 (멱등성: UPSERT 및 updatedCount 증가 확인)
        mockMvc.perform(post("/api/batch/salary-monitoring?userId=1")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.updatedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 캘린더 이벤트 매핑 확인 (eventType=PAYCHECK, title='8월 급여 입금', status='COMPLETED', sourceType='PAYCHECK')
        mockMvc.perform(get("/api/calendar/events")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'PAYCHECK' && @.title == '8월 급여 입금')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[?(@.eventType == 'PAYCHECK' && @.title == '8월 급여 입금')].sourceType").value("PAYCHECK"));
    }

    @Test
    @DisplayName("시드 데이터 재초기화 API (POST /api/dev/reset-seed)")
    void testResetSeed() throws Exception {
        mockMvc.perform(post("/api/dev/reset-seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
