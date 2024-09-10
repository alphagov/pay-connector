package uk.gov.pay.connector.rules;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.List;
import java.util.function.Consumer;

import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

public class SqsTestDocker {
    private static final Logger logger = LoggerFactory.getLogger(SqsTestDocker.class);

    private static GenericContainer sqsContainer;

    public static AmazonSQS initialise(List<String> queueNames) {
        try {
            createContainer();
            return createQueues(queueNames);
        } catch (Exception e) {
            logger.error("Exception initialising SQS Container - {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void createContainer() {
        if (sqsContainer == null) {
            logger.info("Creating SQS Container");

            sqsContainer = new GenericContainer("softwaremill/elasticmq-native:1.4.2")
                    .withExposedPorts(9324)
                    .withCreateContainerCmdModifier((Consumer<CreateContainerCmd>) cmd -> cmd.withHostConfig(
                            new HostConfig().withPortBindings(new PortBinding(
                                    Ports.Binding.bindIp("127.0.0.1"),
                                    new ExposedPort(9324)
                            ))
                    ))
                    .waitingFor(Wait.forLogMessage(".*ElasticMQ server.*.*started.*", 1));
            sqsContainer.start();
        }
    }

    private static AmazonSQS createQueues(List<String> queueNames) {
        AmazonSQS amazonSQS = getSqsClient();
        queueNames.forEach(amazonSQS::createQueue);

        return amazonSQS;
    }

    public static String getQueueUrl(String queueName) {
        return getEndpoint() + "/queue/" + queueName;
    }

    public static String getEndpoint() {
        return "http://localhost:" + sqsContainer.getMappedPort(9324);
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
                .withRequestHandlers()
                .build();
    }
}
