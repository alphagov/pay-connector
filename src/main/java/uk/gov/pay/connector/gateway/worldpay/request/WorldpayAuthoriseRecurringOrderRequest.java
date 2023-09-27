package uk.gov.pay.connector.gateway.worldpay.request;

import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.gateway.util.AuthUtil;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;

public class WorldpayAuthoriseRecurringOrderRequest extends WorldpayOrderRequest {
    private static final TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/WorldpayAuthoriseRecurringOrderTemplate.xml");
    
    private String description;
    private String amount;
    private String paymentTokenId;
    private String schemeTransactionIdentifier;
    private String agreementId;

    public WorldpayAuthoriseRecurringOrderRequest(String transactionId,
                                                  String merchantCode,
                                                  String description,
                                                  String amount,
                                                  String paymentTokenId,
                                                  String schemeTransactionIdentifier,
                                                  String agreementId) {
        super(transactionId, merchantCode);
        this.description = description;
        this.amount = amount;
        this.paymentTokenId = paymentTokenId;
        this.schemeTransactionIdentifier = schemeTransactionIdentifier;
        this.agreementId = agreementId;
    }
    
    public static WorldpayAuthoriseRecurringOrderRequest from(RecurringPaymentAuthorisationGatewayRequest request) {
        var paymentInstrument = request.getPaymentInstrument().orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken().orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));
        
        String merchantCode = AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(),
                request.getAuthorisationMode(), request.isForRecurringPayment());
        
        return new WorldpayAuthoriseRecurringOrderRequest(
                request.getGatewayTransactionId().orElse(""),
                merchantCode,
                request.getDescription(),
                request.getAmount(),
                Optional.ofNullable(recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY)).orElse(""),
                recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY),
                request.getAgreementId());
    }

    public String getDescription() {
        return description;
    }

    public String getAmount() {
        return amount;
    }

    public String getPaymentTokenId() {
        return paymentTokenId;
    }

    public String getSchemeTransactionIdentifier() {
        return schemeTransactionIdentifier;
    }

    public String getAgreementId() {
        return agreementId;
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected TemplateBuilder getTemplateBuilder() {
        return templateBuilder;
    }
}
