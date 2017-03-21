package uk.gov.pay.connector.service;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureProcessScheduler implements Managed {
    static final String CAPTURE_PROCESS_SCHEDULER_NAME = "capture-process";
    static final int SCHEDULER_THREADS = 1;
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
        scheduledExecutorService.scheduleAtFixedRate(cardCaptureProcess::runCapture, 1,2, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }
}
