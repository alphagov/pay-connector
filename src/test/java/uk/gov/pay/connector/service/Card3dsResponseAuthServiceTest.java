package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;

@RunWith(MockitoJUnitRunner.class)
public class Card3dsResponseAuthServiceTest extends CardServiceTest {

    public static final String PA_REQ_VALUE_FROM_PROVIDER = "pa-req-value-from-provider";
    public static final String ISSUER_URL_FROM_PROVIDER = "issuer-url-from-provider";

    private final Auth3dsDetailsFactory auth3dsDetailsFactory = new Auth3dsDetailsFactory();

    @Mock
    private Future<Either<Error, GatewayResponse>> mockFutureResponse;

    private ChargeEntity charge = createNewChargeWith(1L, AUTHORISATION_3DS_REQUIRED);
    private ChargeEntity reloadedCharge = spy(charge);

    private Card3dsResponseAuthService card3dsResponseAuthService;
    private CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    @Before
    public void setUpCardAuthorisationService() {
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        card3dsResponseAuthService = new Card3dsResponseAuthService(mockedChargeDao, mockedProviders, mockExecutorService, mockMetricRegistry);
    }

    public void setupMockExecutorServiceMock() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private void setupPaymentProviderMock(String transactionId, AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponse authorisationResponse = GatewayResponse.with(worldpayResponse);
        when(mockedPaymentProvider.authorise3dsResponse(any())).thenReturn(authorisationResponse);
    }

    @Test
    public void shouldRespondAuthorisationSuccess() throws Exception {
        String transactionId = "transaction-id";
        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();

        GatewayResponse response = anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, auth3dsDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
    }




//    @Test
//    public void shouldRespondWith3dsResponseFor3dsOrders() {
//        String transactionId = "transaction-id";
//        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
//
//        GatewayResponse response = anAuthorisation3dsRequiredResponse(charge, reloadedCharge, transactionId, authCardDetails);
//
//        assertThat(response.isSuccessful(), is(true));
//        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.toString()));
//        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
//        assertThat(reloadedCharge.get3dsDetails().getPaRequest(), is(PA_REQ_VALUE_FROM_PROVIDER));
//        assertThat(reloadedCharge.get3dsDetails().getIssuerUrl(), is(ISSUER_URL_FROM_PROVIDER));
//    }
//
//    @Test
//    public void shouldRespondAuthorisationSuccess_whenTransactionIdIsGenerated() throws Exception {
//        String generatedTransactionId = "generated-transaction-id";
//        String providerTransactionId = "provider-transaction-id";
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
//        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
//
//        setupMockExecutorServiceMock();
//        setupPaymentProviderMock(providerTransactionId, AuthoriseStatus.AUTHORISED, null);
//
//        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
//        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
//
//        GatewayResponse response = card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//
//        assertThat(response.isSuccessful(), is(true));
//        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
//        assertThat(reloadedCharge.getGatewayTransactionId(), is(providerTransactionId));
//    }
//
//    @Test
//    public void shouldRetainGeneratedTransactionIdIfAuthorisationAborted() throws Exception {
//        String generatedTransactionId = "generated-transaction-id";
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
//        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
//
//        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
//        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
//
//        setupMockExecutorServiceMock();
//
//        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);
//
//        try {
//            card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//            fail("Won’t get this far");
//        } catch (RuntimeException e) {
//            assertThat(reloadedCharge.getGatewayTransactionId(), is(generatedTransactionId));
//        }
//    }
//
//
//    @Test
//    public void shouldRespondAuthorisationRejected() throws Exception {
//        String transactionId = "transaction-id";
//        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
//        ChargeEntity reloadedCharge = spy(charge);
//        GatewayResponse response = anAuthorisationRejectedResponse(charge, reloadedCharge);
//
//        assertThat(response.isSuccessful(), is(true));
//        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_REJECTED.toString()));
//        assertThat(reloadedCharge.getGatewayTransactionId(), is(transactionId));
//    }
//
//    @Test
//    public void shouldRespondAuthorisationError() throws Exception {
//        GatewayResponse response = anAuthorisationErrorResponse(charge, reloadedCharge);
//
//        assertThat(response.isFailed(), is(true));
//        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ERROR.toString()));
//        assertThat(reloadedCharge.getGatewayTransactionId(), is(nullValue()));
//    }
//
//    @Test
//    public void shouldStoreCardDetailsIfAuthorisationSuccess() {
//        String transactionId = "transaction-id";
//        AuthCardDetails authCardDetails = AuthUtils.aValidAuthorisationDetails();
//        anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, authCardDetails);
//
//        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
//    }
//
//    @Test
//    public void shouldStoreCardDetailsEvenIfAuthorisationRejected() {
//        anAuthorisationRejectedResponse(charge, reloadedCharge);
//        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
//    }
//
//    @Test
//    public void shouldStoreCardDetailsEvenIfInAuthorisationError() {
//        anAuthorisationErrorResponse(charge, reloadedCharge);
//        assertThat(reloadedCharge.getCardDetails(), is(notNullValue()));
//    }
//
//    @Test
//    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(any())).thenReturn(charge);
//        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));
//
//        try {
//            card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//            fail("Exception not thrown.");
//        } catch (OperationAlreadyInProgressRuntimeException e) {
//            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
//            assertThat(e.getResponse().getEntity(), is(expectedMessage));
//        }
//    }
//    @Test(expected = ChargeNotFoundRuntimeException.class)
//    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {
//        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
//
//        when(mockedChargeDao.findByExternalId(chargeId))
//                .thenReturn(Optional.empty());
//
//        card3dsResponseAuthService.doAuthorise(chargeId, AuthUtils.aValidAuthorisationDetails());
//    }
//
//    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
//    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisationReady() {
//        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(any())).thenReturn(charge);
//
//        setupMockExecutorServiceMock();
//
//        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
//    }
//
//    @Test(expected = IllegalStateRuntimeException.class)
//    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {
//        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.CREATED);
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(any())).thenReturn(charge);
//
//        setupMockExecutorServiceMock();
//
//        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
//    }
//
//    @Test(expected = ConflictRuntimeException.class)
//    public void shouldThrowAConflictRuntimeException() throws Exception {
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());
//
//        setupMockExecutorServiceMock();
//
//        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
//    }
//
//    private GatewayResponse anAuthorisationRejectedResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
//        String transactionId = "transaction-id";
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
//        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
//
//        setupMockExecutorServiceMock();
//        setupPaymentProviderMock(transactionId, AuthoriseStatus.REJECTED, null);
//
//        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
//        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
//
//        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//    }
//
    private GatewayResponse anAuthorisationSuccessResponse(ChargeEntity charge, ChargeEntity reloadedCharge, String transactionId, Auth3dsDetails auth3dsDetails) {

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, AuthoriseStatus.AUTHORISED, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), auth3dsDetails);
    }
//
//    private GatewayResponse anAuthorisation3dsRequiredResponse(ChargeEntity charge, ChargeEntity reloadedCharge, String transactionId, AuthCardDetails authCardDetails) {
//
//        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
//                .thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
//        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
//
//        setupMockExecutorServiceMock();
//        setupPaymentProviderMockFor3ds();
//
//        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
//        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(transactionId));
//
//        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), authCardDetails);
//    }
//
//    private GatewayResponse anAuthorisationErrorResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
//        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
//        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
//        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);
//
//        setupMockExecutorServiceMock();
//        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, "error-code");
//
//        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
//        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
//
//        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());
//    }
}
