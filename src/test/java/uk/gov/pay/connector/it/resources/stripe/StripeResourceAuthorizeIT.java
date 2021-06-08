package uk.gov.pay.connector.it.resources.stripe;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.collection.IsIn;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
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
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlForApplePay;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlForGooglePay;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml", withDockerSQS = true)
public class StripeResourceAuthorizeIT {
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
    private RestAssuredClient connectorRestApiClient;

    private String stripeAccountId;
    private final String validAuthorisationDetails = buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, CARD_NUMBER, CVC,
            EXPIRY.toString(), CARD_BRAND, CARD_TYPE, ADDRESS_LINE_1, ADDRESS_LINE_2, ADDRESS_CITY,
            "London", ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    private final String validAuthorisationDetailsWithoutBillingAddress = buildJsonAuthorisationDetailsWithoutAddress();
    private final String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");
    private final String paymentProvider = PaymentGatewayName.STRIPE.getName();
    private String accountId;
    private StripeMockClient stripeMockClient;
    private DatabaseTestHelper databaseTestHelper;

    @DropwizardTestContext
    private TestContext testContext;

    private WireMockServer wireMockServer;

    @Before
    public void setup() {
        wireMockServer = testContext.getWireMockServer();
        stripeMockClient = new StripeMockClient(wireMockServer);
        
        stripeAccountId = String.valueOf(RandomUtils.nextInt());
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());

        
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
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
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntent();
        addGatewayAccountWith3DS2Enabled(ImmutableMap.of("stripe_account_id", stripeAccountId));

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

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

    }


    @Test
    public void shouldAuthoriseChargeWithoutBillingAddress() {
        stripeMockClient.mockCreatePaymentMethod();
        stripeMockClient.mockCreatePaymentIntent();
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

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
    public void shouldRespondAs3dsRequired_whenAuthorisationRequires3ds() {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));
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

    private String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }
}
