package uk.gov.pay.connector.charge.model.domain;


import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeeTypeConverterTest {
    
    private FeeTypeConverter feeTypeConverter = new FeeTypeConverter();
    
    @Test
    void shouldReturnNullForDatabaseColumnValueFromNull() {
        String databaseColumn = feeTypeConverter.convertToDatabaseColumn(null);
        assertThat(databaseColumn, is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource
    void shouldReturnCorrectFeeTypeValueForDatabaseColumnFromFeeType(FeeType feeType) {
        String databaseColumn = feeTypeConverter.convertToDatabaseColumn(feeType);

        assertThat(databaseColumn, is(feeType.getName()));
    }

    @Test
    void shouldReturnNullForNullString() {
        FeeType feeType = feeTypeConverter.convertToEntityAttribute(null);
        assertThat(feeType, is(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"radar", "three_ds", "transaction" })
    void shouldReturnCorrectFeeTypeForCorrespondingFeeType(String feeTypeValue) {
        FeeType feeType = feeTypeConverter.convertToEntityAttribute(feeTypeValue);
        assertThat(feeType.getName(), is(feeTypeValue));
    }
    
    @Test
    void shouldThrowIllegalArgumentExceptionForUnrecognizedFeeType() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> feeTypeConverter.convertToEntityAttribute("unknown"));
        assertThat(thrown.getMessage(), Matchers.is("fee type not recognized: unknown"));
    }
}
