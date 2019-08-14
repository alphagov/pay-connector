package uk.gov.pay.connector.it.events;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true,
        configOverrides = {
                @ConfigOverride(key = "captureProcessConfig.backgroundProcessingEnabled", value = "true"),
                @ConfigOverride(key = "eventQueue.eventQueueEnabled", value = "true")
        }
)
public class StateTransitionsIT extends ChargingITestBase {

    public StateTransitionsIT() {
        super("sandbox");
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        purgeEventQueue();
    }

    @Test
    public void shouldPutPaymentStateTransitionMessageOntoQueueGivenAuthCancel() throws InterruptedException {
        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "transaction-id-transition-it");

        cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        Thread.sleep(200);

        List<Message> messages = readMessagesFromEventQueue();

        assertThat(messages.size(), is(2));

        JsonObject cancelledMessage = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("CANCELLED_BY_EXTERNAL_SERVICE"))
                .findFirst().get();

        assertThat(cancelledMessage.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(cancelledMessage.get("event_type").getAsString(), is("CANCELLED_BY_EXTERNAL_SERVICE"));

        Optional<JsonObject> refundMessage = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("REFUND_AVAILABILITY_UPDATED"))
                .findFirst();
        assertThat(refundMessage.isPresent(), is(true));

    }

    @Test
    public void shouldEmitCorrectRefundEvents() throws Exception{
        String chargeId = addCharge(CAPTURED, "ref", ZonedDateTime.now().minusHours(1), "transaction-id-transition-it");
        Long refundAmount = 50L;
        Long refundAmountAvailable = AMOUNT;
        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", refundAmount, "refund_amount_available", refundAmountAvailable);
        String refundPayload = new Gson().toJson(refundData);

        ValidatableResponse response = givenSetup()
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", chargeId))
                .then()
                .statusCode(202);
        Thread.sleep(500L);

        String refundId = response.extract().response().jsonPath().get("refund_id");
        
        List<Message> messages = readMessagesFromEventQueue();

        assertThat(messages.size(), is(4));

        JsonObject message1 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_CREATED_BY_SERVICE".equals(m.get("event_type").getAsString()))
                .findFirst().get();
        assertThat(message1.get("event_type").getAsString(), is("REFUND_CREATED_BY_SERVICE"));
        assertThat(message1.get("resource_external_id").getAsString(), is(refundId));
        assertThat(message1.get("parent_resource_external_id").getAsString(), is(chargeId));
        assertThat(message1.get("event_details").getAsJsonObject().get("amount").getAsInt(), is(50));

        JsonObject message2 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_AVAILABILITY_UPDATED".equals(m.get("event_type").getAsString()))
                .findFirst().get();
        assertThat(message2.get("event_type").getAsString(), is("REFUND_AVAILABILITY_UPDATED"));
        assertThat(message2.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(message2.get("event_details").getAsJsonObject().get("refund_amount_available").getAsInt(), is(6184));
        assertThat(message2.get("event_details").getAsJsonObject().get("refund_amount_refunded").getAsInt(), is(50));
        assertThat(message2.get("event_details").getAsJsonObject().get("refund_status").getAsString(), is("available"));

        JsonObject message3 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_SUBMITTED".equals(m.get("event_type").getAsString()))
                .findFirst().get();
        assertThat(message3.get("event_type").getAsString(), is("REFUND_SUBMITTED"));
        assertThat(message3.get("resource_external_id").getAsString(), is(refundId));

        JsonObject message4 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_SUCCEEDED".equals(m.get("event_type").getAsString()))
                .findFirst().get();

        assertThat(message4.get("event_type").getAsString(), is("REFUND_SUCCEEDED"));
        assertThat(message4.get("resource_external_id").getAsString(), is(refundId));
        assertThat(message4.get("event_details").getAsJsonObject().get("reference").getAsString(), is(notNullValue()));
    }

    private ZonedDateTime getTimestampFromMessage(Message message) {
        return ZonedDateTime.parse(new JsonParser().parse(message.getBody()).getAsJsonObject().get("timestamp").getAsString());
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
}
