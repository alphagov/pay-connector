package uk.gov.pay.connector.gateway.worldpay;

import org.checkerframework.checker.units.qual.N;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseOrderRequest.WorldpayAuthoriseOrderRequestBuilder.aWorldpayAuthoriseOrderRequest;

public class WorldpayAuthoriseOrderRequest extends WorldpayOrderRequest {
    private static final TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayAuthoriseOrderTemplate.xml");
    private static final NorthAmericanRegionMapper northAmericanRegionMapper = new NorthAmericanRegionMapper();
    private String description;
    private AuthCardDetails authCardDetails;
    private String amount;
    private String paymentPlatformReference;
    private String state;
    private boolean savePaymentInstrumentToAgreement;
    private boolean requires3ds;
    private String sessionId;
    private String payerIpAddress;
    private String payerEmail;
    private String agreementId;
    private String browserLanguage;
    private int integrationVersion3ds;
    private boolean exemptionEngineEnabled;

    private WorldpayAuthoriseOrderRequest(String transactionId,
                                          String merchantCode,
                                          String description,
                                          AuthCardDetails authCardDetails,
                                          String amount,
                                          String paymentPlatformReference,
                                          String state,
                                          boolean savePaymentInstrumentToAgreement,
                                          boolean requires3ds,
                                          String sessionId,
                                          String payerIpAddress,
                                          String payerEmail,
                                          String agreementId,
                                          String browserLanguage,
                                          int integrationVersion3ds,
                                          boolean exemptionEngineEnabled) {
        super(transactionId, merchantCode);
        this.description = description;
        this.authCardDetails = authCardDetails;
        this.amount = amount;
        this.paymentPlatformReference = paymentPlatformReference;
        this.state = state;
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        this.requires3ds = requires3ds;
        this.sessionId = sessionId;
        this.payerIpAddress = payerIpAddress;
        this.payerEmail = payerEmail;
        this.agreementId = agreementId;
        this.browserLanguage = browserLanguage;
        this.integrationVersion3ds = integrationVersion3ds;
        this.exemptionEngineEnabled = exemptionEngineEnabled;
    }

    public static WorldpayAuthoriseOrderRequest createAuthoriseOrderRequest(
            CardAuthorisationGatewayRequest request,
            AcceptLanguageHeaderParser acceptLanguageHeaderParser,
            boolean exemptionEngineEnabled) {

        WorldpayAuthoriseOrderRequestBuilder builder = aWorldpayAuthoriseOrderRequest();
        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            request.getAuthCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }

        if (request.getGatewayAccount().isSendPayerEmailToGateway()) {
            Optional.ofNullable(request.getEmail()).ifPresent(builder::withPayerEmail);
        }

        if (request.getGatewayAccount().isSendReferenceToGateway()) {
            builder.withDescription(request.getReference().toString());
        } else {
            builder.withDescription(request.getDescription());
        }

        request.getAuthCardDetails().getAddress()
                .flatMap(northAmericanRegionMapper::getNorthAmericanRegionForCountry)
                .map(NorthAmericaRegion::getAbbreviation)
                .ifPresent(builder::withState);

        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        return builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getGovUkPayPaymentId()).toString())
                .withRequires3ds(is3dsRequired)
                .withSavePaymentInstrumentToAgreement(request.isSavePaymentInstrumentToAgreement())
                .withAgreementId(request.getAgreement().map(AgreementEntity::getExternalId).orElse(null))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.getAmount())
                .withAuthCardDetails(request.getAuthCardDetails())
                .withIntegrationVersion3ds(request.getGatewayAccount().getIntegrationVersion3ds())
                .withPaymentPlatformReference(request.getGovUkPayPaymentId())
                .withBrowserLanguage(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(request.getAuthCardDetails().getAcceptLanguageHeader()))
                .withExemptionEngineEnabled(exemptionEngineEnabled)
                .build();
    }

    public String getDescription() {
        return description;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public String getAmount() {
        return amount;
    }

    public String getPaymentPlatformReference() {
        return paymentPlatformReference;
    }

    public String getState() {
        return state;
    }

    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPayerIpAddress() {
        return payerIpAddress;
    }

    public String getPayerEmail() {
        return payerEmail;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getBrowserLanguage() {
        return browserLanguage;
    }

    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    public boolean isExemptionEngineEnabled() {
        return exemptionEngineEnabled;
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected TemplateBuilder getTemplateBuilder() {
        return templateBuilder;
    }

    static final class WorldpayAuthoriseOrderRequestBuilder {
        private String description;
        private AuthCardDetails authCardDetails;
        private String amount;
        private String paymentPlatformReference;
        private String state;
        private boolean savePaymentInstrumentToAgreement;
        private boolean requires3ds;
        private String sessionId;
        private String payerIpAddress;
        private String payerEmail;
        private String agreementId;
        private String browserLanguage;
        private int integrationVersion3ds;
        private boolean exemptionEngineEnabled;
        private String transactionId;
        private String merchantCode;

        private WorldpayAuthoriseOrderRequestBuilder() {
        }

        public static WorldpayAuthoriseOrderRequestBuilder aWorldpayAuthoriseOrderRequest() {
            return new WorldpayAuthoriseOrderRequestBuilder();
        }

        public WorldpayAuthoriseOrderRequestBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withAuthCardDetails(AuthCardDetails authCardDetails) {
            this.authCardDetails = authCardDetails;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withAmount(String amount) {
            this.amount = amount;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withPaymentPlatformReference(String paymentPlatformReference) {
            this.paymentPlatformReference = paymentPlatformReference;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withSessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withPayerIpAddress(String payerIpAddress) {
            this.payerIpAddress = payerIpAddress;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withPayerEmail(String payerEmail) {
            this.payerEmail = payerEmail;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withAgreementId(String agreementId) {
            this.agreementId = agreementId;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withBrowserLanguage(String browserLanguage) {
            this.browserLanguage = browserLanguage;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withIntegrationVersion3ds(int integrationVersion3ds) {
            this.integrationVersion3ds = integrationVersion3ds;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withExemptionEngineEnabled(boolean exemptionEngineEnabled) {
            this.exemptionEngineEnabled = exemptionEngineEnabled;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public WorldpayAuthoriseOrderRequestBuilder withMerchantCode(String merchantCode) {
            this.merchantCode = merchantCode;
            return this;
        }

        public WorldpayAuthoriseOrderRequest build() {
            return new WorldpayAuthoriseOrderRequest(transactionId, merchantCode, description, authCardDetails, amount, paymentPlatformReference, state, savePaymentInstrumentToAgreement, requires3ds, sessionId, payerIpAddress, payerEmail, agreementId, browserLanguage, integrationVersion3ds, exemptionEngineEnabled);
        }
    }
}
