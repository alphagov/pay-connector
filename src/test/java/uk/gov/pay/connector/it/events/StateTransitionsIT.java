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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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

        assertThat(messages.size(), is(1));

        JsonObject message = new JsonParser().parse(messages.get(0).getBody()).getAsJsonObject();

        assertThat(message.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(message.get("event_type").getAsString(), is("CANCELLED_BY_EXTERNAL_SERVICE"));
    }

    @Test
    public void shouldBeAbleToRequestARefund_partialAmount() throws Exception{
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

        assertThat(messages.size(), is(3));

        messages.sort(Comparator.comparing(message -> getTimestampFromMessage(message)));

        JsonObject message1 = new JsonParser().parse(messages.get(0).getBody()).getAsJsonObject();
        assertThat(message1.get("event_type").getAsString(), is("REFUND_CREATED_BY_SERVICE"));
        assertThat(message1.get("resource_external_id").getAsString(), is(refundId));
        assertThat(message1.get("parent_resource_external_id").getAsString(), is(chargeId));
        assertThat(message1.get("event_details").getAsJsonObject().get("amount").getAsInt(), is(50));

        JsonObject message2 = new JsonParser().parse(messages.get(1).getBody()).getAsJsonObject();
        assertThat(message2.get("event_type").getAsString(), is("REFUND_SUBMITTED"));
        assertThat(message2.get("resource_external_id").getAsString(), is(refundId));

        JsonObject message3 = new JsonParser().parse(messages.get(2).getBody()).getAsJsonObject();
        assertThat(message3.get("event_type").getAsString(), is("REFUND_SUCCEEDED"));
        assertThat(message3.get("resource_external_id").getAsString(), is(refundId));
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
