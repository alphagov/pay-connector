package uk.gov.pay.connector.util;

import com.spotify.docker.client.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.*;
import java.util.Optional;

public class PostgresDockerRule implements TestRule {

    private static String host;
    private static PostgresContainer container;

    static {
        try {
            host = Optional.ofNullable(new URI(System.getenv("DOCKER_HOST")).getHost()).orElse("localhost");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public PostgresDockerRule() {
        startPostgresIfNecessary();
    }

    public String getConnectionUrl() {
        return container.getConnectionUrl();
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return statement;
    }

    private void startPostgresIfNecessary() {
        try {
            if (container == null) {
                DockerClient docker = DefaultDockerClient.fromEnv().build();
                container = new PostgresContainer(docker, host);
            }
        } catch (DockerCertificateException | InterruptedException | DockerException | IOException | ClassNotFoundException e) {
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
}