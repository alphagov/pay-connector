package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class ExecutorServiceConfig extends Configuration {

    private int threadsPerCpu;

    public int getThreadsPerCpu() {
        return threadsPerCpu;
    }
}
