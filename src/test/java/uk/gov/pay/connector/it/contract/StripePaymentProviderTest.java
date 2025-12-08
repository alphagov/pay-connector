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
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

/**
 * This is an integration test with Stripe that can be run locally, but is ignored and so it won't be run by CI.
 * In order to make it work you need to set the following environment variables:
 * - GDS_CONNECTOR_STRIPE_AUTH_TOKEN: set this to the "Account API key" for the Stripe test environment
 * - STRIPE_PLATFORM_ACCOUNT_ID: set this to the platform account ID for our Stripe test account - get this from the
 * stripe dashboard.
 * - TEST_STRIPE_CONNECT_ACCOUNT_ID: a Stripe connect account ID that exists in the test Stripe environment
 */
@Ignore
public class StripePaymentProviderTest {

    private static final Long chargeAmount = 500L;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(false);

    private StripePaymentProvider stripePaymentProvider;

    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    private final String validCardExpiryDate = ZonedDateTime.now().plusYears(1).format(ofPattern("MM/yy"));

    @Before
    public void setup() {
        String stripeConnectAccountId = envOrThrow("TEST_STRIPE_CONNECT_ACCOUNT_ID");

        stripePaymentProvider = app.getInstanceFromGuiceContainer(StripePaymentProvider.class);
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", stripeConnectAccountId))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
        gatewayAccountEntity.setId(123L);
        gatewayAccountEntity.setType(TEST);
    }

    @Test
    public void createCharge() {
        GatewayResponse gatewayResponse = authorise();
        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    public void shouldAuthoriseSuccessfully_WithNoBillingAddress() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .withEndDate(CardExpiryDate.valueOf(validCardExpiryDate))
                .build();
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request, charge);

        assertTrue(gatewayResponse.getBaseResponse().isPresent());
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
                .withEndDate(CardExpiryDate.valueOf(validCardExpiryDate))
                .build();
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request, charge);

        assertTrue(gatewayResponse.getBaseResponse().isPresent());
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
                .withEndDate(CardExpiryDate.valueOf(validCardExpiryDate))
                .build();
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request, charge);

        assertTrue(gatewayResponse.getBaseResponse().isPresent());
    }

    @Test
    public void shouldAuthoriseSuccessfully_WhenSettingUpRecurringPaymentAgreement() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withEndDate(CardExpiryDate.valueOf(validCardExpiryDate))
                .build();
        ChargeEntity charge = getChargeWithAgreement();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge, authCardDetails);
        GatewayResponse<StripeAuthorisationResponse> gatewayResponse = stripePaymentProvider.authorise(request, charge);

        StripeAuthorisationResponse response = gatewayResponse.getBaseResponse().get();

        assertTrue(gatewayResponse.getBaseResponse().isPresent());
        assertThat(response.getGatewayRecurringAuthToken().isPresent(), is(true));
        assertThat(response.getGatewayRecurringAuthToken().get(), hasKey(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY));
        assertThat(response.getGatewayRecurringAuthToken().get(), hasKey(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY));
    }

    @Test
    public void shouldBeAbleToTakeRecurringPaymentUsingStoredPaymentDetails() {
        ChargeEntity setUpAgreementCharge = setUpAgreement();

        ChargeEntity recurringCharge = getCharge();
        recurringCharge.setAuthorisationMode(AuthorisationMode.AGREEMENT);
        recurringCharge.setAgreementEntity(setUpAgreementCharge.getAgreement().get());
        recurringCharge.setPaymentInstrument(setUpAgreementCharge.getPaymentInstrument().get());

        var gatewayRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(recurringCharge);
        GatewayResponse authoriseUserNotPresentResponse = stripePaymentProvider.authoriseUserNotPresent(gatewayRequest);

        assertTrue(authoriseUserNotPresentResponse.getBaseResponse().isPresent());
    }

    @Test
    public void cancelCharge() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();
        ChargeEntity chargeEntity = getCharge();
        chargeEntity.setGatewayTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        GatewayResponse<BaseCancelResponse> cancelResponse = stripePaymentProvider.cancel(CancelGatewayRequest.valueOf(chargeEntity));
        assertTrue(cancelResponse.getBaseResponse().isPresent());
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

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity);
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

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity);
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

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.getError().isPresent());
        assertThat(refundResponse.getError().get().getMessage(), is("Stripe refund response (error: Refund amount (£5.01) is greater than charge amount (£5.00))"));
        assertThat(refundResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void refundCharge_failWhenChargeAlreadyRefunded() {
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = authorise();

        ChargeEntity chargeEntity = getChargeWithTransactionId(gatewayResponse.getBaseResponse().get().getTransactionId());
        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);

        stripePaymentProvider.capture(request);
        final RefundEntity refundEntity = new RefundEntity(chargeAmount, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        RefundGatewayRequest refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity);
        GatewayRefundResponse refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.isSuccessful());

        RefundEntity secondRefundEntity = new RefundEntity(1L, "some-user-external-id", userEmail, chargeEntity.getExternalId());

        refundRequest = RefundGatewayRequest.valueOf(Charge.from(chargeEntity), secondRefundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity);
        refundResponse = stripePaymentProvider.refund(refundRequest);

        assertTrue(refundResponse.getError().isPresent());
        // full message looks like "The transfer tr_blah_blah_blah is already fully reversed."
        assertThat(refundResponse.getError().get().getMessage(), containsString("error code: charge_already_refunded"));
        assertThat(refundResponse.getError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldDeleteStoredPaymentDetails() throws Exception {
        ChargeEntity setUpAgreementCharge = setUpAgreement();

        var request = DeleteStoredPaymentDetailsGatewayRequest.from(setUpAgreementCharge.getAgreement().get(), setUpAgreementCharge.getPaymentInstrument().get());
        stripePaymentProvider.deleteStoredPaymentDetails(request);

        // attempt to take recurring payment to ensure customer is deleted
        ChargeEntity recurringCharge = getCharge();
        recurringCharge.setAuthorisationMode(AuthorisationMode.AGREEMENT);
        recurringCharge.setAgreementEntity(setUpAgreementCharge.getAgreement().get());
        recurringCharge.setPaymentInstrument(setUpAgreementCharge.getPaymentInstrument().get());

        var gatewayRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(recurringCharge);
        GatewayResponse authoriseUserNotPresentResponse = stripePaymentProvider.authoriseUserNotPresent(gatewayRequest);

        assertThat(authoriseUserNotPresentResponse.getBaseResponse().get(), instanceOf(StripeAuthorisationFailedResponse.class));
    }

    private GatewayResponse<BaseAuthoriseResponse> authorise() {
        ChargeEntity charge = getCharge();
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(charge,
                anAuthCardDetails().withEndDate(CardExpiryDate.valueOf(validCardExpiryDate)).build());
        return stripePaymentProvider.authorise(request, charge);
    }

    private ChargeEntity setUpAgreement() {
        AuthCardDetails authCardDetails = anAuthCardDetails().withEndDate(CardExpiryDate.valueOf(validCardExpiryDate)).build();
        ChargeEntity setUpAgreementCharge = getChargeWithAgreement();
        var request = new CardAuthorisationGatewayRequest(setUpAgreementCharge, authCardDetails);
        GatewayResponse<StripeAuthorisationResponse> setUpAgreementResponse = stripePaymentProvider.authorise(request, setUpAgreementCharge);

        Map<String, String> recurringAuthToken = setUpAgreementResponse.getBaseResponse().get().getGatewayRecurringAuthToken().get();
        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity()
                .withRecurringAuthToken(recurringAuthToken)
                .build();
        setUpAgreementCharge.setPaymentInstrument(paymentInstrument);
        return setUpAgreementCharge;
    }


    private ChargeEntity getChargeWithTransactionId(String transactionId) {
        ChargeEntity chargeEntity = getCharge();
        chargeEntity.setGatewayTransactionId(transactionId);
        return chargeEntity;
    }

    private ChargeEntity getCharge() {
        return aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withAmount(chargeAmount)
                .withTransactionId(randomUUID().toString())
                .withDescription("stripe payment provider test charge")
                .build();
    }

    private ChargeEntity getChargeWithAgreement() {
        var agreementEntity = anAgreementEntity().withGatewayAccount(gatewayAccountEntity).build();

        return aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withAmount(chargeAmount)
                .withTransactionId(randomUUID().toString())
                .withDescription("stripe payment provider test charge")
                .withAgreementEntity(agreementEntity)
                .withSavePaymentInstrumentToAgreement(true)
                .build();
    }
}
