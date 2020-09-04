package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
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
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationErrorToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.northamericaregion.UsState;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.ChargeResponse.ChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
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
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;

@RunWith(JUnitParamsRunner.class)
public class ChargeServiceTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    protected static final String SERVICE_HOST = "http://my-service";
    protected static final long GATEWAY_ACCOUNT_ID = 10L;
    protected static final long CHARGE_ENTITY_ID = 12345L;
    protected static final String[] EXTERNAL_CHARGE_ID = new String[1];
    protected static final int RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS = 1;
    protected static final int MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS = 10;
    protected static final List<Map<String, Object>> EMPTY_LINKS = new ArrayList<>();

    protected ChargeCreateRequestBuilder requestBuilder;
    protected TelephoneChargeCreateRequest.Builder telephoneRequestBuilder;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    protected TokenDao mockedTokenDao;
    
    @Mock
    protected ChargeDao mockedChargeDao;
    
    @Mock
    protected ChargeEventDao mockedChargeEventDao;
    
    @Mock
    protected ChargeEventEntity mockChargeEvent;

    @Mock
    protected LedgerService ledgerService;

    @Mock
    protected GatewayAccountDao mockedGatewayAccountDao;
    
    @Mock
    protected CardTypeDao mockedCardTypeDao;
    
    @Mock
    protected ConnectorConfiguration mockedConfig;
    
    @Mock
    protected UriInfo mockedUriInfo;
    
    @Mock
    protected LinksConfig mockedLinksConfig;
    
    @Mock
    protected PaymentProviders mockedProviders;
    
    @Mock
    protected PaymentProvider mockedPaymentProvider;
    
    @Mock
    protected EventService mockEventService;
    
    @Mock
    protected StateTransitionService mockStateTransitionService;

    @Mock
    protected RefundDao mockRefundDao;

    @Mock
    protected NorthAmericanRegionMapper mockNorthAmericanRegionMapper;

    @Mock
    protected CaptureProcessConfig mockedCaptureProcessConfig;
    
    @Captor
    protected ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;
    
    @Captor ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor;

    protected ChargeService service;
    protected GatewayAccountEntity gatewayAccount;

    @Before
    public void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("http://return-service.com")
                .withDescription("This is a description")
                .withReference("Pay reference");

        telephoneRequestBuilder = new TelephoneChargeCreateRequest.Builder()
                .withAmount(100L)
                .withReference("Some reference")
                .withDescription("Some description")
                .withCreatedDate("2018-02-21T16:04:25Z")
                .withAuthorisedDate("2018-02-21T16:05:33Z")
                .withProcessorId("1PROC")
                .withProviderId("1PROV")
                .withAuthCode("666")
                .withNameOnCard("Jane Doe")
                .withEmailAddress("jane.doe@example.com")
                .withTelephoneNumber("+447700900796")
                .withCardType("visa")
                .withCardExpiry("01/19")
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456");

        gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        when(mockedConfig.getLinks())
                .thenReturn(mockedLinksConfig);

        when(mockedConfig.getCaptureProcessConfig()).thenReturn(mockedCaptureProcessConfig);
        when(mockedConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        service = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockEventService, mockRefundDao, mockNorthAmericanRegionMapper);
    }

    @After
    public void tearDown() {
        telephoneRequestBuilder = null;
    }

    @Test
    public void forcingChargeToCapturedState_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(CAPTURED).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class))).thenReturn(chargeEventEntity);
        
        ChargeEntity updatedCharge = service.forceTransitionChargeState(charge, CAPTURED);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(CAPTURED.getValue()));
        assertThat(updatedCharge.getStatus(), is(CAPTURED.getValue()));
        
        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(), 
                AUTHORISATION_SUCCESS, 
                CAPTURED, 
                chargeEventEntity, 
                StatusCorrectedToCapturedToMatchGatewayStatus.class);
    }

    @Test
    public void forcingChargeToAuthorisationError_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(AUTHORISATION_ERROR).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class))).thenReturn(chargeEventEntity);

        ChargeEntity updatedCharge = service.forceTransitionChargeState(charge, AUTHORISATION_ERROR);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(updatedCharge.getStatus(), is(AUTHORISATION_ERROR.getValue()));

        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(),
                AUTHORISATION_SUCCESS,
                AUTHORISATION_ERROR,
                chargeEventEntity,
                StatusCorrectedToAuthorisationErrorToMatchGatewayStatus.class);
    }


    @Test
    public void forcingChargeToAuthorisationRejected_shouldSucceedAndEmitEvent() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();

        ChargeEventEntity chargeEventEntity = aChargeEventEntity().withChargeEntity(charge).withStatus(AUTHORISATION_REJECTED).build();
        when(mockedChargeEventDao.persistChargeEventOf(any(ChargeEntity.class))).thenReturn(chargeEventEntity);

        ChargeEntity updatedCharge = service.forceTransitionChargeState(charge, AUTHORISATION_REJECTED);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(updatedCharge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));

        verify(mockStateTransitionService).offerPaymentStateTransition(
                charge.getExternalId(),
                AUTHORISATION_SUCCESS,
                AUTHORISATION_REJECTED,
                chargeEventEntity,
                StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus.class);
    }
    
    @Test(expected = InvalidForceStateTransitionException.class)
    @Parameters({
            "USER CANCELLED",
            "USER CANCEL SUBMITTED",
            "CAPTURE APPROVED RETRY"
    })
    public void forcingChargeToInvalidState_shouldThrowException(String status) {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().withStatus(AUTHORISATION_SUCCESS).build();
        service.forceTransitionChargeState(charge, ChargeStatus.fromString(status));
    }
    
    @Test
    public void shouldUpdateEmailToCharge() {
        ChargeEntity createdChargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
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

        service.updateCharge(chargeEntityExternalId, patchRequest);
    }

    @Test
    public void shouldUpdateTransactionStatus_whenUpdatingChargeStatusFromInitialStatus() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), any(List.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        when(mockedChargeDao.findByExternalId(createdChargeEntity.getExternalId()))
                .thenReturn(Optional.of(createdChargeEntity));


        service.updateFromInitialStatus(createdChargeEntity.getExternalId(), ENTERING_CARD_DETAILS);

    }

    @Deprecated
    protected ChargeResponseBuilder chargeResponseBuilderOf(ChargeEntity chargeEntity) {
        ChargeResponse.RefundSummary refunds = new ChargeResponse.RefundSummary();
        refunds.setAmountAvailable(chargeEntity.getAmount());
        refunds.setAmountSubmitted(0L);
        refunds.setStatus(EXTERNAL_AVAILABLE.getStatus());

        ChargeResponse.SettlementSummary settlement = new ChargeResponse.SettlementSummary();
        settlement.setCapturedTime(null);
        settlement.setCaptureSubmitTime(null);

        ExternalChargeState externalChargeState = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal();
        return aChargeResponseBuilder()
                .withChargeId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()))
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withEmail(chargeEntity.getEmail())
                .withRefunds(refunds)
                .withSettlement(settlement)
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withLanguage(chargeEntity.getLanguage())
                .withMoto(chargeEntity.isMoto());
    }

    @Test
    public void shouldBeRetriableGivenChargeHasNotExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS);
        when(mockedCaptureProcessConfig.getMaximumRetries()).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS);

        assertThat(service.isChargeRetriable(anyString()), is(true));
    }

    @Test
    public void shouldNotBeRetriableGivenChargeExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS + 1);

        assertThat(service.isChargeRetriable(anyString()), is(false));
    }

    @Test
    public void shouldReturnNumberOf3dsRequiredEvents() {
        when(mockedChargeDao.count3dsRequiredEventsForChargeExternalId(EXTERNAL_CHARGE_ID[0])).thenReturn(42);

        int authorisation3dsRequiredEvents = service.count3dsRequiredEvents(EXTERNAL_CHARGE_ID[0]);

        assertThat(authorisation3dsRequiredEvents, is(42));
    }

    @Test
    public void shouldUpdateChargeEntityAndPersistChargeEventForAValidStateTransition() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());

        service.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        verify(chargeSpy).setStatus(ENTERING_CARD_DETAILS);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    public void shouldOfferPaymentStateTransition() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);

        service.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        verify(mockStateTransitionService).offerPaymentStateTransition(chargeSpy.getExternalId(), CREATED,
                ENTERING_CARD_DETAILS, chargeEvent);
    }

    @Test
    public void updateChargeAndEmitEventPostAuthorisation_shouldEmitEvent() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(chargeEvent.getStatus()).thenReturn(ENTERING_CARD_DETAILS);
        when(chargeEvent.getUpdated()).thenReturn(now());
        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);
        when(mockedChargeDao.findByExternalId(chargeSpy.getExternalId())).thenReturn(Optional.of(chargeSpy));
        when(chargeSpy.getEvents()).thenReturn(List.of(chargeEvent));

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("1234567890");
        service.updateChargeAndEmitEventPostAuthorisation(chargeSpy.getExternalId(), ENTERING_CARD_DETAILS,
                authCardDetails, null, null, null, null, null);

        verify(mockEventService).emitAndRecordEvent(PaymentDetailsEntered.from(chargeSpy));
    }

    @Test
    public void updateChargeAndEmitEventPostAuthorisation_shouldEmitEventWhenCountryIsInUnitedStates() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        AuthCardDetails authCardDetails = new AuthCardDetails();
        Address address = new Address();
        address.setCountry("US");
        address.setPostcode("20500");
        authCardDetails.setAddress(address);
        authCardDetails.setCardNo("1234567890");

        when(chargeEvent.getStatus()).thenReturn(ENTERING_CARD_DETAILS);
        when(chargeEvent.getUpdated()).thenReturn(now());
        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);
        when(mockedChargeDao.findByExternalId(chargeSpy.getExternalId())).thenReturn(Optional.of(chargeSpy));
        when(mockNorthAmericanRegionMapper.getNorthAmericanRegionForCountry(any(Address.class))).thenAnswer((Answer<Optional<? extends NorthAmericaRegion>>) invocationOnMock -> Optional.of(UsState.WASHINGTON_DC));
        when(chargeSpy.getEvents()).thenReturn(List.of(chargeEvent));

        service.updateChargeAndEmitEventPostAuthorisation(chargeSpy.getExternalId(), ENTERING_CARD_DETAILS,
                authCardDetails, null, null, null, null, null);

        verify(mockEventService).emitAndRecordEvent(PaymentDetailsEntered.from(chargeSpy));
    }

    @Test
    public void shouldUpdateStateOrProvinceForChargeWhenCountryIsUs() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        AuthCardDetails authCardDetails = new AuthCardDetails();
        Address address = new Address();
        address.setCountry("US");
        address.setPostcode("20500");
        authCardDetails.setAddress(address);
        authCardDetails.setCardNo("1234567890");

        when(chargeEvent.getStatus()).thenReturn(ENTERING_CARD_DETAILS);
        when(chargeEvent.getUpdated()).thenReturn(now());
        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);
        when(mockedChargeDao.findByExternalId(chargeSpy.getExternalId())).thenReturn(Optional.of(chargeSpy));
        when(mockNorthAmericanRegionMapper.getNorthAmericanRegionForCountry(any(Address.class))).thenAnswer((Answer<Optional<? extends NorthAmericaRegion>>) invocationOnMock -> Optional.of(UsState.WASHINGTON_DC));
        when(chargeSpy.getEvents()).thenReturn(List.of(chargeEvent));

        service.updateChargeAndEmitEventPostAuthorisation(chargeSpy.getExternalId(), ENTERING_CARD_DETAILS,
                authCardDetails, null, null, null, null, null);

        assertThat(chargeSpy.getCardDetails().getBillingAddress().get().getStateOrProvince(), is("DC"));
    }

    @Test
    public void shouldNotUpdateStateOrProvinceForChargeWhenCountryIsNotInNorthAmerica() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        AuthCardDetails authCardDetails = new AuthCardDetails();
        Address address = new Address();
        address.setCountry("GB");
        address.setPostcode("C48 4AJ");
        authCardDetails.setAddress(address);
        authCardDetails.setCardNo("1234567890");

        when(chargeEvent.getStatus()).thenReturn(ENTERING_CARD_DETAILS);
        when(chargeEvent.getUpdated()).thenReturn(now());
        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);
        when(mockedChargeDao.findByExternalId(chargeSpy.getExternalId())).thenReturn(Optional.of(chargeSpy));
        when(chargeSpy.getEvents()).thenReturn(List.of(chargeEvent));

        service.updateChargeAndEmitEventPostAuthorisation(chargeSpy.getExternalId(), ENTERING_CARD_DETAILS,
                authCardDetails, null, null, null, null, null);

        assertThat(chargeSpy.getCardDetails().getBillingAddress().get().getStateOrProvince(), is(nullValue()));
    }

    @Test
    public void shouldNotTransitionChargeStateForNonSkippableNonConfirmedCharge() {
        ChargeEntity createdChargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        final String chargeEntityExternalId = createdChargeEntity.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(createdChargeEntity));

        thrown.expect(ConflictRuntimeException.class);
        thrown.expectMessage("HTTP 409 Conflict");
        service.markDelayedCaptureChargeAsCaptureApproved(chargeEntityExternalId);
    }

    @Test
    public void shouldUpdateChargePost3dsAuthorisationWithoutTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));

        service.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_REJECTED, AUTHORISATION_3DS, null,
                null, null);

        verify(chargeSpy, never()).setGatewayTransactionId(anyString());
        verify(chargeSpy).setStatus(AUTHORISATION_REJECTED);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    public void shouldUpdateChargePost3dsAuthorisationWithTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        
        service.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_SUCCESS, AUTHORISATION_3DS, "transaction-id",
                null, null);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).setStatus(AUTHORISATION_SUCCESS);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    public void shouldUpdateChargePost3dsAuthorisationIf3dsRequiredAgainAndTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));

        final Auth3dsRequiredEntity mockedAuth3dsRequiredEntity = mock(Auth3dsRequiredEntity.class);
        
        service.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS, "transaction-id",
                mockedAuth3dsRequiredEntity, ProviderSessionIdentifier.of("provider-session-identifier"));

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).set3dsRequiredDetails(mockedAuth3dsRequiredEntity);
        verify(chargeSpy).setProviderSessionId("provider-session-identifier");
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }
}
