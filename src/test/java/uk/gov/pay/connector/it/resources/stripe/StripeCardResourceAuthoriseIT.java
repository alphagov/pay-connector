package uk.gov.pay.connector.it.resources.stripe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.collection.IsIn;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlForApplePay;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml", withDockerSQS = true)
public class StripeCardResourceAuthoriseIT {
    private static final String CARD_HOLDER_NAME = "Scrooge McDuck";
    private static final String CVC = "123";
    private static final CardExpiryDate EXPIRY = CardExpiryDate.valueOf("11/99");
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String AMOUNT = "6234";
    private static final String CARD_TYPE = "CREDIT";

    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "Moneybags Avenue";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CARD_BRAND = "cardBrand";
    private static final String AGREEMENT_DESCRIPTION = "An agreement description";
    private RestAssuredClient connectorRestApiClient;

    private String stripeAccountId;
    private final String validAuthorisationDetails = buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, CARD_NUMBER, CVC,
            EXPIRY.toString(), CARD_BRAND, CARD_TYPE, ADDRESS_LINE_1, ADDRESS_LINE_2, ADDRESS_CITY,
            "London", ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    private final String validAuthorisationDetailsWithoutBillingAddress = buildJsonAuthorisationDetailsWithoutAddress();
    private final String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");
    private final String paymentProvider = PaymentGatewayName.STRIPE.getName();
    private final ObjectMapper mapper = new ObjectMapper();
    private String accountId;
    private StripeMockClient stripeMockClient;
    private DatabaseTestHelper databaseTestHelper;
    private AddGatewayAccountCredentialsParams accountCredentialsParams;

    @DropwizardTestContext
    private TestContext testContext;

    private WireMockServer wireMockServer;

    @Before
    public void setup() {
        wireMockServer = testContext.getWireMockServer();
        stripeMockClient = new StripeMockClient(wireMockServer);

        var ledgerStub = new LedgerStub(wireMockServer);
        ledgerStub.acceptPostEvent();

        stripeAccountId = String.valueOf(RandomUtils.nextInt());
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());

        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);

        accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(paymentProvider)
                .withGatewayAccountId(Long.valueOf(accountId))
                .withState(ACTIVE)
                .withCredentials(Map.of("stripe_account_id", stripeAccountId))
                .build();
    }

    @Test
    public void cardAuthorisationWithPaymentIntentsFailureShouldReturnBadRequest() {
        stripeMockClient.mockCreatePaymentMethodAuthorisationRejected();

        addGatewayAccountWith3DS2Enabled();

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(BAD_REQUEST_400)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_REJECTED.toString());
    }

    @Test
    public void authoriseCharge() {
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntent();
        addGatewayAccountWith3DS2Enabled();

        String externalChargeId = addCharge();

        ValidatableResponse validatableResponse = given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then();

        validatableResponse
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(OK_200);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_methods"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        verifyPaymentMethodRequest();
        verifyPaymentIntentRequest(externalChargeId, stripeAccountId);
    }

    @Test
    public void shouldAuthoriseChargeWithoutBillingAddress() {
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntent();
        addGatewayAccount();

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetailsWithoutBillingAddress)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(OK_200);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_methods"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

    }

    @Test
    public void authoriseChargeToSetUpRecurringPaymentAgreement() throws Exception {
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreateCustomer();
        stripeMockClient.mockCreatePaymentIntentWithCustomer();
        addGatewayAccountWith3DS2Enabled();

        String agreementId = addAgreement();
        String externalChargeId = addChargeWithAgreement(ENTERING_CARD_DETAILS, agreementId);

        ValidatableResponse validatableResponse = given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then();

        validatableResponse
                .statusCode(OK_200)
                .body("status", is(AUTHORISATION_SUCCESS.toString()));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_methods"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        verifyPaymentMethodRequest();
        verifyCustomerRequest();
        verifyPaymentIntentRequest(externalChargeId, stripeAccountId);

        Map<String, Object> paymentInstrument = databaseTestHelper.getPaymentInstrumentByChargeExternalId(externalChargeId.toString());
        Map<String, String> recurringAuthTokenMap = mapper.readValue(paymentInstrument.get("recurring_auth_token").toString(), new TypeReference<Map<String, String>>() {});

        assertThat(recurringAuthTokenMap, hasKey(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY));
        assertThat(recurringAuthTokenMap, hasKey(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY));
    }

    @Test
    public void shouldRespondAs3dsRequired_whenAuthorisationRequires3ds() {
        addGatewayAccount();
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntentRequiring3DS();

        String externalChargeId = addCharge();
        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(OK_200);

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldReturnInternalServerResponseWhenGatewayAccountHasNoStripeAccountId() {
        addGatewayAccountWithEmptyCredentials();

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(500)
                .body("message", contains(containsString("Exception occurred while doing authorisation")))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldAuthoriseApplePayPayment() {
        stripeMockClient.mockCreateToken();
        stripeMockClient.mockCreatePaymentIntent();

        addGatewayAccount();
        String chargeId = addCharge();
        String applePayAuthorisationRequest = buildJsonApplePayAuthorisationDetails("Someone", "foo@example.com");

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(applePayAuthorisationRequest)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(200)
                .body("status", is(AUTHORISATION_SUCCESS.toString()));

        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .body("email", is("foo@example.com"))
                .body("card_details.cardholder_name", is("Someone"))
                .body("card_details.last_digits_card_number", is("4242"))
                .body("card_details.card_type", is("debit"))
                .body("card_details.card_brand", is("Visa"))
                .body("card_details.expiry_date", is("08/24"));;
    }

    @Test
    public void shouldRejectApplePayPayment_andSaveCardDetails() {
        stripeMockClient.mockCreateToken();
        stripeMockClient.mockCreatePaymentIntentAuthorisationRejected();

        addGatewayAccount();
        String chargeId = addCharge();
        String applePayAuthorisationRequest = buildJsonApplePayAuthorisationDetails("Someone", "foo@example.com");

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(applePayAuthorisationRequest)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(400);

        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(AUTHORISATION_REJECTED.toString()))
                .body("email", is("foo@example.com"))
                .body("card_details.cardholder_name", is("Someone"))
                .body("card_details.last_digits_card_number", is("4242"))
                .body("card_details.card_type", is("debit"))
                .body("card_details.card_brand", is("Visa"))
                .body("card_details.expiry_date", is(nullValue()));;
    }
    
    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {

        addGatewayAccount();

        String externalChargeId = addChargeWithStatus(AUTHORISATION_SUCCESS);

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(StringUtils.EMPTY)
                .post(captureChargeUrlFor(externalChargeId))
                .then().statusCode(NO_CONTENT_204);

        assertConnectorHasRecordedChargeAsReadyForCapture(externalChargeId);
    }

    private void assertConnectorHasRecordedChargeAsReadyForCapture(String externalChargeId) {
        /*
         * There's a race condition where the background capture queue may attempt to capture the charge before we check
         * the status. We care whether it has progressed to CAPTURE_APPROVED or beyond.
         */
        connectorRestApiClient
                .withChargeId(externalChargeId)
                .getFrontendCharge()
                .body("status", IsIn.oneOf(
                        CAPTURE_APPROVED.getValue(),
                        CAPTURE_APPROVED_RETRY.getValue(),
                        CAPTURE_READY.getValue(),
                        CAPTURED.getValue(),
                        CAPTURE_SUBMITTED.getValue()
                ));
    }

    private String addChargeWithStatus(ChargeStatus chargeStatus) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withPaymentProvider("stripe")
                .withGatewayAccountId(accountId)
                .withAmount(Long.valueOf(AMOUNT))
                .withStatus(chargeStatus)
                .withGatewayCredentialId(accountCredentialsParams.getId())
                .build());
        return externalChargeId;
    }

    private String addChargeWithAgreement(ChargeStatus chargeStatus, String agreementExternalId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withPaymentProvider("stripe")
                .withGatewayAccountId(accountId)
                .withAmount(Long.valueOf(AMOUNT))
                .withStatus(chargeStatus)
                .withGatewayCredentialId(accountCredentialsParams.getId())
                .withAgreementExternalId(agreementExternalId)
                .withSavePaymentInstrumentToAgreement(true)
                .build());
        return externalChargeId;
    }

    private String addAgreement() {
        var addAgreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withDescription(AGREEMENT_DESCRIPTION)
                .build();
        databaseTestHelper.addAgreement(addAgreementParams);
        return addAgreementParams.getExternalAgreementId();
    }

    private void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    private void verifyCustomerRequest() {
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/customers"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withHeader("Authorization", equalTo("Bearer sk_test"))
                .withRequestBody(containing(queryParamWithValue("name", CARD_HOLDER_NAME)))
                .withRequestBody(containing(queryParamWithValue("description", AGREEMENT_DESCRIPTION))));
    }

    private void verifyPaymentIntentRequest(String externalChargeId, String stripeAccountId) {
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withHeader("Authorization", equalTo("Bearer sk_test"))
                .withRequestBody(containing(queryParamWithValue("amount", "6234")))
                .withRequestBody(containing(queryParamWithValue("confirm", "true")))
                .withRequestBody(containing(queryParamWithValue("on_behalf_of", stripeAccountId)))
                .withRequestBody(containing(queryParamWithValue("transfer_group", externalChargeId)))
                .withRequestBody(containing(queryParamWithValue("capture_method", "manual")))
                .withRequestBody(containing(queryParamWithValue("description", "Test description")))
                .withRequestBody(containing(queryParamWithValue("confirmation_method", "automatic")))
                .withRequestBody(containing(queryParamWithValue("payment_method", "pm_1FHEP1EZsufgnuO0Y22yNAKu")))
                .withRequestBody(containing(queryParamWithValue("currency", "GBP")))
                .withRequestBody(containing(queryParamWithValue("return_url",
                        format("http://CardFrontend//card_details/%s/3ds_required_in", externalChargeId))))
        );
    }

    private void verifyPaymentMethodRequest() {
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_methods"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withHeader("Authorization", equalTo("Bearer sk_test"))
                .withRequestBody(containing(queryParamWithValue("billing_details[name]", "Scrooge McDuck")))
                .withRequestBody(containing(queryParamWithValue("type", "card")))
                .withRequestBody(containing(queryParamWithValue("card[exp_month]", "11")))
                .withRequestBody(containing(queryParamWithValue("billing_details[address[line1]]", "The Money Pool")))
                .withRequestBody(containing(queryParamWithValue("card[exp_year]", "99")))
                .withRequestBody(containing(queryParamWithValue("card[cvc]", "123")))
                .withRequestBody(containing(queryParamWithValue("billing_details[address[postal_code]]", "DO11 4RS")))
                .withRequestBody(containing(queryParamWithValue("billing_details[address[line2]]", "Moneybags Avenue")))
                .withRequestBody(containing(queryParamWithValue("card[number]", "4242424242424242")))
                .withRequestBody(containing(queryParamWithValue("billing_details[address[city]]", "London")))
                .withRequestBody(containing(queryParamWithValue("billing_details[address[country]]", "GB")))
        );
    }

    private String queryParamWithValue(String queryParam, String value) {
        return String.join("=", encode(queryParam, UTF_8), encode(value, UTF_8));
    }

    private String addCharge() {
        return addChargeWithStatus(ENTERING_CARD_DETAILS);
    }

    private void addGatewayAccount() {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .withIntegrationVersion3ds(1)
                .build());
    }

    private void addGatewayAccountWithEmptyCredentials() {
        accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(paymentProvider)
                .withGatewayAccountId(Long.valueOf(accountId))
                .withState(ACTIVE)
                .withCredentials(Map.of())
                .build();

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .withIntegrationVersion3ds(1)
                .build());
    }

    private void addGatewayAccountWith3DS2Enabled() {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .withIntegrationVersion3ds(2)
                .build());
    }

    private String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }
}
