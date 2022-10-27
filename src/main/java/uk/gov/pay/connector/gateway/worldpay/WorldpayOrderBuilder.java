package uk.gov.pay.connector.gateway.worldpay;

import org.apache.commons.lang3.RandomStringUtils;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;
import java.util.UUID;

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

        builder.withSetUpPaymentInstrument(request.isSavePaymentInstrumentToAgreement());

        // at the moment the payment instrument is actually created when the authorisation is complete
        // we'll probably need an optional interface that can specify the external ID of the payment instrument IFF its set by the provider 
        // to allow this code to specify an ID that's then actually used
        // @FIXME(sfount) for now it doesn't actually matter too much as there will be no debugging on worldpay side so will just use random
        builder.withPaymentInstrumentExternalId(
                request.getPaymentInstrument()
                        .map(PaymentInstrumentEntity::getExternalId)
                        .orElse(RandomStringUtils.randomAlphanumeric(10))
        );
        builder.withAgreementId(request.getAgreementId());

        if (request.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            builder.withAuthModeAgreement(true);
            builder.withPaymentTokenId(request.getPaymentInstrument().map(paymentInstrument -> paymentInstrument.getRecurringAuthToken().get("payment_token_id")).orElse(""));
            builder.withSchemeTransactionIdentifier(request.getPaymentInstrument().map(paymentInstrumentEntity -> paymentInstrumentEntity.getRecurringAuthToken().get("scheme_transaction_identifier")).orElse(""));
        } else {
            builder.withAuthModeAgreement(false);
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
