package uk.gov.pay.connector.gateway.worldpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.OrderRequestBuilder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.templates.PayloadBuilder;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.Optional;

public class WorldpayOrderRequestBuilder extends OrderRequestBuilder {

    private static final Logger logger = LoggerFactory.getLogger(WorldpayOrderRequestBuilder.class);

    static public class WorldpayTemplateData extends TemplateData {
        private String reference;
        private String amount;
        private LocalDate captureDate;
        private String sessionId;
        private String acceptHeader;
        private String userAgentHeader;
        private boolean requires3ds;
        private String paResponse3ds;
        private String payerIpAddress;
        private String state;
        private boolean exemptionEngineEnabled;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        @Override
        public String getAmount() {
            return amount;
        }

        @Override
        public void setAmount(String amount) {
            this.amount = amount;
        }

        public LocalDate getCaptureDate() {
            return captureDate;
        }

        public void setCaptureDate(LocalDate captureDate) {
            this.captureDate = captureDate;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(WorldpayAuthoriseOrderSessionId sessionId) {
            this.sessionId = sessionId.toString();
        }

        public String getAcceptHeader() {
            return acceptHeader;
        }

        public void setAcceptHeader(String acceptHeader) {
            this.acceptHeader = acceptHeader;
        }

        public String getUserAgentHeader() {
            return userAgentHeader;
        }

        public void setUserAgentHeader(String userAgentHeader) {
            this.userAgentHeader = userAgentHeader;
        }

        public boolean isRequires3ds() {
            return requires3ds;
        }

        public void setRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
        }

        public Optional<String> getPaResponse3ds() {
            return Optional.ofNullable(paResponse3ds);
        }

        public void setPaResponse3ds(String paResponse3ds) {
            this.paResponse3ds = paResponse3ds;
        }

        public String getPayerIpAddress() {
            return payerIpAddress;
        }

        public void setPayerIpAddress(String payerIpAddress) {
            this.payerIpAddress = payerIpAddress;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public boolean isExemptionEngineEnabled() {
            return exemptionEngineEnabled;
        }

        public void setExemptionEngineEnabled(boolean exemptionEngineEnabled) {
            this.exemptionEngineEnabled = exemptionEngineEnabled;
        }
    }

    public static final TemplateBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseOrderTemplate.xml");
    public static final TemplateBuilder AUTHORISE_APPLE_PAY_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseApplePayOrderTemplate.xml");
    public static final TemplateBuilder AUTHORISE_GOOGLE_PAY_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseGooglePayOrderTemplate.xml");
    public static final TemplateBuilder AUTH_3DS_RESPONSE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/Worldpay3dsResponseAuthOrderTemplate.xml");
    public static final TemplateBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayCaptureOrderTemplate.xml");
    public static final TemplateBuilder CANCEL_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayCancelOrderTemplate.xml");
    public static final TemplateBuilder REFUND_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayRefundOrderTemplate.xml");
    public static final TemplateBuilder INQUIRY_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayInquiryOrderTemplate.xml");

    private final WorldpayTemplateData worldpayTemplateData;
    private final NorthAmericanRegionMapper northAmericanRegionMapper;

    public static WorldpayOrderRequestBuilder aWorldpayAuthoriseOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE);
    }

    public static WorldpayOrderRequestBuilder aWorldpayAuthoriseWalletOrderRequestBuilder(WalletType walletType) {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), walletType.getWorldPayTemplate(), walletType.getOrderRequestType());
    }

    public static WorldpayOrderRequestBuilder aWorldpay3dsResponseAuthOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), AUTH_3DS_RESPONSE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE_3DS);
    }

    public static WorldpayOrderRequestBuilder aWorldpayCaptureOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), CAPTURE_ORDER_TEMPLATE_BUILDER, OrderRequestType.CAPTURE);
    }

    public static WorldpayOrderRequestBuilder aWorldpayCancelOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), CANCEL_ORDER_TEMPLATE_BUILDER, OrderRequestType.CANCEL);
    }

    public static WorldpayOrderRequestBuilder aWorldpayRefundOrderRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), REFUND_ORDER_TEMPLATE_BUILDER, OrderRequestType.REFUND);
    }

    public static WorldpayOrderRequestBuilder aWorldpayInquiryRequestBuilder() {
        return new WorldpayOrderRequestBuilder(new WorldpayTemplateData(), INQUIRY_TEMPLATE_BUILDER, OrderRequestType.QUERY);
    }

    private WorldpayOrderRequestBuilder(WorldpayTemplateData worldpayTemplateData, PayloadBuilder payloadBuilder, OrderRequestType orderRequestType) {
        super(worldpayTemplateData, payloadBuilder, orderRequestType);
        this.northAmericanRegionMapper = new NorthAmericanRegionMapper();
        this.worldpayTemplateData = worldpayTemplateData;
    }

    public WorldpayOrderRequestBuilder withReference(String reference) {
        worldpayTemplateData.setReference(reference);
        return this;
    }

    public WorldpayOrderRequestBuilder withAmount(String amount) {
        worldpayTemplateData.setAmount(amount);
        return this;
    }

    public WorldpayOrderRequestBuilder withDate(LocalDate date) {
        worldpayTemplateData.setCaptureDate(date);
        return this;
    }

    public WorldpayOrderRequestBuilder withSessionId(WorldpayAuthoriseOrderSessionId sessionId) {
        worldpayTemplateData.setSessionId(sessionId);
        return this;
    }

    public WorldpayOrderRequestBuilder withWalletTemplateData(WalletAuthorisationData walletTemplateData) {
        worldpayTemplateData.setWalletAuthorisationData(walletTemplateData);
        return this;
    }

    public WorldpayOrderRequestBuilder withAcceptHeader(String acceptHeader) {
        worldpayTemplateData.setAcceptHeader(acceptHeader);
        return this;
    }

    public WorldpayOrderRequestBuilder withUserAgentHeader(String userAgentHeader) {
        worldpayTemplateData.setUserAgentHeader(userAgentHeader);
        return this;
    }

    public WorldpayOrderRequestBuilder with3dsRequired(boolean requires3ds) {
        logger.info("3DS requirement is: " + requires3ds + " for " + worldpayTemplateData.sessionId);
        worldpayTemplateData.setRequires3ds(requires3ds);
        return this;
    }
    
    public WorldpayOrderRequestBuilder withExemptionEngine(boolean exemptionEngine) {
        worldpayTemplateData.setExemptionEngineEnabled(exemptionEngine);
        return this;
    }

    public WorldpayOrderRequestBuilder withPayerIpAddress(String payerIpAddress) {
        worldpayTemplateData.setPayerIpAddress(payerIpAddress);
        return this;
    }

    @Override
    public OrderRequestBuilder withAuthorisationDetails(AuthCardDetails authCardDetails) {
        OrderRequestBuilder orderRequestBuilder = super.withAuthorisationDetails(authCardDetails);
        authCardDetails.getAddress()
                .flatMap(northAmericanRegionMapper::getNorthAmericanRegionForCountry)
                .map(NorthAmericaRegion::getAbbreviation)
                .ifPresent(worldpayTemplateData::setState);
        return orderRequestBuilder;
    }

    public WorldpayOrderRequestBuilder withPaResponse3ds(String paResponse) {
        worldpayTemplateData.setPaResponse3ds(paResponse);
        return this;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_XML_TYPE;
    }
}
