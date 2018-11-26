package uk.gov.pay.connector.charge.util;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;

public class CorporateCardSurchargeCalculatorTest {

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
        shouldApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID, CREDIT_CARD_PREPAID_SURCHARGE_AMOUNT);
    }

    @Test
    public void shouldGetCorporateSurchargeForPrepaidCorporateDebitCard() {
        shouldApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID, DEBIT_CARD_PREPAID_SURCHARGE_AMOUNT);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateCreditCardWhenNoCorporatePrepaidCreditSurcharge() {
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(0L);
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateDebitCardWhenNoCorporatePrepaidDebitSurcharge() {
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(0L);
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidCorporateCreditOrDebitCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.PREPAID);
    }

    //endregion

    //region Corporate Not Prepaid

    @Test
    public void shouldGetCorporateSurchargeForNotPrepaidCorporateCreditCard() {
        shouldApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID, CREDIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
    }

    @Test
    public void shouldGetCorporateSurchargeForNotPrepaidCorporateDebitCard() {
        shouldApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID, DEBIT_CARD_NON_PREPAID_SURCHARGE_AMOUNT);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditCardWhenNoCorporateCreditSurcharge() {
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateDebitCardWhenNoCorporateDebitSurcharge() {
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(0L);
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidCorporateCreditOrDebitCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    //endregion

    //region Corporate Unknown Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateDebitCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditOrDebitCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.TRUE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateCreditCardWithNullPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.TRUE, null);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCorporateDebitCardWithNullPrepaid() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.TRUE, null);
    }

    //endregion

    //region Consumer Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerDebitCard() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForPrepaidConsumerCreditOrDebitCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.PREPAID);
    }

    //endregion

    //region Consumer Not Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerDebitCard() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForNotPrepaidConsumerCreditOrDebitCard() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.NOT_PREPAID);
    }

    //endregion

    //region Consumer Unknown Prepaid

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditOrDebitCardWithUnknownPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT_OR_DEBIT, Boolean.FALSE, PayersCardPrepaidStatus.UNKNOWN);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardWithNullPrepaid() {
        shouldNotApplySurcharge(PayersCardType.CREDIT, Boolean.FALSE, null);
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCardWithNullPrepaid() {
        shouldNotApplySurcharge(PayersCardType.DEBIT, Boolean.FALSE, null);
    }

    //endregion

    //region getAmount tests

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().get();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(0L).build();
        Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        Long expectedAmount = chargeEntity.getAmount();

        assertThat(actualAmount, is(expectedAmount));
    }

    //endregion

    private void shouldApplySurcharge(PayersCardType payersCardType, Boolean corporateCard, PayersCardPrepaidStatus payersCardPrepaidStatus, long expectedSurchargeAmount) {
        AuthCardDetails authCardDetails = getAuthCardDetails(payersCardType, corporateCard, payersCardPrepaidStatus);
        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(authCardDetails, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(expectedSurchargeAmount));
    }

    private void shouldNotApplySurcharge(PayersCardType payersCardType, Boolean corporateCard, PayersCardPrepaidStatus payersCardPrepaidStatus) {
        AuthCardDetails authCardDetails = getAuthCardDetails(payersCardType, corporateCard, payersCardPrepaidStatus);

        assertThat(getCorporateCardSurchargeFor(authCardDetails, chargeEntity).isPresent(), is(false));
    }

    private AuthCardDetails getAuthCardDetails(PayersCardType payersCardType, Boolean corporateCard, PayersCardPrepaidStatus payersCardPrepaidStatus) {
        return AuthCardDetailsFixture
                .anAuthCardDetails()
                .withCardType(payersCardType)
                .withCorporateCard(corporateCard)
                .withPayersCardPrepaidStatus(payersCardPrepaidStatus)
                .build();
    }
}
