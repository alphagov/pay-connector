package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
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

public class ChargesApiResourceTelephonePaymentsIT {

    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension("sandbox");
    private static final HashMap<String, Object> postBody = new HashMap<>();
    private static final String stringOf51Characters = StringUtils.repeat("*", 51);
    private static final String stringOf50Characters = StringUtils.repeat("*", 50);

    private final String providerId = "17498-8412u9-1273891239";

    @BeforeEach
    public void setUpPostBody() {
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
    
    @AfterEach
    public void tearDown() {
        postBody.clear();
    }

    @Test
    public void createTelephoneChargeForOnlyRequiredFields() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.parseLong(app.getAccountId()));
        ValidatableResponse response = app.getConnectorRestApiClient()
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
        String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId).get("gateway_account_credential_id").toString();
        String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(app.getAccountId())).get(0).get("id").toString();

        assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
    }
    
    @Test
    public void createTelephoneChargeForStatusOfSuccessForAllFields() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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
        
        app.getConnectorRestApiClient()
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

        DatabaseTestHelper testHelper = app.getDatabaseTestHelper();
        Map<String, Object> chargeDetails = testHelper.getChargeByGatewayTransactionId(providerId).get(0);
        Long chargeId = Long.parseLong(chargeDetails.get("id").toString());
        assertThat(chargeDetails.get("language"), is("en"));

        List<Map<String, Object>> chargeEvents = testHelper.getChargeEvents(chargeId);

        assertThat(chargeEvents, app.hasEvent(PAYMENT_NOTIFICATION_CREATED));
        assertThat(chargeEvents, app.hasEvent(CAPTURE_SUBMITTED));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0010() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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

        app.getConnectorRestApiClient()
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
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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
        
        app.getConnectorRestApiClient()
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
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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

        app.getConnectorRestApiClient()
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
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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

        app.getConnectorRestApiClient()
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
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.replace("payment_outcome", Map.of("status", "success"));
        postBody.replace("processor_id", stringOf51Characters);

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON);

        DatabaseTestHelper testHelper = app.getDatabaseTestHelper();
        Map<String, Object> chargeDetails = testHelper.getChargeByGatewayTransactionId(providerId).get(0);

        assertThat(chargeDetails.get("source"), is(CARD_EXTERNAL_TELEPHONE.toString()));
    }

    @Test
    public void shouldReturnResponseForAlreadyExistingTelephoneCharge() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.put("card_expiry", "02/19");
        postBody.put("card_type", "master-card");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201);

        app.getConnectorRestApiClient()
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

        DatabaseTestHelper testHelper = app.getDatabaseTestHelper();
        List<Map<String, Object>> chargesByGatewayTransactionId = testHelper.getChargeByGatewayTransactionId(providerId);

        assertThat(chargesByGatewayTransactionId.size(), is(1));
    }

    @Test
    public void shouldReturn400ForInvalidCardExpiryDate() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.put("card_expiry", "99/99");

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(400)
                .body("message", hasItem(containsString("CardExpiryDate")));
    }

    @Test
    public void shouldReturn422ForInvalidCardType() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.put("card_type", "invalid-card");
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [card_type] must be either master-card, visa, maestro, diners-club, american-express or jcb"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatus() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "invalid"
                )
        );
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatusAndCode() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.replace("payment_outcome",
                Map.of(
                        "status", "success",
                        "code", "P0010"
                )
        );
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeErrorCode() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
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
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void shouldReturn422ForInvalidCreatedDate() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.put("created_date", "invalid");

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [created_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForInvalidAuthorisedDate() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.put("authorised_date", "invalid");

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [authorised_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForMissingAmount() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("amount");
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [amount] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingReference() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("reference");
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [reference] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingDescription() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("description");
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [description] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingProcessorID() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("processor_id");
                
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [processor_id] cannot be null"));
    }
    
    @Test
    public void shouldReturn422ForMissingProviderID() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("provider_id");
        
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [provider_id] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingPaymentOutcome() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        postBody.remove("payment_outcome");

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] cannot be null"));
    }

    @Test
    public void shouldReturn422ForTelephoneChargeCreateRequestNull() {
        app.getDatabaseTestHelper().allowTelephonePaymentNotifications(Long.valueOf(app.getAccountId()));
        String payload = toJson(null);

        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(payload)
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("must not be null"));
    }

    @Test
    public void shouldReturn403IfTelephoneNotificationsNotAllowedForAccount() {
        app.getConnectorRestApiClient()
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(403)
                .contentType(JSON)
                .body("message[0]", is("Telephone payment notifications are not enabled for this gateway account"))
                .body("error_identifier", is("TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED"));
    }

}
