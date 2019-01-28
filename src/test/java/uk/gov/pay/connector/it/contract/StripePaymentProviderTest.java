package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.TestClientFactory;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

/**
 * This is an integration test with Stripe that should be run manually. In order to make it work you need to set
 * a valid stripe.authToken in the test-it-config.yaml and a valid stripeAccountId (a field in the test).
 * This test will hit the external https://api.stripe.com which is set in in stripe.url in the test-it-config.yaml.
 */
@Ignore
public class StripePaymentProviderTest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private StripePaymentProvider stripePaymentProvider;

    private static final Long chargeAmount = 500L;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Before
    public void setup() {
        ConnectorConfiguration connectorConfig = app.getInstanceFromGuiceContainer(ConnectorConfiguration.class);
        MetricRegistry metricRegistry = app.getInstanceFromGuiceContainer(Environment.class).metrics();
        StripeGatewayClient stripeGatewayClient = new StripeGatewayClient(TestClientFactory.createJerseyClient(), metricRegistry);
        stripePaymentProvider = new StripePaymentProvider(stripeGatewayClient, connectorConfig, objectMapper);
    }

    @Test
    public void createCharge() {
        GatewayResponse gatewayResponse = authorise();
        assertTrue(gatewayResponse.isSuccessful());
    }

    @Test
    public void shouldAuthoriseSuccessfully_WithNoBillingAddress() {

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .withEndDate("01/30")
                .build();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(getCharge(), authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request);
        assertTrue(gatewayResponse.isSuccessful());
    }

    @Test
    public void cancelCharge() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();
        ChargeEntity chargeEntity = getCharge();
        chargeEntity.setGatewayTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        GatewayResponse<BaseCancelResponse> cancelResponse = stripePaymentProvider.cancel(CancelGatewayRequest.valueOf(chargeEntity));
        assertTrue(cancelResponse.isSuccessful());
    }

    @Test
    public void captureCharge() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId()));
        CaptureResponse captureGatewayResponse = stripePaymentProvider.capture(request);

        assertTrue(captureGatewayResponse.isSuccessful());
    }

    @Test
    public void refundChargeInFull() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeEntity, chargeAmount, "some-user-external-id");

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    public void refundChargePartial() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeEntity, chargeAmount / 2, "some-user-external-id");

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    public void refundCharge_failWhenAmountOverChargeAmount() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeEntity, chargeAmount + 1L, "some-user-external-id");

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.getError().isPresent());
        assertThat(refundResponse.getError().get().getMessage(), is("Refund amount (£5.01) is greater than charge amount (£5.00)"));
        assertThat(refundResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void refundCharge_failWhenChargeAlreadyRefunded() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeEntity, chargeAmount, "some-user-external-id");

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());

        RefundEntity secondRefundEntity = new RefundEntity(chargeEntity, 1L, "some-user-external-id");

        refundRequest = RefundGatewayRequest.valueOf(secondRefundEntity);
        refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.getError().isPresent());
        // full message looks like "The transfer tr_blah_blah_blah is already fully reversed."
        assertThat(refundResponse.getError().get().getMessage(), containsString("is already fully reversed"));
        assertThat(refundResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    private GatewayResponse<BaseAuthoriseResponse> authorise() {
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(getCharge(), anAuthCardDetails().withEndDate("01/21").build());
        return stripePaymentProvider.authorise(request);
    }

    private ChargeEntity getChargeWithTransactionId(String transactionId) {
        ChargeEntity chargeEntity = getCharge();
        chargeEntity.setGatewayTransactionId(transactionId);
        return chargeEntity;
    }

    private ChargeEntity getCharge() {
        GatewayAccountEntity validGatewayAccount = new GatewayAccountEntity();
        validGatewayAccount.setId(123L);
        validGatewayAccount.setGatewayName(PaymentGatewayName.STRIPE.getName());
        String stripeAccountId = "<replace me>";
        validGatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", stripeAccountId));
        validGatewayAccount.setType(TEST);
        return aValidChargeEntity()
                .withGatewayAccountEntity(validGatewayAccount)
                .withAmount(chargeAmount)
                .withTransactionId(randomUUID().toString())
                .withDescription("stripe payment provider test charge")
                .build();
    }
}
