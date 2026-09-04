package com.foreigninone.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.domain.exitcheck.dto.ExitCheckAnalyzeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ExitCheckApiIntegrationTest {

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
    @DisplayName("출국 정산 분석 - 서류/계좌 모두 준비된 경우 (POST /api/exit-checks/analyze)")
    void testAnalyzeExitCheckReady() throws Exception {
        ExitCheckAnalyzeRequest request = ExitCheckAnalyzeRequest.builder()
                .expectedExitDate(LocalDate.of(2027, 3, 1))
                .hasInsuranceRecord(true)
                .hasOwnAccount(true)
                .hasExitProof(true)
                .pensionDeducted(false)
                .hasRecentPayslip(true)
                .build();

        mockMvc.perform(post("/api/exit-checks/analyze")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insuranceStatus").value("READY"))
                .andExpect(jsonPath("$.data.retirementStatus").value("READY"))
                .andExpect(jsonPath("$.data.pensionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.readinessScore").value(100))
                .andExpect(jsonPath("$.data.checklist.length()").value(6))
                .andExpect(jsonPath("$.data.missingDocuments.length()").value(0));
    }

    @Test
    @DisplayName("출국 정산 분석 - 답변 미입력 시 확인/서류 필요 상태 (POST /api/exit-checks/analyze)")
    void testAnalyzeExitCheckCheckRequired() throws Exception {
        // 시드 사용자(민수)는 workStartDate(2025-03-10) 기준 이미 근속 12개월을 넘겨,
        // retirementStatus는 CHECK_REQUIRED가 아니라 서류 미제출(MISSING_DOCUMENT)로 판정된다.
        ExitCheckAnalyzeRequest request = ExitCheckAnalyzeRequest.builder()
                .expectedExitDate(LocalDate.of(2027, 3, 1))
                .build();

        mockMvc.perform(post("/api/exit-checks/analyze")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insuranceStatus").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.data.pensionStatus").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.data.retirementStatus").value("MISSING_DOCUMENT"))
                .andExpect(jsonPath("$.data.status").value("MISSING_DOCUMENT"))
                .andExpect(jsonPath("$.data.workDurationMonths").isNumber());
    }

    @Test
    @DisplayName("출국 정산 목록/단건 조회 (GET /api/exit-checks, GET /api/exit-checks/{id})")
    void testGetExitChecks() throws Exception {
        ExitCheckAnalyzeRequest request = ExitCheckAnalyzeRequest.builder()
                .expectedExitDate(LocalDate.of(2027, 3, 1))
                .hasInsuranceRecord(false)
                .build();

        mockMvc.perform(post("/api/exit-checks/analyze")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/exit-checks")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].insuranceStatus").value("MISSING_DOCUMENT"));
    }

    @Test
    @DisplayName("출국 정산 분석 시 캘린더 이벤트 동기화 검증 (출국일 및 D-30)")
    void testAnalyzeExitCheckSyncsCalendarEvents() throws Exception {
        LocalDate exitDate = LocalDate.of(2027, 5, 20);
        ExitCheckAnalyzeRequest request = ExitCheckAnalyzeRequest.builder()
                .expectedExitDate(exitDate)
                .hasInsuranceRecord(true)
                .hasOwnAccount(true)
                .hasExitProof(true)
                .pensionDeducted(false)
                .hasRecentPayslip(true)
                .build();

        mockMvc.perform(post("/api/exit-checks/analyze")
                        .header("X-Demo-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 캘린더 이벤트 조회 검증
        mockMvc.perform(get("/api/calendar/events")
                        .header("X-Demo-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'EXIT' && @.title == '예상 출국일')].startAt").value(org.hamcrest.Matchers.hasItem("2027-05-20T09:00:00")))
                .andExpect(jsonPath("$.data[?(@.eventType == 'EXIT' && @.title == '출국만기보험/퇴직금 신청 기한')].startAt").value(org.hamcrest.Matchers.hasItem("2027-04-20T09:00:00")));
    }
}
