package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ExpungeConfig extends Configuration {

    @Valid
    @NotNull
    private int minimumAgeOfChargeInDays;
    
    @Valid
    @NotNull
    @Min(1)
    private int numberOfChargesToExpunge;

    public int getMinimumAgeOfChargeInDays() {
        return minimumAgeOfChargeInDays;
    }

    public int getNumberOfChargesToExpunge() {
        return numberOfChargesToExpunge;
    }
}
