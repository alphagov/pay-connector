package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripePaymentMethodRequest extends StripeRequest {
    private final AuthCardDetails authCardDetails;
    
    public StripePaymentMethodRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            AuthCardDetails authCardDetails)
    {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.authCardDetails = authCardDetails;
    }
    
    public static StripePaymentMethodRequest of(CardAuthorisationGatewayRequest request, StripeGatewayConfig config) {
        return new StripePaymentMethodRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                config,
                request.getAuthCardDetails()
        );
    }

    @Override
    protected Map<String, String> params() {
        Map<String, String> localParams = new HashMap<>();
        localParams.put("card[exp_month]", authCardDetails.expiryMonth());
        localParams.put("card[exp_year]", authCardDetails.expiryYear());
        localParams.put("card[number]", authCardDetails.getCardNo());
        localParams.put("card[cvc]", authCardDetails.getCvc());
        localParams.put("card[name]", authCardDetails.getCardHolder());
        localParams.put("type", "card");

        authCardDetails.getAddress().ifPresent(address -> {
            localParams.put("card[address_line1]", address.getLine1());
            if (StringUtils.isNotBlank(address.getLine2())) {
                localParams.put("card[address_line2]", address.getLine2());
            }
            localParams.put("card[address_city]", address.getCity());
            localParams.put("card[address_country]", address.getCountry());
            localParams.put("card[address_zip]", address.getPostcode());
        });
        
        return Map.copyOf(localParams);
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_methods";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_method";
    }
}
