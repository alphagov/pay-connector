package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.DatabaseConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ConnectorConfiguration extends Configuration {

    @Valid
    @NotNull
    private GatewayCredentialsConfig worldpayConfig;

    @Valid
    @NotNull
    private SmartpayCredentialsConfig smartpayConfig;

    @Valid
    @NotNull
    private DataSourceFactory dataSourceFactory;

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

    @Valid
    @NotNull
    @JsonProperty("databaseJpa")
    private DatabaseConfig databaseConfig;

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    public LinksConfig getLinks() {
        return links;
    }

    @JsonProperty("worldpay")
    public GatewayCredentialsConfig getWorldpayConfig() {
        return worldpayConfig;
    }

    @JsonProperty("smartpay")
    public SmartpayCredentialsConfig getSmartpayConfig() {
        return smartpayConfig;
    }

    public JerseyClientConfiguration getClientConfiguration() {
        return jerseyClientConfig;
    }

    public CustomJerseyClientConfiguration getCustomJerseyClient() {
        return customJerseyClient;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
}
