package com.foreigninone.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.document.entity.DocumentType;
import com.foreigninone.backend.domain.document.entity.OcrStatus;
import com.foreigninone.backend.domain.document.repository.DocumentRepository;
import com.foreigninone.backend.domain.paycheck.entity.Paycheck;
import com.foreigninone.backend.domain.paycheck.entity.PaycheckStatus;
import com.foreigninone.backend.domain.paycheck.repository.PaycheckRepository;
import com.foreigninone.backend.domain.taxcheck.entity.TaxCheck;
import com.foreigninone.backend.domain.taxcheck.repository.TaxCheckRepository;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import com.foreigninone.backend.init.DataInitializer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class TaxCheckApiIntegrationTest {
    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private PaycheckRepository paychecks;
    @Autowired private DocumentRepository documents;
    @Autowired private TaxCheckRepository taxChecks;
    @Autowired private EntityManager entityManager;
    @Autowired private DataInitializer dataInitializer;
    private MockMvc mvc;
    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        user = newUser("TaxCheck test");
        other = newUser("Other user");
        pay(user, "2025-12", new BigDecimal("999999"));
        pay(user, "2026-01", new BigDecimal("2600000"));
        pay(user, "2026-02", null);
        pay(other, "2026-01", new BigDecimal("777777"));
        entityManager.flush();
    }

    @Test
    void analyzePersistsAndRoundTripsSnapshotWithoutNetToGross() throws Exception {
        JsonNode created = analyze(user.getUserId(), confirmed("30000000", "2000000"));
        long id = created.path("taxCheckId").asLong();
        assertThat(id).isPositive();
        assertThat(created.path("paySummary").path("totalReceivedPay").decimalValue()).isEqualByComparingTo("2600000");
        assertThat(created.path("paySummary").path("recordedMonths").asInt()).isEqualTo(2);
        assertThat(created.path("paySummary").path("amountKnownMonths").asInt()).isEqualTo(1);
        assertThat(created.path("result").path("flatTaxEstimate").decimalValue()).isEqualByComparingTo("6080000");
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/tax-checks/{id}", id).header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.generalTaxEstimate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.result.taxDifference").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.result.calculation.eligibilityConfirmed").value(false))
                .andExpect(jsonPath("$.data.result.flatTaxEstimate").value(6080000.0));
        TaxCheck saved = taxChecks.findById(id).orElseThrow();
        assertThat(saved.getAnnualIncome()).isEqualByComparingTo("30000000");
        assertThat(saved.getFlatTaxEstimate()).isEqualByComparingTo("6080000");
    }

    @Test
    void analyzedTaxCheckAppearsInRecords() throws Exception {
        long id = analyze(user.getUserId(), confirmed("30000000", "2000000"))
                .path("taxCheckId").asLong();
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/records").param("type", "TAX_CHECK")
                        .header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].recordKey").value("TAX_CHECK:" + id))
                .andExpect(jsonPath("$.data.items[0].sourceId").value(id))
                .andExpect(jsonPath("$.data.items[0].type").value("TAX_CHECK"))
                .andExpect(jsonPath("$.data.counts.taxCheck").value(1));
    }

    @Test
    void missingIncomeDoesNotUsePaycheckOrOcr() throws Exception {
        Document document = document(user, DocumentType.TAX_DOCUMENT);
        JsonNode created = analyze(user.getUserId(), "{\"taxYear\":2026,\"taxDocumentId\":" + document.getDocumentId() + "}");
        assertThat(created.path("result").path("flatTaxEstimate").isNull()).isTrue();
        assertThat(created.path("result").path("annualIncome").isNull()).isTrue();
        assertThat(created.path("result").path("calculation").path("missingFields").toString()).contains("INCOME_CONFIRMATION_REQUIRED");
    }

    @Test
    void simulationDoesNotPersistOrMutateOriginalOrRefreshPaycheck() throws Exception {
        JsonNode created = analyze(user.getUserId(), confirmed("30000000", "2000000"));
        long id = created.path("taxCheckId").asLong();
        entityManager.flush();
        entityManager.clear();
        TaxCheck original = taxChecks.findById(id).orElseThrow();
        String originalSnapshot = objectMapper.writeValueAsString(original.getBenefitSummary());
        var originalUpdatedAt = original.getUpdatedAt();
        long count = taxChecks.count();
        Paycheck changedPay = paychecks.findByUser_UserIdAndPayPeriod(user.getUserId(), "2026-01").orElseThrow();
        changedPay.setActualAmount(new BigDecimal("9000000"));
        entityManager.flush();

        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"income\":{\"annualIncome\":40000000,\"nonTaxableIncome\":2000000,\"confirmed\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulation").value(true))
                .andExpect(jsonPath("$.data.taxCheckId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.sourceTaxCheckId").value(id))
                .andExpect(jsonPath("$.data.result.flatTaxEstimate").value(7980000.0))
                .andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(2600000.0));
        entityManager.flush();
        entityManager.clear();
        TaxCheck after = taxChecks.findById(id).orElseThrow();
        assertThat(taxChecks.count()).isEqualTo(count);
        assertThat(after.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(objectMapper.writeValueAsString(after.getBenefitSummary())).isEqualTo(originalSnapshot);
        assertThat(after.getFlatTaxEstimate()).isEqualByComparingTo("6080000");
        mvc.perform(get("/api/tax-checks/{id}", id).header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(2600000.0));
    }

    @Test
    void conditionSimulationInheritsIncomeButDoesNotDisqualifyDeductions() throws Exception {
        long id = analyze(user.getUserId(), confirmed("30000000", "0")).path("taxCheckId").asLong();
        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conditions\":{\"usesDeductions\":true}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.flatTaxEstimate").value(5700000.0))
                .andExpect(jsonPath("$.data.result.cards[2].status").value("REVIEW_REQUIRED"));
    }

    @Test
    void emptySimulationKeepsOriginalAndIncompleteIncomeGroupClearsCalculation() throws Exception {
        long id = analyze(user.getUserId(), confirmed("30000000", "0")).path("taxCheckId").asLong();
        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.flatTaxEstimate").value(5700000.0));
        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"income\":{\"confirmed\":false}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result.flatTaxEstimate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void crossUserDetailAndSimulationReturnNotFound() throws Exception {
        long id = analyze(user.getUserId(), confirmed("30000000", "0")).path("taxCheckId").asLong();
        mvc.perform(get("/api/tax-checks/{id}", id).header("X-Demo-User-Id", other.getUserId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAXCHECK_NOT_FOUND"));
        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-Demo-User-Id", other.getUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAXCHECK_NOT_FOUND"));
    }

    @Test
    void listFiltersOwnerAndYear() throws Exception {
        analyze(user.getUserId(), "{\"taxYear\":2025}");
        analyze(user.getUserId(), "{\"taxYear\":2026}");
        analyze(other.getUserId(), "{\"taxYear\":2026}");
        mvc.perform(get("/api/tax-checks?taxYear=2026").header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].taxYear").value(2026));
        mvc.perform(get("/api/tax-checks").header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void documentOwnershipAndTypeChecked() throws Exception {
        Document foreignDocument = document(other, DocumentType.TAX_DOCUMENT);
        Document wrongType = document(user, DocumentType.BANK_RECEIPT);
        mvc.perform(post("/api/tax-checks/analyze").header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxYear\":2026,\"taxDocumentId\":" + foreignDocument.getDocumentId() + "}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(post("/api/tax-checks/analyze").header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxYear\":2026,\"taxDocumentId\":" + wrongType.getDocumentId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidInputsFailWithoutSaving() throws Exception {
        long before = taxChecks.count();
        for (String body : new String[]{"{}", "{\"taxYear\":1999}", "{\"taxYear\":9999}",
                "{\"taxYear\":2026,\"taxDocumentId\":0}",
                confirmed("-1", "0"), confirmed("0.001", "0"), confirmed("9999999999999.99", "0.01"),
                "{\"taxYear\":2026,\"income\":{\"annualIncome\":\"abc\"}}", "{broken"}) {
            mvc.perform(post("/api/tax-checks/analyze").header("X-Demo-User-Id", user.getUserId())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(taxChecks.count()).isEqualTo(before);
    }

    @Test
    void missingIdsAndInvalidQueryAreHandled() throws Exception {
        mvc.perform(get("/api/tax-checks/9223372036854775807").header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tax-checks?taxYear=abc").header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tax-checks?userId=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tax-checks?userId=9223372036854775807")).andExpect(status().isNotFound());
    }

    @Test
    void unsupportedSnapshotVersionRequestsReanalysis() throws Exception {
        long id = analyze(user.getUserId(), confirmed("30000000", "0")).path("taxCheckId").asLong();
        taxChecks.findById(id).orElseThrow().getBenefitSummary().put("schemaVersion", 99);
        entityManager.flush();
        entityManager.clear();
        mvc.perform(post("/api/tax-checks/{id}/simulate", id).header("X-Demo-User-Id", user.getUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAXCHECK_SNAPSHOT_INVALID"));
    }

    @Test
    void zeroAndNotWorkingAreDifferentFromMissing() throws Exception {
        user.setEmploymentStatus("NOT_WORKING");
        JsonNode missing = analyze(user.getUserId(), "{\"taxYear\":2025}");
        assertThat(missing.path("result").path("flatTaxEstimate").isNull()).isTrue();
        JsonNode zero = analyze(user.getUserId(), confirmed("0", "0"));
        assertThat(zero.path("result").path("flatTaxEstimate").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void seedResetRespectsNewTaxCheckForeignKeys() throws Exception {
        Document document = document(user, DocumentType.TAX_DOCUMENT);
        analyze(user.getUserId(), "{\"taxYear\":2026,\"taxDocumentId\":" + document.getDocumentId() + "}");
        entityManager.flush();
        entityManager.clear();
        // Test H2 only; never call the dev seed-reset HTTP endpoint against a real database.
        assertThatCode(() -> dataInitializer.resetSeedData()).doesNotThrowAnyException();
        entityManager.flush();
        assertThat(taxChecks.count()).isZero();
    }

    @Test
    void analyzeSyncsCalendarTaxEvent() throws Exception {
        JsonNode created = analyze(user.getUserId(), confirmed("30000000", "2000000"));
        long taxCheckId = created.path("taxCheckId").asLong();

        mvc.perform(get("/api/calendar/events").header("X-Demo-User-Id", user.getUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'TAX' && @.title == '2026년 귀속 연말정산 서류 제출 기한')].startAt").value(org.hamcrest.Matchers.hasItem("2027-01-25T09:00:00")))
                .andExpect(jsonPath("$.data[?(@.eventType == 'TAX' && @.title == '2026년 귀속 연말정산 서류 제출 기한')].endAt").value(org.hamcrest.Matchers.hasItem("2027-01-25T18:00:00")))
                .andExpect(jsonPath("$.data[?(@.eventType == 'TAX' && @.title == '2026년 귀속 연말정산 서류 제출 기한')].sourceId").value(org.hamcrest.Matchers.hasItem((int) taxCheckId)))
                .andExpect(jsonPath("$.data[?(@.eventType == 'TAX' && @.title == '2026년 귀속 연말정산 서류 제출 기한')].sourceType").value(org.hamcrest.Matchers.hasItem("SYSTEM")));
    }

    private JsonNode analyze(Long userId, String body) throws Exception {
        String json = mvc.perform(post("/api/tax-checks/analyze").header("X-Demo-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data");
    }

    private String confirmed(String gross, String nonTaxable) {
        return "{\"taxYear\":2026,\"income\":{\"annualIncome\":" + gross
                + ",\"nonTaxableIncome\":" + nonTaxable + ",\"confirmed\":true}}";
    }

    private User newUser(String name) {
        return users.save(User.builder().name(name).phone(UUID.randomUUID().toString().substring(0, 20))
                .entryDate(LocalDate.of(2025, 1, 1)).employmentStatus("WORKING").build());
    }

    private void pay(User owner, String period, BigDecimal actual) {
        paychecks.save(Paycheck.builder().user(owner).payPeriod(period).actualAmount(actual)
                .contractAmount(new BigDecimal("9000000")).payslipAmount(new BigDecimal("8000000"))
                .status(PaycheckStatus.INSUFFICIENT_DATA).build());
    }

    private Document document(User owner, DocumentType type) {
        return documents.save(Document.builder().user(owner).documentType(type).ocrStatus(OcrStatus.SUCCESS)
                .extractedData(Map.of("annualIncome", 99999999, "nonTaxableIncome", 0, "confirmed", true)).build());
    }
}
