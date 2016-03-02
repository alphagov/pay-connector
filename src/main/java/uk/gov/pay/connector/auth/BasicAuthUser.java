package uk.gov.pay.connector.auth;

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
