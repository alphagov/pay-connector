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

import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
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
public class PaymentStateTransitionsIT extends ChargingITestBase {

    public PaymentStateTransitionsIT() {
        super("sandbox");
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
        assertThat(message.get("event_type").getAsString(), is("SYSTEM_CANCELLED"));
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
