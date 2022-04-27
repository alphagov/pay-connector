package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.cardtype.dao.CardTypeEntityBuilder;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationFixture;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.sandbox.SandboxAuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {
    
    private static final ProviderSessionIdentifier SESSION_IDENTIFIER = ProviderSessionIdentifier.of("session-identifier");
    private static final String TRANSACTION_ID = "transaction-id";

    private final ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private Counter mockCounter;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private EventService mockEventService;    
    
    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;

    @Mock
    private RefundService mockRefundService;
    
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private AuthorisationRequestSummaryStringifier mockAuthorisationRequestSummaryStringifier;

    @Mock
    private AuthorisationRequestSummaryStructuredLogging mockAuthorisationRequestSummaryStructuredLogging;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private CardDetailsEntity mockCardDetailsEntity;

    private CardAuthoriseService cardAuthorisationService;

    @Before
    public void setUpCardAuthorisationService() {
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockedPaymentProvider.generateAuthorisationRequestSummary(any(ChargeEntity.class), any(AuthCardDetails.class)))
                .thenReturn(new SandboxAuthorisationRequestSummary());
        when(mockAuthorisationRequestSummaryStringifier.stringify(any(AuthorisationRequestSummary.class))).thenReturn("");

        ConnectorConfiguration mockConfiguration = mock(ConnectorConfiguration.class);
        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, null, mockConfiguration, null,
                stateTransitionService, ledgerService, mockRefundService, mockEventService, mockPaymentInstrumentService, 
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService);

        AuthorisationService authorisationService = new AuthorisationService(mockExecutorService, mockEnvironment);
        cardAuthorisationService = new CardAuthoriseService(
                mockedCardTypeDao,
                mockedProviders,
                authorisationService,
                chargeService,
                new AuthorisationLogger(mockAuthorisationRequestSummaryStringifier, mockAuthorisationRequestSummaryStructuredLogging), 
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
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    private void setupPaymentProviderMock(Exception gatewayError) throws Exception {
        when(mockedPaymentProvider.authorise(any())).thenThrow(gatewayError);
    }

    @Test
    public void doAuthorise_shouldPublishEvent() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventService, times(1)).emitAndRecordEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    public void doAuthoriseWithNonCorporateCard_shouldRespondAuthorisationSuccess() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    public void doAuthoriseWithCorporateCard_shouldRespondAuthorisationSuccess_whenNoCorporateSurchargeSet() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.CREDIT)
                .build();

        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        charge.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    public void doAuthoriseWithCreditCorporateSurcharge_shouldRespondAuthorisationSuccess() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.CREDIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        charge.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().get(), is(250L));
    }

    @Test
    public void doAuthoriseWithDebitCorporateSurcharge_shouldRespondAuthorisationSuccess() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.DEBIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().get(), is(50L));
        assertThat(charge.getWalletType(), is(nullValue()));
    }

    @Test
    public void doAuthoriseShouldIgnoreCorporateCardSurchargeForChargeWithMotoApiAuthorisationMode() throws Exception {

        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.DEBIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr Test", CardExpiryDate.valueOf("12/99"), "VISA", CardType.DEBIT, null);
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);

        charge.setAuthorisationMode(MOTO_API);
        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());

        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationSuccess_overridingGeneratedTransactionId() throws Exception {

        providerWillAuthorise();

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
    }

    @Test
    public void doAuthorise_shouldRespondWith3dsResponseForEpdq3dsOrders() throws Exception {
        epdqProviderWillRequire3ds();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getHtmlOut(), is(notNullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
    }
    
    @Test
    public void doAuthorise_shouldRetainGeneratedTransactionId_WhenProviderAuthorisationFails() throws Exception {

        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(charge.getGatewayTransactionId(), is(generatedTransactionId));
        }
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationFailed_When3dsRequiredConflictingConfigurationOfCardTypeWithGatewayAccount() {

        AuthCardDetails authCardDetails = AuthCardDetailsFixture
                .anAuthCardDetails()
                .build();
        CardTypeEntity cardTypeEntity = CardTypeEntityBuilder
                .aCardTypeEntity()
                .withRequires3ds(true)
                .withBrand(authCardDetails.getCardBrand())
                .build();

        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withType(GatewayAccountType.LIVE)
                .withGatewayName(WORLDPAY.getName())
                .withRequires3ds(false)
                .build();

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
        } catch (IllegalStateRuntimeException e) {
            assertThat(charge.getStatus(), is(AUTHORISATION_ABORTED.toString()));
            verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        }
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() throws Exception {

        providerWillReject();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REJECTED));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() throws Exception {

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.CANCELLED));

        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {

        providerWillError();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreCardDetails_evenIfAuthorisationRejected() throws Exception {

        providerWillReject();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreCardDetails_evenIfInAuthorisationError() throws Exception {

        providerWillError();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(mockCardDetailsEntity);

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthorise_shouldStoreProviderSessionId_evenIfAuthorisationRejected() throws Exception {

        providerWillReject();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
    }

    @Test
    public void doAuthorise_shouldNotProviderSessionId_whenAuthorisationError() throws Exception {

        providerWillError();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(charge.getProviderSessionId(), is(nullValue()));
    }

    @Test
    public void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenTimeout() {

        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            ErrorResponse response = (ErrorResponse) e.getResponse().getEntity();
            assertThat(response.getMessages(), contains(format("Authorisation for charge already in progress, %s", charge.getExternalId())));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void doAuthorise_shouldThrowAChargeNotFoundRuntimeException_whenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(chargeId, authCardDetails);
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenStatusIsAuthorisationReady() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void doAuthorise_shouldThrowAnIllegalStateRuntimeException_whenInvalidStatus() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.UNDEFINED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    public void doAuthorise_shouldReportAuthorisationTimeout_whenProviderTimeout() throws Exception {

        providerWillRespondWithError(new GatewayException.GatewayConnectionTimeoutException("Connection timed out"));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_CONNECTION_TIMEOUT_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test
    public void doAuthorise_shouldReportUnexpectedError_whenProviderError() throws Exception {

        providerWillRespondWithError(new GatewayException.GatewayErrorException("Malformed response received"));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    @Test
    public void doAuthoriseSnyc_shouldPublishEvent() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillAuthorise();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventService, times(1)).emitAndRecordEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_SUBMITTED_BY_API"));
    }

    @Test
    public void doAuthoriseSync_shouldIgnoreCorporateCardSurchargeForChargeWithMotoApiAuthorisationMode() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillAuthorise();

        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());

        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    public void doAuthoriseSync_shouldRespondAuthorisationSuccess_overridingGeneratedTransactionId() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillAuthorise();

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
    }

    @Test
    public void doAuthoriseSync_shouldRetainGeneratedTransactionId_WhenProviderAuthorisationFails() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any())).thenThrow(RuntimeException.class);

        try {
            AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(charge.getGatewayTransactionId(), is(generatedTransactionId));
        }
    }

    @Test
    public void doAuthoriseSync_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillReject();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REJECTED));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void doAuthoriseSync_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED, null);
        providerWillRespondToAuthoriseWith(authResponse);

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.CANCELLED));

        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    public void doAuthoriseSync_shouldRespondAuthorisationError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        providerWillError();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    public void doAuthoriseSync_shouldStoreCardDetails_IfAuthorisationRejected() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr. Pay", CardExpiryDate.valueOf("11/99"), "VISA", CardType.DEBIT, null);
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(any())).thenReturn(cardDetailsEntity);
        providerWillReject();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthoriseSync_shouldStoreCardDetails_ForAuthorisationError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr. Pay", CardExpiryDate.valueOf("11/99"), "VISA", CardType.DEBIT, null);
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(any())).thenReturn(cardDetailsEntity);

        providerWillError();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    public void doAuthoriseSync_shouldSetChargeStatusToAuthorisationTimeout_whenGatewayTimedout() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillRespondWithError(new GatewayException.GatewayConnectionTimeoutException("Connection timed out"));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);
        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_CONNECTION_TIMEOUT_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void doAuthoriseSync_shouldThrowAnIllegalStateRuntimeException_whenChargeIsInInvalidStatus() {
        charge.setAuthorisationMode(MOTO_API);
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.UNDEFINED);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    public void doAuthoriseSync_shouldReportUnexpectedError_whenProviderError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        providerWillRespondWithError(new GatewayException.GatewayErrorException("Malformed response received"));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseSync(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    private void providerWillRespondToAuthoriseWith(GatewayResponse value) throws Exception {
        when(mockedPaymentProvider.authorise(any())).thenReturn(value);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillAuthorise() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.AUTHORISED, null);
        providerWillRespondToAuthoriseWith(authResponse);
    }
    
    private void epdqProviderWillRequire3ds() throws Exception {
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

    private void providerWillReject() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.REJECTED, null);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillError() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthResponse(null, AuthoriseStatus.ERROR, "error-code");
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillRespondWithError(Exception gatewayError) throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        setupPaymentProviderMock(gatewayError);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }
}
