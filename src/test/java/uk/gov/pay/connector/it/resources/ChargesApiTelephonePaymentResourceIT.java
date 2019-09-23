package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml"
)
public class ChargesApiTelephonePaymentResourceIT extends ChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";
    private static final HashMap<String, Object> postBody = new HashMap<>();
    private static final String stringOf51Characters = StringUtils.repeat("*", 51);
    private static final String stringOf50Characters = StringUtils.repeat("*", 50);

    public ChargesApiTelephonePaymentResourceIT() {
        super(PROVIDER_NAME);
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
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
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(201)
                .contentType(JSON)
                .body("amount", isNumber(12000))
                .body("reference", is("MRPC12345"))
                .body("description", is("New passport application"))
                .body("processor_id", is("183f2j8923j8"))
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is(nullValue()))
                .body("card_details.last_digits_card_number", is(nullValue()))
                .body("card_details.first_digits_card_number", is(nullValue()))
                .body("payment_outcome.status", is("success"))
                .body("charge_id.length()", is(26))
                .body("state.status", is("success"))
                .body("state.finished", is(true));
    }
    
    @Test
    public void createTelephoneChargeForStatusOfSuccessForAllFields() {
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
                .body("state.finished", is(true));
    }

    @Test
    public void createTelephoneChargeForFailedStatusP0010() {
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
                .body("provider_id", is("17498-8412u9-1273891239"))
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
                .body("provider_id", is("17498-8412u9-1273891239"))
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
                .body("provider_id", is("17498-8412u9-1273891239"))
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
                .body("provider_id", is("17498-8412u9-1273891239"))
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
    public void shouldReturnResponseForAlreadyExistingTelephoneCharge() {
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
                .body("provider_id", is("17498-8412u9-1273891239"))
                .body("payment_outcome.status", is("success"))
                .body("card_details.card_type", is(nullValue()))
                .body("card_details.expiry_date", is("02/19"))
                .body("card_details.last_digits_card_number", is("1234"))
                .body("card_details.first_digits_card_number", is("123456"))
                .body("charge_id.length()", is(26))
                .body("state.status", is("success"))
                .body("state.finished", is(true));
    }

    @Test
    public void shouldReturn422ForInvalidCardType() {
        
        postBody.put("card_type", "invalid-card");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [card_type] must be either master-card, visa, maestro, diners-club or american-express"));
    }

    @Test
    public void shouldReturn422ForInvalidCardExpiryDate() {
        postBody.put("card_expiry", "99/99");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [card_expiry] must have valid MM/YY"));
    }

    @Test
    public void shouldReturn422ForInvalidPaymentOutcomeStatus() {
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
        postBody.put("created_date", "invalid");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [created_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForInvalidAuthorisedDate() {
        postBody.put("authorised_date", "invalid");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("Field [authorised_date] must be a valid ISO-8601 time and date format"));
    }

    @Test
    public void shouldReturn422ForMissingAmount() {
        postBody.remove("amount");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [amount] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingReference() {
        postBody.remove("reference");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [reference] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingDescription() {
        postBody.remove("description");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [description] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingProcessorID() {
        postBody.remove("processor_id");
                
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [processor_id] cannot be null"));
    }
    
    @Test
    public void shouldReturn422ForMissingProviderID() {
        postBody.remove("provider_id");
        
        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [provider_id] cannot be null"));
    }

    @Test
    public void shouldReturn422ForMissingPaymentOutcome() {
        postBody.remove("payment_outcome");

        connectorRestApiClient
                .postCreateTelephoneCharge(toJson(postBody))
                .statusCode(422)
                .body("message[0]", is("Field [payment_outcome] cannot be null"));
    }

    @Test
    public void shouldReturn422ForTelephoneChargeCreateRequestNull() {
        String payload = toJson(null);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(422)
                .contentType(JSON)
                .body("message[0]", is("may not be null"));
    }
}
