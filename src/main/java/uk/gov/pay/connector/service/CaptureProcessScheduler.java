package uk.gov.pay.connector.service;

import com.amazonaws.xray.AWSXRay;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureProcessScheduler implements Managed {
    final Logger logger = LoggerFactory.getLogger(CaptureProcessScheduler.class);

    static final String CAPTURE_PROCESS_SCHEDULER_NAME = "capture-process";
    static final int SCHEDULER_THREADS = 1;

    static final long INITIAL_DELAY_IN_SECONDS = 20L;
    static final long RANDOM_INTERVAL_MINIMUM_IN_SECONDS = 150L;
    static final long RANDOM_INTERVAL_MAXIMUM_IN_SECONDS = 200L;

    private long initialDelayInSeconds = INITIAL_DELAY_IN_SECONDS;
    private long randomIntervalMinimumInSeconds = RANDOM_INTERVAL_MINIMUM_IN_SECONDS;
    private long randomIntervalMaximumInSeconds = RANDOM_INTERVAL_MAXIMUM_IN_SECONDS;

    private final CardCaptureProcess cardCaptureProcess;
    ScheduledExecutorService scheduledExecutorService;

    public CaptureProcessScheduler(ConnectorConfiguration configuration, Environment environment, CardCaptureProcess cardCaptureProcess) {
        this.cardCaptureProcess = cardCaptureProcess;

        if ((configuration != null) && (configuration.getCaptureProcessConfig() != null)) {
            CaptureProcessConfig captureProcessConfig = configuration.getCaptureProcessConfig();
            initialDelayInSeconds = captureProcessConfig.getSchedulerInitialDelayInSeconds();
            randomIntervalMinimumInSeconds = captureProcessConfig.getSchedulerRandomIntervalMinimumInSeconds();
            randomIntervalMaximumInSeconds = captureProcessConfig.getSchedulerRandomIntervalMaximumInSeconds();
        }

        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(CAPTURE_PROCESS_SCHEDULER_NAME)
                .threads(SCHEDULER_THREADS)
                .build();
    }

    public void start() {
        long interval = randomTimeInterval();
        logger.info("Scheduling CardCaptureProcess to run every {} seconds (will start in {} seconds)", interval, initialDelayInSeconds);

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                AWSXRay.beginSegment("pay-connector");
                cardCaptureProcess.runCapture();
            } catch (Exception e) {
                logger.error("Unexpected error running capture operations", e);
            } finally {
                AWSXRay.endSegment();
            }
        }, initialDelayInSeconds, interval, TimeUnit.SECONDS);
    }

    private long randomTimeInterval() {
        if (randomIntervalMaximumInSeconds == randomIntervalMinimumInSeconds) {
            return randomIntervalMinimumInSeconds;
        }
        Random rn = new Random();
        return rn.longs(randomIntervalMinimumInSeconds, randomIntervalMaximumInSeconds).findFirst().getAsLong();
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }
}
