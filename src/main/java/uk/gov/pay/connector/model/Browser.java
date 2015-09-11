package uk.gov.pay.connector.model;

public class Browser {
    private final String acceptHeader;
    private final String userAgentHeader;

    public Browser(String acceptHeader, String userAgentHeader) {
        this.acceptHeader = acceptHeader;
        this.userAgentHeader = userAgentHeader;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public String getUserAgentHeader() {
        return userAgentHeader;
    }
}
