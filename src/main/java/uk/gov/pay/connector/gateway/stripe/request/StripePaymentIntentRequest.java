package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class StripePaymentIntentRequest extends StripePostRequest {

    private static final String GOVUK_PAY_TRANSACTION_EXTERNAL_ID = "govuk_pay_transaction_external_id";

    private final String amount;
    private final String paymentMethodId;
    private final String transferGroup;
    private final String frontendUrl;
    private final String chargeExternalId;
    private final String description;
    private boolean moto;
    private final String customerId;
    private boolean offSession;
    private String tokenId;

    private StripePaymentIntentRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String amount,
            String paymentMethodId,
            String transferGroup,
            String frontendUrl,
            String chargeExternalId,
            String description,
            boolean moto,
            GatewayCredentials credentials,
            String customerId,
            boolean offSession,
            String tokenId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.transferGroup = transferGroup;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
        this.description = description;
        this.moto = moto;
        this.customerId = customerId;
        this.offSession = offSession;
        this.tokenId = tokenId;
    }

    public static StripePaymentIntentRequest createOneOffPaymentIntentRequest(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getGovUkPayPaymentId(),
                frontendUrl,
                request.getGovUkPayPaymentId(),
                request.getDescription(),
                request.isMoto(),
                request.getGatewayCredentials(),
                null, 
                false,
                null);
    }

    public static StripePaymentIntentRequest createPaymentIntentRequestWithSetupFutureUsage(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            String customerId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl
    ) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getGovUkPayPaymentId(),
                frontendUrl,
                request.getGovUkPayPaymentId(),
                request.getDescription(),
                request.isMoto(),
                request.getGatewayCredentials(),
                customerId, 
                false,
                null);
    }

    public static StripePaymentIntentRequest createPaymentIntentRequestUseSavedPaymentDetails(
            RecurringPaymentAuthorisationGatewayRequest request,
            String paymentMethodId,
            String customerId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl
    ) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getGovUkPayPaymentId(),
                frontendUrl,
                request.getGovUkPayPaymentId(),
                request.getDescription(),
                false,
                request.getGatewayCredentials(),
                customerId, 
                true,
                null);
    }

    public static StripePaymentIntentRequest createPaymentIntentRequestWithToken(
            AuthorisationGatewayRequest request,
            String tokenId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId(),
                stripeGatewayConfig,
                request.getAmount(),
                null,
                request.getGovUkPayPaymentId(),
                frontendUrl,
                request.getGovUkPayPaymentId(),
                request.getDescription(),
                false,
                request.getGatewayCredentials(),
                null,
                false,
                tokenId);
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getTokenId() {
        return tokenId;
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected Map<String, String> params() {
        Map<String, String> params = new HashMap<>(Map.ofEntries(
                entry("amount", amount),
                entry("confirmation_method", "automatic"),
                entry("capture_method", "manual"),
                entry("currency", "GBP"),
                entry("description", description),
                entry("transfer_group", transferGroup),
                entry("on_behalf_of", stripeConnectAccountId),
                entry("confirm", "true"),
                entry("return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)),
                entry(String.format("metadata[%s]", GOVUK_PAY_TRANSACTION_EXTERNAL_ID), chargeExternalId)));

        if (paymentMethodId != null) {
            params.put("payment_method", paymentMethodId);
        }
        if (moto) {
            params.put("payment_method_options[card[moto]]", "true");
        }
        if (customerId != null) {
            params.put("customer", customerId);
            if (offSession) {
                params.put("off_session", "true");
            } else {
                params.put("setup_future_usage", "off_session");
            }
        }
        if (tokenId != null) {
            params.put("payment_method_data[type]", "card");
            params.put("payment_method_data[card][token]", tokenId);
        }

        return params;
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent";
    }

    @Override
    protected List<String> expansionFields() {
        return List.of("payment_method.card");
    }
    

}
