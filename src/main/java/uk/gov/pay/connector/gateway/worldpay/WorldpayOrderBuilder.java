package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseRecurringOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;

public interface WorldpayOrderBuilder {

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
