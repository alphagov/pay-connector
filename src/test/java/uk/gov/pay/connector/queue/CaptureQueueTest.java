package uk.gov.pay.connector.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.capture.ChargeCaptureMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptureQueueTest {

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        CaptureProcessConfig captureProcessConfig = mock(CaptureProcessConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getCaptureQueueUrl()).thenReturn("");
        when(captureProcessConfig.getFailedCaptureRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getCaptureProcessConfig()).thenReturn(captureProcessConfig);
    }

    @Test
    void shouldParseChargeIdReceivedFromQueueGivenWellFormattedJSON() throws QueueException {
                String validJsonMessage = "{ \"chargeId\": \"my-charge-id\"}";
        SendMessageResponse messageResult = mock(SendMessageResponse.class);

        List<QueueMessage> messages = Arrays.asList(
                QueueMessage.of(messageResult, validJsonMessage)
        );
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        CaptureQueue queue = new CaptureQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<ChargeCaptureMessage> chargeCaptureMessages = queue.retrieveChargesForCapture();

        assertNotNull(chargeCaptureMessages);
        assertEquals("my-charge-id", chargeCaptureMessages.get(0).getChargeId());
    }

    @Test
    void shouldSendValidSerialisedChargeToQueue() throws QueueException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExternalId("charge-id").build();
        when(sqsQueueService.sendMessage(anyString(), anyString())).thenReturn(mock(QueueMessage.class));

        CaptureQueue queue = new CaptureQueue(sqsQueueService, connectorConfiguration, objectMapper);
        queue.sendForCapture(chargeEntity);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getCaptureQueueUrl(),
                "{\"chargeId\":\"charge-id\"}");
    }
}
