package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZonedDateTime;
import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml", withDockerSQS = true)
public class ChargesFrontendResourceIT {

    @DropwizardTestContext
    private TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private String accountId = String.valueOf(nextLong());
    private String description = "Test description";
    private String returnUrl = "http://whatever.com";
    private String email = randomAlphabetic(242) + "@example.com";
    private String serviceName = "a cool service";
    private String analyticsId = "test-123";
    private String type = "test";
    private String paymentProvider = "sandbox";
    private long expectedAmount = 6234L;
    private long corporateCreditCardSurchargeAmount = 213L;
    private long corporateDebitCardSurchargeAmount = 57L;

    private RestAssuredClient connectorRestApi;

    @Before
    public void setupGatewayAccount() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        CardTypeEntity mastercardCredit = databaseTestHelper.getMastercardCreditCard();
        CardTypeEntity visaCredit = databaseTestHelper.getVisaCreditCard();
        var gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withDescription(description)
                .withAnalyticsId(analyticsId)
                .withCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount)
                .withCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount)
                .withServiceName("a cool service")
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        databaseTestHelper.addAcceptedCardType(Long.valueOf(accountId), mastercardCredit.getId());
        databaseTestHelper.addAcceptedCardType(Long.valueOf(accountId), visaCredit.getId());
        connectorRestApi = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @Test
    public void getChargeShouldIncludeExpectedLinksAndGatewayAccount() {

        String chargeId = postToCreateACharge(expectedAmount);
        String expectedLocation = "https://localhost:" + testContext.getPort() + "/v1/frontend/charges/" + chargeId;

        validateGetCharge(expectedAmount, chargeId, CREATED, true)
                .body("links", hasSize(3))
                .body("links", containsLink("self", GET, expectedLocation))
                .body("links", containsLink("cardAuth", POST, expectedLocation + "/cards"))
                .body("links", containsLink("cardCapture", POST, expectedLocation + "/capture")).extract().response();
    }

    @Test
    public void getChargeShouldIncludeCorporateCardSurchargeAndTotalAmount() {

        String chargeExternalId = postToCreateACharge(expectedAmount);
        String expectedLocation = "https://localhost:" + testContext.getPort() + "/v1/frontend/charges/" + chargeExternalId;
        final Long chargeId = databaseTestHelper.getChargeIdByExternalId(chargeExternalId);
        databaseTestHelper.updateCorporateSurcharge(chargeId, corporateCreditCardSurchargeAmount);

        getChargeFromResource(chargeExternalId)
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeExternalId))
                .body("containsKey('reference')", is(false))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("status", is(CREATED.getValue()))
                .body("return_url", is(returnUrl))
                .body("email", is(email))
                .body("created_date", is(notNullValue()))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{1,3}.\\d{0,3}Z"))
                .body("created_date", isWithin(10, SECONDS))
                .body("language", is("en"))
                .body("delayed_capture", is(true))
                .body("corporate_card_surcharge", is(213))
                .body("total_amount", is(6447))
                .body("moto", is(false))
                .body("links", hasSize(3))
                .body("links", containsLink("self", GET, expectedLocation))
                .body("links", containsLink("cardAuth", POST, expectedLocation + "/cards"))
                .body("links", containsLink("cardCapture", POST, expectedLocation + "/capture"));
    }

    @Test
    public void getChargeShouldIncludeWalletType() {
        String externalChargeId = postToCreateACharge(expectedAmount);
        final long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        databaseTestHelper.addWalletType(chargeId, WalletType.APPLE_PAY);

        getChargeFromResource(externalChargeId)
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(externalChargeId))
                .body("wallet_type", is(WalletType.APPLE_PAY.toString()));
    }

    @Test
    public void getChargeShouldIncludeFeeIfItExists() {
        String externalChargeId = postToCreateACharge(expectedAmount);
        final long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        final long feeCollected = 100L;
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id");

        getChargeFromResource(externalChargeId)
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(externalChargeId))
                .body("fee", is(100));
    }

    @Test
    public void getChargeShouldIncludeNetAmountIfFeeExists() {
        String externalChargeId = postToCreateACharge(expectedAmount);
        final long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        final long feeCollected = 100L;
        databaseTestHelper.addFee(RandomIdGenerator.newId(), chargeId, 100L, feeCollected, ZonedDateTime.now(), "irrelevant_id");

        getChargeFromResource(externalChargeId)
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(externalChargeId))
                .body("net_amount", is(6134));
    }

    @Test
    public void shouldReturnInternalChargeStatusIfStatusIsAuthorised() {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = nextLong();

        CardTypeEntity mastercardCredit = databaseTestHelper.getMastercardCreditCard();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(expectedAmount)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(returnUrl)
                .withDelayedCapture(false)
                .withEmail(email)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, mastercardCredit.getBrand(), "1234", "123456",
                "Mr. McPayment", CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city",
                null, "country");
        validateGetCharge(expectedAmount, externalChargeId, AUTHORISATION_SUCCESS, false);
    }

    @Test
    public void shouldReturnEmptyCardBrandLabelIfStatusIsAuthorisedAndBrandUnknown() {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = nextLong();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(expectedAmount)
                .withStatus(AUTHORISATION_SUCCESS)
                .withReturnUrl(returnUrl)
                .withDelayedCapture(false)
                .withEmail(email)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");
        validateGetCharge(expectedAmount, externalChargeId, AUTHORISATION_SUCCESS, false);
    }

    @Test
    public void shouldIncludeAuth3dsDataInResponse() {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = nextLong();
        String issuerUrl = "https://issuer.example.com/3ds";
        String paRequest = "test-pa-request";

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(expectedAmount)
                .withStatus(AUTHORISATION_3DS_REQUIRED)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");
        databaseTestHelper.updateCharge3dsDetails(chargeId, issuerUrl, paRequest, null);

        connectorRestApi
                .withChargeId(externalChargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("auth_3ds_data.paRequest", is(paRequest))
                .body("auth_3ds_data.issuerUrl", is(issuerUrl));
    }

    @Test
    public void shouldNotIncludeBillingAddress_whenNoAddressDetailsPresentInDB() {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = nextLong();

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(expectedAmount)
                .withStatus(AUTHORISATION_SUCCESS)
                .build());
        databaseTestHelper.updateChargeCardDetails(chargeId, "unknown", "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, null, null, null, null, null, null);

        connectorRestApi
                .withChargeId(externalChargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("$card_details", not(hasKey("billing_address")));
    }

    @Test
    public void shouldUpdateChargeStatusToEnteringCardDetails() {

        String chargeId = postToCreateACharge(expectedAmount);
        String putBody = toJson(ImmutableMap.of("new_status", ENTERING_CARD_DETAILS.getValue()));

        connectorRestApi
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .putChargeStatus(putBody)
                .statusCode(NO_CONTENT.getStatusCode())
                .body(emptyOrNullString());

        validateGetCharge(expectedAmount, chargeId, ENTERING_CARD_DETAILS, true);
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() {
        String chargeId = RandomIdGenerator.newId();
        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body("message", contains(format("Charge with id [%s] not found.", chargeId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void patchValidEmailOnChargeWithReplaceOp_shouldReturnOk() {
        String chargeId = postToCreateACharge(expectedAmount);
        String email = randomAlphabetic(242) + "@example.com";

        String patchBody = createPatch("replace", "email", email);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("email", is(email));
    }

    @Test
    public void sanitize_patchValidEmailOnChargeWithReplaceOp_shouldReturnOk_withSanitizedData() {

        String chargeId = postToCreateACharge(expectedAmount);
        String email = "r-12-34-5  Ju&^6501-76@example.com";
        String sanitizedEmail = "r-**-**-*  Ju&^****-**@example.com";

        String patchBody = createPatch("replace", "email", email);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("email", is(sanitizedEmail));
    }

    @Test
    public void patchValidEmailOnChargeWithAnyOpExceptReplace_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String patchBody = createPatch("delete", "email", "a@b.c");

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("Bad patch parameters{op=delete, path=email, value=a@b.c}"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void patchTooLongEmailOnCharge_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String tooLongEmail = randomAlphanumeric(243) + "@example.com";
        String patchBody = createPatch("replace", "email", tooLongEmail);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains(format("Invalid patch parameters{op=replace, path=email, value=%s}", tooLongEmail)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void patchUnpatchableChargeField_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String patchBody = createPatch("replace", "amount", "1");

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("Bad patch parameters{op=replace, path=amount, value=1}"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private String postToCreateACharge(long expectedAmount) {
        String reference = "Test reference";
        String postBody = createChargePostBody(description, expectedAmount, accountId, returnUrl, email);
        ValidatableResponse response = connectorRestApi
                .withAccountId(accountId)
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body("charge_id", is(notNullValue()))
                .body("reference", is(reference))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .body("email", is(email))
                .body("created_date", is(notNullValue()))
                .body("language", is("en"))
                .body("delayed_capture", is(true))
                .body("corporate_card_surcharge", is(nullValue()))
                .body("total_amount", is(nullValue()))
                .contentType(JSON);

        return response.extract().path("charge_id");
    }

    private ValidatableResponse getChargeFromResource(String chargeId) {
        return connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge();
    }

    private ValidatableResponse validateGetCharge(long expectedAmount, String chargeId, ChargeStatus chargeStatus,
                                                  boolean delayedCapture) {
        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("containsKey('reference')", is(false))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("status", is(chargeStatus.getValue()))
                .body("return_url", is(returnUrl))
                .body("email", is(email))
                .body("created_date", is(notNullValue()))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{1,3}.\\d{0,3}Z"))
                .body("created_date", isWithin(10, SECONDS))
                .body("language", is("en"))
                .body("delayed_capture", is(delayedCapture))
                .body("corporate_card_surcharge", is(nullValue()))
                .body("total_amount", is(nullValue()));
        validateGatewayAccount(response);
        validateCardDetails(response, chargeStatus);
        return response;
    }

    private void validateGatewayAccount(ValidatableResponse response) {
        response
                .body("containsKey('gateway_account')", is(true))
                .body("gateway_account.gateway_account_id", is(Long.valueOf(accountId)))
                .body("gateway_account.containsKey('credentials')", is(false))
                .body("gateway_account.service_name", is(serviceName))
                .body("gateway_account.payment_provider", is(paymentProvider))
                .body("gateway_account.type", is(type))
                .body("gateway_account.analytics_id", is(analyticsId))
                .body("gateway_account.corporate_credit_card_surcharge_amount", isNumber(corporateCreditCardSurchargeAmount))
                .body("gateway_account.corporate_debit_card_surcharge_amount", isNumber(corporateDebitCardSurchargeAmount))
                .body("gateway_account.integration_version_3ds", is(2))
                .body("gateway_account.card_types", is(notNullValue()))
                .body("gateway_account.card_types", containsInAnyOrder(
                        allOf(
                                hasKey("id"),
                                hasEntry("label", "Mastercard"),
                                hasEntry("type", "CREDIT"),
                                hasEntry("brand", "master-card")
                        ),
                        allOf(
                                hasKey("id"),
                                hasEntry("label","Visa"),
                                hasEntry("type", "CREDIT"),
                                hasEntry("brand", "visa")
                        )
                ));
    }

    private void validateCardDetails(ValidatableResponse response, ChargeStatus status) {
        if (status.equals(ChargeStatus.AUTHORISATION_SUCCESS)) {
            response
                    .body("card_details", is(notNullValue()))
                    .body("card_details.charge_id", is(nullValue()))
                    .body("card_details.last_digits_card_number", is("1234"))
                    .body("card_details.first_digits_card_number", is("123456"))
                    .body("card_details.cardholder_name", is("Mr. McPayment"))
                    .body("card_details.expiry_date", is("03/18"))
                    .body("card_details.billing_address", is(notNullValue()))
                    .body("card_details.billing_address.line1", is("line1"))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is("city"))
                    .body("card_details.billing_address.postcode", is("postcode"))
                    .body("card_details.billing_address.country", is("country"))
                    .body("card_details.billing_address.county", is(nullValue()));
        } else {
            response.body("containsKey('card_details')", is(false));
        }
    }

    private void setupLifeCycleEventsFor(Long chargeId, List<ChargeStatus> statuses) {
        statuses.forEach(st -> databaseTestHelper.addEvent(chargeId, st.getValue()));
    }

    private static String createPatch(String op, String path, String value) {
        return toJson(ImmutableMap.of("op", op, "path", path, "value", value));

    }
}
