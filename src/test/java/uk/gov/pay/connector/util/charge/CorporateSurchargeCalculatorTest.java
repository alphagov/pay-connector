package uk.gov.pay.connector.util.charge;


import org.junit.Test;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.AuthCardDetailsBuilder;
import uk.gov.pay.connector.model.domain.PayersCardType;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static uk.gov.pay.connector.util.charge.CorporateSurchargeCalculator.setCorporateSurchargeFor;
import static uk.gov.pay.connector.util.charge.CorporateSurchargeCalculator.getTotalAmountFor;

public class CorporateSurchargeCalculatorTest {
    @Test
    public void shouldSetCorporateSurchargeForCreditCard() {
        AuthCardDetails corporateCreditCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(corporateCreditCard, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(250L));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + 250L));
    }

    @Test
    public void shouldSetCorporateSurchargeForDebitCard() {
        AuthCardDetails corporateDebitCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(corporateDebitCard, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(50L));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + 50L));
    }

    @Test
    public void shouldNotSetCorporateSurchargeForConsumerCreditCardCard() {
        AuthCardDetails creditNoCorporateCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(creditNoCorporateCard, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldNotSetCorporateSurchargeForConsumerDebitCard() {
        AuthCardDetails debitNoCorporateCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(debitNoCorporateCard, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldNotSetCorporateSurchargeForCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT_OR_CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldNotSetCorporateSurchargeForConsumerCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT_OR_CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldNotSetCorporateSurchargeForNull() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        setCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(chargeEntity.getCorporateSurcharge(), is(nullValue()));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        final Long actualAmount = CorporateSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge();
        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        final Long actualAmount = CorporateSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount();
        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(0L).build();
        final Long actualAmount = CorporateSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount();
        assertThat(actualAmount, is(expectedAmount));
    }
}
