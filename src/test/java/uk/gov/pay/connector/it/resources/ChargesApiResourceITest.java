package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.CardFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardCaptureProcess;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest extends ChargingITestBase {

    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String JSON_EMAIL_KEY = "email";
    private static final String JSON_PROVIDER_KEY = "payment_provider";
    private static final String JSON_LANGUAGE_KEY = "language";
    private static final String JSON_DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String JSON_CORPORATE_SURCHARGE_KEY = "corporate_surcharge";
    private static final String JSON_TOTAL_AMOUNT_KEY = "total_amount";
    private static final String PROVIDER_NAME = "sandbox";
    private static final Long AMOUNT = 6234L;

    private String returnUrl = "http://service.url/success-page/";
    private String email = randomAlphabetic(242) + "@example.com";

    private RestAssuredClient createChargeApi = new RestAssuredClient(app, accountId);
    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);

    public ChargesApiResourceITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void makeChargeAndRetrieveAmount() {
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email)
                .put(JSON_LANGUAGE_KEY, "cy")
                .build()
        );

        ValidatableResponse response = createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body(JSON_EMAIL_KEY, is(email))
                .body(JSON_LANGUAGE_KEY, is("cy"))
                .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                .body("containsKey('card_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("refund_summary.amount_submitted", is(0))
                .body("refund_summary.amount_available", isNumber(AMOUNT))
                .body("refund_summary.status", is("pending"))
                .body("settlement_summary.capture_submit_time", nullValue())
                .body("settlement_summary.captured_time", nullValue())
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(accountId, externalChargeId);
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String hrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
        String hrefNextUrlPost = "http://Frontend" + FRONTEND_CARD_DETAILS_URL;

        response.header("Location", is(documentLocation))
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", hrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
                    put("chargeTokenId", chargeTokenId);
                }}));

        ValidatableResponse getChargeResponse = getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body(JSON_EMAIL_KEY, is(email))
                .body(JSON_LANGUAGE_KEY, is("cy"))
                .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                .body(JSON_CORPORATE_SURCHARGE_KEY, is(nullValue()))
                .body(JSON_TOTAL_AMOUNT_KEY, is(nullValue()))
                .body("containsKey('card_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("settlement_summary.capture_submit_time", nullValue())
                .body("settlement_summary.captured_time", nullValue())
                .body("refund_summary.amount_submitted", is(0))
                .body("refund_summary.amount_available", isNumber(AMOUNT))
                .body("refund_summary.status", is("pending"));


        // Reload the charge token which as it should have changed
        String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String newHrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

        getChargeResponse
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
                    put("chargeTokenId", newChargeTokenId);
                }}));

    }

    @Test
    public void makeChargeWithNoExplicitLanguageDefaultsToEnglish() {
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, "Test reference")
                .put(JSON_DESCRIPTION_KEY, "Test description")
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email)
                .build()
        );

        ValidatableResponse response = createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_LANGUAGE_KEY, is("en"))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_LANGUAGE_KEY, is("en"));
    }

    @Test
    public void makeChargeSubmitCaptureAndCheckSettlementSummary() {
        ZonedDateTime startOfTest = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC);
        String expectedDayOfCapture = DateTimeUtils.toUTCDateString(startOfTest);

        String chargeId = authoriseNewCharge();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        // Trigger the capture process programmatically which normally would be invoked by the scheduler.
        app.getInstanceFromGuiceContainer(CardCaptureProcess.class).runCapture();

        getCharge(chargeId)
                .body("settlement_summary.capture_submit_time", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("settlement_summary.capture_submit_time", isWithin(10, SECONDS))
                .body("settlement_summary.captured_date", equalTo(expectedDayOfCapture))
        ;
    }

    @Test
    public void makeChargeNoEmailField_shouldReturnOK() {
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_EMAIL_KEY, email)
                .put(JSON_RETURN_URL_KEY, returnUrl).build());


        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body("containsKey('card_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

    }

    @Test
    public void shouldReturn404WhenCreatingChargeAccountIdIsNonNumeric() {

        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "Test reference",
                JSON_DESCRIPTION_KEY, "Test description",
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));

        createChargeApi
                .withAccountId("invalidAccountId")
                .postCreateCharge(postBody)
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404OnGetChargeWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("wrongAccount")
                .withChargeId("123")
                .withHeader(HttpHeaders.ACCEPT, JSON.getAcceptHeader())
                .getCharge()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404OnGetTransactionsWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn400WhenAmountIsOverMaxAmount() {

        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, 10000001)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email).build());

        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());

    }

    @Test
    public void shouldReturn400WhenAmountIsLessThanMinAmount() {

        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, 0)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email).build());

        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void shouldReturn400WhenLanguageNotSupported() {
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, "Test reference")
                .put(JSON_DESCRIPTION_KEY, "Test description")
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email)
                .put(JSON_LANGUAGE_KEY, "not a supported language")
                .build()
        );

        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());

    }

    @Test
    public void shouldGetCardDetails_ifStatusIsBeyondAuthorised() {
        long chargeId = nextInt();
        String externalChargeId = "charge1";

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, CardFixture.aValidCard().withCardNo("12345678").build());
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details", is(notNullValue()))
                .body("card_details.last_digits_card_number", is("5678"))
                .body("card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_STATE_KEY, is(EXTERNAL_SUBMITTED.getStatus()));
    }

    @Test
    public void shouldReturnCardBrandLabelWhenChargeIsAuthorised() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        DatabaseFixtures.TestCardType testCardType = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aMastercardCreditCardType()
                .insert();
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null,
                ServicePaymentReference.of("ref"), null, email);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, testCardType.getBrand(), "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");

        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(testCardType.getLabel()));
    }

    @Test
    public void shouldReturnEmptyCardBrandLabelWhenChargeIsAuthorisedAndBrandUnknown() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null,
                ServicePaymentReference.of("ref"), null, email);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("card_details.card_brand", is(""));
    }

    @Test
    public void shouldReturnCorporateSurchargeAndTotalAmount_V1() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null,
                ServicePaymentReference.of("ref"), null, email);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "unknown-brand", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().updateCorporateSurcharge(chargeId, 50L);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0]." + JSON_CHARGE_KEY, is(externalChargeId))
                .body("results[0]." + JSON_CORPORATE_SURCHARGE_KEY, is(50))
                .body("results[0]." + JSON_TOTAL_AMOUNT_KEY, is(AMOUNT.intValue() + 50));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() {

        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null,
                ServicePaymentReference.of("My reference"), createdDate);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "VISA", "1234", "123456", "Mr. McPayment",
                "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        String description = "Test description";

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].gateway_account", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].return_url", is(returnUrl))
                .body("results[0].description", is(description))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].payment_provider", is(PROVIDER_NAME));
    }

    @Test
    public void shouldGetChargeLegacyTransactions() {

        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null,
                ServicePaymentReference.of("My reference"), createdDate);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "visa", null, null, null, null,
                null, null, null, null, null, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].card_details.card_brand", is("Visa"))
                .body("results[0].card_details.cardholder_name", nullValue())
                .body("results[0].card_details.last_digits_card_number", nullValue())
                .body("results[0].card_details.first_digits_card_number", nullValue())
                .body("results[0].card_details.expiry_date", nullValue());
    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", DateTimeUtils.toUTCDateTimeString(now().minusDays(1)))
                .withQueryParam("to_date", DateTimeUtils.toUTCDateTimeString(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].settlement_summary.capture_submit_time", nullValue())
                .body("results[0].settlement_summary.captured_time", nullValue())
                .body("results[0].refund_summary.amount_submitted", is(0))
                .body("results[0].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[0].refund_summary.status", is("pending"))
                .body("results[1].settlement_summary.capture_submit_time", nullValue())
                .body("results[1].settlement_summary.captured_time", nullValue())
                .body("results[1].refund_summary.amount_submitted", is(0))
                .body("results[1].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[1].refund_summary.status", is("pending"));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1", "ref-2"));
        assertThat(references, not(contains("ref-3")));

        // collect statuses from states
        List<Object> statuses = results
                .stream()
                .map(result -> ((Map<Object, Object>) result.get("state")).get("status"))
                .collect(Collectors.toList());

        assertThat(statuses, containsInAnyOrder("created", "started"));
        assertThat(statuses, not(contains("confirmed")));

        List<String> createdDateStrings = collect(results, "created_date");
        datesFrom(createdDateStrings).forEach(createdDate ->
                assertThat(createdDate, is(within(1, DAYS, now())))
        );
        List<String> emails = collect(results, "email");
        assertThat(emails, contains(email, email));
    }

    @Test
    public void shouldFilterTransactionsByEmail() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("email", "@example.com")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].email", endsWith("example.com"));
    }

    @Test
    public void shouldFilterTransactionsByCardHolderName() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("cardholder_name", "McPayment")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.cardholder_name", is("Mr. McPayment"));
    }

    @Test
    public void shouldFilterTransactionsByFirstDigitsCardNumber() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "123456")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldIgnoreFirstDigitsCardNumberIfBlank() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldIgnoreFirstDigitsCardNumberIfPartial() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "12")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldFilterTransactionsByLastDigitsCardNumber() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "1234")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.last_digits_card_number", is("1234"));
    }

    @Test
    public void shouldIgnoreLastDigitsCardNumberIfBlank() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.last_digits_card_number", is("1234"));
    }

    @Test
    public void shouldIgnoreLastDigitsCardNumberIfPartial() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "12")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }
    
    @Test
    public void shouldFilterTransactionsByCardBrand() {
        String searchedCardBrand = "visa";

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), searchedCardBrand);
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now(), "master-card");
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().minusDays(2), searchedCardBrand);

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("card_brand", searchedCardBrand)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Visa"))
                .body("results[1].card_details.card_brand", endsWith("Visa"));
    }

    @Test
    public void shouldFilterTransactionsByBlankCardBrand() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), "visa");
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now(), "master-card");

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("card_brand", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Mastercard"))
                .body("results[1].card_details.card_brand", endsWith("Visa"));
    }

    @Test
    public void shouldFilterTransactionsByMultipleCardBrand() {
        String visa = "visa";
        String mastercard = "master-card";

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), visa);
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusHours(1), mastercard);
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2), "american-express");

        getChargeApi
                .withQueryParams("card_brand", asList(visa, mastercard))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Visa"))
                .body("results[1].card_details.card_brand", endsWith("Mastercard"));
    }

    @Test
    public void shouldShowTotalCountResultsAndHalLinksForCharges() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusDays(1));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-4"), now().minusDays(3));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-5"), now().minusDays(4));

        assertNavigationLinksWhenNoResultFound();
        assertResultsWhenPageAndDisplaySizeNotSet();
        assertResultsAndJustSelfLinkWhenJustOneResult();
        assertResultsAndNoPrevLinkWhenOnFirstPage();
        assertResultsAndAllLinksWhenOnMiddlePage();
        assertResultsAndNoNextLinksWhenOnLastPage();
        assert404WhenRequestingInvalidPage();
        assertBadRequestForNegativePageDisplaySize();
    }

    @Test
    public void shouldShowValidationsForDateAndPageDisplaySize() {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'from_date' not in correct format",
                "query param 'to_date' not in correct format",
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("to_date", "-1")
                .withQueryParam("from_date", "-1")
                .withQueryParam("page", "-1")
                .withQueryParam("display_size", "-2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    @Test
    public void shouldGetAllTransactionsForDefault_page_1_size_100_inCreationDateOrder() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(5));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "charge_id");
        assertThat(references, is(ImmutableList.of(id_5, id_4, id_3, id_2, id_1)));
    }

    @Test
    public void shouldGetTransactionsForPageAndSizeParams_inCreationDateOrder() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_5, id_4)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_3, id_2)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "3")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_1)));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "Test reference",
                JSON_DESCRIPTION_KEY, "Test description",
                JSON_EMAIL_KEY, email,
                JSON_RETURN_URL_KEY, returnUrl));

        createChargeApi
                .withAccountId(missingGatewayAccount)
                .postCreateCharge(postBody)
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForInvalidSizeOfFields() {
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, randomAlphabetic(256))
                .put(JSON_DESCRIPTION_KEY, randomAlphanumeric(256))
                .put(JSON_EMAIL_KEY, randomAlphanumeric(255))
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl).build());

        createChargeApi.postCreateCharge(postBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) are too big: [description, reference, email]"));
    }

    @Test
    public void cannotMakeChargeForMissingFields() {
        createChargeApi.postCreateCharge("{}")
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) missing: [amount, description, reference, return_url]"));
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() {
        String chargeId = "23235124";
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(format("Charge with id [%s] not found.", chargeId)));
    }

    @Test
    public void shouldGetSuccessAndFailedResponseForExpiryChargeTask() {
        //create charge
        String extChargeId = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        // run expiry task
        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetSuccessResponseForExpiryChargeTaskFor3dsRequiredPayments() {
        String extChargeId = addChargeAndCardDetails(ChargeStatus.AUTHORISATION_3DS_REQUIRED, ServicePaymentReference.of("ref"),
                ZonedDateTime.now().minusMinutes(90));

        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetSuccessForExpiryChargeTask_withStatus_awaitingCaptureRequest() {
        //create charge
        String extChargeId = addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        // run expiry task
        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_awaitingCaptureRequest() {
        //create charge
        String extChargeId = addChargeAndCardDetails(AWAITING_CAPTURE_REQUEST,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(NO_CONTENT.getStatusCode());

        // get the charge back and assert its status is expired
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNoContentForMarkChargeAsCaptureApproved_withStatus_captureApproved() {
        //create charge
        String extChargeId = addChargeAndCardDetails(CAPTURE_APPROVED,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(NO_CONTENT.getStatusCode());

        // get the charge back and assert its status is expired
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(CAPTURE_APPROVED.toExternal().getStatus()));

    }

    @Test
    public void shouldGetNotFoundFor_markChargeAsCaptureApproved_whenNoChargeExists() {

        getChargeApi
                .withAccountId(accountId)
                .withChargeId("i-do-not-exist")
                .postMarkChargeAsCaptureApproved()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is("Charge with id [i-do-not-exist] not found."));
    }

    @Test
    public void shouldGetConflictExceptionFor_markChargeAsCaptureApproved_whenNoChargeExists() {
        //create charge
        String extChargeId = addChargeAndCardDetails(EXPIRED,
                ServicePaymentReference.of("ref"), ZonedDateTime.now().minusMinutes(90));

        final String expectedErrorMessage = format("Operation for charge conflicting, %s, attempt to perform delayed capture on charge not in AWAITING CAPTURE REQUEST state.", extChargeId);
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .postMarkChargeAsCaptureApproved()
                .statusCode(CONFLICT.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(expectedErrorMessage));
    }

    private void assert404WhenRequestingInvalidPage() {
        // when 5 charges are there, page is 10, display-size is 2
        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "10")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", is("the requested page not found"));
    }

    private void assertBadRequestForNegativePageDisplaySize() {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "-1")
                .withQueryParam("display_size", "-2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    private void assertResultsAndJustSelfLinkWhenJustOneResult() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1"));
        assertThat(references, not(contains("ref-1", "ref-3", "ref-4", "ref-5")));
    }

    private void assertResultsWhenPageAndDisplaySizeNotSet() {
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1"));
        assertThat(references, not(contains("ref-1", "ref-3", "ref-4", "ref-5")));
    }

    private void assertNavigationLinksWhenNoResultFound() {
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "junk-yard")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0))
                .body("total", is(0))
                .body("count", is(0))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        collect(results, "reference");
        assertTrue(results.isEmpty());
    }

    private void assertResultsAndNoPrevLinkWhenOnFirstPage() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(5))
                .body("count", is(2))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")))
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1", "ref-2"));
        assertThat(references, not(contains("ref-3", "ref-4", "ref-5")));
    }

    private void assertResultsAndAllLinksWhenOnMiddlePage() {
        // when 5 charges are there, page is 2, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "2")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(5))
                .body("count", is(2))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.prev_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-3", "ref-4"));
        assertThat(references, not(contains("ref-1", "ref-2", "ref-5")));
    }

    private void assertResultsAndNoNextLinksWhenOnLastPage() {
        // when 5 charges are there, page is 3, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "3")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getCharges()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(5))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")))
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-5"));
        assertThat(references, not(contains("ref-1", "ref-2", "ref-3", "ref-4")));
    }

    private List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }

    private String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate) {
        return addChargeAndCardDetails(status, reference, fromDate, "");

    }

    private String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        return addChargeAndCardDetails(nextLong(), status, reference, fromDate, cardBrand);
    }

    private String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, reference, fromDate, email);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, cardBrand, "1234", "123456", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");

        return externalChargeId;
    }

    private List<String> collect(List<Map<String, Object>> results, String field) {
        return results.stream().map(result -> result.get(field).toString()).collect(Collectors.toList());
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "https://localhost:" + app.getLocalPort() + "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "https://localhost:" + app.getLocalPort()
                + "/v1/api/accounts/{accountId}/charges".replace("{accountId}", accountId)
                + queryParams;
    }
}
