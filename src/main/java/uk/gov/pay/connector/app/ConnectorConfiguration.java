package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ConnectorConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory dataSourceFactory = new DataSourceFactory();

    @Valid
    @NotNull
    private LinksConfig links = new LinksConfig();

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    public LinksConfig getLinks() {
        return links;
    }
}
