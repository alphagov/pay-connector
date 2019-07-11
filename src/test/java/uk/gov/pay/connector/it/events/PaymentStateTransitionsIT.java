package uk.gov.pay.connector.it.events;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
public class PaymentStateTransitionsIT extends ChargingITestBase {

    public PaymentStateTransitionsIT() {
        super("sandbox");
    }

    @Test
    public void shouldPutPaymentStateTransitionMessageOntoQueueGivenAuthSuccess() throws Exception {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        Thread.sleep(400);

        List<Message> messages = readMessagesFromEventQueue();
        assertThat(messages.size(), is(2));

        JsonObject approvedMessage = new JsonParser().parse(messages.get(0).getBody()).getAsJsonObject();
        JsonObject submittedMessage = new JsonParser().parse(messages.get(1).getBody()).getAsJsonObject();

        assertThat(approvedMessage.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(approvedMessage.get("event_type").getAsString(), is("USER_APPROVED_FOR_CAPTURE"));

        assertThat(submittedMessage.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(submittedMessage.get("event_type").getAsString(), is("CAPTURE_SUBMITTED"));

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
}
