package uk.gov.pay.connector.paymentprocessor.service;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.XrayUtils;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureProcessScheduler implements Managed {
    private final Logger logger = LoggerFactory.getLogger(CaptureProcessScheduler.class);

    static final String CAPTURE_PROCESS_SCHEDULER_NAME = "capture-process";
    static final int SCHEDULER_THREADS = 1;

    static final long INITIAL_DELAY_IN_SECONDS = 20L;
    static final long RANDOM_INTERVAL_MINIMUM_IN_SECONDS = 10L;
    static final long RANDOM_INTERVAL_MAXIMUM_IN_SECONDS = 20L;

    private long initialDelayInSeconds = INITIAL_DELAY_IN_SECONDS;
    private long randomIntervalMinimumInSeconds = RANDOM_INTERVAL_MINIMUM_IN_SECONDS;
    private long randomIntervalMaximumInSeconds = RANDOM_INTERVAL_MAXIMUM_IN_SECONDS;
    private int schedulerThreads = SCHEDULER_THREADS;

    private final CardCaptureProcess cardCaptureProcess;
    private final ScheduledExecutorService scheduledExecutorService;
    private final XrayUtils xrayUtils;

    public CaptureProcessScheduler(ConnectorConfiguration configuration,
                                   Environment environment,
                                   CardCaptureProcess cardCaptureProcess,
                                   XrayUtils xrayUtils) {
        this.cardCaptureProcess = cardCaptureProcess;
        this.xrayUtils = xrayUtils;

        if ((configuration != null) && (configuration.getCaptureProcessConfig() != null)) {
            CaptureProcessConfig captureProcessConfig = configuration.getCaptureProcessConfig();
            initialDelayInSeconds = captureProcessConfig.getSchedulerInitialDelayInSeconds();
            randomIntervalMinimumInSeconds = captureProcessConfig.getSchedulerRandomIntervalMinimumInSeconds();
            randomIntervalMaximumInSeconds = captureProcessConfig.getSchedulerRandomIntervalMaximumInSeconds();
            schedulerThreads = captureProcessConfig.getSchedulerThreads();
        }

        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(CAPTURE_PROCESS_SCHEDULER_NAME)
                .threads(schedulerThreads + 1)
                .build();
    }

    public void start() {
        long interval = randomTimeInterval();
        logger.info("Scheduling CardCaptureProcess to run every {} seconds (will start in {} seconds)", interval, initialDelayInSeconds);

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            cardCaptureProcess.loadCaptureQueue();
            scheduleCaptureThreads();
        }, initialDelayInSeconds, interval, TimeUnit.SECONDS);
    }

    private void scheduleCaptureThreads() {
        for (int threadNumber = 1; threadNumber <= schedulerThreads; threadNumber++) {
            final int finalThreadNumber = threadNumber;
            scheduledExecutorService.schedule(() -> {
                try {
                    xrayUtils.beginSegment();
                    cardCaptureProcess.runCapture(finalThreadNumber);
                } catch (Exception e) {
                    logger.error("Unexpected error running capture operations", e);
                } finally {
                    xrayUtils.endSegment();
                }
            }, 0, TimeUnit.SECONDS);
        }
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
