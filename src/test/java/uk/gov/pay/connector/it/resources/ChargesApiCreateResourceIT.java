package uk.gov.pay.connector.it.resources;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.gson.JsonParser;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.util.PGobject;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.util.ExternalMetadataConverter;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import javax.ws.rs.core.Response.Status;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertNull;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.JsonEncoder.toJsonWithNulls;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true, 
        configOverrides = {
                @ConfigOverride(key = "eventQueue.eventQueueEnabled", value = "true"),
                @ConfigOverride(key = "captureProcessConfig.backgroundProcessingEnabled", value = "true")
        }
)
public class ChargesApiCreateResourceIT extends ChargingITestBase {

    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String JSON_EMAIL_KEY = "email";
    private static final String JSON_PROVIDER_KEY = "payment_provider";
    private static final String JSON_LANGUAGE_KEY = "language";
    private static final String JSON_DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String JSON_CORPORATE_CARD_SURCHARGE_KEY = "corporate_card_surcharge";
    private static final String JSON_TOTAL_AMOUNT_KEY = "total_amount";
    private static final String JSON_METADATA_KEY = "metadata";
    private static final String PROVIDER_NAME = "sandbox";
    private static final String JSON_PREFILLED_CARDHOLDER_DETAILS_KEY = "prefilled_cardholder_details";
    private static final String JSON_BILLING_ADDRESS_KEY = "billing_address";
    private static final String JSON_ADDRESS_LINE_1_KEY = "line1";
    private static final String JSON_ADDRESS_LINE_2_KEY = "line2";
    private static final String JSON_ADDRESS_POST_CODE_KEY = "postcode";
    private static final String JSON_CARDHOLDER_NAME_KEY = "cardholder_name";
    private static final String JSON_ADDRESS_LINE_CITY = "city";
    private static final String JSON_ADDRESS_LINE_COUNTRY_CODE = "country";

    private static final String JSON_REFERENCE_VALUE = "Test reference";
    private static final String JSON_DESCRIPTION_VALUE = "Test description";

    public ChargesApiCreateResourceIT() {
        super(PROVIDER_NAME);
    }
    
    @Before
    @Override
    public void setUp() {
        purgeEventQueue();
        super.setUp();
    }

    @Test
    public void makeChargeAndRetrieveAmount() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_LANGUAGE_KEY, "cy"
        ));

        ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body(JSON_EMAIL_KEY, is(EMAIL))
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
        String chargeTokenId = databaseTestHelper.getChargeTokenByExternalChargeId(externalChargeId);

        String hrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
        String hrefNextUrlPost = "http://Frontend" + FRONTEND_CARD_DETAILS_URL;

        response.header("Location", is(documentLocation))
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", hrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                    put("chargeTokenId", chargeTokenId);
                }}));

        ValidatableResponse getChargeResponse = connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body(JSON_EMAIL_KEY, is(EMAIL))
                .body(JSON_LANGUAGE_KEY, is("cy"))
                .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                .body(JSON_CORPORATE_CARD_SURCHARGE_KEY, is(nullValue()))
                .body(JSON_TOTAL_AMOUNT_KEY, is(nullValue()))
                .body("containsKey('card_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("settlement_summary.capture_submit_time", nullValue())
                .body("settlement_summary.captured_time", nullValue())
                .body("refund_summary.amount_submitted", is(0))
                .body("refund_summary.amount_available", isNumber(AMOUNT))
                .body("refund_summary.status", is("pending"));


        // Reload the charge token which as it should have changed
        String newChargeTokenId = databaseTestHelper.getChargeTokenByExternalChargeId(externalChargeId);

        String newHrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

        getChargeResponse
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                    put("chargeTokenId", newChargeTokenId);
                }}));

    }

    @Test
    public void makeChargeWithNoExplicitLanguageDefaultsToEnglish() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_LANGUAGE_KEY, is("en"))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_LANGUAGE_KEY, is("en"));
    }

    @Test
    public void makeChargeNoEmailField_shouldReturnOK() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_EMAIL_KEY, EMAIL,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));


        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("containsKey('card_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

    }

    @Test
    public void shouldReturn404WhenCreatingChargeAccountIdIsNonNumeric() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient
                .withAccountId("invalidAccountId")
                .postCreateCharge(postBody)
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn422WhenAmountIsZeroIfAccountDoesNotAllowIt() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, 0,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Zero amount charges are not enabled for this gateway account"))
                .body("error_identifier", is(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED.toString()));
    }

    @Test
    public void shouldMakeChargeWhenAmountIsZeroIfAccountAllowsIt() {
        allowZeroAmountForGatewayAccount();

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, 0,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(201)
                .contentType(JSON)
                .body("amount", is(0));
    }

    @Test
    public void shouldReturn400WhenLanguageNotSupported() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_LANGUAGE_KEY, "not a supported language"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());

    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_EMAIL_KEY, EMAIL,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient
                .withAccountId(missingGatewayAccount)
                .postCreateCharge(postBody)
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, contains("Unknown gateway account: " + missingGatewayAccount))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void cannotMakeChargeForInvalidSizeOfFields() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, randomAlphabetic(256),
                JSON_DESCRIPTION_KEY, randomAlphanumeric(256),
                JSON_EMAIL_KEY, randomAlphanumeric(255),
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                        "Field [email] can have a size between 0 and 254",
                        "Field [description] can have a size between 0 and 255",
                        "Field [reference] can have a size between 0 and 255"
                ));
    }

    @Test
    public void cannotMakeChargeForMissingFields() {
        connectorRestApiClient.postCreateCharge("{}")
                .statusCode(422)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                        "Field [reference] cannot be null",
                        "Field [return_url] cannot be null",
                        "Field [description] cannot be null",
                        "Field [amount] cannot be null"
                ));
    }

    @Test
    public void shouldReturn422WhenPrefilledCardHolderDetailsFieldsAreLongerThanMaximum() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, randomAlphanumeric(256),
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, randomAlphanumeric(256),
                                JSON_ADDRESS_LINE_2_KEY, randomAlphanumeric(256),
                                JSON_ADDRESS_LINE_CITY, randomAlphanumeric(256),
                                JSON_ADDRESS_POST_CODE_KEY, randomAlphanumeric(26),
                                JSON_ADDRESS_LINE_COUNTRY_CODE, randomAlphanumeric(3)
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                        "Field [line1] can have a size between 0 and 255",
                        "Field [line2] can have a size between 0 and 255",
                        "Field [postcode] can have a size between 0 and 25",
                        "Field [country] can have an exact size of 2",
                        "Field [city] can have a size between 0 and 255",
                        "Field [cardholder_name] can have a size between 0 and 255"));
    }

    @Test
    public void shouldReturn201WhenPrefilledCardHolderDetailsFieldsAreMaximum() {
        String line1 = randomAlphanumeric(255);
        String city = randomAlphanumeric(255);
        String postCode = randomAlphanumeric(25);
        String countryCode = "GB";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, randomAlphanumeric(255),
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_LINE_CITY, city,
                                JSON_ADDRESS_POST_CODE_KEY, postCode,
                                JSON_ADDRESS_LINE_COUNTRY_CODE, countryCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL));
    }

    @Test
    public void shouldReturn201WithAllPrefilledCardHolderDetailsFields() {
        String cardholderName = "Joe Bogs";
        String line1 = "Line 1";
        String city = "City";
        String postCode = "AB1 CD2";
        String countryCode = "GB";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName,
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_LINE_CITY, city,
                                JSON_ADDRESS_POST_CODE_KEY, postCode,
                                JSON_ADDRESS_LINE_COUNTRY_CODE, countryCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_1_KEY, is(line1))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_2_KEY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_CITY, is(city))
                .body("card_details.billing_address." + JSON_ADDRESS_POST_CODE_KEY, is(postCode))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_COUNTRY_CODE, is(countryCode));
    }

    @Test
    public void shouldReturn201WithSomePrefilledCardHolderDetailsFields() {
        String cardholderName = "Joe Bogs";
        String line1 = "Line 1";
        String postCode = "AB1 CD2";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName,
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_POST_CODE_KEY, postCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_1_KEY, is(line1))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_2_KEY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_POST_CODE_KEY, is(postCode))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_CITY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_COUNTRY_CODE, is(nullValue()));
    }

    @Test
    public void shouldReturn201WithNoCardDetailsWhenPrefilledCardHolderDetailsFieldsAreNotPresent() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("containsKey('card_details')", is(false));
    }

    @Test
    public void shouldReturn201WithBillingAddresssWhenPrefilledCardHolderDetailsFieldsContainsCardHolderNameOnly() {
        String cardholderName = "Joe Bogs";
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName
                )
        ));
        
        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("containsKey('billing_address')", is(false));
    }
    
    @Test
    public void shouldReturnChargeWithNoMetadataField_whenCreatedWithEmptyMetadata() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, Map.of()
        ));

        ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body("$", not(hasKey("metadata")));

        String chargeExternalId = response.extract().path(JSON_CHARGE_KEY);
        connectorRestApiClient
                .withChargeId(chargeExternalId)
                .getCharge()
                .body("$", not(hasKey("metadata")));
        
        connectorRestApiClient
                .withQueryParam("reference", JSON_REFERENCE_VALUE)
                .getChargesV1()
                .body("results[0]", not(hasKey("metadata")));
        
        assertNull(databaseTestHelper.getChargeByExternalId(chargeExternalId).get("metadata"));
    }

    @Test
    public void shouldCreateChargeWithExternalMetadata() {
        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.23
        );

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, metadata
        ));

        ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_METADATA_KEY + ".key1", is("string"))
                .body(JSON_METADATA_KEY + ".key2", is(true))
                .body(JSON_METADATA_KEY + ".key3", is(123));

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(externalChargeId);
        ExternalMetadataConverter converter = new ExternalMetadataConverter();
        ExternalMetadata externalMetadata = converter.convertToEntityAttribute((PGobject) charge.get("external_metadata"));

        assertThat(externalMetadata.getMetadata(), equalTo(metadata));
    }

    @Test
    public void shouldReturn422ForInvalidMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", null);
        metadata.put("key2", new HashMap<>());
        metadata.put("", "validValue");
        metadata.put("key3", "");
        metadata.put("key4", "This value is too long because it is over fifty characters!");
        metadata.put("This is key number 5 and it is too long", "This is valid");

        String postBody = toJsonWithNulls(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, metadata
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                        "Field [metadata] values must be of type String, Boolean or Number",
                        "Field [metadata] keys must be between 1 and 30 characters long",
                        "Field [metadata] must not have null values",
                        "Field [metadata] values must be no greater than 50 characters long"));
    }

    @Test
    public void shouldReturn201IfMetadataIsNull_BecauseWeDoNotDeserializeNullValues() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(JSON_AMOUNT_KEY, AMOUNT);
        payload.put(JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE);
        payload.put(JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE);
        payload.put(JSON_RETURN_URL_KEY, RETURN_URL);
        payload.put(JSON_EMAIL_KEY, EMAIL);
        payload.put(JSON_METADATA_KEY, null);

        String postBody = toJsonWithNulls(payload);

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(201)
                .contentType(JSON);
    }

    @Test
    public void shouldFailValidationWhenMetadataIsAString() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, "metadata cannot be a string"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains("Field [metadata] must be an object of JSON key-value pairs"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldFailValidationWhenMetadataIsAnArray() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, new Object[1]
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains("Field [metadata] must be an object of JSON key-value pairs"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
    
    @Test
    public void shouldEmitPaymentCreatedEventWhenChargeIsSuccessfullyCreated() throws Exception {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        final ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(201);

        String chargeExternalId = response.extract().path(JSON_CHARGE_KEY);
        final Map<String, Object> persistedCharge = databaseTestHelper.getChargeByExternalId(chargeExternalId);
        final ZonedDateTime persistedCreatedDate = ZonedDateTime.ofInstant(((Timestamp) persistedCharge.get("created_date")).toInstant(), ZoneOffset.UTC);

        Thread.sleep(100);
        List<Message> messages = readMessagesFromEventQueue();
        
        final Message message = messages.get(0);
        ZonedDateTime eventTimestamp = ZonedDateTime.parse(
                new JsonParser()
                        .parse(message.getBody())
                        .getAsJsonObject()
                        .get("timestamp")
                        .getAsString()
        );
        assertThat(message.getBody(), hasJsonPath("$.event_type", equalTo("PAYMENT_CREATED")));
        assertThat(eventTimestamp, is(within(200, MILLIS, persistedCreatedDate)));
    }

    private List<Message> readMessagesFromEventQueue() {
        AmazonSQS sqsClient = testContext.getInstanceFromGuiceContainer(AmazonSQS.class);

        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(testContext.getEventQueueUrl());
        receiveMessageRequest
                .withMessageAttributeNames()
                .withWaitTimeSeconds(1)
                .withMaxNumberOfMessages(10);

        ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

        return receiveMessageResult.getMessages();
    }
    
    private void purgeEventQueue() {
        AmazonSQS sqsClient = testContext.getInstanceFromGuiceContainer(AmazonSQS.class);
        sqsClient.purgeQueue(new PurgeQueueRequest(testContext.getEventQueueUrl()));
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "https://localhost:" + testContext.getPort() + "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }
}
