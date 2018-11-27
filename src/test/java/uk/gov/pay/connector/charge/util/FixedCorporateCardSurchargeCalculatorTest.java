package uk.gov.pay.connector.charge.util;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FixedCorporateCardSurchargeCalculatorTest {

    private static final long CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT = 50L;
    private static final long CREDIT_CARD_PREPAID_SURCHARGE_AMOUNT = 150L;
    private static final long DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT = 75L;
    private static final long DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT = 175L;

    private static ChargeEntity chargeEntity;

    @Before
    public void setUp() {
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.setCorporateCreditCardSurchargeAmount(CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
        gatewayAccountEntity.setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_PREPAID_SURCHARGE_AMOUNT);
        gatewayAccountEntity.setCorporateDebitCardSurchargeAmount(DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
        gatewayAccountEntity.setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT);

        chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
    }

    //region Corporate Prepaid

    @Test
    public void shouldGetCorporateSurchargeForPrepaidCorporateCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(CREDIT_CARD_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldGetCorporateSurchargeForPrepaidCorporateDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateCreditCardWhenNoCorporatePrepaidCreditSurcharge() {
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateDebitCardWhenNoCorporatePrepaidDebitSurcharge() {
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Corporate Not Prepaid

    @Test
    public void shouldGetCorporateSurchargeForNotPrepaidCorporateCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldGetCorporateSurchargeForNotPrepaidCorporateDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditCardWhenNoCorporateCreditSurcharge() {
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateDebitCardWhenNoCorporateDebitSurcharge() {
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(0L);
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Corporate Unknown Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditOrDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.TRUE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateDebitCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.TRUE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Not Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditOrDebitCard() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    //endregion

    //region Consumer Unknown Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditOrDebitCardWithUnknownPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardWithNullPrepaid() {
        AuthCardDetails authCardDetails = getAuthCardDetails(PayersCardType.CREDIT, Boolean.FALSE, null);
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        assertThat(surcharge, is(0L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCardWithNullPrepaid() {
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
