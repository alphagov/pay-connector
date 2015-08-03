package uk.gov.pay.connector.util;

import com.spotify.docker.client.*;
import com.spotify.docker.client.messages.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.*;

import static java.lang.Integer.parseInt;

public class PostgresDockerRule implements TestRule {

    public static final String POSTGRES = "postgres:9.4.4";
    public static final String INTERNAL_PORT = "5432";
    private static DockerClient docker = null;
    private static String containerId = null;
    private static int port;
    private static String host;

    static {
        try {
            host = new URI(System.getenv("DOCKER_HOST")).getHost();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public PostgresDockerRule()  {
        try {
            if (docker == null) {
                startPostgresContainer();
            }
        } catch (DockerCertificateException | InterruptedException | DockerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getConnectionDetails() {
        return "jdbc:postgresql://" + host + ":" + port + "/";
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        // TODO: Clear the database before each test.
        return statement;
    }

    private static void startPostgresContainer() throws DockerCertificateException, DockerException, InterruptedException, IOException {
        docker = DefaultDockerClient.fromEnv().build();
        docker.pull(POSTGRES);

        final HostConfig hostConfig = HostConfig.builder().publishAllPorts(true).build();
        ContainerConfig config = ContainerConfig.builder().image(POSTGRES).exposedPorts(INTERNAL_PORT).hostConfig(hostConfig).build();
        ContainerCreation containerCreation = docker.createContainer(config);
        containerId = containerCreation.id();
        docker.startContainer(containerId);
        port = hostPortNumber(docker.inspectContainer(containerId));
        registerShutdownHook();
        waitForPostgresToStart();
    }

    private static int hostPortNumber(ContainerInfo containerInfo) {
        return parseInt(containerInfo.networkSettings().ports().get(INTERNAL_PORT + "/tcp").get(0).hostPort());
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                docker.killContainer(containerId);
                docker.removeContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                System.err.println("Could not shutdown " + containerId);
            }
        }));
    }

    private static void waitForPostgresToStart() throws DockerException, InterruptedException, IOException {
        boolean succeeded = false;
        while (!succeeded) {
            Thread.sleep(10);
            try {
                try (Socket socket = new Socket(host, port)) {
                    succeeded = true;
                }
            } catch (ConnectException except) {
                // Twiddle thumbs.
            }
        }
    }
}
