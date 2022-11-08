package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public interface WorldpayOrderBuilder {
    
    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithExemptionEngine(WorldpayOrderRequestBuilder builder,
                                                                              CardAuthorisationGatewayRequest request,
                                                                              AcceptLanguageHeaderParser acceptLanguageHeaderParser) {
        
        return buildAuthoriseOrderWithoutExemptionEngine(builder, request, acceptLanguageHeaderParser).withExemptionEngine(true);
    }

    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithoutExemptionEngine(WorldpayOrderRequestBuilder builder, 
                                                                                 CardAuthorisationGatewayRequest request,
                                                                                 AcceptLanguageHeaderParser acceptLanguageHeaderParser) {
        
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

        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        return (WorldpayOrderRequestBuilder) builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getGovUkPayPaymentId()))
                .with3dsRequired(is3dsRequired)
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .withIntegrationVersion3ds(request.getGatewayAccount().getIntegrationVersion3ds())
                .withBrowserLanguage(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(request.getAuthCardDetails().getAcceptLanguageHeader()));
    }
    
    static GatewayOrder buildAuthoriseOrderWithExemptionEngine(CardAuthorisationGatewayRequest request, boolean withExemptionEngine, AcceptLanguageHeaderParser acceptLanguageHeaderParser) {
        if (withExemptionEngine) {
            return buildAuthoriseOrderWithExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request, acceptLanguageHeaderParser).build();
        } else {
            return buildAuthoriseOrderWithoutExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request, acceptLanguageHeaderParser).build();
        }
    }
}
