package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;

public class StripeGetPaymentIntentRequest extends StripeRequest {
    private final String paymentIntentId;

    private StripeGetPaymentIntentRequest(
            GatewayAccountEntity gatewayAccount,
            StripeGatewayConfig stripeGatewayConfig,
            Map<String, String> credentials,
            String paymentIntentId) {
        super(gatewayAccount, null, stripeGatewayConfig, credentials);
        this.paymentIntentId = paymentIntentId;
    }

    public static GatewayClientRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                request.getGatewayAccount(),
                stripeGatewayConfig,
                request.getGatewayCredentials(),
                request.getTransactionId()
        );
    }
    
    public static GatewayClientRequest of(ChargeEntity charge,
                                          StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                charge.getGatewayAccount(),
                stripeGatewayConfig,
                charge.getGatewayAccountCredentialsEntity().getCredentials(),
                charge.getGatewayTransactionId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/" + paymentIntentId;
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.QUERY;
    }
}
