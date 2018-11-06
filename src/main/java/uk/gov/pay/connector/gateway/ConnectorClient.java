package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;

public interface ConnectorClient {

    default void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }
}
