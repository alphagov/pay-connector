package uk.gov.pay.connector.gateway.smartpay.auth;

import java.security.Principal;

public class BasicAuthUser implements Principal {

    private String userName;

    public BasicAuthUser(String username) {
        this.userName = username;
    }

    @Override
    public String getName() {
        return userName;
    }
}
