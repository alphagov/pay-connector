package uk.gov.pay.connector.util;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static java.lang.Integer.parseInt;

public class PostgresDockerRule implements TestRule {

    public static final String POSTGRES = "postgres:9.4.4";
    public static final String INTERNAL_PORT = "5432";
    public static final String DB_PASSWORD = "mysecretpassword";
    private static DockerClient docker = null;
    private static String containerId = null;
    private static int port;
    private static String host;

    static {
        try {
            host = Optional.ofNullable(new URI(System.getenv("DOCKER_HOST")).getHost()).orElse("localhost");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public PostgresDockerRule() {
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
        ContainerConfig containerConfig = ContainerConfig.builder().image(POSTGRES).hostConfig(hostConfig).env("POSTGRES_PASSWORD=" + DB_PASSWORD).build();
        containerId = docker.createContainer(containerConfig).id();
        docker.startContainer(containerId);
        port = hostPortNumber(docker.inspectContainer(containerId));
        registerShutdownHook();
        waitForPostgresToStart();
    }

    private static int hostPortNumber(ContainerInfo containerInfo) {
        System.out.println("Postgres host port:");
        List<PortBinding> portBindings = containerInfo.networkSettings().ports().get(INTERNAL_PORT + "/tcp");
        portBindings.stream().forEach(p -> System.out.println(p.hostPort()));
        return parseInt(portBindings.get(0).hostPort());
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.err.println("Killing postgres container with ID: " + containerId);
                LogStream logs = docker.logs(containerId, DockerClient.LogsParameter.STDOUT, DockerClient.LogsParameter.STDERR);
                System.err.println("Killed container logs:\n" + logs.readFully());
                docker.stopContainer(containerId, 5);
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
            succeeded = "localhost".equals(host) ? checkUnixSocket() : checkINETSocket();
        }
        System.out.println("Postgres docker container started");
    }

    private static boolean checkINETSocket() throws IOException {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (ConnectException except) {
            return false;
        }
    }

    private static boolean checkUnixSocket() throws IOException, InterruptedException {
        //TODO: Check unix domain socket connection
        Thread.sleep(5000);
        return true;
    }
}