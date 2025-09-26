package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import java.util.HashMap;
import java.util.Map;

public class StripePaymentMethodRequest extends StripePostRequest {
    private final AuthCardDetails authCardDetails;
    private final NorthAmericanRegionMapper northAmericanRegionMapper;

    public StripePaymentMethodRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            AuthCardDetails authCardDetails,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.authCardDetails = authCardDetails;
        this.northAmericanRegionMapper = new NorthAmericanRegionMapper();
    }

    public static StripePaymentMethodRequest of(CardAuthorisationGatewayRequest request, StripeGatewayConfig config) {
        return new StripePaymentMethodRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                config,
                request.getAuthCardDetails(),
                request.getGatewayCredentials()
        );
    }

    @Override
    protected Map<String, String> params() {
        Map<String, String> localParams = new HashMap<>();
        localParams.put("card[exp_month]", Integer.valueOf(authCardDetails.getEndDate().getTwoDigitMonth()).toString());
        localParams.put("card[exp_year]", authCardDetails.getEndDate().getTwoDigitYear());
        localParams.put("card[number]", authCardDetails.getCardNo());
        localParams.put("card[cvc]", authCardDetails.getCvc());
        localParams.put("billing_details[name]", authCardDetails.getCardHolder());
        localParams.put("type", "card");

        authCardDetails.getAddress().ifPresent(address -> {
            localParams.put("billing_details[address[line1]]", address.getLine1());
            if (StringUtils.isNotBlank(address.getLine2())) {
                localParams.put("billing_details[address[line2]]", address.getLine2());
            }
            northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                    .map(NorthAmericaRegion::getFullName)
                    .ifPresent(stateOrProvince -> localParams.put("billing_details[address[state]]", stateOrProvince));
            if (StringUtils.isNotBlank(address.getCity())) {
                localParams.put("billing_details[address[city]]", address.getCity());
            }
            if (StringUtils.isNotBlank(address.getCountry())) {
                localParams.put("billing_details[address[country]]", address.getCountry());
            }
            if (StringUtils.isNotBlank(address.getPostcode())) {
                localParams.put("billing_details[address[postal_code]]", address.getPostcode());
            }
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
