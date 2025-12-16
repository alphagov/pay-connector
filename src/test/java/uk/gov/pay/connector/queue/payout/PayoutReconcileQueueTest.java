package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.app.config.PayoutReconcileProcessConfig;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutReconcileQueueTest {

    private static ObjectMapper objectMapper = new ObjectMapper();
    
    @Mock
    private SqsQueueService sqsQueueService;
    @Mock
    private ConnectorConfiguration connectorConfiguration;
    private PayoutReconcileQueue payoutReconcileQueue;

    @BeforeEach
    void setUp() {
        PayoutReconcileProcessConfig payoutReconcileProcessConfig = mock(PayoutReconcileProcessConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getPayoutReconcileQueueUrl()).thenReturn("");
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getPayoutReconcileProcessConfig()).thenReturn(payoutReconcileProcessConfig);

        payoutReconcileQueue = new PayoutReconcileQueue(sqsQueueService, connectorConfiguration, objectMapper);
    }

    @Test
    void shouldParsePayoutFromQueueGivenWellFormattedJSON() throws QueueException {
        String validJsonMessage = "{ \"gateway_payout_id\": \"payout-id\", \"connect_account_id\": \"connect-accnt-id\",\"created_date\":\"2020-05-01T10:30:00.000000Z\"}";
        SendMessageResponse messageResult = mock(SendMessageResponse.class);

        List<QueueMessage> messages = Arrays.asList(
                QueueMessage.of(messageResult, validJsonMessage)
        );
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);

        List<PayoutReconcileMessage> payoutReconcileMessages = payoutReconcileQueue.retrievePayoutMessages();

        assertNotNull(payoutReconcileMessages);
        assertEquals("payout-id", payoutReconcileMessages.getFirst().getGatewayPayoutId());
        assertEquals("connect-accnt-id", payoutReconcileMessages.getFirst().getConnectAccountId());
        assertEquals(Instant.parse("2020-05-01T10:30:00.000Z"), payoutReconcileMessages.getFirst().getCreatedDate());
    }

    @Test
    void shouldSendValidSerialisedPayoutToQueue() throws QueueException, JsonProcessingException {
        Payout payout = new Payout("payout-id", "connect-accnt-id", Instant.parse("2020-05-01T10:30:00.000Z"));
        when(sqsQueueService.sendMessage(anyString(), anyString())).thenReturn(mock(QueueMessage.class));

        payoutReconcileQueue.sendPayout(payout);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getPayoutReconcileQueueUrl(),
                "{\"gateway_payout_id\":\"payout-id\",\"connect_account_id\":\"connect-accnt-id\",\"created_date\":\"2020-05-01T10:30:00.000000Z\"}");
    }
}
