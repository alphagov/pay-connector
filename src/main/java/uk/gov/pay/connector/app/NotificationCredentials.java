package uk.gov.pay.connector.app;

import io.dropwizard.auth.basic.BasicCredentials;

public class NotificationCredentials {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public BasicCredentials asBasicCredentials() {
        return new BasicCredentials(username, password);
    }
}
