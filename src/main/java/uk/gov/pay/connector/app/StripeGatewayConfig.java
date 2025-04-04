package uk.gov.pay.connector.app;

import io.dropwizard.core.Configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class StripeGatewayConfig extends Configuration {

    @Valid
    @NotNull
    private String url;

    @Valid
    @NotNull
    private StripeAuthTokens authTokens;

    @Valid
    @NotNull
    private List<String> webhookSigningSecrets;
    
    @Valid
    private Double feePercentage;

    @Valid
    private String platformAccountId;

    @Valid
    @NotNull
    private Boolean collectFee;

    @Valid
    @NotNull
    @Min(1)
    private int notification3dsWaitDelay;

    @Valid
    @NotNull
    private List<String> allowedCidrs;

    @Valid
    private int radarFeeInPence;

    @Valid
    private int threeDsFeeInPence;

    @Valid
    private Instant rechargeServicesForLivePaymentDisputesFromDate;
    
    @Valid
    private Instant rechargeServicesForTestPaymentDisputesFromDate;

    public String getUrl() {
        return url;
    }

    public StripeAuthTokens getAuthTokens() {
        return authTokens;
    }

    public List<String> getWebhookSigningSecrets() {
        return webhookSigningSecrets.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    public Boolean isCollectFee() {
        return collectFee;
    }

    public String getPlatformAccountId() {
        return platformAccountId;
    }

    public int getNotification3dsWaitDelay() {
        return notification3dsWaitDelay;
    }

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    public Double getFeePercentage() {
        return feePercentage;
    }

    public int getRadarFeeInPence() {
        return radarFeeInPence;
    }

    public int getThreeDsFeeInPence() {
        return threeDsFeeInPence;
    }

    public Instant getRechargeServicesForLivePaymentDisputesFromDate() {
        return rechargeServicesForLivePaymentDisputesFromDate;
    }

    public Instant getRechargeServicesForTestPaymentDisputesFromDate() {
        return rechargeServicesForTestPaymentDisputesFromDate;
    }
}
