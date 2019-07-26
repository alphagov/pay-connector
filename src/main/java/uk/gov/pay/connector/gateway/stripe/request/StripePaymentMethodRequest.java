package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripePaymentMethodRequest extends StripeRequest {
    private String cvc;
    private String expiryMonth;
    private String expiryYear;
    private String cardNo;

    public StripePaymentMethodRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
            String cvc, String expiryMonth, String expiryYear, String cardNo)
    {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.cvc = cvc;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.cardNo = cardNo;
    }
    
    public static StripePaymentMethodRequest of(CardAuthorisationGatewayRequest request, StripeGatewayConfig config) {
        return new StripePaymentMethodRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                config,
                request.getAuthCardDetails().getCvc(),
                request.getAuthCardDetails().expiryMonth(),
                request.getAuthCardDetails().expiryYear(),
                request.getAuthCardDetails().getCardNo()
        );
    }

    @Override
    protected Map<String, String> params() {
        return Map.of(
                "card[cvc]", cvc,
                "card[exp_month]", expiryMonth,
                "card[exp_year]", expiryYear,
                "card[number]", cardNo,
                "type", "card");
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_methods";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }
}
