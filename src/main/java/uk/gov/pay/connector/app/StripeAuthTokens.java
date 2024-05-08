package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class StripeAuthTokens extends Configuration {

    @Valid
    @NotNull
    private String test;

    @Valid
    @NotNull
    private String live;

    public String getTest() {
        return test;
    }

    public String getLive() {
        return live;
    }
}
