package uk.gov.pay.connector.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.card.model.CardDetailsEntity;
import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.card.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.CLOSED_ACCOUNT;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity.PaymentInstrumentEntityBuilder.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
class CardServicePostAuthorisationTest {

    private static final String EXTERNAL_ID = "some-external-id";
    private static final String TRANSACTION_ID = "some-transaction-id";
    private static final String CARD_NUMBER = "4000056655665556";
    private static final String CARDHOLDER_NAME = "Ms Payment";
    private static final String EMAIL = "test@email.test";
    private static final ProviderSessionIdentifier PROVIDER_SESSION_IDENTIFIER = ProviderSessionIdentifier.of("some-session-identifier");
    private static final Map<String, String> TOKEN = Map.of("token", "value");

    @Mock private ChargeDao mockChargeDao;
    @Mock private ChargeEventDao mockChargeEventDao;
    @Mock private AgreementDao mockAgreementDao;
    @Mock private GatewayAccountDao mockGatewayAccountDao;
    @Mock private TokenDao mockTokenDao;
    @Mock private CardTypeDao mockCardTypeDao;
    @Mock private RefundService mockRefundService;
    @Mock private PaymentInstrumentService mockPaymentInstrumentService;
    @Mock private LedgerService mockLedgerService;
    @Mock private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    @Mock private StateTransitionService mockStateTransitionService;
    @Mock private EventService mockEventService;
    @Mock private TaskQueueService mockTaskQueueService;
    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    @Mock private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;
    @Mock private PaymentProviders mockProviders;
    @Mock private ConnectorConfiguration mockConnectorConfig;
    @Mock private CardDetailsEntity mockCardDetailsEntity;
    @Mock private ChargeEventEntity mockChargeEventEntity;
    @Mock private PaymentInstrumentEntity mockPaymentInstrumentEntity;
    @Mock
    private IdempotencyDao mockIdempotencyDao;
    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Captor
    private ArgumentCaptor<AgreementInactivated> agreementInactivatedArgumentCaptor;

    private static ObjectMapper objectMapper = new ObjectMapper();
    private final Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
    private final ChargeEntityFixture chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity();
    private AuthCardDetails authCardDetails;

    @InjectMocks
    private CardService cardService;

    @BeforeEach
    void setUp() {
        when(mockConnectorConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        var chargeEventEntity = ChargeEventEntity.ChargeEventEntityBuilder
                .aChargeEventEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .withUpdated(ZonedDateTime.parse("2022-04-05T20:52:23Z"))
                .build();

        chargeEntityFixture
                .withStatus(AUTHORISATION_READY)
                .withExternalId(EXTERNAL_ID)
                .withEmail(EMAIL)
                .withProviderSessionId(PROVIDER_SESSION_IDENTIFIER.toString())
                .withTransactionId(TRANSACTION_ID)
                .withEvents(List.of(chargeEventEntity));

        authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(CARDHOLDER_NAME)
                .withCardNo(CARD_NUMBER)
                .build();
    }

    @Test
    void updateChargePostAuthorisationUpdatesChargeAndEmitsEvent() {
        var chargeEntity = chargeEntityFixture.build();
        when(mockChargeDao.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(chargeEntity));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);
        when(mockChargeEventDao.persistChargeEventOf(chargeEntity, null)).thenReturn(mockChargeEventEntity);
        
        cardService.updateChargePostCardAuthorisation(EXTERNAL_ID, AUTHORISATION_SUCCESS, TRANSACTION_ID, auth3dsRequiredEntity,
                PROVIDER_SESSION_IDENTIFIER, authCardDetails, TOKEN, null, null);

        assertThat(chargeEntity.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(chargeEntity.getEmail(), is(EMAIL));
        assertThat(chargeEntity.getChargeCardDetails().getProviderSessionId(), is(PROVIDER_SESSION_IDENTIFIER.toString()));
        assertThat(chargeEntity.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(chargeEntity.getWalletType(), is(nullValue()));
        assertThat(chargeEntity.getChargeCardDetails(), is(mockCardDetailsEntity));
        assertThat(chargeEntity.getCanRetry(), is(nullValue()));

        verify(mockStateTransitionService).offerPaymentStateTransition(EXTERNAL_ID, AUTHORISATION_READY, AUTHORISATION_SUCCESS, mockChargeEventEntity);
        verify(mockEventService).emitAndRecordEvent(PaymentDetailsEntered.from(chargeEntity));
    }

    @Test
    void updateChargePostAuthorisationUpdatesChargeAndEmitsEvent_forAuthorisationModeAgreement() {
        var cardDetails = defaultCardDetails();
        var paymentInstrument = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        var agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .build();
        var chargeEntity = chargeEntityFixture
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCardDetails(cardDetails)
                .withAgreementEntity(agreementEntity)
                .build();
        when(mockChargeDao.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(chargeEntity));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetails);
        when(mockChargeEventDao.persistChargeEventOf(chargeEntity, null)).thenReturn(mockChargeEventEntity);

        cardService.updateChargePostCardAuthorisation(EXTERNAL_ID, AUTHORISATION_SUCCESS, TRANSACTION_ID, auth3dsRequiredEntity,
                PROVIDER_SESSION_IDENTIFIER, authCardDetails, TOKEN, true, null);

        assertThat(chargeEntity.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(chargeEntity.getEmail(), is(EMAIL));
        assertThat(chargeEntity.getChargeCardDetails().getProviderSessionId(), is(PROVIDER_SESSION_IDENTIFIER.toString()));
        assertThat(chargeEntity.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(chargeEntity.getWalletType(), is(nullValue()));
        assertThat(chargeEntity.getChargeCardDetails(), is(cardDetails));
        assertThat(chargeEntity.getCanRetry(), is(true));

        assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.ACTIVE));

        verify(mockStateTransitionService).offerPaymentStateTransition(EXTERNAL_ID, AUTHORISATION_READY, AUTHORISATION_SUCCESS, mockChargeEventEntity);
        verify(mockEventService).emitAndRecordEvent(PaymentDetailsTakenFromPaymentInstrument.from(chargeEntity));

        verifyNoInteractions(mockLedgerService);
    }

    @Test
    void updateChargePostAuthorisationUpdatesChargeAndInactivateAgreementAndEmitsEvents_forAuthorisationModeAgreement() {
        var cardDetails = defaultCardDetails();
        var paymentInstrument = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .withCardDetails(cardDetails)
                .build();
        var agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .withServiceId("This is the service id")
                .build();
        var chargeEntity = chargeEntityFixture
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCardDetails(cardDetails)
                .withAgreementEntity(agreementEntity)
                .withPaymentInstrument(paymentInstrument)
                .build();
        when(mockChargeDao.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(chargeEntity));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetails);
        when(mockChargeEventDao.persistChargeEventOf(chargeEntity, null)).thenReturn(mockChargeEventEntity);

        cardService.updateChargePostCardAuthorisation(EXTERNAL_ID, AUTHORISATION_SUCCESS, TRANSACTION_ID, auth3dsRequiredEntity,
                PROVIDER_SESSION_IDENTIFIER, authCardDetails, TOKEN, false, CLOSED_ACCOUNT.name());

        assertThat(chargeEntity.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(chargeEntity.getEmail(), is(EMAIL));
        assertThat(chargeEntity.getChargeCardDetails().getProviderSessionId(), is(PROVIDER_SESSION_IDENTIFIER.toString()));
        assertThat(chargeEntity.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(chargeEntity.getWalletType(), is(nullValue()));
        assertThat(chargeEntity.getChargeCardDetails(), is(cardDetails));
        assertThat(chargeEntity.getCanRetry(), is(false));

        assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.INACTIVE));

        verify(mockStateTransitionService).offerPaymentStateTransition(EXTERNAL_ID, AUTHORISATION_READY, AUTHORISATION_SUCCESS, mockChargeEventEntity);
        verify(mockEventService).emitAndRecordEvent(PaymentDetailsTakenFromPaymentInstrument.from(chargeEntity));

        verify(mockLedgerService).postEvent(agreementInactivatedArgumentCaptor.capture());

        AgreementInactivated event = agreementInactivatedArgumentCaptor.getValue();
        assertThat(event.getEventType(), is("AGREEMENT_INACTIVATED"));
        assertThat(event.getServiceId(), is("This is the service id"));
        assertThat(event.isLive(), is(false));
        AgreementInactivated.AgreementInactivatedEventDetails eventDetails = (AgreementInactivated.AgreementInactivatedEventDetails)event.getEventDetails();
        assertThat(eventDetails.getReason(), is(CLOSED_ACCOUNT.toString()));
        assertThat(eventDetails.getPaymentInstrumentExternalId(), is(paymentInstrument.getExternalId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"web", "moto_api"})
    void updateChargePostAuthorisationShouldNotInactivateAgreement_WhenAuthorisationModeIsNotAgreement(String authMode) {
        var cardDetails = defaultCardDetails();
        var paymentInstrument = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .withCardDetails(cardDetails)
                .build();
        var agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .withServiceId("This is the service id")
                .build();
        var chargeEntity = chargeEntityFixture
                .withAuthorisationMode(AuthorisationMode.of(authMode))
                .withCardDetails(cardDetails)
                .withAgreementEntity(agreementEntity)
                .withPaymentInstrument(paymentInstrument)
                .build();
        when(mockChargeDao.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(chargeEntity));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetails);
        when(mockChargeEventDao.persistChargeEventOf(chargeEntity, null)).thenReturn(mockChargeEventEntity);

        cardService.updateChargePostCardAuthorisation(EXTERNAL_ID, AUTHORISATION_SUCCESS, TRANSACTION_ID, auth3dsRequiredEntity,
                PROVIDER_SESSION_IDENTIFIER, authCardDetails, TOKEN, false, CLOSED_ACCOUNT.name());

        assertThat(chargeEntity.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(chargeEntity.getEmail(), is(EMAIL));
        assertThat(chargeEntity.getChargeCardDetails().getProviderSessionId(), is(PROVIDER_SESSION_IDENTIFIER.toString()));
        assertThat(chargeEntity.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(chargeEntity.getWalletType(), is(nullValue()));
        assertThat(chargeEntity.getChargeCardDetails(), is(cardDetails));
        assertThat(chargeEntity.getCanRetry(), is(nullValue()));

        assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.ACTIVE));

        verify(mockStateTransitionService).offerPaymentStateTransition(EXTERNAL_ID, AUTHORISATION_READY, AUTHORISATION_SUCCESS, mockChargeEventEntity);
        verifyNoInteractions(mockLedgerService);
    }

    @Test
    void updateChargePostAuthorisationWithSavePaymentInstrumentSavesInstrument() {
        var chargeEntity = chargeEntityFixture.withSavePaymentInstrumentToAgreement(true).build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);
        when(mockChargeDao.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentInstrumentService.createPaymentInstrument(chargeEntity, TOKEN)).thenReturn(mockPaymentInstrumentEntity);

        cardService.updateChargePostCardAuthorisation(EXTERNAL_ID, AUTHORISATION_SUCCESS, TRANSACTION_ID, auth3dsRequiredEntity,
                PROVIDER_SESSION_IDENTIFIER, authCardDetails, TOKEN, null, null);

        assertThat(chargeEntity.getPaymentInstrument().isPresent(), is(true));
        assertThat(chargeEntity.getPaymentInstrument().get(), is(mockPaymentInstrumentEntity));
    }

}
