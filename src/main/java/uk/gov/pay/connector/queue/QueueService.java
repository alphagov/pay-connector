package uk.gov.pay.connector.queue;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.ChargeStatusChangeEvent;

public class QueueService {
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    private static AmazonSQS sqs = AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration("http://queue:9324", "eu-west-1")
            )
            .withClientConfiguration(
                    new ClientConfiguration().withProtocol(Protocol.HTTP)
            )
            .build();

    public void push(ChargeStatusChangeEvent chargeStatusChangeEvent) {
        logger.info("Pushing message onto queue");

        try {
            SendMessageRequest sendMessageRequest =  new SendMessageRequest()
                    .withQueueUrl("http://queue:9324/queue/default")
                    .withMessageBody("hello world");


            SendMessageResult result = sqs.sendMessage(sendMessageRequest);
            logger.info("Pushed message onto queue {}", result);
        } catch(Exception e) {
            logger.error("ERRRROR!", e);
        }
        
    }
}
