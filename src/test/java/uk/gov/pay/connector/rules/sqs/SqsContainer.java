package uk.gov.pay.connector.rules.sqs;

import com.google.common.base.Stopwatch;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.LogConfig;
import com.spotify.docker.client.messages.PortBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.joining;

public class SqsContainer {

    private static final Logger logger = LoggerFactory.getLogger(SqsContainer.class);

    private final String containerId;
    private final int port;
    private DockerClient docker;
    private volatile boolean stopped = false;
    private static final int SQS_TIMEOUT_SEC = 15;
    private static final String ALPINE_SQS_IMAGE = "roribio16/alpine-sqs";
    private static final String INTERNAL_PORT = "9324";

    public SqsContainer(DockerClient docker) throws DockerException, InterruptedException, IOException, ClassNotFoundException {
        this.docker = docker;

        failsafeDockerPull(docker, ALPINE_SQS_IMAGE);
        docker.listImages(DockerClient.ListImagesParam.create("name", ALPINE_SQS_IMAGE));

        final HostConfig hostConfig = HostConfig.builder().logConfig(LogConfig.create("json-file")).publishAllPorts(true).build();
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(ALPINE_SQS_IMAGE)
                .hostConfig(hostConfig)
                .env("AWS_ACCESS_KEY_ID=your_access_key_id , AWS_SECRET_KEY=your_secret_access_key")
                .build();
        containerId = docker.createContainer(containerConfig).id();
        docker.startContainer(containerId);
        port = hostPortNumber(docker.inspectContainer(containerId));
        registerShutdownHook();
        waitForContainerToStart();
    }

    public SqsContainer() throws InterruptedException, IOException, ClassNotFoundException, DockerCertificateException, DockerException {
        this(DefaultDockerClient.fromEnv().build());
    }

    private void failsafeDockerPull(DockerClient docker, String image) {
        try {
            docker.pull(image);
        } catch (Exception e) {
            logger.error("Docker image " + image + " could not be pulled from DockerHub", e);
        }
    }

    private static int hostPortNumber(ContainerInfo containerInfo) {
        List<PortBinding> portBindings = containerInfo.networkSettings().ports().get(INTERNAL_PORT + "/tcp");
        logger.info("SQS port: {}", portBindings.stream().map(PortBinding::hostPort).collect(joining(", ")));
        return parseInt(portBindings.get(0).hostPort());
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void waitForContainerToStart() throws InterruptedException {
        Stopwatch timer = Stopwatch.createStarted();
        boolean succeeded = false;
        while (!succeeded && timer.elapsed(TimeUnit.SECONDS) < SQS_TIMEOUT_SEC) {
            Thread.sleep(10000);
            succeeded = checkSQSContainerAvailability();
        }
        if (!succeeded) {
            throw new RuntimeException("SQS did not start in " + SQS_TIMEOUT_SEC + " seconds.");
        }
        logger.info("SQS docker container started in {}.", timer.elapsed(TimeUnit.MILLISECONDS));
    }

    public String getUrl() {
        return "http://" + docker.getHost() + ":" + port;
    }

    private boolean checkSQSContainerAvailability() {
        //todo: http check http://localhost:port/queue/default?
        return true;
    }

    public void stop() {
        if (stopped) {
            return;
        }
        try {
            stopped = true;
            System.err.println("Killing SQS container with ID: " + containerId);
            LogStream logs = docker.logs(containerId, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr());
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
