package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public class AdyenGatewayAccountRequest extends GatewayAccountRequest {

    private final AdyenCredentials credentials;

    public AdyenGatewayAccountRequest(@JsonProperty("type") String providerAccountType,
                                      @JsonProperty("payment_provider") String paymentProvider,
                                      @JsonProperty("service_name") String serviceName,
                                      @JsonProperty("service_id") String serviceId,
                                      @JsonProperty("description") String description,
                                      @JsonProperty("analytics_id") String analyticsId,
                                      @JsonProperty("credentials") AdyenCredentials credentials,
                                      @JsonProperty("requires_3ds") boolean requires3ds,
                                      @JsonProperty("allow_apple_pay") boolean allowApplePay,
                                      @JsonProperty("allow_google_pay") boolean allowGooglePay,
                                      @JsonProperty("send_payer_email_to_gateway") boolean sendPayerEmailToGateway,
                                      @JsonProperty("send_payer_ip_address_to_gateway") boolean sendPayerIPAddressToGateway
    ) {
        super(providerAccountType,
                paymentProvider,
                serviceName,
                serviceId,
                description,
                analyticsId,
                requires3ds,
                allowApplePay,
                allowGooglePay,
                sendPayerEmailToGateway,
                sendPayerIPAddressToGateway);
        this.credentials = credentials;
    }

    @Override
    public Map<String, String> getCredentialsAsMap() {
        return Optional.ofNullable(credentials)
                .map(AdyenCredentials::toMap)
                .orElse(Map.of());
    }
}
