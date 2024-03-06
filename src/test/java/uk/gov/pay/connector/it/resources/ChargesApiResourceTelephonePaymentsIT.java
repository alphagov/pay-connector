package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;

public class ChargesApiResourceTelephonePaymentsIT extends NewChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";
    private static final HashMap<String, Object> postBody = new HashMap<>();
    private static final String stringOf51Characters = StringUtils.repeat("*", 51);
    private static final String stringOf50Characters = StringUtils.repeat("*", 50);

    private final String providerId = "17498-8412u9-1273891239";

    public ChargesApiResourceTelephonePaymentsIT() {
        super(PROVIDER_NAME);
    }

    @Before
    public void setUp() {
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", providerId);
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                )
        );
     }
    
    @After
    @Override
    public void tearDown() {
        super.tearDown();
        postBody.clear();
    }

    @Test
    public void createTelephoneChargeForOnlyRequiredFields() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.parseLong(accountId));
        ValidatableResponse response = connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("payment_outcome.status", is("success"))
                .body("charge_id.length()", is(26))
                .body("state.status", is("success"))
                .body("state.finished", is(true))
                .body("charge_id", is(notNullValue()))
                .body("authorisation_mode", is("external"));

        String chargeExternalId = response.extract().path("charge_id").toString();
        String actualGatewayAccountCredentialId = databaseTestHelper.getChargeByExternalId(chargeExternalId).get("gateway_account_credential_id").toString();
        String expectedGatewayAccountCredentialId = databaseTestHelper.getGatewayAccountCredentialsForAccount(Long.parseLong(accountId)).get(0).get("id").toString();

        assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
    }
    
    @Test
    public void createTelephoneChargeForStatusOfSuccessForAllFields() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("auth_code", "666");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("telephone_number", "+447700900796");
        postBody.put("card_expiry", "02/19");
        postBody.put("card_type", "master-card");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25.000Z"))
                .body("authorised_date", is("2018-02-21T16:05:33.000Z"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("success"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.cardholder_name", is("Jane Doe"))
                .body("email", is("jane_doe@example.com"))
                .body("card_details.expiry_date", is("02/19"))
                .body("card_details.last_digits_card_number", is("1234"))
                .body("card_details.first_digits_card_number", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("charge_id.length()", is(26))
                .body("state.status", is("success"))
                .body("state.finished", is(true));

        DatabaseTestHelper testHelper = connectorApp.getDatabaseTestHelper();
        Map<String, Object> chargeDetails = testHelper.getChargeByGatewayTransactionId(providerId).get(0);
        Long chargeId = Long.parseLong(chargeDetails.get("id").toString());
        assertThat(chargeDetails.get("language"), is("en"));

        List<Map<String, Object>> chargeEvents = testHelper.getChargeEvents(chargeId);

        assertThat(chargeEvents, hasEvent(PAYMENT_NOTIFICATION_CREATED));
        assertThat(chargeEvents, hasEvent(CAPTURE_SUBMITTED));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0010() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0010",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0010"))
                .body("payment_outcome.supplemental.error_code", is("ECKOH01234"))
                .body("payment_outcome.supplemental.error_message", is("textual message describing error code"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("charge_id.length()", is(26))
                .body("state.status", is("failed"))
                .body("state.code", is("P0010"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment method rejected"));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0050() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0050",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );
        postBody.put("auth_code", "666");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("telephone_number", "+447700900796");
        postBody.put("card_expiry", null);
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0050"))
                .body("payment_outcome.supplemental.error_code", is("ECKOH01234"))
                .body("payment_outcome.supplemental.error_message", is("textual message describing error code"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("charge_id.length()", is(26))
                .body("state.status", is("failed"))
                .body("state.code", is("P0050"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment provider returned an error"));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0030() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0030",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0030"))
                .body("payment_outcome.supplemental.error_code", is("ECKOH01234"))
                .body("payment_outcome.supplemental.error_message", is("textual message describing error code"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("charge_id.length()", is(26))
                .body("state.status", is("failed"))
                .body("state.code", is("P0030"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment was cancelled by the user"));
    }

    @Test
    public void createTelephoneChargeWithTruncatedMetaData() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0030",
                        "supplemental", Map.of(
                                "error_code", stringOf51Characters,
                                "error_message", stringOf51Characters
                        )
                )
        );
        postBody.replace("processor_id", stringOf51Characters);
        postBody.put("auth_code", stringOf51Characters);
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("telephone_number", stringOf51Characters);

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25.000Z"))
                .body("authorised_date", is("2018-02-21T16:05:33.000Z"))
                .body("processor_id", is(stringOf50Characters))
                .body("provider_id", is(providerId))
                .body("auth_code", is(stringOf50Characters))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0030"))
                .body("payment_outcome.supplemental.error_code", is(stringOf50Characters))
                .body("payment_outcome.supplemental.error_message", is(stringOf50Characters))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("telephone_number", is(stringOf50Characters))
                .body("charge_id.length()", is(26))
                .body("state.status", is("failed"))
                .body("state.code", is("P0030"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment was cancelled by the user"));
        
    }

    @Test
    public void createTelephoneChargeWithSource() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome", Map.of("status", "success"));
        postBody.replace("processor_id", stringOf51Characters);

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON);

        DatabaseTestHelper testHelper = connectorApp.getDatabaseTestHelper();
        Map<String, Object> chargeDetails = testHelper.getChargeByGatewayTransactionId(providerId).get(0);

        assertThat(chargeDetails.get("source"), is(CARD_EXTERNAL_TELEPHONE.toString()));
    }

    @Test
    public void shouldReturnResponseForAlreadyExistingTelephoneCharge() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("card_expiry", "02/19");
        postBody.put("card_type", "master-card");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201);

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(200)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is(providerId))
                .body("payment_outcome.status", is("success"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is("02/19"))
                .body("card_details.last_digits_card_number", is("1234"))
                .body("card_details.first_digits_card_number", is("123456"))
                .body("charge_id.length()", is(26))
                .body("state.status", is("success"))
                .body("state.finished", is(true));

        DatabaseTestHelper testHelper = connectorApp.getDatabaseTestHelper();
        List<Map<String, Object>> chargesByGatewayTransactionId = testHelper.getChargeByGatewayTransactionId(providerId);

        assertThat(chargesByGatewayTransactionId.size(), is(1));
    }

    @Test
    public void shouldReturn400ForInvalidCardExpiryDate() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("card_expiry", "99/99");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(400)
                .body("message", hasItem(containsString("CardExpiryDate")));
    }

    @Test
    public void shouldReturn422ForInvalidCardType() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("card_type", "invalid-card");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [card_type] must be either master-card, visa, maestro, diners-club, american-express or jcb"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatus() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "invalid"
                )
        );
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatusAndCode() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "success",
                        "code", "P0010"
                )
        );
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeErrorCode() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "error",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidCreatedDate() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("created_date", "invalid");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [created_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForInvalidAuthorisedDate() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.put("authorised_date", "invalid");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [authorised_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForMissingAmount() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("amount");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [amount] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingReference() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("reference");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [reference] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingDescription() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("description");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [description] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingProcessorID() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("processor_id");
                
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [processor_id] cannot be null"));
    }
    
    @Test
    public void shouldReturn422ForMissingProviderID() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("provider_id");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [provider_id] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingPaymentOutcome() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        postBody.remove("payment_outcome");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] cannot be null"));
    }

    @Test
    public void shouldReturn422ForTelephoneChargeCreateRequestNull() {
        databaseTestHelper.allowTelephonePaymentNotifications(Long.valueOf(accountId));
        String payload = toJson(null);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("must not be null"));
    }

    @Test
    public void shouldReturn403IfTelephoneNotificationsNotAllowedForAccount() {
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(403)
                .contentType(JSON)
                .body("message[0]", is("Telephone payment notifications are not enabled for this gateway account"))
                .body("error_identifier", is("TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED"));
    }

}
