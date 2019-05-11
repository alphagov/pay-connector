package uk.gov.pay.connector.rules;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import uk.gov.pay.connector.util.PostgresContainer;

import java.io.IOException;

public class PostgresDockerRule implements TestRule {

    private static PostgresContainer container;

    public PostgresDockerRule() throws DockerException {
        startPostgresIfNecessary();
    }

    public String getConnectionUrl() {
        return container.getConnectionUrl();
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return statement;
    }

    private void startPostgresIfNecessary() throws DockerException {
        try {
            if (container == null) {
                DockerClient docker = DefaultDockerClient.fromEnv().build();
                container = new PostgresContainer(docker);
            }
        } catch (DockerCertificateException | InterruptedException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUsername() {
        return container.getUsername();
    }

    public String getPassword() {
        return container.getPassword();
    }

    public void stop() {
        container.stop();
        container = null;
    }

    public String getDriverClass() {
        return "org.postgresql.Driver";
    }
}
