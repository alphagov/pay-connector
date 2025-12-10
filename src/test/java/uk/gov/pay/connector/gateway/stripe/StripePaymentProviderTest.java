package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.gateway.stripe.request.StripePostRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import java.util.List;
import java.util.Map;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class StripePaymentProviderTest {
    private static final String ISSUER_URL = "http://stripe.url/3ds";
    private static final String THREE_DS_VERSION = "2.0.1";
    
    @Captor
    private ArgumentCaptor<StripeTransferInRequest> stripeTransferInRequestCaptor;

    @Captor
    private ArgumentCaptor<StripePostRequest> stripePostRequestCaptor;

    private StripePaymentProvider provider;
    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private GatewayClientFactory gatewayClientFactory;
    @Mock
    private Environment environment;
    @Mock
    private MetricRegistry metricRegistry;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private StripeGatewayConfig gatewayConfig;
    @Mock
    private LinksConfig linksConfig = mock(LinksConfig.class);
    @Mock
    private GatewayClient.Response customerResponse;
    @Mock
    private GatewayClient.Response paymentMethodResponse;
    @Mock
    private GatewayClient.Response paymentIntentsResponse;
    @Mock
    private GatewayClient.Response tokenResponse;
    @Mock
    private RefundEntityFactory refundEntityFactory;
    @Mock
    private StripeSdkClient stripeSDKClient;

    private final JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(configuration.getStripeConfig()).thenReturn(gatewayConfig);
        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(gatewayClientFactory.createGatewayClient(eq(STRIPE), any(MetricRegistry.class))).thenReturn(gatewayClient);
        when(environment.metrics()).thenReturn(metricRegistry);

        provider = new StripePaymentProvider(gatewayClientFactory, configuration, objectMapper, environment, refundEntityFactory, stripeSDKClient);
    }

    @Test
    void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("stripe"));
    }

    @Test
    void shouldGenerateNoTransactionId() {
        assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Nested
    class AuthorisationFor3DS {

        @Test
        void shouldMark3DSChargeAsSuccess_when3DSAuthDetailsStatusIsAuthorised() {
            Auth3dsResponseGatewayRequest request
                    = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED);

            Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

            assertThat(response.isSuccessful(), is(true));
            assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_SUCCESS));
            assert3dsRequiredEntityForResponse(response);
        }

        @Test
        void shouldReject3DSCharge_when3DSAuthDetailsStatusIsRejected() {
            Auth3dsResponseGatewayRequest request
                    = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.DECLINED);

            Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

            assertThat(response.isSuccessful(), is(false));
            assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
            assert3dsRequiredEntityForResponse(response);
        }

        @Test
        void shouldCancel3DSCharge_when3DSAuthDetailsStatusIsCanceled() {
            Auth3dsResponseGatewayRequest request
                    = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.CANCELED);

            Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

            assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_CANCELLED));
            assert3dsRequiredEntityForResponse(response);
        }

        @Test
        void shouldMark3DSChargeAsError_when3DSAuthDetailsStatusIsError() {
            Auth3dsResponseGatewayRequest request
                    = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.ERROR);

            Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

            assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
            assert3dsRequiredEntityForResponse(response);
        }

        @Test
        void shouldKeep3DSChargeInAuthReadyState_when3DSAuthDetailsAreNotAvailable() {
            Auth3dsResponseGatewayRequest request
                    = build3dsResponseGatewayRequest(null);

            Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

            assertTrue(response.isSuccessful());
            assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_3DS_READY));
        }
    }
    
    @Nested
    class TransferDisputeAmount {

        @Test
        void shouldMakeTransferRequestForLostDispute() throws Exception {
            BalanceTransaction balanceTransaction = new BalanceTransaction(-6500L, 1500L, -8000L);
            EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
            String stripeDisputeId = "du_1LIaq8Dv3CZEaFO2MNQJK333";
            String paymentIntentId = "pi_123456789";
            String stripePlatformAccountId = "platform-account-id";
            String disputeExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeId);

            StripeDisputeData stripeDisputeData = new StripeDisputeData(stripeDisputeId,
                    paymentIntentId, "needs_response", 6500L, "fraudulent",
                    1642579160L, List.of(balanceTransaction), evidenceDetails, null, false);

            var gatewayAccountEntity = buildTestGatewayAccountEntity();
            ChargeEntity chargeEntity = buildTestCharge(gatewayAccountEntity);
            Charge charge = Charge.from(chargeEntity);

            when(gatewayConfig.getPlatformAccountId()).thenReturn(stripePlatformAccountId);

            GatewayClient.Response response = mock(GatewayClient.Response.class);
            when(response.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
            when(gatewayClient.postRequestFor(any(StripeTransferInRequest.class))).thenReturn(response);

            provider.transferDisputeAmount(stripeDisputeData, charge, gatewayAccountEntity, chargeEntity.getGatewayAccountCredentialsEntity(), 8000L);

            verify(gatewayClient).postRequestFor(stripeTransferInRequestCaptor.capture());

            String payload = stripeTransferInRequestCaptor.getValue().getGatewayOrder().getPayload();

            assertThat(payload, containsString("destination=" + stripePlatformAccountId));
            assertThat(payload, containsString("amount=8000"));
            assertThat(payload, containsString("transfer_group=" + charge.getExternalId()));
            assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
            assertThat(payload, containsString("expand%5B%5D=destination_payment"));
            assertThat(payload, containsString("currency=GBP"));
            assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + paymentIntentId));
            assertThat(payload, containsString("metadata%5Bgovuk_pay_transaction_external_id%5D=" + disputeExternalId));
        }

    }

    @Nested
    class DeleteCustomer {
        @Test
        void shouldMakeRequestToStripeToDeleteCustomer() throws Exception {
            String customerId = "cus_123";
            AgreementEntity agreementEntity = createAgreementWithPaymentInstrument(customerId);

            var request = DeleteStoredPaymentDetailsGatewayRequest.from(agreementEntity, agreementEntity.getPaymentInstrument().get());
            provider.deleteStoredPaymentDetails(request);

            verify(stripeSDKClient).deleteCustomer(customerId, true);
        }

        @Test
        void shouldWrapStripeExceptionIntoGatewayException() throws Exception {
            String customerId = "cus_123";
            AgreementEntity agreementEntity = createAgreementWithPaymentInstrument(customerId);

            StripeException mockStripeException = mock(StripeException.class);
            when(mockStripeException.getStatusCode()).thenReturn(418);
            when(mockStripeException.getCode()).thenReturn("im_a_teapot");
            when(mockStripeException.getMessage()).thenReturn("I'm a teapot");
            doThrow(mockStripeException).when(stripeSDKClient).deleteCustomer(customerId, true);

            var request = DeleteStoredPaymentDetailsGatewayRequest.from(agreementEntity, agreementEntity.getPaymentInstrument().get());
            GatewayException gatewayException = assertThrows(GatewayException.class, () -> provider.deleteStoredPaymentDetails(request));
            assertThat(gatewayException.getMessage(), is("Error when attempting to delete Stripe customer cus_123. Status code: 418, Error code: im_a_teapot, Message: I'm a teapot"));
        }

        private AgreementEntity createAgreementWithPaymentInstrument(String customerId) {
            PaymentInstrumentEntity paymentInstrumentEntity = aPaymentInstrumentEntity()
                    .withStripeRecurringAuthToken(customerId, "pm_123")
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(LIVE)
                    .withActiveStripeGatewayAccountCredentials()
                    .build();
            return anAgreementEntity()
                    .withPaymentInstrument(paymentInstrumentEntity)
                    .withGatewayAccount(gatewayAccountEntity)
                    .withLive(true)
                    .build();
        }
    }

    private void assert3dsRequiredEntityForResponse(Gateway3DSAuthorisationResponse response) {
        assertThat(response.getGateway3dsRequiredParams().isPresent(), is(true));
        Auth3dsRequiredEntity auth3dsRequiredEntity = response.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity();
        assertThat(auth3dsRequiredEntity.getIssuerUrl(), is(ISSUER_URL));
        assertThat(auth3dsRequiredEntity.getThreeDsVersion(), is(THREE_DS_VERSION));
    }

    private Auth3dsResponseGatewayRequest build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome auth3dsResultOutcome) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        if (auth3dsResultOutcome != null) {
            auth3dsResult.setAuth3dsResult(auth3dsResultOutcome.toString());
            auth3dsResult.setThreeDsVersion(THREE_DS_VERSION);
        }
        ChargeEntity chargeEntity = build3dsRequiredTestCharge();

        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);
    }

    private ChargeEntity buildTestCharge(GatewayAccountEntity accountEntity) {
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();
    }

    private ChargeEntity build3dsRequiredTestCharge() {
        String transactionId = "pi_a-payment-intent-id";

        Auth3dsRequiredEntity auth3dsRequiredEntity = anAuth3dsRequiredEntity().withIssuerUrl(ISSUER_URL).build();
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withTransactionId(transactionId)
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .build();
    }
    
    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        var gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName("stripe")
                .withRequires3ds(false)
                .withType(TEST)
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        return gatewayAccountEntity;
    }
}
