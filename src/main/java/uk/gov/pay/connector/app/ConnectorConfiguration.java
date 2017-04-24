package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ConnectorConfiguration extends Configuration {

    @Valid
    @NotNull
    private WorldpayConfig worldpayConfig;

    @Valid
    @NotNull
    private ExecutorServiceConfig executorServiceConfig = new ExecutorServiceConfig();

    @Valid
    @NotNull
    private CaptureProcessConfig captureProcessConfig;

    @Valid
    @NotNull
    @JsonProperty("transactionsPaginationServiceConfig")
    private TransactionsPaginationServiceConfig transactionsPaginationServiceConfig;

    @Valid
    @NotNull
    @JsonProperty("notifyConfig")
    private NotifyConfiguration notifyConfig;

    @Valid
    @NotNull
    private GatewayConfig smartpayConfig;

    @Valid
    @NotNull
    private DataSourceFactory dataSourceFactory;

    @Valid
    @NotNull
    private JPAConfiguration jpaConfiguration;

    @Valid
    @NotNull
    private LinksConfig links = new LinksConfig();

    @Valid
    @NotNull
    @JsonProperty("jerseyClient")
    private JerseyClientConfiguration jerseyClientConfig;

    @Valid
    @NotNull
    @JsonProperty("customJerseyClient")
    private CustomJerseyClientConfiguration customJerseyClient;

    @NotNull
    private String graphiteHost;

    @NotNull
    private String graphitePort;

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    public LinksConfig getLinks() {
        return links;
    }

    @JsonProperty("worldpay")
    public WorldpayConfig getWorldpayConfig() {
        return worldpayConfig;
    }

    @JsonProperty("smartpay")
    public GatewayConfig getSmartpayConfig() {
        return smartpayConfig;
    }

    public GatewayConfig getGatewayConfigFor(PaymentGatewayName gateway) {
        if (gateway == PaymentGatewayName.WORLDPAY) {
            return getWorldpayConfig();
        }

        if (gateway == PaymentGatewayName.SMARTPAY) {
            return getSmartpayConfig();
        }

        throw new PaymentGatewayName.Unsupported();
    }

    @JsonProperty("jpa")
    public JPAConfiguration getJpaConfiguration() {
        return jpaConfiguration;
    }

    public ExecutorServiceConfig getExecutorServiceConfig() {
        return executorServiceConfig;
    }

    public TransactionsPaginationServiceConfig getTransactionsPaginationConfig() {
        return transactionsPaginationServiceConfig;
    }

    public NotifyConfiguration getNotifyConfiguration() {
        return notifyConfig;
    }

    public JerseyClientConfiguration getClientConfiguration() {
        return jerseyClientConfig;
    }

    public CustomJerseyClientConfiguration getCustomJerseyClient() {
        return customJerseyClient;
    }

    public String getGraphiteHost() {
        return graphiteHost;
    }

    public String getGraphitePort() {
        return graphitePort;
    }

    public CaptureProcessConfig getCaptureProcessConfig() {
        return captureProcessConfig;
    }
}
