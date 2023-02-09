package uk.gov.pay.connector.it.resources.epdq;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.io.IOException;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_NEW_ORDER;
import static uk.gov.pay.connector.gateway.model.Auth3dsResult.Auth3dsResultOutcome.AUTHORISED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class EpdqCardResourceIT extends ChargingITestBase {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private String authorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");

    public EpdqCardResourceIT() {
        super("epdq");
    }

    @Test
    public void shouldAuthorise_whenTransactionIsSuccessful() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        epdqMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());

        verifyPostToPath(String.format("/epdq/%s", ROUTE_FOR_NEW_ORDER), getExpectedAuthRequestBody(chargeId));
    }

    @Test
    public void shouldAuthorise_whenRequires3dsAnd3dsAuthenticationSuccessful() {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        epdqMockClient.mockAuthorisation3dsSuccess();

        ValidatableResponse response = givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then();

        response
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldSuccessfully_authorise3ds() throws Exception {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, RandomIdGenerator.newId());
        epdqMockClient.mockAuthorisationQuerySuccess();

        Map<String, String> payload = ImmutableMap.of("auth_3ds_result", AUTHORISED.name());

        givenSetup()
                .body(objectMapper.writeValueAsString(payload))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotAuthorise_whenTransactionIsRefused() {
        epdqMockClient.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAnApplePayPayment() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for ePDQ"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAGooglePayPayment() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validPayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for ePDQ"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldNotAuthorise_whenTransactionIsInError() {
        epdqMockClient.mockAuthorisationError();

        String expectedErrorMessage = "ePDQ authorisation response (PAYID: 0, STATUS: 0, NCERROR: 50001111, " +
                "NCERRORPLUS: An error has occurred; please try again later. If you are the owner or the integrator " +
                "of this website, please log into the  back office to see the details of the error.)";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInAnyOtherNonSupportedState() {
        epdqMockClient.mockAuthorisationOther();

        String expectedErrorMessage = "ePDQ authorisation response (PAYID: 3014644340, STATUS: 52, NCERROR: 0, NCERRORPLUS: !)";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInWaitingExternalState() {
        epdqMockClient.mockAuthorisationWaitingExternal();

        String expectedErrorMessage = "This transaction was deferred.";
        String expectedChargeStatus = AUTHORISATION_SUBMITTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInWaitingState() {
        epdqMockClient.mockAuthorisationWaiting();

        String expectedErrorMessage = "This transaction was deferred.";
        String expectedChargeStatus = AUTHORISATION_SUBMITTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    private void verifyPostToPath(String path, String body) {
        wireMockServer.verify(
                postRequestedFor(urlPathEqualTo(path))
                        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(equalTo(body))
        );
    }

    private String getExpectedAuthRequestBody(String chargeId) {
        EpdqPayloadDefinitionForNewOrder epdqPayloadDefinition = new EpdqPayloadDefinitionForNewOrder();
        epdqPayloadDefinition.setAmount("6234");
        epdqPayloadDefinition.setOrderId(chargeId);
        epdqPayloadDefinition.setPspId(credentials.get(CREDENTIALS_MERCHANT_ID).toString());
        epdqPayloadDefinition.setUserId(credentials.get(CREDENTIALS_USERNAME).toString());
        epdqPayloadDefinition.setPassword(credentials.get(CREDENTIALS_PASSWORD).toString());
        epdqPayloadDefinition.setShaInPassphrase(credentials.get(CREDENTIALS_SHA_IN_PASSPHRASE).toString());

        Address address = new Address(ADDRESS_LINE_1, null, ADDRESS_POSTCODE, ADDRESS_CITY, ADDRESS_CITY, ADDRESS_COUNTRY_GB);
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Scrooge McDuck")
                .withCardNo("4444333322221111")
                .withCvc("123")
                .withEndDate(CardExpiryDate.valueOf("11/99"))
                .withAddress(address)
                .build();
        epdqPayloadDefinition.setAuthCardDetails(authCardDetails);

        return epdqPayloadDefinition.createGatewayOrder().getPayload();
    }
}
