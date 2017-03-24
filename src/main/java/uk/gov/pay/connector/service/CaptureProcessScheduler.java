package uk.gov.pay.connector.service;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final CardCaptureProcess cardCaptureProcess;
    ScheduledExecutorService scheduledExecutorService;

    public CaptureProcessScheduler(Environment environment, CardCaptureProcess cardCaptureProcess) {
        this.cardCaptureProcess = cardCaptureProcess;
        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(CAPTURE_PROCESS_SCHEDULER_NAME)
                .threads(SCHEDULER_THREADS)
                .build();
    }

    public void start() {
        long interval = randomTimeInterval();
        logger.info("Scheduling CardCaptureProcess to run every {} seconds (will start in {} seconds)", interval, INITIAL_DELAY_IN_SECONDS);

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                cardCaptureProcess.runCapture();
            } catch (Exception e) {
                logger.error("Unexpected error running capture operations", e);
            }
        }, INITIAL_DELAY_IN_SECONDS, interval, TimeUnit.SECONDS);
    }

    private long randomTimeInterval() {
        Random rn = new Random();
        return rn.longs(RANDOM_INTERVAL_MINIMUM_IN_SECONDS, RANDOM_INTERVAL_MAXIMUM_IN_SECONDS).findFirst().getAsLong();
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }
}
