package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;

public class ChargesApiResourceTelephonePaymentsIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    private static final String stringOf51Characters = StringUtils.repeat("*", 51);
    private static final String stringOf50Characters = StringUtils.repeat("*", 50);
    private static final String VALID_SERVICE_ID = "valid-service-id";
    private String gatewayAccountId;

    @BeforeEach
    void createGatewayAccount() {
        gatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", VALID_SERVICE_ID,
                        "type", GatewayAccountType.TEST,
                        "payment_provider", PaymentGatewayName.SANDBOX.getName(),
                        "service_name", "my-test-service-name"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");
    }
    
    @Nested
    class ByGatewayAccountId {

        @BeforeEach
        void allowTelephonePaymentNotifications() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", true)))
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
        }
        
        @Test
        public void createTelephoneCharge_withAllFields() {
            var payload = Map.ofEntries(
                    Map.entry("amount", 12000),
                    Map.entry("reference", "MRPC12345"),
                    Map.entry("description", "New passport application"),
                    Map.entry("processor_id", "183f2j8923j8"),
                    Map.entry("provider_id", "17498-8412u9-1273891239"),
                    Map.entry("payment_outcome", Map.of("status", "success")),
                    Map.entry("auth_code", "666"),
                    Map.entry("created_date", "2018-02-21T16:04:25Z"),
                    Map.entry("authorised_date", "2018-02-21T16:05:33Z"),
                    Map.entry("name_on_card", "Jane Doe"),
                    Map.entry("email_address", "jane_doe@example.com"),
                    Map.entry("telephone_number", "+447700900796"),
                    Map.entry("card_expiry", "02/19"),
                    Map.entry("card_type", "master-card"),
                    Map.entry("last_four_digits", "1234"),
                    Map.entry("first_six_digits", "123456")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", is("2018-02-21T16:04:25.000Z"))
                    .body("authorised_date", is("2018-02-21T16:05:33.000Z"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
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
                    .body("state.finished", is(true))
                    .extract().path("charge_id").toString();

            Map<String, Object> chargeDetails = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);
            assertThat(chargeDetails.get("source"), is(CARD_EXTERNAL_TELEPHONE.toString()));

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details.card_type", is(nullValue()))
                    .body("card_details.cardholder_name", is("Jane Doe"))
                    .body("card_details.expiry_date", is("02/19"))
                    .body("card_details.last_digits_card_number", is("1234"))
                    .body("card_details.first_digits_card_number", is("123456"))
                    .body("email", is("jane_doe@example.com"))
                    .body("state.status", is("success"))
                    .body("state.finished", is(true))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("metadata.telephone_number", is("+447700900796"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.created_date", is("2018-02-21T16:04:25Z"))
                    .body("metadata.authorised_date", is("2018-02-21T16:05:33Z"))
                    .body("metadata.status", is("success"))
                    .body("metadata.auth_code", is("666"));

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("events", hasSize(2))
                    .body("events[0].state.status", is("created"))
                    .body("events[1].state.status", is("success"));

        }

        @Test
        public void createTelephoneCharge_withOnlyRequiredFields() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("state.status", is("success"))
                    .body("state.finished", is(true))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.status", is("success"));

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("events", hasSize(2))
                    .body("events[0].state.status", is("created"))
                    .body("events[1].state.status", is("success"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0010() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0010",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            )
                    ));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("failed"))
                    .body("state.code", is("P0010"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment method rejected"))
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0010"))
                    .body("metadata.status", is("failed"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0050() {
            var payload = Map.ofEntries(
                    Map.entry("amount", 12000),
                    Map.entry("reference", "MRPC12345"),
                    Map.entry("description", "New passport application"),
                    Map.entry("processor_id", "183f2j8923j8"),
                    Map.entry("provider_id", "17498-8412u9-1273891239"),
                    Map.entry("payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0050",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            ))),
                    Map.entry("auth_code", "666"),
                    Map.entry("created_date", "2018-02-21T16:04:25Z"),
                    Map.entry("authorised_date", "2018-02-21T16:05:33Z"),
                    Map.entry("name_on_card", "Jane Doe"),
                    Map.entry("email_address", "jane_doe@example.com"),
                    Map.entry("telephone_number", "+447700900796"));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details.card_type", is(nullValue()))
                    .body("card_details.cardholder_name", is("Jane Doe"))
                    .body("email", is("jane_doe@example.com"))
                    .body("card_details.expiry_date", is(nullValue()))
                    .body("card_details.last_digits_card_number", is(nullValue()))
                    .body("card_details.first_digits_card_number", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("error")) // P0050 maps to external state "error", not "failed" 
                    .body("state.code", is("P0050"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment provider returned an error"))
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0050"))
                    .body("metadata.status", is("failed"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0030() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0030",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            )
                    ));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("error"))
                    .body("state.code", is("P0050")) // charges with payment code P0030 are saved as AUTHORISATION_ERROR which maps to EXTERNAL_ERROR_GATEWAY P0050 in the response.
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment provider returned an error")) // this is as a result of the above
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0030"))
                    .body("metadata.status", is("failed"));
        }

        @Test
        public void createTelephoneChargeWithTruncatedMetaData() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", stringOf51Characters,
                    "auth_code", stringOf51Characters,
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0010",
                            "supplemental", Map.of(
                                    "error_code", stringOf51Characters,
                                    "error_message", stringOf51Characters
                            )),
                    "telephone_number", stringOf51Characters);

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("metadata.telephone_number", is(stringOf50Characters))
                    .body("metadata.processor_id", is(stringOf50Characters))
                    .body("metadata.auth_code", is(stringOf50Characters))
                    .body("metadata.status", is("failed"))
                    .body("metadata.code", is("P0010"))
                    .body("metadata.error_code", is(stringOf50Characters))
                    .body("metadata.error_message", is(stringOf50Characters));
        }

        @Test
        public void shouldReturn200ResponseForAlreadyExistingTelephoneCharge() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
                    .body("payment_outcome.status", is("success"))
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("charge_id", is(chargeExternalId))
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
                    .body("payment_outcome.status", is("success"));

            List<Map<String, Object>> chargesByGatewayTransactionId = app.getDatabaseTestHelper().getChargeByGatewayTransactionId("17498-8412u9-1273891239");
            assertThat(chargesByGatewayTransactionId.size(), is(1));
        }

        @Test
        public void shouldReturn403IfTelephoneNotificationsNotAllowedForAccount() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", false)))
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());

            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/telephone-charges", gatewayAccountId))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body("message[0]", is("Telephone payment notifications are not enabled for this gateway account"))
                    .body("error_identifier", is("TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED"));
        }
    }

    @Nested
    class ByServiceIdAndAccountType {

        @BeforeEach
        void allowTelephonePaymentNotifications() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", true)))
                    .patch(format("/v1/api/service/%s/account/%s", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode());
        }

        @Test
        public void createTelephoneCharge_withAllFields() {
            var payload = Map.ofEntries(
                    Map.entry("amount", 12000),
                    Map.entry("reference", "MRPC12345"),
                    Map.entry("description", "New passport application"),
                    Map.entry("processor_id", "183f2j8923j8"),
                    Map.entry("provider_id", "17498-8412u9-1273891239"),
                    Map.entry("payment_outcome", Map.of("status", "success")),
                    Map.entry("auth_code", "666"),
                    Map.entry("created_date", "2018-02-21T16:04:25Z"),
                    Map.entry("authorised_date", "2018-02-21T16:05:33Z"),
                    Map.entry("name_on_card", "Jane Doe"),
                    Map.entry("email_address", "jane_doe@example.com"),
                    Map.entry("telephone_number", "+447700900796"),
                    Map.entry("card_expiry", "02/19"),
                    Map.entry("card_type", "master-card"),
                    Map.entry("last_four_digits", "1234"),
                    Map.entry("first_six_digits", "123456")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", is("2018-02-21T16:04:25.000Z"))
                    .body("authorised_date", is("2018-02-21T16:05:33.000Z"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
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
                    .body("state.finished", is(true))
                    .extract().path("charge_id").toString();

            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId).get("gateway_account_credential_id").toString();
            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(gatewayAccountId)).getFirst().get("id").toString();
            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));

            Map<String, Object> chargeDetails = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);
            assertThat(chargeDetails.get("source"), is(CARD_EXTERNAL_TELEPHONE.toString()));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details.card_type", is(nullValue()))
                    .body("card_details.cardholder_name", is("Jane Doe"))
                    .body("card_details.expiry_date", is("02/19"))
                    .body("card_details.last_digits_card_number", is("1234"))
                    .body("card_details.first_digits_card_number", is("123456"))
                    .body("email", is("jane_doe@example.com"))
                    .body("state.status", is("success"))
                    .body("state.finished", is(true))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("metadata.telephone_number", is("+447700900796"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.created_date", is("2018-02-21T16:04:25Z"))
                    .body("metadata.authorised_date", is("2018-02-21T16:05:33Z"))
                    .body("metadata.status", is("success"))
                    .body("metadata.auth_code", is("666"));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("events", hasSize(2))
                    .body("events[0].state.status", is("created"))
                    .body("events[1].state.status", is("success"));

        }

        @Test
        public void createTelephoneCharge_withOnlyRequiredFields() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("state.status", is("success"))
                    .body("state.finished", is(true))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.status", is("success"));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("events", hasSize(2))
                    .body("events[0].state.status", is("created"))
                    .body("events[1].state.status", is("success"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0010() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0010",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            )
                    ));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("failed"))
                    .body("state.code", is("P0010"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment method rejected"))
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0010"))
                    .body("metadata.status", is("failed"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0050() {
            var payload = Map.ofEntries(
                    Map.entry("amount", 12000),
                    Map.entry("reference", "MRPC12345"),
                    Map.entry("description", "New passport application"),
                    Map.entry("processor_id", "183f2j8923j8"),
                    Map.entry("provider_id", "17498-8412u9-1273891239"),
                    Map.entry("payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0050",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            ))),
                    Map.entry("auth_code", "666"),
                    Map.entry("created_date", "2018-02-21T16:04:25Z"),
                    Map.entry("authorised_date", "2018-02-21T16:05:33Z"),
                    Map.entry("name_on_card", "Jane Doe"),
                    Map.entry("email_address", "jane_doe@example.com"),
                    Map.entry("telephone_number", "+447700900796"));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details.card_type", is(nullValue()))
                    .body("card_details.cardholder_name", is("Jane Doe"))
                    .body("email", is("jane_doe@example.com"))
                    .body("card_details.expiry_date", is(nullValue()))
                    .body("card_details.last_digits_card_number", is(nullValue()))
                    .body("card_details.first_digits_card_number", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("error")) // P0050 maps to external state "error", not "failed" 
                    .body("state.code", is("P0050"))
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment provider returned an error"))
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0050"))
                    .body("metadata.status", is("failed"));

        }

        @Test
        public void createTelephoneChargeForFailedStatusP0030() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0030",
                            "supplemental", Map.of(
                                    "error_code", "ECKOH01234",
                                    "error_message", "textual message describing error code"
                            )
                    ));

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("created_date", isWithin(10, ChronoUnit.SECONDS))
                    .body("authorisation_mode", is("external"))
                    .body("card_details", is(nullValue()))
                    .body("email", is(nullValue()))
                    .body("gateway_transaction_id", is("17498-8412u9-1273891239"))
                    .body("state.status", is("error"))
                    .body("state.code", is("P0050")) // charges with payment code P0030 are saved as AUTHORISATION_ERROR which maps to EXTERNAL_ERROR_GATEWAY P0050 in the response.
                    .body("state.finished", is(true))
                    .body("state.message", is("Payment provider returned an error")) // this is as a result of the above
                    .body("metadata.error_message", is("textual message describing error code"))
                    .body("metadata.processor_id", is("183f2j8923j8"))
                    .body("metadata.error_code", is("ECKOH01234"))
                    .body("metadata.code", is("P0030"))
                    .body("metadata.status", is("failed"));
        }

        @Test
        public void createTelephoneChargeWithTruncatedMetaData() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", stringOf51Characters,
                    "auth_code", stringOf51Characters,
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of(
                            "status", "failed",
                            "code", "P0010",
                            "supplemental", Map.of(
                                    "error_code", stringOf51Characters,
                                    "error_message", stringOf51Characters
                            )),
                    "telephone_number", stringOf51Characters);

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("metadata.telephone_number", is(stringOf50Characters))
                    .body("metadata.processor_id", is(stringOf50Characters))
                    .body("metadata.auth_code", is(stringOf50Characters))
                    .body("metadata.status", is("failed"))
                    .body("metadata.code", is("P0010"))
                    .body("metadata.error_code", is(stringOf50Characters))
                    .body("metadata.error_message", is(stringOf50Characters));
        }

        @Test
        public void shouldReturn200ResponseForAlreadyExistingTelephoneCharge() {
            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            String chargeExternalId = app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
                    .body("payment_outcome.status", is("success"))
                    .extract().path("charge_id").toString();

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .body("charge_id", is(chargeExternalId))
                    .body("amount", isNumber(12000))
                    .body("reference", is("MRPC12345"))
                    .body("description", is("New passport application"))
                    .body("processor_id", is("183f2j8923j8"))
                    .body("provider_id", is("17498-8412u9-1273891239"))
                    .body("payment_outcome.status", is("success"));

            List<Map<String, Object>> chargesByGatewayTransactionId = app.getDatabaseTestHelper().getChargeByGatewayTransactionId("17498-8412u9-1273891239");
            assertThat(chargesByGatewayTransactionId.size(), is(1));
        }

        @Test
        public void shouldReturn403IfTelephoneNotificationsNotAllowedForAccount() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", false)))
                    .patch(format("/v1/api/service/%s/account/%s", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode());

            var payload = Map.of("amount", 12000,
                    "reference", "MRPC12345",
                    "description", "New passport application",
                    "processor_id", "183f2j8923j8",
                    "provider_id", "17498-8412u9-1273891239",
                    "payment_outcome", Map.of("status", "success")
            );

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/telephone-charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body("message[0]", is("Telephone payment notifications are not enabled for this gateway account"))
                    .body("error_identifier", is("TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED"));
        }
    }
}
