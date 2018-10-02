package uk.gov.pay.connector.util.charge;


import org.junit.Test;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.AuthCardDetailsBuilder;
import uk.gov.pay.connector.model.domain.PayersCardType;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static uk.gov.pay.connector.util.charge.CorporateSurchargeCalculator.getCorporateSurchargeFor;
import static uk.gov.pay.connector.util.charge.CorporateSurchargeCalculator.getTotalAmountFor;

public class CorporateSurchargeCalculatorTest {
    @Test
    public void shouldGetCorporateSurchargeForCreditCard() {
        AuthCardDetails corporateCreditCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);
        
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        chargeEntity.setCorporateSurcharge(250L);
        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(corporateCreditCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(250L));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + 250L));
    }

    @Test
    public void shouldGetCorporateSurchargeForDebitCard() {
        AuthCardDetails corporateDebitCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(corporateDebitCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(50L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardCard() {
        AuthCardDetails creditNoCorporateCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(creditNoCorporateCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCard() {
        AuthCardDetails debitNoCorporateCard = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(debitNoCorporateCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT_OR_CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT_OR_CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurcharge_whenCorporateSurchargeIsNull() {
        AuthCardDetails creditOrDebit = AuthCardDetailsBuilder.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        final Long actualAmount = CorporateSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().get();
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
