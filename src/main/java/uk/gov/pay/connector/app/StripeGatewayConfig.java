package uk.gov.pay.connector.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.dropwizard.Configuration;
import uk.gov.pay.connector.app.config.StringToListConverter;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private Double feePercentageV2;

    @Valid
    private long feePercentageV2Date;

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

    @NotNull
    private List<String> credentials;

    @Valid
    private int radarFeeInPence;

    @Valid
    private int threeDsFeeInPence;

    @Valid
    private long collectFeeForStripeFailedPaymentsFromDate;

    @Valid
    @JsonDeserialize(converter = StringToListConverter.class)
    private List<String> enableTransactionFeeV2ForGatewayAccountsList;

    @Valid
    private Boolean enableTransactionFeeV2ForTestAccounts;

    public String getUrl() {
        return url;
    }

    public StripeAuthTokens getAuthTokens() {
        return authTokens;
    }

    public List<String> getWebhookSigningSecrets() {
        return webhookSigningSecrets.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
    }
    
    public Double getFeePercentage() {
        return Optional.ofNullable(feePercentage).orElse(0.0);
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

    public List<String> getCredentials() {
        return credentials;
    }

    public Double getFeePercentageV2() {
        return feePercentageV2;
    }

    public Instant getFeePercentageV2Date() {
        return Instant.ofEpochSecond(feePercentageV2Date);
    }

    public int getRadarFeeInPence() {
        return radarFeeInPence;
    }

    public int getThreeDsFeeInPence() {
        return threeDsFeeInPence;
    }

    public Instant getCollectFeeForStripeFailedPaymentsFromDate() {
        return Instant.ofEpochSecond(collectFeeForStripeFailedPaymentsFromDate);
    }

    public List<String> getEnableTransactionFeeV2ForGatewayAccountsList() {
        return enableTransactionFeeV2ForGatewayAccountsList;
    }

    public Boolean isEnableTransactionFeeV2ForTestAccounts() {
        return enableTransactionFeeV2ForTestAccounts;
    }
}
