package uk.gov.pay.connector.pact.event;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit.MessagePactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.rules.AppWithPostgresAndSqsRule;
import uk.gov.pay.connector.rules.SqsTestDocker;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class ServiceArchivedEventIT {

    static ObjectMapper objectMapper = new ObjectMapper();
    @Rule
    public MessagePactProviderRule mockProvider = new MessagePactProviderRule(this);
    @Rule
    public AppWithPostgresAndSqsRule app = new AppWithPostgresAndSqsRule(
            config("taskQueue.taskQueueEnabled", "true")
    );


    private byte[] currentMessage;

    private String serviceExternalId = "service-external-id";

    @Pact(provider = "adminusers", consumer = "connector")
    public MessagePact createServiceArchivedEventPact(MessagePactBuilder builder) throws Exception {
        ServiceArchivedTaskData taskData = new ServiceArchivedTaskData(serviceExternalId);
        Task event = new Task(objectMapper.writeValueAsString(taskData), TaskType.SERVICE_ARCHIVED);

        return builder
                .expectsToReceive("a service archived event")
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(getAsPact(event))
                .toPact();
    }

    private DslPart getAsPact(Task event) {
        PactDslJsonBody eventDetails = new PactDslJsonBody();
        eventDetails.stringType("task", event.getTaskType().getName());
        eventDetails.stringType("data", event.getData());
        return eventDetails;
    }

    @Test
    @PactVerification({"adminusers"})
    public void test() throws Exception {
        long gatewayAccountId = secureRandomLong();
        String externalId = randomUuid();
        Map<String, Object> credMap = Map.of("some_payment_provider_account_id", String.valueOf(gatewayAccountId));
        app.getDatabaseTestHelper().addGatewayAccount(
                anAddGatewayAccountParams()
                        .withAccountId(String.valueOf(gatewayAccountId))
                        .withPaymentGateway("sandbox")
                        .withServiceName("service name")
                        .withCredentials(credMap)
                        .withDisabled(false)
                        .withExternalId(externalId)
                        .withServiceId(serviceExternalId)
                        .build()
        );

        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(SqsTestDocker.getQueueUrl("tasks-queue"))
                .messageBody(new String(currentMessage))
                .build();

        app.getSqsClient().sendMessage(sendMessageRequest);

        await().atMost(2, TimeUnit.SECONDS).until(() ->
                Boolean.valueOf(app.getDatabaseTestHelper().getGatewayAccount(gatewayAccountId).get("disabled").toString())
        );

        var updatedGatewayAccount = app.getDatabaseTestHelper().getGatewayAccount(gatewayAccountId);
        assertThat(updatedGatewayAccount.get("disabled").toString(), is("true"));
        var credentialsMap = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(gatewayAccountId).getFirst();
        assertThat(GatewayAccountCredentialState.valueOf(credentialsMap.get("state").toString()), is(GatewayAccountCredentialState.RETIRED));
    }

    public void setMessage(byte[] messageContents) {
        currentMessage = messageContents;
    }
}
