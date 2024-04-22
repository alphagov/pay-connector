package uk.gov.pay.connector.wallets;

import com.amazonaws.util.json.Jackson;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WalletAuthoriseServiceForGooglePay3dsTest {

    private WalletAuthoriseService walletAuthoriseService;
    
    @Mock
    private PaymentProviders mockedProviders;
    
    @Mock
    private PaymentProvider mockedPaymentProvider;
    
    @Mock
    private ChargeService chargeService;

    @Mock
    private WalletPaymentInfoToAuthCardDetailsConverter mockWalletPaymentInfoToAuthCardDetailsConverter;

    @Mock
    private Environment mockEnvironment;

    @Mock
    protected MetricRegistry mockMetricRegistry;

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private Counter mockCounter;

    @Mock
    private AuthCardDetails mockAuthCardDetails;
    
    @Mock
    private ConnectorConfiguration mockConfiguration;
    
    @Mock
    private AuthorisationConfig mockAuthorisationConfig;

    @Captor
    private ArgumentCaptor<Optional<Auth3dsRequiredEntity>> auth3dsRequiredEntityArgumentCaptor;
    
    private ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
    
    @BeforeEach
    void setup() {
        when(mockedProviders.byName(any())).thenReturn(mockedPaymentProvider);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockConfiguration.getAuthorisationConfig()).thenReturn(mockAuthorisationConfig);
        when(mockAuthorisationConfig.getAsynchronousAuthTimeoutInMilliseconds()).thenReturn(1000);

        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class), anyInt());
        
        AuthorisationService authorisationService = new AuthorisationService(mockExecutorService, mockEnvironment, mockConfiguration);
        walletAuthoriseService = new WalletAuthoriseService(
                mockedProviders,
                chargeService,
                authorisationService,
                mockWalletPaymentInfoToAuthCardDetailsConverter,
                mock(AuthorisationLogger.class), 
                mockEnvironment);
        
        when(chargeService.lockChargeForProcessing(anyString(), any(OperationType.class))).thenReturn(chargeEntity);
        when(mockWalletPaymentInfoToAuthCardDetailsConverter.convert(any(WalletPaymentInfo.class), nullable(CardExpiryDate.class)))
                .thenReturn(mockAuthCardDetails);
        when(chargeService.updateChargePostWalletAuthorisation(anyString(), any(ChargeStatus.class), anyString(), 
                isNull(), eq(mockAuthCardDetails), any(WalletType.class), any(), 
                any(Optional.class))
        ).thenReturn(chargeEntity);
    }

    @Test
    void the_charge_service_should_be_called_with_auth3dsRequiredDetails_when_3ds_is_required_for_a_google_payment() throws Exception {
        String successPayload = load(WORLDPAY_3DS_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);
        providerRequestsFor3dsAuthorisation(worldpayOrderStatusResponse);

        GooglePayAuthRequest authorisationData =
                Jackson.getObjectMapper().readValue(load("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);

        walletAuthoriseService.authorise(chargeEntity.getExternalId(), authorisationData);

        verify(chargeService).updateChargePostWalletAuthorisation(
                anyString(),
                any(ChargeStatus.class),
                anyString(),
                isNull(),
                eq(mockAuthCardDetails),
                any(WalletType.class),
                anyString(),
                auth3dsRequiredEntityArgumentCaptor.capture());
        
        assertThat(auth3dsRequiredEntityArgumentCaptor.getValue().isPresent(), is(true));
        assertThat(auth3dsRequiredEntityArgumentCaptor.getValue().get().getIssuerUrl(), is(worldpayOrderStatusResponse.getIssuerUrl()));
        assertThat(auth3dsRequiredEntityArgumentCaptor.getValue().get().getPaRequest(), is(worldpayOrderStatusResponse.getPaRequest()));
    }

    private GatewayResponse providerRequestsFor3dsAuthorisation(WorldpayOrderStatusResponse worldpayOrderStatusResponse) throws Exception {
        GatewayResponse.GatewayResponseBuilder<WorldpayOrderStatusResponse> responseBuilder = responseBuilder();
        responseBuilder.withResponse(worldpayOrderStatusResponse);
        GatewayResponse authResponse = responseBuilder.build();
        when(mockedPaymentProvider.authoriseGooglePay(any(GooglePayAuthorisationGatewayRequest.class))).thenReturn(authResponse);
        return authResponse;
    }
}
