package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.charge.model.AuthMode;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public interface WorldpayOrderBuilder {
    
    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithExemptionEngine(WorldpayOrderRequestBuilder builder,
                                                                              CardAuthorisationGatewayRequest request) {
        
        return buildAuthoriseOrderWithoutExemptionEngine(builder, request).withExemptionEngine(true);
    }

    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithoutExemptionEngine(WorldpayOrderRequestBuilder builder, 
                                                                                 CardAuthorisationGatewayRequest request) {
        
        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            request.getAuthCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }

        if (request.getGatewayAccount().isSendPayerEmailToGateway()) {
            Optional.ofNullable(request.getCharge().getEmail()).ifPresent(builder::withPayerEmail);
        }
        
        builder.withSetUpPaymentInstrument(request.getCharge().isSavePaymentInstrumentToAgreement());
        
        if (request.getCharge().isSavePaymentInstrumentToAgreement()) {
            // fetch agreement or pass it down
//            builder.withAgreementDescription(request.getCharge().getAgreementId())
            builder.withPaymentInstrumentExternalId(request.getCharge().getPaymentInstrument().getExternalId());
        }
        
        if (request.getCharge().getAuthMode() == AuthMode.API) {
            var instrument = request.getCharge().getPaymentInstrument();
            builder.withAuthModeAPI(true);
            
            if (instrument != null) {
                builder.withAuthToken(instrument.getRecurringAuthToken().get("payment_token_id"));
            }
        } else {
            builder.withAuthModeAPI(false);
        }

        if (request.getGatewayAccount().isSendReferenceToGateway()) {
            builder.withDescription(request.getReference());
        } else {
            builder.withDescription(request.getDescription());
        }

        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        return (WorldpayOrderRequestBuilder) builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .with3dsRequired(is3dsRequired)
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails());
    }
    
    static GatewayOrder buildAuthoriseOrderWithExemptionEngine(CardAuthorisationGatewayRequest request, boolean withExemptionEngine) {
        if (withExemptionEngine) {
            return buildAuthoriseOrderWithExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request).build();
        } else {
            return buildAuthoriseOrderWithoutExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request).build();
        }
    }
}
