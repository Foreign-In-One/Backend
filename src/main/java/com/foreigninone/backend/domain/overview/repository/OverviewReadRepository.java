package com.foreigninone.backend.domain.overview.repository;

import com.foreigninone.backend.domain.overview.dto.RecordSummary;
import com.foreigninone.backend.domain.overview.dto.RecordType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Reads existing domain tables; deliberately has no DDL or insert/update/delete path. */
@Repository
public class OverviewReadRepository {
    private final JdbcTemplate jdbc;

    public OverviewReadRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public boolean userExists(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Long.class, userId);
        return count != null && count > 0;
    }

    public List<RecordSummary> findAllByUserId(long userId) {
        // Java dependencies on unmerged TaxCheck/ExitCheck entities are not required.
        // Their real tables ARE required at runtime: do not replace SQL failures with empty results.
        var records = new ArrayList<RecordSummary>();
        records.addAll(jdbc.query("""
                SELECT paycheck_id, pay_period, actual_amount, status,
                       analysis_summary, next_action, analyzed_at, created_at
                FROM paychecks WHERE user_id = ?
                """, (rs, row) -> summary(rs, RecordType.PAYCHECK, rs.getLong("paycheck_id")), userId));
        records.addAll(jdbc.query("""
                SELECT tax_check_id, tax_year, status,
                       analysis_summary, next_action, analyzed_at, created_at
                FROM tax_checks WHERE user_id = ?
                """, (rs, row) -> summary(rs, RecordType.TAX_CHECK, rs.getLong("tax_check_id")), userId));
        records.addAll(jdbc.query("""
                SELECT exit_check_id, expected_exit_date, readiness_score, status,
                       analysis_summary, next_action, analyzed_at, created_at
                FROM exit_checks WHERE user_id = ?
                """, (rs, row) -> summary(rs, RecordType.EXIT_CHECK, rs.getLong("exit_check_id")), userId));
        return List.copyOf(records);
    }

    private RecordSummary summary(ResultSet rs, RecordType type, long id) throws SQLException {
        LocalDateTime analyzedAt = dateTime(rs, "analyzed_at");
        LocalDateTime recordedAt = analyzedAt != null ? analyzedAt : dateTime(rs, "created_at");
        var exitDate = type == RecordType.EXIT_CHECK ? rs.getDate("expected_exit_date") : null;
        return new RecordSummary(type.name() + ":" + id, type, id, recordedAt, analyzedAt,
                rs.getString("status"), rs.getString("analysis_summary"), rs.getString("next_action"),
                type == RecordType.PAYCHECK ? rs.getString("pay_period") : null,
                type == RecordType.TAX_CHECK ? rs.getObject("tax_year", Integer.class) : null,
                exitDate == null ? null : exitDate.toLocalDate(),
                type == RecordType.PAYCHECK ? rs.getBigDecimal("actual_amount") : null,
                type == RecordType.EXIT_CHECK ? rs.getObject("readiness_score", Integer.class) : null);
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
