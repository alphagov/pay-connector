package uk.gov.pay.connector.pact.event;

import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.tasks.TaskQueueMessageHandler;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;

import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "adminusers", providerType = ProviderType.ASYNCH)
public class ServiceArchivedEventIT {
    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension("worldpay",
            config("taskQueue.taskQueueEnabled", "true"));

    static ObjectMapper objectMapper = new ObjectMapper();

    private final String serviceExternalId = "service-external-id";

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

    @BeforeEach
    void setUp() {
        app.purgeEventQueue();
    }

    @Test
    @PactTestFor(pactMethod = "createServiceArchivedEventPact")
    public void test(List<Message> messages) throws Exception {
        System.out.println("Message received -> " + messages.get(0).contentsAsString());
        
        var gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setDisabled(false);
        gatewayAccount.setExternalId(randomUuid());
        gatewayAccount.setServiceId(serviceExternalId);
        var gatewayAccountCredentials = new GatewayAccountCredentialsEntity(gatewayAccount, WORLDPAY.getName(), Map.of(), ACTIVE);
        gatewayAccountCredentials.setExternalId(randomUuid());
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentials));
        gatewayAccountDao.persist(gatewayAccount);
        
        app.getSqsClient().sendMessage(app.getTasksQueueUrl(), messages.get(0).contentsAsString());
        app.getInstanceFromGuiceContainer(TaskQueueMessageHandler.class).processMessages();

        var updatedGatewayAccount = gatewayAccountDao.findByExternalId(gatewayAccount.getExternalId()).get();
        assertThat(updatedGatewayAccount.isDisabled(), is(true));
        assertThat(updatedGatewayAccount.getGatewayAccountCredentials().get(0).getState(), is(GatewayAccountCredentialState.RETIRED));
    }
}
