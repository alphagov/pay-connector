package uk.gov.pay.connector.rules.sqs;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;

public class SqsDockerRule implements TestRule {

    private static SqsContainer container;

    public SqsDockerRule() throws DockerException {
        startSqsContainerIfNecessary();
    }

    public String getUrl() {
        return container.getUrl();
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return statement;
    }

    private void startSqsContainerIfNecessary() throws DockerException {
        try {
            if (container == null) {
                DockerClient docker = DefaultDockerClient.fromEnv().build();
                container = new SqsContainer(docker);
            }
        } catch (DockerCertificateException | InterruptedException | IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        container.stop();
        container = null;
    }
}
