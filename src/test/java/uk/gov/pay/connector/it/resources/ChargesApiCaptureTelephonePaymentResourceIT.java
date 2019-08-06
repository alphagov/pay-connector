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
import static org.hamcrest.Matchers.*;
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
public class ChargesApiCaptureTelephonePaymentResourceIT extends ChargingITestBase {
    
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_EMAIL_KEY = "email";
    private static final String JSON_PROVIDER_KEY = "payment_provider";
    private static final String JSON_LANGUAGE_KEY = "language";
    private static final String JSON_METADATA_KEY = "metadata";
    private static final String PROVIDER_NAME = "sandbox";

    private static final String JSON_REFERENCE_VALUE = "Test reference";
    private static final String JSON_DESCRIPTION_VALUE = "Test description";

    public ChargesApiCaptureTelephonePaymentResourceIT() {
        super(PROVIDER_NAME);
    }
    
    @Before
    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void makeTelephoneCharge() {
        HashMap<String, Object> postBody = new HashMap<>();
        postBody.put("amount", 12000);
        postBody.put("reference", "MRPC12345");
        postBody.put("description", "New passport application");
        postBody.put("created_date", "2018-02-21T16:04:25Z");
        postBody.put("authorised_date", "2018-02-21T16:05:33Z");
        postBody.put("processor_id", "183f2j8923j8");
        postBody.put("provider_id", "17498-8412u9-1273891239");
        postBody.put("auth_code", "666");
        postBody.put("payment_outcome", Map.of("status", "success",
                "code", "P0010",
                "supplemental", Map.of("errorCode", "ECKOH01234",
                        "errorMessage", "textual message describing error code")));
        postBody.put("card_type", "master-card");
        postBody.put("name_on_card", "Jane Doe");
        postBody.put("email_address", "jane_doe@example.com");
        postBody.put("card_expiry", "02/19");
        postBody.put("last_four_digits", "1234");
        postBody.put("first_six_digits", "654321");
        postBody.put("telephone_number", "+447700900796");
        postBody.put("paymentId", "hu20sqlact5260q2nanm0q8u93");
        postBody.put("state", Map.of("status", "success",
                "finished", true,
                "message", "created",
                "code", "P0010"));
        
        String payload = toJson(postBody);

        connectorRestApiClient
                .postCreateTelephoneCharge(payload)
                .statusCode(200)
                .contentType(JSON)
                .body("amount", isNumber(100))
                .body("reference", is("Some reference"))
                .body("reference", is("Some reference"))
                .body("description", is("Some description"))
                .body("created_date", is("2018-02-21T16:04:25Z"))
                .body("authorised_date", is("2018-02-21T16:05:33Z"))
                .body("processor_id", is("1PROC"))
                .body("auth_code", is("666"))
                .body("payment_outcome.status", is("success"))
                .body("card_type", is("visa"))
                .body("name_on_card", is("Jane Doe"))
                .body("email_address", is("jane_doe@example.com"))
                .body("card_expiry", is("01/08"))
                .body("last_four_digits", is("1234"))
                .body("first_six_digits", is("123456"))
                .body("telephone_number", is("+447700900796"))
                .body("payment_id", is("hu20sqlact5260q2nanm0q8u93"))
                .body("state.status", is("success"))
                .body("state.finished", is(true))
                .body("state.message", is("Created"))
                .body("state.code", is("P0010"));
                
    }
}
