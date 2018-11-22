package uk.gov.pay.connector.charge.util;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.PayersPrepaidCardType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getCorporateCardSurchargeFor;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getTotalAmountFor;

public class CorporateCardSurchargeCalculatorTest {

    private static long CREDIT_CARD_SURCHARGE_AMOUNT = 250L;
    private static long DEBIT_CARD_SURCHARGE_AMOUNT = 50L;

    @Test
    public void shouldNotGetSurchargeWhenNotEnabledOnGatewayAccount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenNotCorporateCard() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenCorporateCardWithUnknownPrepaid() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.UNKNOWN)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenCorporateCardWithNullPrepaid() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(null)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldGetCorporateSurchargeForPrepaidCorporateCreditCard() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(CREDIT_CARD_SURCHARGE_AMOUNT));

        chargeEntity.setCorporateSurcharge(optionalSurcharge.get());
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + CREDIT_CARD_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldGetCorporateSurchargeForPrepaidCorporateDebitCard() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(DEBIT_CARD_SURCHARGE_AMOUNT));

        chargeEntity.setCorporateSurcharge(optionalSurcharge.get());
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + DEBIT_CARD_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldNotGetSurchargeWhenPrepaidCreditCorporateCardAndCreditCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(0L);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenPrepaidDebitCorporateCardAndDebitCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(0L);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenNotPrepaidCreditCorporateCardAndNoCreditCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldGetSurchargeWhenNotPrepaidCreditCorporateCardAndCreditCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(0L);
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(CREDIT_CARD_SURCHARGE_AMOUNT));

        chargeEntity.setCorporateSurcharge(optionalSurcharge.get());
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + CREDIT_CARD_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldNotGetSurchargeWhenNotPrepaidDebitCorporateCardAndNoDebitCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(0L);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldGetSurchargeWhenNotPrepaidDebitCorporateCardAndDebitCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(0L);
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(DEBIT_CARD_SURCHARGE_AMOUNT));

        chargeEntity.setCorporateSurcharge(optionalSurcharge.get());
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + DEBIT_CARD_SURCHARGE_AMOUNT));
    }

    @Test
    public void shouldNotGetSurchargeWhenUnknownPrepaidCreditCorporateCardAndNoCreditCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.UNKNOWN)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldNotGetSurchargeWhenUnknownPrepaidCreditCorporateCardAndCreditCardSurchargeAmount() {
        AuthCardDetails corporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.UNKNOWN)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidDebitCardSurchargeAmount(DEBIT_CARD_SURCHARGE_AMOUNT);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        assertThat(getCorporateCardSurchargeFor(corporateCard, chargeEntity).isPresent(), is(false));
    }

    @Test
    public void shouldGetCorporateSurchargeForCorporateNotPrepaidCreditCard() {
        AuthCardDetails corporateCreditCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));
        chargeEntity.setCorporateSurcharge(250L);
        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateCreditCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(250L));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount() + 250L));
    }

    @Test
    public void shouldGetCorporateSurchargeForCorporateNotPrepaidDebitCard() {
        AuthCardDetails corporateDebitCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(corporateDebitCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(true));
        assertThat(optionalSurcharge.get(), is(50L));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardCard() {
        AuthCardDetails creditNoCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(creditNoCorporateCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerDebitCard() {
        AuthCardDetails debitNoCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(debitNoCorporateCard, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT_OR_DEBIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditOrDebitCardType() {
        AuthCardDetails creditOrDebit = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT_OR_DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardAndPrepaidCardType() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardAndNotPrepaidCardType() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.NOT_PREPAID)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurchargeForConsumerCreditCardAndUnknownCardType() {
        AuthCardDetails nonCorporateCard = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.FALSE)
                .withPayersPrepaidCardType(PayersPrepaidCardType.UNKNOWN)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().setCorporateCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);
        chargeEntity.getGatewayAccount().setCorporatePrepaidCreditCardSurchargeAmount(CREDIT_CARD_SURCHARGE_AMOUNT);

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(nonCorporateCard, chargeEntity);
        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldNotGetCorporateSurcharge_whenCorporateSurchargeIsNull() {
        AuthCardDetails creditOrDebit = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT)
                .withCorporateCard(Boolean.TRUE)
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();

        assertThat(chargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(getTotalAmountFor(chargeEntity), is(chargeEntity.getAmount()));

        Optional<Long> optionalSurcharge = getCorporateCardSurchargeFor(creditOrDebit, chargeEntity);

        assertThat(optionalSurcharge.isPresent(), is(false));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeGreaterThanZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(250L).build();
        final Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().get();
        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsNull() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        final Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount();
        assertThat(actualAmount, is(expectedAmount));
    }

    @Test
    public void shouldCalculateTotalAmountForCorporateSurchargeIsZero() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withCorporateSurcharge(0L).build();
        final Long actualAmount = CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity);
        final Long expectedAmount = chargeEntity.getAmount();
        assertThat(actualAmount, is(expectedAmount));
    }
}
