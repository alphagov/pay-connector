package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseRecurringOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpaySendExemptionEngineRequest.SEND_EXEMPTION_ENGINE_REQUEST;

public interface WorldpayOrderBuilder {

    static GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request,
                                            WorldpaySendExemptionEngineRequest sendExemptionEngineRequest,
                                            AcceptLanguageHeaderParser acceptLanguageHeaderParser) {

        WorldpayOrderRequestBuilder builder = aWorldpayAuthoriseOrderRequestBuilder();

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
        
        boolean isCorporateExemptionEnabled = request.getGatewayAccount().getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isCorporateExemptionsEnabled)
                .orElse(false);

        boolean requestExemptionEngineExemption =
                switch (sendExemptionEngineRequest) {
                    case SEND_EXEMPTION_ENGINE_REQUEST -> true;
                    case DO_NOT_SEND_EXEMPTION_ENGINE_REQUEST -> false;
                };

        return builder
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getGovUkPayPaymentId()))
                .with3dsRequired(is3dsRequired)
                .withCorporateExemptionEnabled(isCorporateExemptionEnabled)
                .withExemptionEngine(requestExemptionEngineExemption)
                .withSavePaymentInstrumentToAgreement(request.isSavePaymentInstrumentToAgreement())
                .withAgreementId(request.getAgreement().map(AgreementEntity::getExternalId).orElse(null))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .withIntegrationVersion3ds(request.getGatewayAccount().getIntegrationVersion3ds())
                .withPaymentPlatformReference(request.getGovUkPayPaymentId())
                .withBrowserLanguage(acceptLanguageHeaderParser.getPreferredLanguageFromAcceptLanguageHeader(request.getAuthCardDetails().getAcceptLanguageHeader()))
                .build();
    }

    static GatewayOrder buildAuthoriseRecurringOrder(RecurringPaymentAuthorisationGatewayRequest request) {
        var paymentInstrument = request.getPaymentInstrument().orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken().orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));

        String merchantCode = AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(),
                request.getAuthorisationMode(), request.isForRecurringPayment());
        WorldpayOrderRequestBuilder builder = (WorldpayOrderRequestBuilder) aWorldpayAuthoriseRecurringOrderRequestBuilder()
                .withAgreementId(request.getAgreementId())
                .withPaymentTokenId(Optional.ofNullable(recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY)).orElse(""))
                .withMerchantCode(merchantCode)
                .withAmount(request.getAmount())
                .withTransactionId(request.getGatewayTransactionId().orElse(""))
                .withDescription(request.getDescription());

        if (recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY) != null) {
            builder.withSchemeTransactionIdentifier(recurringAuthToken.get(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY));
        }

        return builder.build();
    }
}
