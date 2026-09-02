package com.foreigninone.backend.domain.overview;

import com.foreigninone.backend.common.exception.BusinessException;
import com.foreigninone.backend.domain.overview.repository.OverviewReadRepository;
import com.foreigninone.backend.domain.overview.service.OverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OverviewServiceTest {
    @Test
    void missingSchemaOrQueryErrorsAreNeverConvertedToEmptyHistory() {
        var repository = mock(OverviewReadRepository.class);
        when(repository.userExists(1L)).thenReturn(true);
        var failure = new BadSqlGrammarException("fixture", "SELECT", new SQLException("missing test table"));
        when(repository.findAllByUserId(1L)).thenThrow(failure);
        var service = new OverviewService(repository);
        assertThatThrownBy(() -> service.records(1L, null)).isSameAs(failure);
        assertThatThrownBy(() -> service.dashboard(1L, 2026)).isSameAs(failure);
    }

    @Test
    void invalidUserIsRejectedBeforeDatabaseAccess() {
        var repository = mock(OverviewReadRepository.class);
        var service = new OverviewService(repository);
        assertThatThrownBy(() -> service.records(0L, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void missingUserDoesNotReadAnyDomainRecords() {
        var repository = mock(OverviewReadRepository.class);
        when(repository.userExists(44L)).thenReturn(false);
        assertThatThrownBy(() -> new OverviewService(repository).records(44L, null)).isInstanceOf(BusinessException.class);
        verify(repository, never()).findAllByUserId(anyLong());
    }
}
