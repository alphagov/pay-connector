package uk.gov.pay.connector.tasks.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.AuthorisationSummary;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZonedDateTime.parse;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.wallets.WalletType.APPLE_PAY;
import static uk.gov.pay.connector.wallets.WalletType.GOOGLE_PAY;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

@ExtendWith(MockitoExtension.class)
class ChargeParityCheckerTest {

    @Mock
    private PaymentInstrumentService paymentInstrumentService;
    @Mock
    private ChargeService chargeService;
    @Mock
    private RefundService mockRefundService;
    @Mock
    private PaymentProviders mockProviders;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    
    private ChargeEntity chargeEntity;
    private ChargeEntity chargeEntityWith3ds;
    private List<RefundEntity> refundEntities = List.of();
    
    @BeforeEach
    void setUp() {
        ChargeEventEntity chargeEventCreated = createChargeEventEntity(CREATED, "2016-01-25T13:23:55Z");
        ChargeEventEntity chargeEventCaptured = createChargeEventEntity(CAPTURED, "2016-01-26T14:23:55Z");
        ChargeEventEntity chargeEventCaptureSubmitted = createChargeEventEntity(CAPTURE_SUBMITTED,
                "2016-01-26T13:23:55Z");

        Auth3dsRequiredEntity auth3dsRequiredEntity = anAuth3dsRequiredEntity()
                .withThreeDsVersion("2.1.0")
                .build();
        
        chargeEntity = aValidChargeEntity()
                .withStatus(CAPTURED)
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withMoto(true)
                .withSource(CARD_PAYMENT_LINK)
                .withFee(Fee.of(null,10L))
                .withCorporateSurcharge(25L)
                .withWalletType(APPLE_PAY)
                .withDelayedCapture(true)
                .withEvents(List.of(chargeEventCreated, chargeEventCaptured, chargeEventCaptureSubmitted))
                .build();
        chargeEntityWith3ds = aValidChargeEntity()
                .withStatus(CAPTURED)
                .withEvents(List.of(chargeEventCreated, chargeEventCaptured, chargeEventCaptureSubmitted))
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .build();
        Logger root = (Logger) LoggerFactory.getLogger(ChargeParityChecker.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void parityCheck_shouldMatchIfChargeMatchesWithLedgerTransaction() {

        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfBillingAddressIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setBillingAddress(null);

        assertThat(transaction.getCardDetails().getBillingAddress(), is(notNullValue()));

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfCardHolderNameIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setCardHolderName(null);

        assertThat(transaction.getCardDetails().getCardholderName(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfEmailIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.setEmail(null);

        assertThat(transaction.getEmail(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchForTelephonePaymentNotificationIgnoringTotalAmount() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        ChargeEventEntity chargeEventPaymentNotification = createChargeEventEntity(PAYMENT_NOTIFICATION_CREATED, "2016-01-25T13:23:55Z");
        ChargeEventEntity chargeEventAuthSuccess = createChargeEventEntity(AUTHORISATION_SUCCESS, "2016-01-26T14:23:55Z");
        chargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withEvents(List.of(chargeEventPaymentNotification, chargeEventAuthSuccess))
                .build();

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withTotalAmount(null).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldReturnMissingInLedgerIfTransactionIsNull() {
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, null);

        assertThat(parityCheckStatus, is(MISSING_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfCardDetailsDoesNotMatchWithLedger() {
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
    void parityCheck_shouldReturnDataMismatchIfGatewayAccountDetailsDoesNotMatch() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withGatewayAccountId(345345L)
                .isLive(true)
                .withPaymentProvider("test-paymemt-provider")
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfFeatureSpecificFieldsDoesNotMatch() {
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
    void parityCheck_shouldReturnDataMismatchIfCaptureFieldsDoesnotMatchWithLedger() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCapturedDate(parse("2016-01-26T14:23:55Z"))
                .withCaptureSubmittedDate(parse("2016-01-26T14:23:55Z"))
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfRefundSummaryStatusDoesnotMatchWithLedger() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withRefundSummary(null)
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfChargeDoesNotMatchWithLedger() {
        LedgerTransaction transaction = aValidLedgerTransaction().withStatus("pending").build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldMatchIfLedgerCreatedDateWithin5sAfterConnectorDate() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCreatedDate(ZonedDateTime.parse("2016-01-25T13:23:59Z"))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfLedgerCreatedDateWithin5sBeforeConnectorDate() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCreatedDate(ZonedDateTime.parse("2016-01-25T13:23:51Z"))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfLedgerCreatedDateMoreThan5sAfterConnectorDate() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCreatedDate(ZonedDateTime.parse("2016-01-25T13:24:00Z"))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(0).getFormattedMessage(), is("Field value does not match between ledger and connector [field_name=created_date]"));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfLedgerCreatedDateMoreThan5sBeforeConnectorDate() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCreatedDate(ZonedDateTime.parse("2016-01-25T13:23:50Z"))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldReturnMatchWhenHas3dsRequiredDetails() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }
    
    @Test
    void parityCheck_shouldReturnDataMismatchIfAuthorisationSummaryNotOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities)
                .withAuthorisationSummary(null)
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(0).getFormattedMessage(), is("Field value does not match between ledger and connector [field_name=authorisation_summary.three_d_secure.required]"));
    }
    
    @Test
    void parityCheck_shouldReturnDataMismatchIfAuthorisationSummaryDoesNotContainThreeDSecure() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        AuthorisationSummary authorisationSummary = new AuthorisationSummary();
        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities)
                .withAuthorisationSummary(authorisationSummary)
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(0).getFormattedMessage(), is("Field value does not match between ledger and connector [field_name=authorisation_summary.three_d_secure.required]"));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfThreeDSecureRequiredFalse() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities)
                .build();
        transaction.getAuthorisationSummary().getThreeDSecure().setRequired(false);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(0).getFormattedMessage(), is("Field value does not match between ledger and connector [field_name=authorisation_summary.three_d_secure.required]"));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfThreeDSecureVersionMismatch() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities)
                .build();
        transaction.getAuthorisationSummary().getThreeDSecure().setVersion("mismatch");

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(0).getFormattedMessage(), is("Field value does not match between ledger and connector [field_name=authorisation_summary.three_d_secure.version]"));
    }

    @Test
    void parityCheck_shouldMatchIfCreatedBeforeDateToCheckForAuthorisationSummaryParity() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider());

        String createdDate = "2021-08-31T00:00:00Z";
        Auth3dsRequiredEntity auth3dsRequiredEntity = anAuth3dsRequiredEntity()
                .withThreeDsVersion("2.1.0")
                .build();
        ChargeEventEntity chargeEventCreated = createChargeEventEntity(CREATED, createdDate);
        ChargeEntity chargeBeforeDate = aValidChargeEntity()
                .withCreatedDate(Instant.parse(createdDate))
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .withEvents(List.of(chargeEventCreated))
                .build();
        LedgerTransaction transaction = from(chargeBeforeDate, List.of())
                .withAuthorisationSummary(null)
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeBeforeDate, transaction);
        
        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    private ChargeEventEntity createChargeEventEntity(ChargeStatus status, String timeStamp) {
        return aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .withTimestamp(parse(timeStamp))
                .build();
    }
}
