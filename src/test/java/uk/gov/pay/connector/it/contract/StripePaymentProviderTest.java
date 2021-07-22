package uk.gov.pay.connector.it.contract;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;

/**
 * This is an integration test with Stripe that should be run manually. In order to make it work you need to set
 * a valid stripe.authToken and platformAccountId in the test-it-config.yaml and a valid stripeAccountId (a field in the test).
 * This test will hit the external https://api.stripe.com which is set in in stripe.url in the test-it-config.yaml.
 */
@Ignore
public class StripePaymentProviderTest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private StripePaymentProvider stripePaymentProvider;

    private static final Long chargeAmount = 500L;
    private GatewayAccountEntity gatewayAccountEntity;

    @Before
    public void setup() {
        stripePaymentProvider = app.getInstanceFromGuiceContainer(StripePaymentProvider.class);
        gatewayAccountEntity = new GatewayAccountEntity();
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
                .withEndDate(CardExpiryDate.valueOf("01/30"))
                .build();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(getCharge(), authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request);

        assertTrue(gatewayResponse.isSuccessful());
    }

    @Test
    public void shouldAuthoriseSuccessfully_WithUsAddress() {
        Address usAddress = new Address();
        usAddress.setLine1("125 Kingsway");
        usAddress.setLine2("Aviation House");
        usAddress.setCity("Washington D.C.");
        usAddress.setPostcode("20500");
        usAddress.setCountry("US");
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(usAddress)
                .withEndDate(CardExpiryDate.valueOf("01/30"))
                .build();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(getCharge(), authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request);

        assertTrue(gatewayResponse.isSuccessful());
    }

    @Test
    public void shouldAuthoriseSuccessfully_WithCanadaAddress() {
        Address canadaAddress = new Address();
        canadaAddress.setLine1("125 Kingsway");
        canadaAddress.setLine2("Aviation House");
        canadaAddress.setPostcode("X0A0A0");
        canadaAddress.setCity("Arctic Bay");
        canadaAddress.setCountry("CA");
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(canadaAddress)
                .withEndDate(CardExpiryDate.valueOf("01/30"))
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
        final RefundEntity refundEntity = new RefundEntity(chargeAmount, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    public void refundChargePartial() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeAmount / 2, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    public void refundCharge_failWhenAmountOverChargeAmount() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeAmount + 1L, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity);
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
        final RefundEntity refundEntity = new RefundEntity(chargeAmount, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());

        RefundEntity secondRefundEntity = new RefundEntity(1L, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), secondRefundEntity, gatewayAccountEntity);
        refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.getError().isPresent());
        // full message looks like "The transfer tr_blah_blah_blah is already fully reversed."
        assertThat(refundResponse.getError().get().getMessage(), containsString("is already fully reversed"));
        assertThat(refundResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    private GatewayResponse<BaseAuthoriseResponse> authorise() {
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(getCharge(),
                anAuthCardDetails().withEndDate(CardExpiryDate.valueOf("01/21")).build());
        return stripePaymentProvider.authorise(request);
    }

    private ChargeEntity getChargeWithTransactionId(String transactionId) {
        ChargeEntity chargeEntity = getCharge();
        chargeEntity.setGatewayTransactionId(transactionId);
        return chargeEntity;
    }

    private ChargeEntity getCharge() {
        gatewayAccountEntity.setId(123L);

        String stripeAccountId = "<replace me>";

        var creds = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", stripeAccountId))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));
        gatewayAccountEntity.setType(TEST);
        return aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withAmount(chargeAmount)
                .withTransactionId(randomUUID().toString())
                .withDescription("stripe payment provider test charge")
                .build();
    }
}
