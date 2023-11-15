package uk.gov.pay.connector.pact.event;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.MessagePactProviderRule;
import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.PactVerification;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.model.v3.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.SqsTestDocker;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.queue.tasks.TaskQueueMessageHandler;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, withDockerSQS = true, config = "config/test-it-config.yaml")
public class ServiceArchivedEventIT {

    static ObjectMapper objectMapper = new ObjectMapper();
    
    @Rule
    public MessagePactProviderRule mockProvider = new MessagePactProviderRule(this);

    @DropwizardTestContext
    private TestContext testContext;

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
        var gatewayAccountDao = testContext.getInstanceFromGuiceContainer(GatewayAccountDao.class);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setDisabled(false);
        gatewayAccount.setExternalId(randomUuid());
        gatewayAccount.setServiceId(serviceExternalId);
        var gatewayAccountCredentials = new GatewayAccountCredentialsEntity(gatewayAccount, WORLDPAY.getName(), Map.of(), ACTIVE);
        gatewayAccountCredentials.setExternalId(randomUuid());
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentials));
        gatewayAccountDao.persist(gatewayAccount);
        
        testContext.getAmazonSQS().sendMessage(SqsTestDocker.getQueueUrl("tasks-queue"), new String(currentMessage));

        testContext.getInstanceFromGuiceContainer(TaskQueueMessageHandler.class).processMessages();

        var updatedGatewayAccount = gatewayAccountDao.findByExternalId(gatewayAccount.getExternalId()).get();
        assertTrue(updatedGatewayAccount.isDisabled());
        assertThat(updatedGatewayAccount.getGatewayAccountCredentials().get(0).getState(), is(GatewayAccountCredentialState.RETIRED));
    }

    public void setMessage(byte[] messageContents) {
        currentMessage = messageContents;
    }
}
