package com.foreigninone.backend;

import com.foreigninone.backend.domain.document.entity.Document;
import com.foreigninone.backend.domain.exitcheck.entity.ExitCheck;
import com.foreigninone.backend.domain.taxcheck.entity.TaxCheck;
import jakarta.persistence.Convert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class JsonColumnMappingTest {

    @Test
    void structuredJsonFieldsUseHibernateJsonBindingInsteadOfStringConverters() throws Exception {
        assertJsonField(Document.class, "extractedData");
        assertJsonField(TaxCheck.class, "benefitSummary");
        assertJsonField(TaxCheck.class, "requiredDocuments");
        assertJsonField(ExitCheck.class, "missingDocuments");
        assertJsonField(ExitCheck.class, "checklist");
    }

    private void assertJsonField(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        assertThat(field.getAnnotation(Convert.class)).isNull();
        assertThat(field.getAnnotation(JdbcTypeCode.class))
                .isNotNull()
                .extracting(JdbcTypeCode::value)
                .isEqualTo(SqlTypes.JSON);
    }
}
