package uk.gov.pay.connector.tasks.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;

import java.util.List;

import static java.time.ZonedDateTime.parse;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.commons.model.Source.CARD_API;
import static uk.gov.pay.commons.model.Source.CARD_PAYMENT_LINK;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;
import static uk.gov.pay.connector.wallets.WalletType.GOOGLE_PAY;

@RunWith(MockitoJUnitRunner.class)
public class ChargeParityCheckerTest {

    @Mock
    private RefundService mockRefundService;
    @Mock
    private PaymentProviders mockProviders;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;
    private ChargeEntity chargeEntity;
    private List<RefundEntity> refundEntities = List.of();

    @Before
    public void setUp() {
        ChargeEventEntity chargeEventCreated = createChargeEventEntity(CREATED, "2016-01-25T13:23:55Z");
        ChargeEventEntity chargeEventCaptured = createChargeEventEntity(CAPTURED, "2016-01-26T14:23:55Z");
        ChargeEventEntity chargeEventCaptureSubmitted = createChargeEventEntity(CAPTURE_SUBMITTED,
                "2016-01-26T13:23:55Z");

        chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURED)
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(10L)
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .withEvents(List.of(chargeEventCreated, chargeEventCaptured, chargeEventCaptureSubmitted))
                .build();

        when(mockRefundService.findRefunds(any())).thenReturn(List.of());
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());
    }

    @Test
    public void parityCheck_shouldMatchIfChargeMatchesWithLedgerTransaction() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    public void parityCheck_shouldMatchIfBillingAddressIsNotAvailableInConnectorButOnLedgerTransaction() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setBillingAddress(null);

        assertThat(transaction.getCardDetails().getBillingAddress(), is(notNullValue()));

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    public void parityCheck_shouldMatchIfCardHolderNameIsNotAvailableInConnectorButOnLedgerTransaction() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setCardHolderName(null);

        assertThat(transaction.getCardDetails().getCardholderName(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    public void parityCheck_shouldMatchIfEmailIsNotAvailableInConnectorButOnLedgerTransaction() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.setEmail(null);

        assertThat(transaction.getEmail(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    public void parityCheck_shouldReturnMissingInLedgerIfTransactionIsNull() {
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, null);

        assertThat(parityCheckStatus, is(MISSING_IN_LEDGER));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfCardDetailsDoesNotMatchWithLedger() {
        chargeEntity.getCardDetails().setBillingAddress(null);
        chargeEntity.getCardDetails().setExpiryDate(null);
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCardDetails(new CardDetails("test-name", null, "test-brand",
                        "6666", "123656", "11/88", null))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfGatewayAccountDetailsDoesNotMatch() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withGatewayAccountId(345345L)
                .isLive(true)
                .withPaymentProvider("test-paymemt-provider")
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfFeatureSpecificFieldsDoesNotMatch() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withSource(CARD_API)
                .withMoto(false)
                .withDelayedCapture(false)
                .withFee(10000L)
                .withCorporateCardSurcharge(10000L)
                .withNetAmount(10000L)
                .withWalletType(GOOGLE_PAY)
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfCaptureFieldsDoesnotMatchWithLedger() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCapturedDate(parse("2016-01-26T14:23:55Z"))
                .withCaptureSubmittedDate(parse("2016-01-26T14:23:55Z"))
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfRefundSummaryStatusDoesnotMatchWithLedger() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withRefundSummary(null)
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    public void parityCheck_shouldReturnDataMismatchIfChargeDoesNotMatchWithLedger() {
        LedgerTransaction transaction = aValidLedgerTransaction().withStatus("pending").build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    private ChargeEventEntity createChargeEventEntity(ChargeStatus status, String timeStamp) {
        return aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .withTimestamp(parse(timeStamp))
                .build();
    }
}
