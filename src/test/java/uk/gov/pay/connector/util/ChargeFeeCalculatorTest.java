package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.fee.model.Fee;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.FIXED;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.SCHEME_FEE;
import static uk.gov.pay.connector.charge.model.domain.FeeType.FRAUD_PROTECTION;
import static uk.gov.pay.connector.charge.model.domain.FeeType.GATEWAY;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.fee.model.Fee.of;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.util.ChargeFeeCalculator.calculateFeeAmount;
import static uk.gov.pay.connector.util.ChargeFeeCalculator.calculateNetAmount;

class ChargeFeeCalculatorTest {

    @Test
    void calculateFeeAmountShouldReturnEmptyFeeAmountWhenThereAreNoFees() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CAPTURED)
                .build();

        assertThat(calculateFeeAmount(charge).isEmpty());
    }

    @Test
    void calculateFeeAmountShouldSumUpFeesIgnoringDuplicateTypeAndSubTypeCombinations() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withFee(of(TRANSACTION, 100L, FIXED))
                .withFee(of(TRANSACTION, 50L, FIXED))
                .withFee(of(TRANSACTION, 10L, SCHEME_FEE))
                .withFee(of(RADAR, 10L))
                .withFee(of(RADAR, 20L))
                .withFee(of(THREE_D_S, 20L))
                .withStatus(CAPTURED)
                .build();

        Optional<Long> netAmount = calculateFeeAmount(charge);

        assertThat(netAmount).isPresent();
        assertThat(netAmount).hasValue(140L);
    }

    @Test
    void getFeeAmountShouldReturnRightAmountOfFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(CAPTURED).build();

        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        
        assertTrue(totalFee.isPresent());
        assertEquals(130L, totalFee.get().longValue());
    }

    @Test
    void calculateNetAmountShouldReturnEmptyOptionalWhenThereAreNoFees() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withStatus(CREATED)
                .build();

        Optional<Long> netAmount = calculateNetAmount(charge);

        assertThat(netAmount).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("netAmountScenarios")
    void calculateNetAmountShouldReturnNetAmountForFees(String TestName, ChargeStatus chargeStatus,
                                                  Long corporateSurcharge, Long expectedNet) {

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withStatus(chargeStatus)
                .withCorporateSurcharge(corporateSurcharge)
                .withFee(of(TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .build();

        Optional<Long> netAmount = calculateNetAmount(charge);

        assertThat(netAmount).hasValue(expectedNet);
    }

    private static Stream<Arguments> netAmountScenarios() {
        return Stream.of(
                Arguments.of("Successful payment without Corporate Surcharge", CAPTURED, null, 870L),
                Arguments.of("Successful payment includes Corporate Surcharge", CAPTURED, 250L, 1120L),
                Arguments.of("non-successful payment returns negative fee total", AUTHORISATION_REJECTED, 250L, -130L)
        );
    }

    private static ChargeEntityFixture adyenCharge() {
        return ChargeEntityFixture.aValidChargeEntity()
                .withPaymentProvider(ADYEN.getName())
                .withAmount(1000L)
                .withFee(of(GATEWAY, 5L))
                .withFee(of(FRAUD_PROTECTION, 1L))
                .withStatus(CAPTURED);
    }
}
