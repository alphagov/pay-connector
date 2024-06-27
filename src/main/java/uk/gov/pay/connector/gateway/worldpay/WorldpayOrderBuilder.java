package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseRecurringOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;

public interface WorldpayOrderBuilder {

    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithExemptionEngine(WorldpayOrderRequestBuilder builder,
                                                                              CardAuthorisationGatewayRequest request,
                                                                              AcceptLanguageHeaderParser acceptLanguageHeaderParser) {

        return buildAuthoriseOrderWithoutExemptionEngine(builder, request, acceptLanguageHeaderParser).withExemptionEngine(true);
    }

    static WorldpayOrderRequestBuilder buildAuthoriseOrderWithoutExemptionEngine(WorldpayOrderRequestBuilder builder,
                                                                                 CardAuthorisationGatewayRequest request,
                                                                                 AcceptLanguageHeaderParser acceptLanguageHeaderParser) {

        if (request.gatewayAccount().isSendPayerIpAddressToGateway()) {
            request.authCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }

        if (request.gatewayAccount().isSendPayerEmailToGateway()) {
            Optional.ofNullable(request.email()).ifPresent(builder::withPayerEmail);
        }

        if (request.gatewayAccount().isSendReferenceToGateway()) {
            builder.withDescription(request.reference().toString());
        } else {
            builder.withDescription(request.description());
        }

        boolean is3dsRequired = request.authCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.gatewayAccount().isRequires3ds();

        return (WorldpayOrderRequestBuilder) builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.govUkPayPaymentId()))
                .with3dsRequired(is3dsRequired)
                .withSavePaymentInstrumentToAgreement(request.isSavePaymentInstrumentToAgreement())
                .withAgreementId(request.agreement().map(AgreementEntity::getExternalId).orElse(null))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.gatewayCredentials(), request.authorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.amount())
                .withAuthorisationDetails(request.authCardDetails())
                .withIntegrationVersion3ds(request.gatewayAccount().getIntegrationVersion3ds())
                .withPaymentPlatformReference(request.govUkPayPaymentId())
                .withBrowserLanguage(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(request.authCardDetails().getAcceptLanguageHeader()));
    }

    static GatewayOrder buildAuthoriseOrderWithExemptionEngine(CardAuthorisationGatewayRequest request, boolean withExemptionEngine, AcceptLanguageHeaderParser acceptLanguageHeaderParser) {
        if (withExemptionEngine) {
            return buildAuthoriseOrderWithExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request, acceptLanguageHeaderParser).build();
        } else {
            return buildAuthoriseOrderWithoutExemptionEngine(aWorldpayAuthoriseOrderRequestBuilder(), request, acceptLanguageHeaderParser).build();
        }
    }

    static GatewayOrder buildAuthoriseRecurringOrder(RecurringPaymentAuthorisationGatewayRequest request) {
        var paymentInstrument = request.getPaymentInstrument().orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken().orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));

        String merchantCode = AuthUtil.getWorldpayMerchantCode(request.gatewayCredentials(),
                request.authorisationMode(), request.isForRecurringPayment());
        WorldpayOrderRequestBuilder builder = (WorldpayOrderRequestBuilder) aWorldpayAuthoriseRecurringOrderRequestBuilder()
                .withAgreementId(request.agreementId())
                .withPaymentTokenId(Optional.ofNullable(recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY)).orElse(""))
                .withMerchantCode(merchantCode)
                .withAmount(request.amount())
                .withTransactionId(request.getGatewayTransactionId().orElse(""))
                .withDescription(request.description());

        if (recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY) != null) {
            builder.withSchemeTransactionIdentifier(recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY));
        }

        return builder.build();
    }
}
