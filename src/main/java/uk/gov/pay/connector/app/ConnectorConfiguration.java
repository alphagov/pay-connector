package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ConnectorConfiguration extends Configuration {

    @Valid
    @NotNull
    private WorldpayNotificationConfig worldpayConfig;

    @Valid
    @NotNull
    private ExecutorServiceConfig executorServiceConfig = new ExecutorServiceConfig();

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
    private SmartpayCredentialsConfig smartpayConfig;

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

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    public LinksConfig getLinks() {
        return links;
    }

    @JsonProperty("worldpay")
    public WorldpayNotificationConfig getWorldpayConfig() {
        return worldpayConfig;
    }

    @JsonProperty("smartpay")
    public SmartpayCredentialsConfig getSmartpayConfig() {
        return smartpayConfig;
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
}
