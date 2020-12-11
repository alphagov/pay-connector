package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxAuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.service.RefundService;

import java.util.Optional;
import java.util.function.Supplier;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.XMLUnmarshaller.unmarshall;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_FLEX_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WorldpayCardAuthoriseServiceTest extends CardServiceTest {

    private static final ProviderSessionIdentifier SESSION_IDENTIFIER = ProviderSessionIdentifier.of("session-identifier");
    private static final String TRANSACTION_ID = "transaction-id";
    
    private CardAuthoriseService cardAuthorisationService;

    private ChargeEntity charge;

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private PaymentProvider mockedWorldpayPaymentProvider;

    @BeforeEach
    void setup() {
        Environment environment = mock(Environment.class);
        
        when(environment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mock(Counter.class));
        
        charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        charge.getGatewayAccount().setGatewayName("worldpay");
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, mock(ConnectorConfiguration.class), null,
                mock(StateTransitionService.class), mock(LedgerService.class), mock(RefundService.class), 
                mock(EventService.class), mock(NorthAmericanRegionMapper.class));
        
        cardAuthorisationService = new CardAuthoriseService(
                mockedCardTypeDao,
                mockedProviders,
                new AuthorisationService(mockExecutorService, environment),
                chargeService,
                mock(AuthorisationRequestSummaryStringifier.class),
                mock(AuthorisationRequestSummaryStructuredLogging.class),
                environment);

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        when(mockedProviders.byName(WORLDPAY)).thenReturn(mockedWorldpayPaymentProvider);
        when(mockedWorldpayPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
        when(mockedWorldpayPaymentProvider.generateAuthorisationRequestSummary(any(ChargeEntity.class), any(AuthCardDetails.class)))
                .thenReturn(new SandboxAuthorisationRequestSummary());
    }
    
    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_orders() throws Exception {
        var worldpayOrderStatusResponse = worldpayProviderWillRequire3ds(null, load(WORLDPAY_3DS_RESPONSE));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getIssuerUrl(), is(worldpayOrderStatusResponse.getIssuerUrl()));
        assertThat(charge.get3dsRequiredDetails().getPaRequest(), is(worldpayOrderStatusResponse.getPaRequest()));
    }

    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_flex_orders() throws Exception {
        var worldpayOrderStatusResponse = worldpayProviderWillRequire3ds(SESSION_IDENTIFIER, load(WORLDPAY_3DS_FLEX_RESPONSE));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeAcsUrl(), is(worldpayOrderStatusResponse.getChallengeAcsUrl()));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeTransactionId(), is(worldpayOrderStatusResponse.getChallengeTransactionId()));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengePayload(), is(worldpayOrderStatusResponse.getChallengePayload()));
        assertThat(charge.get3dsRequiredDetails().getThreeDsVersion(), is(worldpayOrderStatusResponse.getThreeDsVersion()));
    }

    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_orders_with_worldpay_machine_cookie() throws Exception {
        var worldpayOrderStatusResponse = worldpayProviderWillRequire3ds(SESSION_IDENTIFIER, load(WORLDPAY_3DS_RESPONSE));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getIssuerUrl(), is(worldpayOrderStatusResponse.getIssuerUrl()));
        assertThat(charge.get3dsRequiredDetails().getPaRequest(), is(worldpayOrderStatusResponse.getPaRequest()));
    }

    private WorldpayOrderStatusResponse worldpayProviderWillRequire3ds(ProviderSessionIdentifier sessionIdentifier,
                                                                       String worldpay3dsPayloadResponse) throws Exception {

        var worldpayOrderStatusResponse = unmarshall(worldpay3dsPayloadResponse, WorldpayOrderStatusResponse.class);
        GatewayResponse<WorldpayOrderStatusResponse> responseBuilder = responseBuilder()
                .withResponse(worldpayOrderStatusResponse)
                .withSessionIdentifier(sessionIdentifier).build();
        when(mockedWorldpayPaymentProvider.authorise(any())).thenReturn(responseBuilder);
        return worldpayOrderStatusResponse;
    }

    private void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }
}
