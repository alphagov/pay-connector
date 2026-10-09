package uk.gov.pay.connector.charge.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.FeeSubType;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.FIXED;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.INTERCHANGE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.SCHEME_FEE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.VARIABLE;
import static uk.gov.pay.connector.charge.model.domain.FeeType.FRAUD_PROTECTION;
import static uk.gov.pay.connector.charge.model.domain.FeeType.GATEWAY;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.charge.util.AdyenFeeCalculator.generateFeeList;
import static uk.gov.pay.connector.charge.util.AdyenFeeCalculator.hasAllExpectedFeeRecordsForAdyen;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferType.FIXED_COMMISSION;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferType.UNSUPPORTED;

class AdyenFeeCalculatorTest {

    @Test
    void shouldSplitFixedCommissionIntoThreeFeeRecords_WhenCommissionIsGreaterThanTotalOfGatewayAndFraudProtectionFee() {
        long gatewayFee = 5;
        long fraudProtectionFee = 15;
        long fixedCommission = 40;

        List<Fee> result = generateFeeList(FIXED_COMMISSION, fixedCommission, gatewayFee, fraudProtectionFee);

        assertEquals(3, result.size());
        assertEquals(Fee.of(GATEWAY, 5L), result.get(0));
        assertEquals(Fee.of(FRAUD_PROTECTION, 15L), result.get(1));
        assertEquals(Fee.of(TRANSACTION, 20L, FIXED), result.get(2));
    }

    @Test
    void shouldSplitFixedCommissionIntoTwoRecords_WhenCommissionIsEqualToTotalOfGatewayAndFraudProtectionFee() {
        long gatewayFee = 5;
        long fraudProtectionFee = 15;
        long fixedCommission = 20;

        List<Fee> result = generateFeeList(FIXED_COMMISSION, fixedCommission, gatewayFee, fraudProtectionFee);

        assertEquals(2, result.size());
        assertEquals(Fee.of(GATEWAY, 5L), result.get(0));
        assertEquals(Fee.of(FRAUD_PROTECTION, 15L), result.get(1));
    }

    @ParameterizedTest
    @MethodSource("expectedFeeSubTypesForFeeTransferType")
    void shouldGenerateFeeForDifferentFeeTypes(AdyenFeeTransferType adyenFeeTransferType, FeeSubType expectedFeeSubType) {
        long feeAmount = 40;

        List<Fee> result = generateFeeList(adyenFeeTransferType, feeAmount, 0, 0);

        assertEquals(1, result.size());
        assertEquals(Fee.of(TRANSACTION, feeAmount, expectedFeeSubType), result.get(0));
    }

    private static Stream<Arguments> expectedFeeSubTypesForFeeTransferType() {
        return Stream.of(
                Arguments.of(AdyenFeeTransferType.VARIABLE_COMMISSION, FeeSubType.VARIABLE),
                Arguments.of(AdyenFeeTransferType.INTERCHANGE, FeeSubType.INTERCHANGE),
                Arguments.of(AdyenFeeTransferType.SCHEME_FEE, FeeSubType.SCHEME_FEE)
        );
    }

    @Test
    void shouldThrowIllegalArgumentError_WhenFixedCommissionIsLessThanTotalOfGatewayAndFraudProtectionFee() {
        long gatewayFee = 5;
        long fraudProtectionFee = 15;
        long fixedCommission = 1;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                generateFeeList(FIXED_COMMISSION, fixedCommission, gatewayFee, fraudProtectionFee));

        assertEquals("Adyen's fixed commission for charge is less than the sum of gateway and fraud protection fees", exception.getMessage());
    }


    @Test
    void shouldThrowExceptionForUnsupportedAdyenFeeTransferType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                generateFeeList(UNSUPPORTED, 0, 0, 0));

        assertEquals("Cannot calculate fees for UNSUPPORTED", exception.getMessage());
    }
    
    @Nested
    @DisplayName("hasAllExpectedFeeRecordsForAdyen")
    class HasAllExpectedFeeRecordsForAdyen {
        
        @ParameterizedTest(name = "{0}")
        @MethodSource("validAdyenChargesWithExpectedFeeRecords")
        void shouldReportAllExpectedAdyenFeeRecordsAsAvailableForEachValidTransactionCombination(String testName,
                                                                                                 List<Fee> fees) {
            assertTrue(hasAllExpectedFeeRecordsForAdyen(fees));
        }

        private static Stream<Arguments> validAdyenChargesWithExpectedFeeRecords() {

            return Stream.of(
                    Arguments.of("fixed and variable",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, FIXED),
                                    Fee.of(TRANSACTION, 3L, VARIABLE)
                            )),
                    Arguments.of("fixed, interchange and scheme fee",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, FIXED),
                                    Fee.of(TRANSACTION, 2L, INTERCHANGE),
                                    Fee.of(TRANSACTION, 1L, SCHEME_FEE))
                    ),
                    Arguments.of("variable",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 3L, VARIABLE))
                    ),
                    Arguments.of("only use transaction fees with a valid sub type",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 10L),
                                    Fee.of(TRANSACTION, 7L, VARIABLE)
                            )
                    )
            );
        }


        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAdyenChargesWithMissingOrUnsupportedFeeRecords")
        void shouldReportAdyenFeeRecordsAsUnavailableForInvalidFeeCombinations(String testName,
                                                                               List<Fee> fees) {
            assertFalse(hasAllExpectedFeeRecordsForAdyen(fees));
        }

        private static Stream<Arguments> invalidAdyenChargesWithMissingOrUnsupportedFeeRecords() {

            return Stream.of(
                    Arguments.of("missing variable for fixed transaction fee",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, FIXED)
                            )
                    ),
                    Arguments.of(" missing gateway fee type",
                            List.of(
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, VARIABLE)
                            )
                    ),
                    Arguments.of("missing fraud protection fee type",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(TRANSACTION, 7L, VARIABLE)
                            )
                    ),
                    Arguments.of("missing fixed or variable fee for interchange and scheme fee",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, INTERCHANGE),
                                    Fee.of(TRANSACTION, 7L, SCHEME_FEE)
                            )
                    ),
                    Arguments.of("missing transaction fee records",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L)
                            )
                    ),
                    Arguments.of("fixed, variable and interchange",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(TRANSACTION, 7L, FIXED),
                                    Fee.of(TRANSACTION, 7L, VARIABLE),
                                    Fee.of(TRANSACTION, 7L, INTERCHANGE)
                            )
                    ),
                    Arguments.of("fee type null",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(null, 7L, VARIABLE)
                            )
                    ),
                    Arguments.of("fee type null",
                            List.of(
                                    Fee.of(GATEWAY, 5L),
                                    Fee.of(FRAUD_PROTECTION, 1L),
                                    Fee.of(null, 7L, VARIABLE)
                            )
                    )
            );
        }

        @Test
        void shouldReturnFalseWhenFeeListIsNullOrEmpty() {
            assertFalse(hasAllExpectedFeeRecordsForAdyen(null));
            assertFalse(hasAllExpectedFeeRecordsForAdyen(List.of()));
            assertFalse(hasAllExpectedFeeRecordsForAdyen(Collections.singletonList(null)));
        }
    }
}
