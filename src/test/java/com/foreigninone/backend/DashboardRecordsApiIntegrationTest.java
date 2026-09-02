package com.foreigninone.backend;

import com.foreigninone.backend.domain.overview.repository.OverviewReadRepository;
import com.foreigninone.backend.domain.overview.service.OverviewService;
import com.foreigninone.backend.domain.user.entity.User;
import com.foreigninone.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:overview_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "paycycle.batch.cron=-"
})
@Sql(scripts = "/dashboard-records-fixture-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class DashboardRecordsApiIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private DataSource dataSource;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private OverviewReadRepository repository;
    @Autowired private OverviewService service;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private long userId;
    private long otherId;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 9, 0);

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = user("Overview synthetic test");
        otherId = user("Other synthetic test");
        pay(10001, userId, "2025-12", "999999", "2026-01-01T10:00:00");
        pay(10002, userId, "2026-01", "2600000.10", "2026-02-01T10:00:00");
        pay(10003, userId, "2026-02", null, "2026-03-01T10:00:00");
        pay(10004, userId, "2026-03", "0.00", "2026-04-01T10:00:00");
        pay(10005, otherId, "2026-01", "777777", "2026-09-01T12:00:00");
        tax(10001, userId, 2025, "2026-08-20T10:00:00");
        tax(10002, userId, 2026, "2026-08-31T10:00:00");
        tax(10003, otherId, 2026, "2026-09-01T12:00:00");
        exit(10001, userId, "2026-08-30T10:00:00");
        exit(10002, otherId, "2026-09-01T12:00:00");
    }

    @Test
    void dashboardUsesAnnualActualDepositsAndAllYearLatestSummaries() throws Exception {
        mvc.perform(get("/api/dashboard").param("year", "2026").header("X-Demo-User-Id", userId))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(2600000.10))
                .andExpect(jsonPath("$.data.paySummary.recordedMonths").value(3))
                .andExpect(jsonPath("$.data.paySummary.amountKnownMonths").value(2))
                .andExpect(jsonPath("$.data.paySummary.missingAmountPeriods[0]").value("2026-02"))
                .andExpect(jsonPath("$.data.latestPaycheck.recordKey").value("PAYCHECK:10004"))
                .andExpect(jsonPath("$.data.latestTaxCheck.recordKey").value("TAX_CHECK:10002"))
                .andExpect(jsonPath("$.data.latestExitCheck.readinessScore").value(0))
                .andExpect(jsonPath("$.data.recentRecords.length()").value(3));
        mvc.perform(get("/api/dashboard").param("year", "2025").header("X-Demo-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(999999))
                .andExpect(jsonPath("$.data.latestTaxCheck.taxYear").value(2026));
    }

    @Test
    void recordsAreOwnerScopedSortedAndDoNotCollapseCrossTypeIds() throws Exception {
        mvc.perform(get("/api/records").header("X-Demo-User-Id", userId))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items.length()").value(7))
                .andExpect(jsonPath("$.data.items[0].recordKey").value("TAX_CHECK:10002"))
                .andExpect(jsonPath("$.data.items[0].recordedAt").value("2026-08-31T10:00:00"))
                .andExpect(jsonPath("$.data.items[1].recordKey").value("EXIT_CHECK:10001"))
                .andExpect(jsonPath("$.data.items[2].recordKey").value("TAX_CHECK:10001"))
                .andExpect(jsonPath("$.data.counts.all").value(7))
                .andExpect(jsonPath("$.data.counts.paycheck").value(4))
                .andExpect(jsonPath("$.data.counts.taxCheck").value(2))
                .andExpect(jsonPath("$.data.counts.exitCheck").value(1));
    }

    @Test
    void filterDoesNotChangeTabCounts() throws Exception {
        mvc.perform(get("/api/records").param("type", "TAX_CHECK").header("X-Demo-User-Id", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("TAX_CHECK"))
                .andExpect(jsonPath("$.data.counts.all").value(7));
        mvc.perform(get("/api/records").param("type", "EXIT_CHECK").header("X-Demo-User-Id", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void emptyUserDoesNotReceiveSeedOrOtherUsersRecords() throws Exception {
        long emptyUser = user("Empty synthetic test");
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(emptyUser)).param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(nullValue()))
                .andExpect(jsonPath("$.data.paySummary.recordedMonths").value(0))
                .andExpect(jsonPath("$.data.latestPaycheck").value(nullValue()))
                .andExpect(jsonPath("$.data.latestTaxCheck").value(nullValue()))
                .andExpect(jsonPath("$.data.latestExitCheck").value(nullValue()))
                .andExpect(jsonPath("$.data.recentRecords.length()").value(0));
        mvc.perform(get("/api/records").param("userId", Long.toString(emptyUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.counts.all").value(0));
    }

    @Test
    void knownZeroAndAllUnknownStayDifferent() throws Exception {
        long zeroUser = user("Zero synthetic test");
        long unknownUser = user("Unknown synthetic test");
        pay(10006, zeroUser, "2026-01", "0.00", null);
        pay(10007, unknownUser, "2026-01", null, null);
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(zeroUser)).param("year", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(0))
                .andExpect(jsonPath("$.data.paySummary.amountKnownMonths").value(1));
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(unknownUser)).param("year", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(nullValue()))
                .andExpect(jsonPath("$.data.paySummary.recordedMonths").value(1))
                .andExpect(jsonPath("$.data.paySummary.amountKnownMonths").value(0));
    }

    @Test
    void missingAnalysisTimeUsesCreationTimeWithoutPretendingAnalyzed() {
        jdbc.update("UPDATE tax_checks SET analyzed_at = NULL WHERE tax_check_id = ?", 10002L);
        var item = repository.findAllByUserId(userId).stream()
                .filter(record -> record.recordKey().equals("TAX_CHECK:10002")).findFirst().orElseThrow();
        assertThat(item.analyzedAt()).isNull();
        assertThat(item.recordedAt()).isEqualTo(CREATED);
    }

    @Test
    void getDoesNotInsertUpdateResetOrRecalculateAnything() throws Exception {
        Map<String, Object> before = snapshot();
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(userId))).andExpect(status().isOk());
        mvc.perform(get("/api/records").param("userId", Long.toString(userId))).andExpect(status().isOk());
        assertThat(snapshot()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void changedPayIsReflectedWithoutChangingStoredTaxSummary() throws Exception {
        var before = jdbc.queryForMap("SELECT analysis_summary, analyzed_at, updated_at FROM tax_checks WHERE tax_check_id = ?", 10002L);
        jdbc.update("UPDATE paychecks SET actual_amount = ? WHERE paycheck_id = ?", new BigDecimal("3000000.25"), 10002L);
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(userId)).param("year", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paySummary.totalReceivedPay").value(3000000.25));
        assertThat(jdbc.queryForMap("SELECT analysis_summary, analyzed_at, updated_at FROM tax_checks WHERE tax_check_id = ?", 10002L))
                .isEqualTo(before);
    }

    @Test
    void malformedOrOutOfRangeRequestsAreBadRequests() throws Exception {
        for (String query : new String[]{"year=abc", "year=1999", "year=9999", "userId=0", "userId=-1", "userId=abc"}) {
            mvc.perform(get("/api/dashboard?" + query)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/records?type=WRONG")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/records?userId=0")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserIsNotAnEmptySuccessfulResponse() throws Exception {
        for (String path : new String[]{"/api/dashboard", "/api/records"}) {
            mvc.perform(get(path).param("userId", Long.toString(Long.MAX_VALUE)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Test
    void currentKoreanYearIsDefaultAndDemoSelectionKeepsExistingPriority() throws Exception {
        mvc.perform(get("/api/dashboard").header("X-Demo-User-Id", otherId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear()))
                .andExpect(jsonPath("$.data.latestTaxCheck.sourceId").value(10002));
        mvc.perform(get("/api/records").param("userId", Long.toString(otherId))
                        .header("X-Demo-User-Id", userId).header("X-User-Id", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.all").value(3));
        // These selectable IDs are a demo compatibility contract, NOT an authentication test.
    }

    @Test
    void corruptStoredPeriodIsServerErrorNotInventedZero() throws Exception {
        jdbc.update("UPDATE paychecks SET pay_period = '2026-13' WHERE paycheck_id = ?", 10002L);
        mvc.perform(get("/api/dashboard").param("userId", Long.toString(userId)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void serviceDeclaresReadOnlyTransactions() {
        assertThat(OverviewService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(service).isNotNull();
    }

    private long user(String name) {
        var user = users.save(User.builder().name(name).phone(UUID.randomUUID().toString().substring(0, 20))
                .employmentStatus("NOT_WORKING").build());
        entityManager.flush();
        return user.getUserId();
    }

    private void pay(long id, long owner, String period, String actual, String analyzed) {
        jdbc.update("""
                INSERT INTO paychecks (paycheck_id, user_id, pay_period, actual_amount, contract_amount,
                  payslip_amount, status, analysis_summary, next_action, analyzed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'INSUFFICIENT_DATA', 'Saved pay summary', 'Check payslip', ?, ?, ?)
                """, id, owner, period, actual == null ? null : new BigDecimal(actual), new BigDecimal("9000000"),
                new BigDecimal("8000000"), time(analyzed), CREATED, CREATED);
    }

    private void tax(long id, long owner, int year, String analyzed) {
        jdbc.update("""
                INSERT INTO tax_checks (tax_check_id, user_id, tax_year, status, analysis_summary, next_action,
                  analyzed_at, created_at, updated_at) VALUES (?, ?, ?, 'REVIEW_REQUIRED', 'Saved tax summary',
                  'Check tax evidence', ?, ?, ?)
                """, id, owner, year, time(analyzed), CREATED, CREATED);
    }

    private void exit(long id, long owner, String analyzed) {
        jdbc.update("""
                INSERT INTO exit_checks (exit_check_id, user_id, expected_exit_date, readiness_score, status,
                  analysis_summary, next_action, analyzed_at, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'CHECK_REQUIRED', 'Saved exit summary', 'Check exit evidence', ?, ?, ?)
                """, id, owner, LocalDate.of(2027, 3, 1), time(analyzed), CREATED, CREATED);
    }

    private LocalDateTime time(String raw) {
        return raw == null ? null : LocalDateTime.parse(raw);
    }

    private Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        // Fixed test-only table names, never request-controlled SQL identifiers.
        for (String table : new String[]{"users", "paychecks", "tax_checks", "exit_checks", "documents", "calendar_events", "bank_transactions"}) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table));
        }
        return result;
    }
}
