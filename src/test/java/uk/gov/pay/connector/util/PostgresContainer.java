package uk.gov.pay.connector.util;

import com.google.common.base.Stopwatch;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.lang.Integer.parseInt;

public class PostgresContainer {

    private final String containerId;
    private final int port;
    private DockerClient docker;
    private String host;

    public static final String DB_PASSWORD = "mysecretpassword";
    public static final String DB_USERNAME = "postgres";
    public static final String POSTGRES = "postgres:9.4.4";
    public static final String INTERNAL_PORT = "5432";

    public PostgresContainer(DockerClient docker, String host) throws DockerException, InterruptedException, IOException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");

        this.docker = docker;
        this.host = host;
        docker.pull(POSTGRES);

        final HostConfig hostConfig = HostConfig.builder().publishAllPorts(true).build();
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(POSTGRES)
                .hostConfig(hostConfig)
                .env("POSTGRES_USER=" + DB_USERNAME, "POSTGRES_PASSWORD=" + DB_PASSWORD)
                .build();
        containerId = docker.createContainer(containerConfig).id();
        docker.startContainer(containerId);
        port = hostPortNumber(docker.inspectContainer(containerId));
        registerShutdownHook();
        waitForPostgresToStart();
    }

    public String getUsername() {
        return DB_USERNAME;
    }

    public String getPassword() {
        return DB_PASSWORD;
    }

    public String getConnectionUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/";
    }

    private static int hostPortNumber(ContainerInfo containerInfo) {
        System.out.println("Postgres host port:");
        List<PortBinding> portBindings = containerInfo.networkSettings().ports().get(INTERNAL_PORT + "/tcp");
        portBindings.stream().forEach(p -> System.out.println(p.hostPort()));
        return parseInt(portBindings.get(0).hostPort());
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void waitForPostgresToStart() throws DockerException, InterruptedException, IOException {
        Stopwatch timer = Stopwatch.createStarted();
        boolean succeeded = false;
        while (!succeeded && timer.elapsed(TimeUnit.SECONDS) < 10) {
            Thread.sleep(10);
            succeeded = checkPostgresConnection();
        }
        if (!succeeded) {
            throw new RuntimeException("Postgres did not start in 10 seconds.");
        }
        System.out.println("Postgres docker container started in " + timer.elapsed(TimeUnit.MILLISECONDS));
    }

    private boolean checkPostgresConnection() throws IOException {

        Properties props = new Properties();
        props.setProperty("user", DB_USERNAME);
        props.setProperty("password", DB_PASSWORD);

        try (Connection connection = DriverManager.getConnection(getConnectionUrl(), props)) {
            return true;
        } catch (Exception except) {
            return false;
        }
    }

    public void stop() {
        try {
            System.err.println("Killing postgres container with ID: " + containerId);
            LogStream logs = docker.logs(containerId, DockerClient.LogsParameter.STDOUT, DockerClient.LogsParameter.STDERR);
            System.err.println("Killed container logs:\n");
            logs.attach(System.err, System.err);
            docker.stopContainer(containerId, 5);
            docker.removeContainer(containerId);
        } catch (DockerException | InterruptedException | IOException e) {
            System.err.println("Could not shutdown " + containerId);
            e.printStackTrace();
        }
    }
}
