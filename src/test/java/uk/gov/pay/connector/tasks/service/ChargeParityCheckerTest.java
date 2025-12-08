package uk.gov.pay.connector.tasks.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.AuthorisationSummary;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.Exemption;
import uk.gov.pay.connector.client.ledger.model.ExemptionOutcome;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.ThreeDSecure;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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
import static uk.gov.pay.connector.charge.model.domain.Exemption3dsType.CORPORATE;
import static uk.gov.pay.connector.charge.model.domain.Exemption3dsType.OPTIMISED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.from;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_HONOURED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_OUT_OF_SCOPE;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_REJECTED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED;
import static uk.gov.pay.connector.tasks.service.ConnectorAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_FALSE;
import static uk.gov.pay.connector.tasks.service.ConnectorAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_NULL_AND_NO_3DS_REQUIRED_DETAILS;
import static uk.gov.pay.connector.tasks.service.ConnectorAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS;
import static uk.gov.pay.connector.tasks.service.ConnectorAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_TRUE;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_NO_AUTHORISATION_SUMMARY;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_SOMETHING_COMPLETELY_DIFFERENT;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_FALSE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_REJECTED;
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
    @Mock
    private RefundEntityFactory mockRefundEntityFactory;
    @InjectMocks
    ChargeParityChecker chargeParityChecker;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private ChargeEntity chargeEntity;
    private ChargeEntity chargeEntityWith3ds;
    private final List<RefundEntity> refundEntities = List.of();

    private static final ChargeEntity CHARGE_ENTITY_WITH_3DS_REQUIRED_NULL = ChargeEntityFactory.createWith3dsRequiredNull();
    private static final ChargeEntity CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE = ChargeEntityFactory.createWith3dsRequired(true);
    private static final ChargeEntity CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE = ChargeEntityFactory.createWith3dsRequired(false);
    private static final ChargeEntity CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL = ChargeEntityFactory.createWith3dsRequiredDetailsBut3dsRequiredNull();

    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_AUTHORISATION_SUMMARY_NULL_3D_SECURE = LedgerTransactionFactory.createWithAuthorisationSummaryButNull3dSecure(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE_DISCREPANCY = LedgerTransactionFactory.createWith3dsRequiredFalse(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_NULL = LedgerTransactionFactory.createWith3dSecureVersionMismatch(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, null);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE_DISCREPANCY = LedgerTransactionFactory.createWith3dsRequiredTrue(CHARGE_ENTITY_WITH_3DS_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE_CONFLICT = LedgerTransactionFactory.createWith3dsRequiredFalse(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE_CONFLICT = LedgerTransactionFactory.createWith3dsRequiredTrue(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_FALSE = LedgerTransactionFactory.createWith3dSecureVersionMismatch(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, false);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_TRUE = LedgerTransactionFactory.createWith3dSecureVersionMismatch(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE, true);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE = LedgerTransactionFactory.createWith3dsRequiredTrue(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE = LedgerTransactionFactory.createWith3dsRequiredFalse(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3DS_DETAILS_BUT_REQUIRED_NULL = LedgerTransactionFactory.createWith3dsRequiredTrue(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_NULL_AUTHORISATION_SUMMARY = LedgerTransactionFactory.createWithNullAuthorisationSummary(CHARGE_ENTITY_WITH_3DS_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_NULL_AUTHORISATION_SUMMARY_AND_3DS_DETAILS_FROM_CHARGE = LedgerTransactionFactory.createWithNullAuthorisationSummary(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL);
    private static final LedgerTransaction LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_BUT_3DS_DETAILS_NULL_FROM_CHARGE = LedgerTransactionFactory.createWith3dSecureVersionMismatch(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, false);


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
                .withFee(Fee.of(null, 10L))
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

        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfBillingAddressIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setBillingAddress(null);

        assertThat(transaction.getCardDetails().getBillingAddress(), is(notNullValue()));

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfCardHolderNameIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.getCardDetails().setCardHolderName(null);

        assertThat(transaction.getCardDetails().getCardholderName(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfEmailIsNotAvailableInConnectorButOnLedgerTransaction() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities).build();
        chargeEntity.setEmail(null);

        assertThat(transaction.getEmail(), is(notNullValue()));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchForTelephonePaymentNotificationIgnoringTotalAmount() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

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
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withRefundSummary(null)
                .withDisputed(false)
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldIgnoreRefundSummaryMismatchIfLedgerTransactionIsDisputed() {
        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withRefundSummary(null)
                .withDisputed(true)
                .build();

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldReturnDataMismatchIfChargeDoesNotMatchWithLedger() {
        LedgerTransaction transaction = aValidLedgerTransaction().withStatus("pending").build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @Test
    void parityCheck_shouldMatchIfLedgerCreatedDateWithin5sAfterConnectorDate() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntity, refundEntities)
                .withCreatedDate(ZonedDateTime.parse("2016-01-25T13:23:59Z"))
                .build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfLedgerCreatedDateWithin5sBeforeConnectorDate() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

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
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        LedgerTransaction transaction = from(chargeEntityWith3ds, refundEntities).build();
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntityWith3ds, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldMatchIfCreatedBeforeDateToCheckForAuthorisationSummaryParity() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

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

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldReturnDataMismatchFor3dsDataDiscrepancies(ChargeEntity chargeEntity, LedgerTransaction ledgerTransaction, String fieldName,
                                                                     ConnectorAuthorisationSummaryState connectorAuthorisationSummaryState, LedgerAuthorisationSummaryState ledgerAuthorisationSummaryState) {
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, ledgerTransaction);
        assertThat(parityCheckStatus, is(DATA_MISMATCH));
        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        StringBuilder expectedLogMessage = new StringBuilder("Field value does not match between ledger and connector [field_name=").append(fieldName).append(']');
        if (connectorAuthorisationSummaryState != null || ledgerAuthorisationSummaryState != null) {
            expectedLogMessage.append(" [calculated_states=").append(connectorAuthorisationSummaryState).append(",").append(ledgerAuthorisationSummaryState).append(']');
        }
        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage.toString()));
    }

    private static Stream<Arguments> parityCheck_shouldReturnDataMismatchFor3dsDataDiscrepancies() {
        return Stream.of(
                Arguments.of(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_NULL_AUTHORISATION_SUMMARY_AND_3DS_DETAILS_FROM_CHARGE,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS, LEDGER_HAS_NO_AUTHORISATION_SUMMARY),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_AUTHORISATION_SUMMARY_NULL_3D_SECURE,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS, LEDGER_HAS_SOMETHING_COMPLETELY_DIFFERENT),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE_DISCREPANCY,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS, LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_NULL,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS, LEDGER_HAS_SOMETHING_COMPLETELY_DIFFERENT),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE_DISCREPANCY,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_NULL_AND_NO_3DS_REQUIRED_DETAILS, LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE_CONFLICT,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_TRUE, LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE_CONFLICT,
                        "authorisation_summary.three_d_secure.required", CONNECTOR_HAS_REQUIRES_3DS_FALSE, LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_FALSE,
                        "authorisation_summary.three_d_secure.version", null, null),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE, LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_TRUE,
                        "authorisation_summary.three_d_secure.version", null, null),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, LEDGER_TRANSACTION_WITH_3D_SECURE_VERSION_MISMATCH_BUT_3DS_DETAILS_NULL_FROM_CHARGE,
                        "authorisation_summary.three_d_secure.version", null, null)
        );
    }

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldNotReturnMismatchWhenExemption3dsRequestedIsOptimisedAndExemptions3dsNullAndExemptionOutcomeNotSet(
            Exemption3dsType chargeExemption3dsRequested,
            Exemption3ds chargeExemption3ds,
            Exemption transactionExemption
    ) {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        chargeEntity.setExemption3ds(chargeExemption3ds);
        chargeEntity.setExemption3dsRequested(chargeExemption3dsRequested);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(transactionExemption);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    private static Stream<Arguments> parityCheck_shouldNotReturnMismatchWhenExemption3dsRequestedIsOptimisedAndExemptions3dsNullAndExemptionOutcomeNotSet() {
        return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of(null, EXEMPTION_NOT_REQUESTED, createExemption(false)),
                Arguments.of(OPTIMISED, null, createExemption(true)),
                Arguments.of(CORPORATE, null, createExemption(true))
        );
    }

    private static Exemption createExemption(boolean requested) {
        return new Exemption(requested, null, null);
    }

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldNotReturnMismatchIfExemption3dsDataMatches(
            Exemption3dsType chargeExemption3dsType,
            Exemption3ds chargeExemption3ds,
            String transactionExemptionOutcomeResult,
            Boolean setRequested
    ) {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        Exemption exemption = new Exemption(setRequested, null, new ExemptionOutcome(transactionExemptionOutcomeResult));

        chargeEntity.setExemption3ds(chargeExemption3ds);
        chargeEntity.setExemption3dsRequested(chargeExemption3dsType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(exemption);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    private static Stream<Arguments> parityCheck_shouldNotReturnMismatchIfExemption3dsDataMatches() {
        return Stream.of(
                Arguments.of(null, EXEMPTION_HONOURED, "honoured", true),
                Arguments.of(null, EXEMPTION_REJECTED, "rejected", true),
                Arguments.of(null, EXEMPTION_OUT_OF_SCOPE, "out of scope", true),
                Arguments.of(OPTIMISED, EXEMPTION_HONOURED, "honoured", true),
                Arguments.of(OPTIMISED, EXEMPTION_REJECTED, "rejected", true),
                Arguments.of(OPTIMISED, EXEMPTION_OUT_OF_SCOPE, "out of scope", true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldNotReturnMismatchIfExemption3dsDataMatchesWithExemptionAndTypeCorporate(
            Exemption3dsType chargeExemption3dsType,
            Exemption3ds chargeExemption3ds,
            String transactionExemptionOutcomeResult,
            Boolean setRequested
    ) {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        Exemption exemption = new Exemption(setRequested, "corporate", new ExemptionOutcome(transactionExemptionOutcomeResult));

        chargeEntity.setExemption3ds(chargeExemption3ds);
        chargeEntity.setExemption3dsRequested(chargeExemption3dsType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(exemption);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    private static Stream<Arguments> parityCheck_shouldNotReturnMismatchIfExemption3dsDataMatchesWithExemptionAndTypeCorporate() {
        return Stream.of(
                Arguments.of(CORPORATE, EXEMPTION_HONOURED, "honoured", true),
                Arguments.of(CORPORATE, EXEMPTION_REJECTED, "rejected", true),
                Arguments.of(CORPORATE, EXEMPTION_OUT_OF_SCOPE, "out of scope", true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldReturnMismatchIfExemption3dsDataDoesNotMatch(
            Exemption3dsType chargeExemption3dsType,
            Exemption3ds chargeExemption3ds,
            String transactionExemptionOutcomeResult,
            Boolean setRequested,
            Connector3dsExemptionResultState connectorExemption3dsState,
            Connector3dsExemptionRequestedState connectorExemption3DsRequestedState,
            LedgerExemptionState ledgerExemptionState
    ) {
        Exemption exemption = new Exemption(setRequested, "corporate", new ExemptionOutcome(transactionExemptionOutcomeResult));


        chargeEntity.setExemption3ds(chargeExemption3ds);
        chargeEntity.setExemption3dsRequested(chargeExemption3dsType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(exemption);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Field value does not match between ledger and connector [field_name=exemption] " +
                "[calculated_states=" + connectorExemption3dsState + ',' + connectorExemption3DsRequestedState + ',' + ledgerExemptionState + ']';
        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    private static Stream<Arguments> parityCheck_shouldReturnMismatchIfExemption3dsDataDoesNotMatch() {
        return Stream.of(
                Arguments.of(null, EXEMPTION_HONOURED, "rejected", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_REJECTED),
                Arguments.of(null, EXEMPTION_REJECTED, "honoured", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED),
                Arguments.of(OPTIMISED, EXEMPTION_OUT_OF_SCOPE, "honoured", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED),
                Arguments.of(CORPORATE, EXEMPTION_HONOURED, "out of scope", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE),
                Arguments.of(OPTIMISED, EXEMPTION_REJECTED, "not requested", false,
                        CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_FALSE),
                Arguments.of(OPTIMISED, EXEMPTION_HONOURED, "out of scope", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE),
                Arguments.of(null, EXEMPTION_HONOURED, "out of scope", true,
                        CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE)
        );
    }


    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldReturnMismatchIfExemption3dsDataDoesNotMatchWithExemptionTypeCorporate(
            Exemption3dsType chargeExemption3dsType,
            Exemption3ds chargeExemption3ds,
            String transactionExemptionOutcomeResult,
            Boolean setRequested
    ) {
        Exemption exemption = new Exemption(setRequested, "corporate", new ExemptionOutcome(transactionExemptionOutcomeResult));


        chargeEntity.setExemption3ds(chargeExemption3ds);
        chargeEntity.setExemption3dsRequested(chargeExemption3dsType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(exemption);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    private static Stream<Arguments> parityCheck_shouldReturnMismatchIfExemption3dsDataDoesNotMatchWithExemptionTypeCorporate() {
        return Stream.of(
                Arguments.of(CORPORATE, EXEMPTION_HONOURED, "rejected", true),
                Arguments.of(CORPORATE, EXEMPTION_REJECTED, "honoured", true),
                Arguments.of(CORPORATE, EXEMPTION_OUT_OF_SCOPE, "not requested", false),
                Arguments.of(CORPORATE, EXEMPTION_HONOURED, "out of scope", false),
                Arguments.of(CORPORATE, EXEMPTION_REJECTED, "honoured", true)
        );
    }

    @Test
    void parityCheck_shouldReturnMismatchIfTransactionHasNoExemptionObject() {
        chargeEntity.setExemption3ds(EXEMPTION_HONOURED);
        chargeEntity.setExemption3dsRequested(OPTIMISED);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithExemption3ds(chargeEntity);
        transaction.setExemption(null);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);

        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @ParameterizedTest
    @MethodSource
    void parityCheck_shouldReturnMatchIf3dsDataMatchesExactly(ChargeEntity chargeEntity, LedgerTransaction ledgerTransaction) {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));
        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, ledgerTransaction);
        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @ParameterizedTest
    @EnumSource(AgreementPaymentType.class)
    void parityCheck_shouldAllowMatchingRecurringAgreementPaymentTypeFields(AgreementPaymentType agreementPaymentType) {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        ChargeEntity chargeEntity = ChargeEntityFactory.createWithAgreementPaymentType(agreementPaymentType);
        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithAgreementPaymentType(chargeEntity);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);
        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @Test
    void parityCheck_shouldAllowNullAgreementPaymentTypeFields() {
        when(mockProviders.byName(any())).thenReturn(new SandboxPaymentProvider(mockRefundEntityFactory));

        ChargeEntity chargeEntity = ChargeEntityFactory.createWithAgreementPaymentType(null);
        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithAgreementPaymentType(chargeEntity);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);
        assertThat(parityCheckStatus, is(EXISTS_IN_LEDGER));
    }

    @ParameterizedTest
    @MethodSource
    void parityChecker_shouldFailForNonMatchingAgreementPaymentTypeFields(AgreementPaymentType connectorType, AgreementPaymentType transactionType) {
        ChargeEntity chargeEntity = ChargeEntityFactory.createWithAgreementPaymentType(connectorType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithAgreementPaymentType(chargeEntity);
        transaction.setAgreementPaymentType(transactionType);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);
        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }


    @ParameterizedTest
    @EnumSource(AgreementPaymentType.class)
    void parityCheck_shouldFailForNonNullAgreementPaymentTypeInConnectorAndNullAgreementPaymentTypeInLedger(AgreementPaymentType agreementPaymentType) {
        ChargeEntity chargeEntity = ChargeEntityFactory.createWithAgreementPaymentType(agreementPaymentType);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithAgreementPaymentType(chargeEntity);
        transaction.setAgreementPaymentType(null);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);
        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    @ParameterizedTest
    @EnumSource(AgreementPaymentType.class)
    void parityCheck_shouldFailForNonNullAgreementPaymentTypeInLedgerAndNullAgreementPaymentTypeInConnector(AgreementPaymentType agreementPaymentType) {
        ChargeEntity chargeEntity = ChargeEntityFactory.createWithAgreementPaymentType(null);

        LedgerTransaction transaction = LedgerTransactionFactory.buildTransactionWithAgreementPaymentType(chargeEntity);
        transaction.setAgreementPaymentType(agreementPaymentType);

        ParityCheckStatus parityCheckStatus = chargeParityChecker.checkParity(chargeEntity, transaction);
        assertThat(parityCheckStatus, is(DATA_MISMATCH));
    }

    private static List<Arguments> parityChecker_shouldFailForNonMatchingAgreementPaymentTypeFields() {
        List<Arguments> arguments = new ArrayList<>();
        for (var connectorType : AgreementPaymentType.values()) {
            for (var ledgerType : AgreementPaymentType.values()) {
                if (connectorType != ledgerType) {
                    arguments.add(Arguments.of(connectorType, ledgerType));
                }
            }
        }
        return List.copyOf(arguments);
    }

    private static Stream<Arguments> parityCheck_shouldReturnMatchIf3dsDataMatchesExactly() {
        return Stream.of(
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_TRUE, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_TRUE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_FALSE, LEDGER_TRANSACTION_WITH_3DS_REQUIRED_FALSE),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_DETAILS_BUT_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_3DS_DETAILS_BUT_REQUIRED_NULL),
                Arguments.of(CHARGE_ENTITY_WITH_3DS_REQUIRED_NULL, LEDGER_TRANSACTION_WITH_NULL_AUTHORISATION_SUMMARY)
        );
    }

    public static class LedgerTransactionFactory {

        public static LedgerTransaction createWithNullAuthorisationSummary(ChargeEntity chargeEntity) {
            return buildTransaction(chargeEntity, null);
        }

        public static LedgerTransaction createWith3dsRequiredFalse(ChargeEntity chargeEntity) {
            return buildTransaction(chargeEntity, createAuthorisationSummary(false));
        }

        public static LedgerTransaction createWith3dsRequiredTrue(ChargeEntity chargeEntity) {
            return buildTransaction(chargeEntity, createAuthorisationSummary(true));
        }

        public static LedgerTransaction createWithAuthorisationSummaryButNull3dSecure(ChargeEntity chargeEntity) {
            return buildTransaction(chargeEntity, createAuthorisationSummary(null));
        }

        public static LedgerTransaction createWith3dSecureVersionMismatch(ChargeEntity chargeEntity, Boolean threeDSecureRequired) {
            LedgerTransaction transaction = buildTransaction(chargeEntity, createAuthorisationSummary(threeDSecureRequired));
            if (transaction.getAuthorisationSummary() != null && transaction.getAuthorisationSummary().getThreeDSecure() != null) {
                transaction.getAuthorisationSummary().getThreeDSecure().setVersion("mismatch");
            }
            return transaction;
        }

        private static LedgerTransaction buildTransaction(ChargeEntity chargeEntity, AuthorisationSummary authorisationSummary) {
            return from(chargeEntity, Collections.emptyList())
                    .withAuthorisationSummary(authorisationSummary)
                    .build();
        }

        private static LedgerTransaction buildTransactionWithExemption3ds(ChargeEntity chargeEntity) {
            return from(chargeEntity, Collections.emptyList())
                    .withExemption3ds(EXEMPTION_HONOURED)
                    .build();
        }

        private static LedgerTransaction buildTransactionWithAgreementPaymentType(ChargeEntity chargeEntity) {
            return from(chargeEntity, Collections.emptyList())
                    .withAgreementPaymentType(chargeEntity.getAgreementPaymentType())
                    .build();
        }

        private static AuthorisationSummary createAuthorisationSummary(Boolean threeDSecureRequired) {
            AuthorisationSummary authorisationSummary = new AuthorisationSummary();

            if (threeDSecureRequired != null) {
                ThreeDSecure threeDSecure = new ThreeDSecure();
                threeDSecure.setRequired(threeDSecureRequired);
                if (threeDSecureRequired) {
                    threeDSecure.setVersion("2.1.0");
                }
                authorisationSummary.setThreeDSecure(threeDSecure);
            }
            return authorisationSummary;
        }
    }

    public static class ChargeEntityFactory {

        public static ChargeEntity createWith3dsRequired(boolean isRequired) {
            ChargeEntity chargeEntity = createChargeEntity();

            if (isRequired) {
                Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
                auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
                chargeEntity.set3dsRequiredDetails(auth3dsRequiredEntity);
            } else {
                chargeEntity.set3dsRequiredDetails(null);
            }

            chargeEntity.setRequires3ds(isRequired);
            return chargeEntity;
        }

        public static ChargeEntity createWith3dsRequiredNull() {
            List<ChargeEventEntity> chargeEvents = createChargeEvents();
            return aValidChargeEntity()
                    .withStatus(CAPTURED)
                    .withEvents(chargeEvents)
                    .withRequires3ds(null)
                    .build();
        }

        public static ChargeEntity createWith3dsRequiredDetailsBut3dsRequiredNull() {
            List<ChargeEventEntity> chargeEvents = createChargeEvents();
            Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
            auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
            ChargeEntity chargeEntity = aValidChargeEntity()
                    .withStatus(CAPTURED)
                    .withEvents(chargeEvents)
                    .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                    .build();
            chargeEntity.setRequires3ds(null);
            return chargeEntity;
        }

        private static ChargeEntity createChargeEntity() {
            List<ChargeEventEntity> chargeEvents = createChargeEvents();
            Auth3dsRequiredEntity auth3dsRequiredEntity = anAuth3dsRequiredEntity()
                    .withThreeDsVersion("2.1.0")
                    .build();
            return aValidChargeEntity()
                    .withStatus(CAPTURED)
                    .withEvents(chargeEvents)
                    .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                    .build();
        }

        private static List<ChargeEventEntity> createChargeEvents() {
            ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                    .withCharge(null)
                    .withChargeStatus(CREATED)
                    .withTimestamp(parse("2016-01-25T13:23:55Z"))
                    .build();

            return List.of(chargeEventEntity);
        }

        private static ChargeEntity createWithAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
            List<ChargeEventEntity> chargeEvents = createChargeEvents();

            return aValidChargeEntity()
                    .withStatus(CAPTURED)
                    .withEvents(chargeEvents)
                    .withAgreementPaymentType(agreementPaymentType)
                    .build();
        }
    }

    private ChargeEventEntity createChargeEventEntity(ChargeStatus status, String timeStamp) {
        return aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(status)
                .withTimestamp(parse(timeStamp))
                .build();
    }
}
