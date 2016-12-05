package uk.gov.pay.connector.util;

import static uk.gov.pay.connector.resources.ApiPaths.API_VERSION;

public class MetricNameBuilder {

    public static String getMetricsNamespace(String url) {
        return url.replaceAll("/(?!"+API_VERSION +")[\\w]*[\\d]+[\\w]*", "/{resource-id}");
    }
}
