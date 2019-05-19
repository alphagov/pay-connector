package uk.gov.pay.connector.junit;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Start a Generic Test container with SQS docker image and creates queues if required.
 * No need to call stop() as container will be stopped after executing the tests
 */
final class SqsTestDocker {

    private static final Logger logger = LoggerFactory.getLogger(SqsTestDocker.class);

    private static GenericContainer sqsContainer;

    public static void initialise(String... queues) {
        try {
            createContainer();
            waitForSQSContainerToStart();
            createQueues(queues);
        } catch (Exception e) {
            logger.error("Exception initialising SQS Container - {}", e.getMessage());
            throw new SqsTestDockerException(e);
        }
    }

    private static void createContainer() {
        if (sqsContainer == null) {
            // wait at least 3 minutes for container to startup to avoid timeout exception. Default is 60 seconds
            sqsContainer = new GenericContainer("roribio16/alpine-sqs")
                    .withStartupTimeout(Duration.ofMinutes(3));

            sqsContainer.start();
        }
    }

    private static void waitForSQSContainerToStart() throws InterruptedException {
        Stopwatch timer = Stopwatch.createStarted();

        boolean succeeded;
        for (succeeded = false; !succeeded && timer.elapsed(TimeUnit.SECONDS) < 15L;
             succeeded = checkSQSAvailability()) {
            Thread.sleep(500L);
        }

        if (!succeeded) {
            throw new RuntimeException("SQS Container did not start in 15 seconds.");
        } else {
            logger.info("SQS docker container started in {}.", timer.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private static void createQueues(String... queues) {
        AmazonSQS amazonSQS = getSqsClient();
        if (queues != null) {
            for (String queue : queues) {
                amazonSQS.createQueue(queue);
            }
        }
    }

    public static String getQueueUrl(String queueName) {
        return getEndpoint() + "/queue/" + queueName;
    }

    private static String getEndpoint() {
        return "http://localhost:" + sqsContainer.getMappedPort(9324);
    }

    /**
     * Checks for `default` queue which indicates SQS server is started and ready to use
     *
     * @return true if `default` queue exists and false otherwise
     */
    private static boolean checkSQSAvailability() {
        AmazonSQS amazonSQS = getSqsClient();

        try {
            ListQueuesResult result = amazonSQS.listQueues();

            for (String queueUrl : result.getQueueUrls()) {
                if (queueUrl.contains("/queue/default"))
                    return true;
            }
        }
        // exceptions (ignored) are thrown if container is not ready yet
        catch (Exception ignored) {

        }

        return false;
    }

    private static AmazonSQS getSqsClient() {
        // random credentials required by AWS SDK to build SQS client
        BasicAWSCredentials awsCreds = new BasicAWSCredentials("x", "x");

        return AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                getEndpoint(),
                                "region-1"
                        ))
                .build();
    }
}
