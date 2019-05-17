package uk.gov.pay.connector.junit;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import uk.gov.pay.connector.rules.sqs.SqsContainer;

public final class SqsTestDocker {

    private static SqsContainer container;

    public static void getOrCreate(String[] queues) {
        try {
            if (container == null) {
                container = new SqsContainer();
                createQueues(queues);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createQueues(String[] queues) {
        for (String queueName : queues) {

            AmazonSQS amazonSQS = AmazonSQSClientBuilder.standard()
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(
                                    getEndpoint(),
                                    getRegion()
                            ))
                    .build();
            amazonSQS.createQueue(queueName);
        }
    }

    public static String getEndpoint() {
        return container.getUrl();
    }

    public static String getRegion() {
        return "region-1";
    }

    public static String getQueueUrl(String queueName) {
        return getEndpoint() + "/queue/" + queueName;
    }
}
