package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public interface WorldpayOrderBuilder {
    
    static WorldpayOrderRequestBuilder buildAuthoriseOrder(WorldpayOrderRequestBuilder builder, 
                                                           CardAuthorisationGatewayRequest request) {

        boolean exemptionEngineEnabled = request.getGatewayAccount().getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isExemptionEngineEnabled)
                .orElse(false);

        return buildAuthoriseOrderWithoutExemptionEngine(builder, request).withExemptionEngine(exemptionEngineEnabled);
    }
    
    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithoutExemptionEngine(WorldpayOrderRequestBuilder builder, 
                                                                                 CardAuthorisationGatewayRequest request) {
        
        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            request.getAuthCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }
        
        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        return (WorldpayOrderRequestBuilder) builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .with3dsRequired(is3dsRequired)
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails());
    }
    
    static GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request, boolean withoutExemptionEngine) {
        if (withoutExemptionEngine) {
            return buildAuthoriseOrderWithoutExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request).build();
        } else {
            return buildAuthoriseOrder(aWorldpayAuthoriseOrderRequestBuilder(), request).build();
        }
    }
}
