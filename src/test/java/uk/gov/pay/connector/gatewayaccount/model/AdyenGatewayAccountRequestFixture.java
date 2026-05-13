package uk.gov.pay.connector.gatewayaccount.model;

public class AdyenGatewayAccountRequestFixture {

    private String providerAccountType;
    private String paymentProvider;
    private String serviceName;
    private String serviceId;
    private String description;
    private String analyticsId;
    private AdyenCredentials credentials;
    private boolean requires3ds;
    private boolean allowApplePay;
    private boolean allowGooglePay;
    private boolean sendPayerEmailToGateway;
    private boolean sendPayerIPAddressToGateway;

    static AdyenGatewayAccountRequestFixture anAdyenGatewayAccountRequest() {
        return new AdyenGatewayAccountRequestFixture();
    }

    public AdyenGatewayAccountRequestFixture withProviderAccountType(String providerAccountType) {
        this.providerAccountType = providerAccountType;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withAnalyticsId(String analyticsId) {
        this.analyticsId = analyticsId;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withCredentials(AdyenCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withAllowApplePay(boolean allowApplePay) {
        this.allowApplePay = allowApplePay;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withAllowGooglePay(boolean allowGooglePay) {
        this.allowGooglePay = allowGooglePay;
        return this;
    }

    public AdyenGatewayAccountRequestFixture withSendPayerEmailToGateway(boolean sendPayerEmailToGateway) {
        this.sendPayerEmailToGateway = sendPayerEmailToGateway;
        return this;
    }

    AdyenGatewayAccountRequestFixture withSendPayerIPAddressToGateway(boolean sendPayerIPAddressToGateway) {
        this.sendPayerIPAddressToGateway = sendPayerIPAddressToGateway;
        return this;
    }

    public AdyenGatewayAccountRequest build() {
        return new AdyenGatewayAccountRequest(
                providerAccountType,
                paymentProvider,
                serviceName,
                serviceId,
                description,
                analyticsId,
                credentials,
                requires3ds,
                allowApplePay,
                allowGooglePay,
                sendPayerEmailToGateway,
                sendPayerIPAddressToGateway
        );
    }
}
