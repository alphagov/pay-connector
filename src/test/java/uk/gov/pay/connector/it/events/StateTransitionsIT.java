package uk.gov.pay.connector.it.events;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;

public class StateTransitionsIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            config("captureProcessConfig.backgroundProcessingEnabled", "true"),
            config("eventQueue.eventQueueEnabled", "true")
    );
    @RegisterExtension
    static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    @BeforeEach
    void setUp() {
        app.purgeEventQueue();
    }

    @Test
    void shouldPutPaymentStateTransitionMessageOntoQueueGivenAuthCancel() throws InterruptedException {
        String chargeId = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(AUTHORISATION_SUCCESS)
                        .withCreatedDate(Instant.now().minus(1, HOURS))
                        .withTransactionId("transaction-id-transition-it"));

        testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        Thread.sleep(200);

        List<Message> messages = readMessagesFromEventQueue();

        assertThat(messages.size(), is(2));

        JsonObject cancelledMessage = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("CANCELLED_BY_EXTERNAL_SERVICE"))
                .findFirst().get();

        assertThat(cancelledMessage.get("resource_external_id").getAsString(), is(chargeId));
        assertThat(cancelledMessage.get("event_type").getAsString(), is("CANCELLED_BY_EXTERNAL_SERVICE"));
        assertThat(cancelledMessage.get("service_id").getAsString(), is("external-service-id"));
        assertThat(cancelledMessage.get("live").getAsBoolean(), is(false));

        Optional<JsonObject> refundMessage = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("REFUND_AVAILABILITY_UPDATED"))
                .findFirst();
        assertThat(refundMessage.isPresent(), is(true));

    }

    @Test
    void shouldEmitCorrectRefundEvents() throws Exception{
        String chargeId = testBaseExtension.addCharge(
                anAddChargeParameters().withChargeStatus(CAPTURED)
                        .withCreatedDate(Instant.now().minus(1, HOURS))
                        .withTransactionId("transaction-id-transition-it"));
        Long refundAmount = 50L;
        Long refundAmountAvailable = AMOUNT;
        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", refundAmount, "refund_amount_available", refundAmountAvailable);
        String refundPayload = new Gson().toJson(refundData);

        ValidatableResponse response = app.givenSetup()
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", testBaseExtension.getAccountId())
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
        assertThat(message1.get("service_id").getAsString(), is("external-service-id"));
        assertThat(message1.get("live").getAsBoolean(), is(false));
        assertThat(message1.get("parent_resource_external_id").getAsString(), is(chargeId));
        assertThat(message1.get("event_details").getAsJsonObject().get("amount").getAsInt(), is(50));

        JsonObject message2 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_AVAILABILITY_UPDATED".equals(m.get("event_type").getAsString()))
                .findFirst().get();
        assertThat(message2.get("event_type").getAsString(), is("REFUND_AVAILABILITY_UPDATED"));
        assertThat(message2.get("service_id").getAsString(), is("external-service-id"));
        assertThat(message2.get("live").getAsBoolean(), is(false));
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
        assertThat(message3.get("service_id").getAsString(), is("external-service-id"));
        assertThat(message3.get("live").getAsBoolean(), is(false));

        JsonObject message4 = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(m -> "REFUND_SUCCEEDED".equals(m.get("event_type").getAsString()))
                .findFirst().get();

        assertThat(message4.get("event_type").getAsString(), is("REFUND_SUCCEEDED"));
        assertThat(message4.get("resource_external_id").getAsString(), is(refundId));
        assertThat(message4.get("event_details").getAsJsonObject().get("gateway_transaction_id").getAsString(), is(notNullValue()));
        assertThat(message4.get("service_id").getAsString(), is("external-service-id"));
        assertThat(message4.get("live").getAsBoolean(), is(false));
    }

    private List<Message> readMessagesFromEventQueue() {
        AmazonSQS sqsClient = app.getInstanceFromGuiceContainer(AmazonSQS.class);

        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(app.getEventQueueUrl());
        receiveMessageRequest
                .withMessageAttributeNames()
                .withWaitTimeSeconds(1)
                .withMaxNumberOfMessages(10);

        ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

        return receiveMessageResult.getMessages();
    }
}
