package uk.gov.pay.connector.queue;

import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import java.util.List;
import java.util.stream.Collectors;

public class SqsQueueReceiveResponse {

    private String messageId;
    private String receiptHandle;
    private String messageBody;

    private SqsQueueReceiveResponse(String messageId, String receiptHandle, String messageBody) {
        this.messageId = messageId;
        this.receiptHandle = receiptHandle;
        this.messageBody = messageBody;
    }

    public static List<SqsQueueReceiveResponse> of(ReceiveMessageResult receiveMessageResult) {

        List<SqsQueueReceiveResponse> sqsQueueReceiveResponse = receiveMessageResult.getMessages()
                .stream()
                .map(c -> new SqsQueueReceiveResponse(c.getMessageId(), c.getReceiptHandle(), c.getBody()))
                .collect(Collectors.toList());

        return sqsQueueReceiveResponse;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getReceiptHandle() {
        return receiptHandle;
    }

    public String getMessageBody() {
        return messageBody;
    }
}
