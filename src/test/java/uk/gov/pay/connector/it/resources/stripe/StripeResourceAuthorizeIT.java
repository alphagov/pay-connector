package uk.gov.pay.connector.it.resources.stripe;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlForApplePay;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlForGooglePay;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml", withDockerSQS = true)
public class StripeResourceAuthorizeIT {
    private static final String CARD_HOLDER_NAME = "Scrooge McDuck";
    private static final String CVC = "123";
    private static final String EXP_MONTH = "11";
    private static final String EXP_YEAR = "99";
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String AMOUNT = "6234";
    private static final String DESCRIPTION = "Test description";

    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "Moneybags Avenue";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CARD_BRAND = "cardBrand";
    private RestAssuredClient connectorRestApiClient;

    private String stripeAccountId;
    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, CARD_NUMBER, CVC,
            EXP_MONTH + "/" + EXP_YEAR, CARD_BRAND, ADDRESS_LINE_1, ADDRESS_LINE_2, ADDRESS_CITY,
            "London", ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    private String validAuthorisationDetailsWithoutBillingAddress = buildJsonAuthorisationDetailsWithoutAddress();
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");
    private String paymentProvider = PaymentGatewayName.STRIPE.getName();
    private String accountId;
    private StripeMockClient stripeMockClient = new StripeMockClient();
    private DatabaseTestHelper databaseTestHelper;

    @DropwizardTestContext
    private TestContext testContext;

    @ClassRule
    public static WireMockClassRule wireMockClassRule = new WireMockClassRule(WIREMOCK_PORT);

    @Rule
    public WireMockClassRule wireMockRule = wireMockClassRule;

    @Before
    public void setup() {
        stripeAccountId = String.valueOf(RandomUtils.nextInt());
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());

        stripeMockClient.mockCreateToken();
        stripeMockClient.mockCreateSource();
        stripeMockClient.mockCreateCharge();

        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @Test
    public void cardAuthorisationFailureShouldReturnBadRequest() {
        stripeMockClient.mockAuthorisationFailed();

        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

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
    public void cardAuthorisationWithPaymentIntentsFailureShouldReturnBadRequest() {
        stripeMockClient.mockAuthorisationFailedWithPaymentIntents();

        addGatewayAccountWith3DS2Enabled(ImmutableMap.of("stripe_account_id", stripeAccountId));

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
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(OK_200);

        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo(constructExpectedTokensRequestBody())));

        verify(postRequestedFor(urlEqualTo("/v1/sources"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo(constructExpectedSourcesRequestBody())));

        verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        List<LoggedRequest> requests = findAll(postRequestedFor(urlMatching("/v1/charges")));
        assertThat(requests).hasSize(1);
        assertExpectedAuthoriseRequestBody(requests.get(0), externalChargeId);
    }

    @Test
    public void authoriseChargeWithPaymentIntents() {
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntent();
        addGatewayAccountWith3DS2Enabled(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(OK_200);

        verify(postRequestedFor(urlEqualTo("/v1/payment_methods"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

    }


    @Test
    public void shouldAuthoriseChargeWithoutBillingAddress() {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetailsWithoutBillingAddress)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(OK_200);

        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(equalTo(constructExpectedTokensRequestBodyWithoutBillingAddress())));

        verify(postRequestedFor(urlEqualTo("/v1/sources"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(matching(constructExpectedSourcesRequestBody())));

        verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        List<LoggedRequest> requests = findAll(postRequestedFor(urlMatching("/v1/charges")));
        assertThat(requests).hasSize(1);
        assertExpectedAuthoriseRequestBody(requests.get(0), externalChargeId);
    }

    @Test
    public void shouldRespondAs3dsRequired_whenAuthorisationRequires3ds() {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        stripeMockClient.mockCreateSourceWithThreeDSecureRequired();
        stripeMockClient.mockCreate3dsSource();

        String externalChargeId = addCharge();
        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(OK_200);

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_3DS_REQUIRED.toString());

        List<LoggedRequest> requests = findAll(postRequestedFor(urlMatching("/v1/tokens")));
        assertThat(requests).hasSize(1);
        requests = findAll(postRequestedFor(urlMatching("/v1/sources")));
        assertThat(requests).hasSize(2);
        requests = findAll(postRequestedFor(urlMatching("/v1/charges")));
        assertThat(requests).hasSize(0);
    }

    @Test
    public void invalidAuthCredentialsShouldReturnAnInternalServerError() {
        stripeMockClient.mockUnauthorizedResponse();

        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR_500)
                .body("message", contains("There was an internal server error authorising charge_external_id: " + externalChargeId))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturnInternalServerResponseWhenGatewayAccountHasNoStripeAccountId() {
        addGatewayAccount(emptyMap());

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
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAnApplePayPayment() {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlForApplePay(externalChargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for Stripe"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAGooglePayPayment() throws IOException {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validPayload)
                .post(authoriseChargeUrlForGooglePay(externalChargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for Stripe"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {

        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String externalChargeId = addChargeWithStatus(AUTHORISATION_SUCCESS);

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(StringUtils.EMPTY)
                .post(captureChargeUrlFor(externalChargeId))
                .then().statusCode(NO_CONTENT_204);

        assertFrontendChargeStatusIs(externalChargeId, CAPTURE_APPROVED.getValue());
    }

    private String addChargeWithStatus(ChargeStatus chargeStatus) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(Long.valueOf(AMOUNT))
                .withStatus(chargeStatus)
                .build());
        return externalChargeId;
    }

    private void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    private String addCharge() {
        return addChargeWithStatus(ENTERING_CARD_DETAILS);
    }

    private void addGatewayAccount(Map credentials) {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withCredentials(credentials)
                .withIntegrationVersion3ds(1)
                .build());
    }

    private void addGatewayAccountWith3DS2Enabled(Map credentials) {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withCredentials(credentials)
                .withIntegrationVersion3ds(2)
                .build());
    }

    private String constructExpectedSourcesRequestBody() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("type", "card"));
        params.add(new BasicNameValuePair("token", "tok_1DJfnpHj08j2jFuBPMcHN1F8")); //This comes from resources/stripe/create_token_response.json
        params.add(new BasicNameValuePair("usage", "single_use"));
        return URLEncodedUtils.format(params, UTF_8);
    }

    private String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }

    private void assertExpectedAuthoriseRequestBody(LoggedRequest request, String chargeExternalId) {
        assertThat(request.getBodyAsString()).contains("amount=" + AMOUNT);
        assertThat(request.getBodyAsString()).contains("on_behalf_of=" + stripeAccountId);
        assertThat(request.getBodyAsString()).contains("transfer_group=" +  chargeExternalId);
        assertThat(request.getBodyAsString()).contains("currency=GBP");
        assertThat(request.getBodyAsString()).contains("source=src_1DT9bn2eZvKYlo2Cg5okt8WC");
        assertThat(request.getBodyAsString()).contains("capture=false");
        assertThat(request.getBodyAsString()).contains("description=Test+description");
    }

    private String constructExpectedTokensRequestBody() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("card[cvc]", CVC));
        params.add(new BasicNameValuePair("card[exp_month]", EXP_MONTH));
        params.add(new BasicNameValuePair("card[exp_year]", EXP_YEAR));
        params.add(new BasicNameValuePair("card[number]", CARD_NUMBER));
        params.add(new BasicNameValuePair("card[name]", CARD_HOLDER_NAME));

        params.add(new BasicNameValuePair("card[address_line1]", ADDRESS_LINE_1));
        params.add(new BasicNameValuePair("card[address_line2]", ADDRESS_LINE_2));
        params.add(new BasicNameValuePair("card[address_city]", ADDRESS_CITY));
        params.add(new BasicNameValuePair("card[address_country]", ADDRESS_COUNTRY_GB));
        params.add(new BasicNameValuePair("card[address_zip]", ADDRESS_POSTCODE));

        return URLEncodedUtils.format(params, UTF_8);
    }

    private String constructExpectedTokensRequestBodyWithoutBillingAddress() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("card[cvc]", CVC));
        params.add(new BasicNameValuePair("card[exp_month]", EXP_MONTH));
        params.add(new BasicNameValuePair("card[exp_year]", EXP_YEAR));
        params.add(new BasicNameValuePair("card[number]", CARD_NUMBER));
        params.add(new BasicNameValuePair("card[name]", CARD_HOLDER_NAME));
        return URLEncodedUtils.format(params, UTF_8);
    }
}
