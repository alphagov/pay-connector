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
    private int minimumAgeForHistoricChargeExceptions;

    @Valid
    @NotNull
    @Min(1)
    private int numberOfChargesOrRefundsToExpunge;

    @Valid
    @NotNull
    @Min(0)
    private int excludeChargesOrRefundsParityCheckedWithInDays;

    private boolean expungeChargesEnabled;

    private boolean expungeRefundsEnabled;

    @Valid
    @NotNull
    private int minimumAgeOfRefundInDays;

    public int getMinimumAgeOfChargeInDays() {
        return minimumAgeOfChargeInDays;
    }

    public int getNumberOfChargesOrRefundsToExpunge() {
        return numberOfChargesOrRefundsToExpunge;
    }

    public int getExcludeChargesOrRefundsParityCheckedWithInDays() {
        return excludeChargesOrRefundsParityCheckedWithInDays;
    }

    public boolean isExpungeChargesEnabled() {
        return expungeChargesEnabled;
    }

    public int getMinimumAgeForHistoricChargeExceptions() {
        return minimumAgeForHistoricChargeExceptions;
    }

    public boolean isExpungeRefundsEnabled() {
        return expungeRefundsEnabled;
    }

    public int getMinimumAgeOfRefundInDays() {
        return minimumAgeOfRefundInDays;
    }
}
