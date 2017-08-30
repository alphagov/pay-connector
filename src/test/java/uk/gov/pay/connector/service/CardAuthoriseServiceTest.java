package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import javax.persistence.OptimisticLockException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.GatewayError.gatewayConnectionTimeoutException;
import static uk.gov.pay.connector.model.GatewayError.malformedResponseReceivedFromGateway;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {

    public static final String PA_REQ_VALUE_FROM_PROVIDER = "pa-req-value-from-provider";
    public static final String ISSUER_URL_FROM_PROVIDER = "issuer-url-from-provider";
    public static final String SESSION_IDENTIFIER = "session-identifier";

    private final Auth3dsDetailsFactory auth3dsDetailsFactory = new Auth3dsDetailsFactory();

    @Mock
    private Future<Either<Error, GatewayResponse>> mockFutureResponse;

    private ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
    private ChargeEntity reloadedCharge = spy(charge);

    private CardAuthoriseService cardAuthorisationService;
    private CardExecutorService mockExecutorService = mock(CardExecutorService.class);
    private final String providerTransactionId = "transaction-id";

    @Before
    public void setUpCardAuthorisationService() {
        Environment mockEnvironment = mock(Environment.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedCardTypeDao, mockedProviders, mockExecutorService,
                auth3dsDetailsFactory, mockEnvironment);
    }

    @Before
    public void configureChargeDaoMock() {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
    }

    public void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private GatewayResponse mockAuthResponse(String providerTransactionId, AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(providerTransactionId);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    private void setupPaymentProviderMock(GatewayError gatewayError) {
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse authorisationResponse = gatewayResponseBuilder
                .withGatewayError(gatewayError)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(authorisationResponse);
    }

    @Test
    public void shouldRespondAuthorisationSuccess() throws Exception {
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
        providerWillAuthorise();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldRespondAuthorisationFailedWhen3dsRequiredConflictingConfigurationOfCardTypeWithGatewayAccount() throws Exception {
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        CardTypeEntity cardTypeEntity = new CardTypeEntity();
        cardTypeEntity.setRequires3ds(true);
        cardTypeEntity.setBrand(authCardDetails.getCardBrand());
        gatewayAccountEntity.setType(GatewayAccountEntity.Type.LIVE);
        gatewayAccountEntity.setGatewayName("worldpay");
        gatewayAccountEntity.setRequires3ds(false);

        ChargeEntity charge = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withStatus(ENTERING_CARD_DETAILS)
                .build();
        ChargeEntity reloadedCharge = spy(charge);

        when(mockedCardTypeDao.findByBrand(authCardDetails.getCardBrand())).thenReturn(newArrayList(cardTypeEntity));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty())).thenReturn(reloadedCharge);

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(response.isSuccessful(), is(false));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ABORTED.toString()));
    }

    @Test
    public void shouldRespondWith3dsResponseFor3dsOrders() {
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
        providerWillRequire3ds();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
        assertThat(reloadedCharge.get3dsDetails().getPaRequest(), is(PA_REQ_VALUE_FROM_PROVIDER));
        assertThat(reloadedCharge.get3dsDetails().getIssuerUrl(), is(ISSUER_URL_FROM_PROVIDER));
    }

    @Test
    public void shouldRespondAuthorisationSuccess_whenTransactionIdIsGenerated() throws Exception {
        String generatedTransactionId = "generated-transaction-id";
        String providerTransactionId = "provider-transaction-id";

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.AUTHORISED, null);
        when(mockedPaymentProvider.authorise(any())).thenReturn(authResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
    }

    @Test
    public void shouldRetainGeneratedTransactionIdIfAuthorisationAborted() throws Exception {
        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(reloadedCharge.getGatewayTransactionId(), is(generatedTransactionId));
        }
    }

    @Test
    public void shouldRespondAuthorisationRejected() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.REJECTED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_REJECTED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
    }

    @Test
    public void shouldRespondAuthorisationCancelled() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.CANCELLED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_CANCELLED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {
        providerWillReject();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ERROR.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void shouldStoreCardDetailsIfAuthorisationSuccess() {
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
        providerWillAuthorise();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreCardDetailsEvenIfAuthorisationRejected() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.REJECTED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreCardDetailsEvenIfInAuthorisationError() {
        providerWillReject();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
    }

    @Test
    public void shouldStoreProviderSessionIdIfAuthorisationSuccess() {
        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
        providerWillAuthorise();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(reloadedCharge.getProviderSessionId(), is(SESSION_IDENTIFIER));
    }

    @Test
    public void shouldStoreProviderSessionIdIfAuthorisationRejected() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.REJECTED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
        assertThat(reloadedCharge.getProviderSessionId(), is(SESSION_IDENTIFIER));
    }

    @Test
    public void shouldNotProviderSessionIdEvenIfInAuthorisationError() {
        providerWillReject();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
        assertNull(reloadedCharge.getProviderSessionId());
    }

    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
            assertThat(e.getResponse().getEntity(), is(expectedMessage));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        cardAuthorisationService.doAuthorise(chargeId, AuthUtils.aValidAuthorisationDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisationReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.CREATED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldThrowAConflictRuntimeException() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    public void shouldReportAuthorisationTimeout_whenProviderTimeout(){
        GatewayError gatewayError = gatewayConnectionTimeoutException("Connection timed out");
        providerWillRespondWithError(gatewayError);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_TIMEOUT.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void shouldReportUnexpectedError_whenProviderError() {
        GatewayError gatewayError = malformedResponseReceivedFromGateway("Malformed response received");
        providerWillRespondWithError(gatewayError);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(nullValue()));
    }

    private void providerWillRespondToAuthoriseWith(GatewayResponse value) {
        when(mockedPaymentProvider.authorise(any())).thenReturn(value);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillAuthorise() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(providerTransactionId, AuthoriseStatus.AUTHORISED, null);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillRequire3ds() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        WorldpayOrderStatusResponse worldpayResponse = new WorldpayOrderStatusResponse();
        worldpayResponse.set3dsPaRequest(PA_REQ_VALUE_FROM_PROVIDER);
        worldpayResponse.set3dsIssuerUrl(ISSUER_URL_FROM_PROVIDER);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse worldpay3dsResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(worldpay3dsResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(providerTransactionId));
    }

    private void providerWillReject() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(null, AuthoriseStatus.REJECTED, "error-code");
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillRespondWithError(GatewayError gatewayError) {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        setupPaymentProviderMock(gatewayError);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        //TODO ?
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }
}
