package uk.gov.pay.connector.it.resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
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
public class ChargesApiCaptureTelephonePaymentResourceIT extends ChargingITestBase {
    
    private static final String PROVIDER_NAME = "sandbox";
    
    public ChargesApiCaptureTelephonePaymentResourceIT() {
        super(PROVIDER_NAME);
    }
    
    @Before
    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void createTelephoneChargeForStatusOfSuccess() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome", 
                Map.of(
                        "status", "success"
                )
        );
        postBody.put("card_type", "master-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");
        
        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25Z"))
                .body("authorised_date", is("2018-02-21T16:05:33Z"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("success"))
                .body("card_type", is("master-card"))
                .body("name_on_card", is("Jane Doe"))
                .body("email_address", is("jane_doe@example.com"))
                .body("card_expiry", is("02/19"))
                .body("last_four_digits", is("1234"))
                .body("first_six_digits", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("payment_id", is("dummypaymentid123notpersisted"))
                .body("state.status", is("success"))
                .body("state.finished", is(true))
                .body("state.message", is("created"));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0010() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0010",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );
        postBody.put("card_type", "master-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25Z"))
                .body("authorised_date", is("2018-02-21T16:05:33Z"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0010"))
                .body("payment_outcome.supplemental.error_code", is("ECKOH01234"))
                .body("payment_outcome.supplemental.error_message", is("textual message describing error code"))
                .body("card_type", is("master-card"))
                .body("name_on_card", is("Jane Doe"))
                .body("email_address", is("jane_doe@example.com"))
                .body("card_expiry", is("02/19"))
                .body("last_four_digits", is("1234"))
                .body("first_six_digits", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("payment_id", is("dummypaymentid123notpersisted"))
                .body("state.status", is("failed"))
                .body("state.code", is("P0010"))
                .body("state.finished", is(true))
                .body("state.message", is("created"));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0050() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "P0050",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );
        postBody.put("card_type", "master-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25Z"))
                .body("authorised_date", is("2018-02-21T16:05:33Z"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("failed"))
                .body("payment_outcome.code", is("P0050"))
                .body("payment_outcome.supplemental.error_code", is("ECKOH01234"))
                .body("payment_outcome.supplemental.error_message", is("textual message describing error code"))
                .body("card_type", is("master-card"))
                .body("name_on_card", is("Jane Doe"))
                .body("email_address", is("jane_doe@example.com"))
                .body("card_expiry", is("02/19"))
                .body("last_four_digits", is("1234"))
                .body("first_six_digits", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("payment_id", is("dummypaymentid123notpersisted"))
                .body("state.status", is("failed"))
                .body("state.code", is("P0050"))
                .body("state.finished", is(true))
                .body("state.message", is("created"));
    }

    @Test
    public void shouldReturnResponseForAlreadyExistingTelephoneCharge() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                )
        );
        postBody.put("card_type", "master-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);
        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(201);
        
        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(200)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("created_date", is("2018-02-21T16:04:25Z"))
                .body("authorised_date", is("2018-02-21T16:05:33Z"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("success"))
                .body("card_type", is("master-card"))
                .body("name_on_card", is("Jane Doe"))
                .body("email_address", is("jane_doe@example.com"))
                .body("card_expiry", is("02/19"))
                .body("last_four_digits", is("1234"))
                .body("first_six_digits", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("payment_id", is("dummypaymentid123notpersisted"))
                .body("state.status", is("success"))
                .body("state.finished", is(true))
                .body("state.message", is("created"));
    }

    @Test
    public void shouldReturn422ForInvalidCardType() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                        )
                );
        postBody.put("card_type", "invalid-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForInvalidCardExpiryDate() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "99/99");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForInvalidCardLastFourDigits() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "12345");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForInvalidCardFirstSixDigits() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success"
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "1234567");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatus() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "invalid"
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeErrorCode() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "failed",
                        "code", "error",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }

    @Test
    public void shouldReturn422ForPaymentOutcomeErrorCodeWithSuccess() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome",
                Map.of(
                        "status", "success",
                        "code", "PP0010"
                )
        );
        postBody.put("card_type", "visa");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "123456");
        postBody.put("telephone_number", "+447700900796");

        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422);
    }
    
}
