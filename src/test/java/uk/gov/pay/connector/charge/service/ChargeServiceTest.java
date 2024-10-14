package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationErrorToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.UserEmailCollected;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;

import javax.ws.rs.core.UriInfo;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final String[] EXTERNAL_CHARGE_ID = new String[1];
    private static final int RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS = 1;
    private static final int MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS = 10;
    private static ObjectMapper objectMapper = new ObjectMapper();

    private ChargeCreateRequestBuilder requestBuilder;

    @Mock
    private TokenDao mockedTokenDao;

    @Mock
    private ChargeDao mockedChargeDao;

    @Mock
    private ChargeEventDao mockedChargeEventDao;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private GatewayAccountDao mockedGatewayAccountDao;

    @Mock
    private CardTypeDao mockedCardTypeDao;

    @Mock
    private AgreementDao mockedAgreementDao;

    @Mock
    private ConnectorConfiguration mockedConfig;
    
    @Mock
    private UriInfo mockedUriInfo;
    
    @Mock
    private LinksConfig mockedLinksConfig;
    
    @Mock
    private PaymentProviders mockedProviders;
    
    @Mock
    private PaymentProvider mockedPaymentProvider;
    
    @Mock
    private EventService mockEventService;

    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;
    
    @Mock
    private StateTransitionService mockStateTransitionService;

    @Mock
    private RefundService mockedRefundService;
    
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private CaptureProcessConfig mockedCaptureProcessConfig;
    
    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;

    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Captor
    private ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @BeforeEach
    void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("http://return-service.com")
                .withDescription("This is a description")
                .withReference("Pay reference");

        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider("sandbox")
                .withCredentials(Map.of())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        gatewayAccount.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);

        when(mockedConfig.getLinks())
                .thenReturn(mockedLinksConfig);

        when(mockedConfig.getCaptureProcessConfig()).thenReturn(mockedCaptureProcessConfig);
        when(mockedConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory, objectMapper, null);
    }

    @Test
    void forcingChargeToCapturedState_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(CAPTURED).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class))).thenReturn(chargeEventEntity);
        
        ZonedDateTime gatewayEventDate = ZonedDateTime.parse("2021-01-01T01:30:00.000Z");
        ChargeEntity updatedCharge = chargeService.forceTransitionChargeState(charge, CAPTURED, gatewayEventDate);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture(), eq(gatewayEventDate));
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(CAPTURED.getValue()));
        assertThat(updatedCharge.getStatus(), is(CAPTURED.getValue()));
        
        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(), 
                AUTHORISATION_SUCCESS, 
                CAPTURED, 
                chargeEventEntity, 
                StatusCorrectedToCapturedToMatchGatewayStatus.class);
        
        verify(mockTaskQueueService).offerTasksOnStateTransition(charge);
    }

    @Test
    void forcingChargeToAuthorisationError_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(AUTHORISATION_ERROR).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class), eq(null))).thenReturn(chargeEventEntity);

        ChargeEntity updatedCharge = chargeService.forceTransitionChargeState(charge, AUTHORISATION_ERROR, null);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture(), eq(null));
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(updatedCharge.getStatus(), is(AUTHORISATION_ERROR.getValue()));

        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(),
                AUTHORISATION_SUCCESS,
                AUTHORISATION_ERROR,
                chargeEventEntity,
                StatusCorrectedToAuthorisationErrorToMatchGatewayStatus.class);

        verify(mockTaskQueueService).offerTasksOnStateTransition(charge);
    }

    @Test
    void forcingChargeToAuthorisationRejected_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(AUTHORISATION_REJECTED).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class), eq(null))).thenReturn(chargeEventEntity);

        ChargeEntity updatedCharge = chargeService.forceTransitionChargeState(charge, AUTHORISATION_REJECTED, null);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture(), eq(null));
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(updatedCharge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));

        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(),
                AUTHORISATION_SUCCESS,
                AUTHORISATION_REJECTED,
                chargeEventEntity,
                StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus.class);

        verify(mockTaskQueueService).offerTasksOnStateTransition(charge);
    }

    @ParameterizedTest
    @EnumSource(names = {
            "USER_CANCELLED",
            "USER_CANCEL_SUBMITTED",
            "CAPTURE_APPROVED_RETRY"
    })
    void forcingChargeToInvalidState_shouldThrowException(ChargeStatus status) {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        assertThrows(InvalidForceStateTransitionException.class, () -> chargeService.forceTransitionChargeState(charge, status, null));
    }

    @Test
    void shouldUpdateEmailToCharge() {
        ChargeEntity createdChargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(createdChargeEntity)
                .withUpdated(ZonedDateTime.now(UTC))
                .withStatus(ENTERING_CARD_DETAILS).build();
        createdChargeEntity.getEvents().add(chargeEventEntity);
        final String chargeEntityExternalId = createdChargeEntity.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId))
                .thenReturn(Optional.of(createdChargeEntity));

        final String expectedEmail = "test@examplecom";
        PatchRequestBuilder.PatchRequest patchRequest = PatchRequestBuilder.aPatchRequestBuilder(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "email",
                        "value", expectedEmail))
                .withValidOps(singletonList("replace"))
                .withValidPaths(ImmutableSet.of("email"))
                .build();

        Optional<ChargeEntity> chargeEntity = chargeService.updateCharge(chargeEntityExternalId, patchRequest);
        assertThat(chargeEntity.get().getEmail(), is(expectedEmail));
        
        verify(mockEventService).emitAndRecordEvent(any(UserEmailCollected.class));
    }

    @Test
    void shouldUpdateTransactionStatus_whenUpdatingChargeStatusFromInitialStatus() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        chargeService.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        when(mockedChargeDao.findByExternalId(createdChargeEntity.getExternalId()))
                .thenReturn(Optional.of(createdChargeEntity));


        chargeService.updateFromInitialStatus(createdChargeEntity.getExternalId(), ENTERING_CARD_DETAILS);

    }

    @Test
    void shouldBeRetriableGivenChargeHasNotExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS);
        when(mockedCaptureProcessConfig.getMaximumRetries()).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS);

        assertThat(chargeService.isChargeRetriable(anyString()), is(true));
    }

    @Test
    void shouldNotBeRetriableGivenChargeExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS + 1);

        assertThat(chargeService.isChargeRetriable(anyString()), is(false));
    }

    @Test
    void shouldReturnNumberOf3dsRequiredEvents() {
        when(mockedChargeDao.count3dsRequiredEventsForChargeExternalId(EXTERNAL_CHARGE_ID[0])).thenReturn(42);

        int authorisation3dsRequiredEvents = chargeService.count3dsRequiredEvents(EXTERNAL_CHARGE_ID[0]);

        assertThat(authorisation3dsRequiredEvents, is(42));
    }

    @Test
    void shouldUpdateChargeEntityAndPersistChargeEventForAValidStateTransition() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());

        chargeService.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        verify(chargeSpy).setStatus(ENTERING_CARD_DETAILS);
        verify(chargeSpy).setUpdatedDate(any());
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldOfferPaymentStateTransition() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);

        chargeService.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        verify(mockStateTransitionService).offerPaymentStateTransition(chargeSpy.getExternalId(), CREATED,
                ENTERING_CARD_DETAILS, chargeEvent);

        verify(chargeSpy).setUpdatedDate(any());
        verify(mockTaskQueueService).offerTasksOnStateTransition(chargeSpy);
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithoutTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));

        chargeService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_REJECTED, AUTHORISATION_3DS, null,
                null, null, null);

        verify(chargeSpy, never()).setGatewayTransactionId(anyString());
        verify(chargeSpy).setStatus(AUTHORISATION_REJECTED);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        
        chargeService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_SUCCESS, AUTHORISATION_3DS, "transaction-id",
                null, null, null);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).setStatus(AUTHORISATION_SUCCESS);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationIf3dsRequiredAgainAndTransactionId() {
        final Auth3dsRequiredEntity mockedAuth3dsRequiredEntity = mock(Auth3dsRequiredEntity.class);
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .withAuth3dsDetailsEntity(mockedAuth3dsRequiredEntity)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        when(mockedAuth3dsRequiredEntity.getThreeDsVersion()).thenReturn("2.1.0");
        
        chargeService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS, "transaction-id",
                mockedAuth3dsRequiredEntity, ProviderSessionIdentifier.of("provider-session-identifier"), null);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).set3dsRequiredDetails(mockedAuth3dsRequiredEntity);
        verify(chargeSpy).setProviderSessionId("provider-session-identifier");
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
        verify(mockEventService).emitAndRecordEvent(any(Gateway3dsInfoObtained.class));
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithGatewayRecurringAuthToken_whenSavePaymentInstrumentToAgreementTrue() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .withSavePaymentInstrumentToAgreement(true)
                .build());

        Map<String, String> recurringAuthToken = Map.of("token", "foo");
        PaymentInstrumentEntity paymentInstrument = new PaymentInstrumentEntity();

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        when(mockPaymentInstrumentService.createPaymentInstrument(chargeSpy, recurringAuthToken)).thenReturn(paymentInstrument);
        
        chargeService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_SUCCESS, AUTHORISATION_3DS, "transaction-id",
                null, null, recurringAuthToken);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).setStatus(AUTHORISATION_SUCCESS);
        verify(chargeSpy).setPaymentInstrument(paymentInstrument);
        verify(mockPaymentInstrumentService).createPaymentInstrument(chargeSpy, recurringAuthToken);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldCreateRefundAvailabilityUpdatedEvent() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withAmount(1000L)
                .build();
        Charge charge = Charge.from(chargeEntity);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withStatus(RefundStatus.REFUNDED)
                .build();
        Refund refund = Refund.from(refundEntity);
        when(mockedRefundService.findRefunds(charge)).thenReturn(List.of(refund));
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(charge, List.of(refund))).thenReturn(EXTERNAL_AVAILABLE);

        RefundAvailabilityUpdated refundAvailabilityUpdated = chargeService.createRefundAvailabilityUpdatedEvent(charge, Instant.now());

        assertThat(refundAvailabilityUpdated.getResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundAvailabilityUpdated.getResourceType(), is(ResourceType.PAYMENT));
        assertThat(refundAvailabilityUpdated.getEventDetails(), is(instanceOf(RefundAvailabilityUpdatedEventDetails.class)));

        RefundAvailabilityUpdatedEventDetails eventDetails = (RefundAvailabilityUpdatedEventDetails) refundAvailabilityUpdated.getEventDetails();
        assertThat(eventDetails.getRefundAmountAvailable(), is(900L));
        assertThat(eventDetails.getRefundAmountRefunded(), is(100L));
        assertThat(eventDetails.getRefundStatus(), is(EXTERNAL_AVAILABLE.getStatus()));
    }


    @Test
    void shouldAddAuthorisationSummary_whenRequires3dsTrueAndAuth3dsRequiredEntityIsNull() {
        Long chargeId = 101L;
        Long amount = 1000L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withRequires3ds(true)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getAuthorisationSummary(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getRequired(), is(true));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getVersion(), is(nullValue()));
    }

    @Test
    void shouldAddAuthorisationSummary_whenRequires3dsTrueAndAuth3dsRequiredEntityIsNotNull() {
        Long chargeId = 101L;
        Long amount = 1000L;

        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1");

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .withRequires3ds(true)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getAuthorisationSummary(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getRequired(), is(true));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getVersion(), is("2.1"));
    }

    @Test
    void shouldAddAuthorisationSummary_whenRequires3dsIsNullAndAuth3dsRequiredEntityIsNotNull() {
        Long chargeId = 101L;
        Long amount = 1000L;
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1");

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .withRequires3ds(null)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getAuthorisationSummary(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getRequired(), is(true));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getVersion(), is("2.1"));
    }

    @Test
    void shouldAddAuthorisationSummaryRequiredOnly_whenRequires3dsFalseAndAuth3dsRequiredEntityIsNull() {
        Long chargeId = 101L;
        Long amount = 1000L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withRequires3ds(false)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getRequired(), is(false));
    }

    @Test
    void shouldAddAuthorisationSummaryRequiredOnly_whenRequires3dsIsNullAndAuth3dsRequiredEntityIsNull() {
        Long chargeId = 101L;
        Long amount = 1000L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withRequires3ds(null)
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure(), is(notNullValue()));
        assertThat(chargeResponse.getAuthorisationSummary().getThreeDSecure().getRequired(), is(false));
    }

    @Nested
    class TestGetLongestDurationOfChargesAwaitingCaptureInMinutes {
        @Test
        void shouldReturnLongestDurationOfChargeAwaitingCapture() {
            when(mockedChargeDao.getEarliestUpdatedDateOfChargesReadyForImmediateCapture(60)).thenReturn(
                    Instant.now().minus(7230, SECONDS)
            );

            Integer result = chargeService.getLongestDurationOfChargesAwaitingCaptureInMinutes(60);
            assertThat(result, is(120));
        }

        @Test
        void shouldReturnNullIfNoChargesAreAwaitingCapture() {
            when(mockedChargeDao.getEarliestUpdatedDateOfChargesReadyForImmediateCapture(60)).thenReturn(null);

            Integer result = chargeService.getLongestDurationOfChargesAwaitingCaptureInMinutes(60);
            assertThat(result, is(nullValue()));
        }
    }
}
