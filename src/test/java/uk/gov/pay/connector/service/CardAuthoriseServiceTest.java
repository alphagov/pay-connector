package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
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
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.epdq.EpdqAuthorisationResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import javax.persistence.OptimisticLockException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import static uk.gov.pay.connector.util.AuthUtils.aValidAuthorisationDetails;
import static uk.gov.pay.connector.util.AuthUtils.addressFor;
import static uk.gov.pay.connector.util.AuthUtils.buildAuthCardDetails;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {

    private static final String PA_REQ_VALUE_FROM_PROVIDER = "pa-req-value-from-provider";
    private static final String ISSUER_URL_FROM_PROVIDER = "issuer-url-from-provider";
    private static final String SESSION_IDENTIFIER = "session-identifier";
    private static final String TRANSACTION_ID = "transaction-id";

    private final ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private Counter mockCounter;

    private CardAuthoriseService cardAuthorisationService;

    @Before
    public void setUpCardAuthorisationService() {
        mockMetricRegistry = mock(MetricRegistry.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedProviders, mockExecutorService,
                mockEnvironment);
    }

    @Before
    public void configureChargeDaoMock() {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
    }

    public void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private GatewayResponse mockAuthResponse(String TRANSACTION_ID, AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(TRANSACTION_ID);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        when(worldpayResponse.getGatewayParamsFor3ds()).thenReturn(Optional.empty());
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
    public void doAuthorise_shouldRespondAuthorisationSuccess() {

        providerWillAuthorise();
        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getSessionIdentifier().get(), is(SESSION_IDENTIFIER));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
        assertThat(charge.get3dsDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(notNullValue()));

    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationSuccess_overridingGeneratedTransactionId() {

        providerWillAuthorise();

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getSessionIdentifier().get(), is(SESSION_IDENTIFIER));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.get3dsDetails(), is(nullValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
    }

    @Test
    public void doAuthorise_shouldRespondWith3dsResponseFor3dsOrders() {

        worldpayProviderWillRequire3ds(null);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
        assertThat(charge.get3dsDetails().getIssuerUrl(), is(ISSUER_URL_FROM_PROVIDER));
        assertThat(charge.get3dsDetails().getPaRequest(), is(PA_REQ_VALUE_FROM_PROVIDER));
    }

    @Test
    public void doAuthorise_shouldRespondWith3dsResponseForEpdq3dsOrders() {
        epdqProviderWillRequire3ds();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
        assertThat(charge.get3dsDetails().getHtmlOut(), is(notNullValue()));
    }

    @Test
    public void doAuthorise_shouldRespondWith3dsResponseFor3dsOrdersWithWorldpayMachineCookie() {

        worldpayProviderWillRequire3ds(SESSION_IDENTIFIER);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
        assertThat(charge.get3dsDetails().getIssuerUrl(), is(ISSUER_URL_FROM_PROVIDER));
        assertThat(charge.get3dsDetails().getPaRequest(), is(PA_REQ_VALUE_FROM_PROVIDER));
    }

    @Test
    public void doAuthorise_shouldRetainGeneratedTransactionId_WhenProviderAuthorisationFails() {

        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(charge.getGatewayTransactionId(), is(generatedTransactionId));
        }
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationFailed_When3dsRequiredConflictingConfigurationOfCardTypeWithGatewayAccount() {

        AuthCardDetails authCardDetails = aValidAuthorisationDetails();

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

        when(mockedCardTypeDao.findByBrand(authCardDetails.getCardBrand())).thenReturn(newArrayList(cardTypeEntity));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);
            fail("Expected test to fail with ConflictRuntimeException due to configuration conflicting in 3ds requirements");
        } catch (ConflictRuntimeException e) {
            assertThat(charge.getStatus(), is(AUTHORISATION_ABORTED.toString()));
            verify(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());
        }
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() {

        providerWillReject();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() {

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationError() {

        providerWillError();

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreExpectedCardDetails_whenAuthorisationSuccess() {

        String cardholderName = "Mr Bright";
        String cardNumber = "4242424242424242";
        String cvc = "123";
        String expiryDate = "12/23";
        String cardBrand = "visa";
        String addressLine1 = "Line 1";
        String addressLine2 = "Line 1";
        String county = "Whatever";
        String city = "London";
        String postcode = "W2 6YG";
        String country = "UK";

        AuthCardDetails authCardDetails = buildAuthCardDetails(cardholderName, cardNumber, cvc, expiryDate, cardBrand,
                addressFor(addressLine1, addressLine2, city, postcode, county, country));

        providerWillAuthorise();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails.getCardHolderName(), is(cardholderName));
        assertThat(cardDetails.getCardBrand(), is(cardBrand));
        assertThat(cardDetails.getExpiryDate(), is(expiryDate));
        assertThat(cardDetails.getLastDigitsCardNumber(), is("4242"));
        assertThat(cardDetails.getFirstDigitsCardNumber(), is("424242"));
        assertThat(cardDetails.getBillingAddress().getLine1(), is(addressLine1));
        assertThat(cardDetails.getBillingAddress().getLine2(), is(addressLine2));
        assertThat(cardDetails.getBillingAddress().getPostcode(), is(postcode));
        assertThat(cardDetails.getBillingAddress().getCity(), is(city));
        assertThat(cardDetails.getBillingAddress().getCountry(), is(country));
        assertThat(cardDetails.getBillingAddress().getCounty(), is(county));
    }

    @Test
    public void doAuthorise_shouldStoreCardDetails_evenIfAuthorisationRejected() {

        providerWillReject();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreCardDetails_evenIfInAuthorisationError() {

        providerWillError();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreProviderSessionId_evenIfAuthorisationRejected() {

        providerWillReject();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER));
    }

    @Test
    public void doAuthorise_shouldNotProviderSessionId_whenAuthorisationError() {

        providerWillError();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(charge.getProviderSessionId(), is(nullValue()));
    }

    @Test
    public void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenTimeout() {

        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
            assertThat(e.getResponse().getEntity(), is(expectedMessage));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void doAuthorise_shouldThrowAChargeNotFoundRuntimeException_whenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        cardAuthorisationService.doAuthorise(chargeId, aValidAuthorisationDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenStatusIsAuthorisationReady() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void doAuthorise_shouldThrowAnIllegalStateRuntimeException_whenInvalidStatus() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.CREATED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    public void doAuthorise_shouldReportAuthorisationTimeout_whenProviderTimeout() {
        GatewayError gatewayError = gatewayConnectionTimeoutException("Connection timed out");
        providerWillRespondWithError(gatewayError);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test
    public void doAuthorise_shouldReportUnexpectedError_whenProviderError() {

        GatewayError gatewayError = malformedResponseReceivedFromGateway("Malformed response received");
        providerWillRespondWithError(gatewayError);

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), aValidAuthorisationDetails());

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    @Test(expected = ConflictRuntimeException.class)
    public void doAuthorise_shouldThrowAConflictRuntimeException_whenOptimisticLockExceptionIsThrownInPreAuthorise() {

        providerWillAuthorise();

        /**
         * FIXME (PP-2626)
         * This is not going to be thrown from this method, but just to test preOp throwing
         * OptimisticLockException when commit the transaction. We won't do merge in pre-op
         * The related code won't be removed until we know is not an issue doing it so, logging
         * will be in place since there are not evidence (through any test or current logging)
         * that is in reality a subject of a real scenario.
         */

        doThrow(new OptimisticLockException())
                .when(mockedChargeDao).findByExternalId(charge.getExternalId());

        cardAuthorisationService.doAuthorise(charge.getExternalId(), AuthUtils.aValidAuthorisationDetails());

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private void providerWillRespondToAuthoriseWith(GatewayResponse value) {
        when(mockedPaymentProvider.authorise(any())).thenReturn(value);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillAuthorise() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.AUTHORISED, null);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void worldpayProviderWillRequire3ds(String sessionIdentifier) {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        WorldpayOrderStatusResponse worldpayResponse = new WorldpayOrderStatusResponse();
        worldpayResponse.set3dsPaRequest(PA_REQ_VALUE_FROM_PROVIDER);
        worldpayResponse.set3dsIssuerUrl(ISSUER_URL_FROM_PROVIDER);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse worldpay3dsResponse = gatewayResponseBuilder
                .withSessionIdentifier(sessionIdentifier)
                .withResponse(worldpayResponse)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(worldpay3dsResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
    }

    private void epdqProviderWillRequire3ds() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        EpdqAuthorisationResponse epdqResponse = new EpdqAuthorisationResponse();
        epdqResponse.setHtmlAnswer("Base64encodedHtmlForm");
        epdqResponse.setStatus("46");

        GatewayResponseBuilder<EpdqAuthorisationResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse epdq3dsResponse = gatewayResponseBuilder
                .withResponse(epdqResponse)
                .build();
        when(mockedPaymentProvider.authorise(any())).thenReturn(epdq3dsResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
    }

    private void providerWillReject() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.REJECTED, null);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillError() {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(null, AuthoriseStatus.ERROR, "error-code");
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
