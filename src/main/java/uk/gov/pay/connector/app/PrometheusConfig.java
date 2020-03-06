package uk.gov.pay.connector.app;


import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;

public class PrometheusConfig extends Configuration {

    @NotNull private boolean prometheusEnabled;
    @NotNull private String path;

    public boolean isPrometheusEnabled() {
        return prometheusEnabled;
    }

    public String getPath() {
        return path;
    }
}
