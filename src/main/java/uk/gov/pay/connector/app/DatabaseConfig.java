package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class DatabaseConfig extends Configuration {
    private String driverClass;
    private String user;
    private String password;
    private String url;

    public String getDriverClass() {
        return driverClass;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }
}
