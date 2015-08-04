package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;

public class Ping extends HealthCheck {

    @Override
    protected Result check() throws Exception {
        return Result.healthy();
    }
}
