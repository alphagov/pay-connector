package uk.gov.pay.connector.charge.util;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FixedCorporateCardSurchargeCalculatorTest {

    private static final long CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT = 50L;
    private static final long DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT = 75L;
    private static final long DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT = 175L;

    private static ChargeEntity chargeEntity;

    @BeforeEach
    public void setUp() {
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.getCardConfigurationEntity().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
        gatewayAccountEntity.getCardConfigurationEntity().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
        gatewayAccountEntity.getCardConfigurationEntity().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT);

        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
    }

    //region Corporate Prepaid

    @Test
    void shouldGetCorporateSurchargeForPrepaidCorporateDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    void shouldNotGetCorporateSurchargeForPrepaidCorporateDebitCardWhenNoCorporatePrepaidDebitSurcharge() {
        chargeEntity.getGatewayAccount().getCardConfigurationEntity().setCorporatePrepaidDebitCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForPrepaidCorporateCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Corporate Not Prepaid

    @Test
    void shouldGetCorporateSurchargeForNotPrepaidCorporateCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    void shouldGetCorporateSurchargeForNotPrepaidCorporateDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditCardWhenNoCorporateCreditSurcharge() {
        chargeEntity.getGatewayAccount().getCardConfigurationEntity().setCorporateCreditCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidCorporateDebitCardWhenNoCorporateDebitSurcharge() {
        chargeEntity.getGatewayAccount().getCardConfigurationEntity().setCorporateDebitCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Corporate Unknown Prepaid

    @Test
    void shouldNotGetCorporateSurchargeForCorporateCreditCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForCorporateDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForCorporateCreditOrDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForCorporateCreditCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForCorporateDebitCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Prepaid

    @Test
    void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForPrepaidConsumerDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Not Prepaid

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidConsumerDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Unknown Prepaid

    @Test
    void shouldNotGetCorporateSurchargeForConsumerCreditCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForConsumerDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForConsumerCreditOrDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForConsumerCreditCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    void shouldNotGetCorporateSurchargeForConsumerDebitCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    private AuthCardDetails getAuthCardDetails(PayersCardType payersCardType, Boolean corporateCard, PayersCardPrepaidStatus payersCardPrepaidStatus) {
        return AuthCardDetailsFixture
                .anAuthCardDetails()
                .withCardType(payersCardType)
                .withCorporateCard(corporateCard)
                .withPayersCardPrepaidStatus(payersCardPrepaidStatus)
                .build();
    }

}
