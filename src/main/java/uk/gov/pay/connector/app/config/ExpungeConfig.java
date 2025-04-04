package uk.gov.pay.connector.app.config;

import io.dropwizard.core.Configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ExpungeConfig extends Configuration {

    @Valid
    @NotNull
    private int minimumAgeOfChargeInDays;

    @Valid
    @NotNull
    private int minimumAgeForHistoricChargeExceptions;

    @Valid
    @NotNull
    private int minimumAgeForHistoricRefundExceptions;

    @Valid
    @NotNull
    @Min(1)
    private int numberOfChargesToExpunge;

    @Valid
    @NotNull
    @Min(1)
    private int numberOfRefundsToExpunge;

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

    public int getNumberOfChargesToExpunge() {
        return numberOfChargesToExpunge;
    }

    public int getNumberOfRefundsToExpunge() {
        return numberOfRefundsToExpunge;
    }

    public int getExcludeChargesOrRefundsParityCheckedWithInDays() {
        return excludeChargesOrRefundsParityCheckedWithInDays;
    }

    public int getMinimumAgeForHistoricRefundExceptions() {
        return minimumAgeForHistoricRefundExceptions;
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
