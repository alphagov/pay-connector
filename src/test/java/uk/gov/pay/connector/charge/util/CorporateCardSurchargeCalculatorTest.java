package uk.gov.pay.connector.charge.util;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CorporateCardSurchargeCalculatorTest {

    @Test
    void getCorporateCardSurchargeFor_shouldReturnEmptyOptionalWhenSurchargeIsZero() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture
                .anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT_OR_DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.UNKNOWN)
                .build();
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .build();

        Optional<Long> result = CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor(authCardDetails, chargeEntity);

        assertThat(result, is(Optional.empty()));
    }

    @Test
    void getCorporateCardSurchargeFor_shouldReturnLongOptionalWhenSurchargeIsNonZero() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture
                .anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.getCardConfigurationEntity().setCorporateCreditCardSurchargeAmount(100L);
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        Optional<Long> result = CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor(authCardDetails, chargeEntity);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(100L));
    }

    @Test
    void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().get();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void shouldCalculateTotalAmountForCorporateSurchargeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    void shouldCalculateTotalAmountForCorporateSurchargeIsZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(0L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }
}
