package uk.gov.pay.connector.gateway.stripe.request;

import com.google.common.collect.ImmutableList;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public abstract class StripePostRequest implements GatewayClientPostRequest {
    
    private String idempotencyKey;
    private GatewayAccountEntity gatewayAccount;
    protected StripeGatewayConfig stripeGatewayConfig;
    protected String stripeConnectAccountId;
    protected String gatewayAccountType;
    protected StripeCredentials credentials;

    protected StripePostRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey,
                                StripeGatewayConfig stripeGatewayConfig, GatewayCredentials credentials) {
        if (gatewayAccount == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest without a gateway account");
        }

        if (!(credentials instanceof StripeCredentials)) {
            throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type StripeCredentials");
        }
        var stripeCredentials = (StripeCredentials) credentials;
        String stripeAccountId = stripeCredentials.getStripeAccountId();
        if (stripeAccountId == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest without a stripe account id in credentials");
        }
        
        this.gatewayAccount = gatewayAccount;
        this.gatewayAccountType = gatewayAccount.getType();
        this.idempotencyKey = idempotencyKey;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.stripeConnectAccountId = stripeAccountId;
        this.credentials = stripeCredentials;
    }

    @Override
    public PaymentGatewayName getPaymentProvider() {
        return STRIPE;
    }

    @Override
    public String getGatewayAccountType() {
        return gatewayAccountType;
    }

    public final URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + urlPath());
    }

    public final Map<String, String> getHeaders() {
        Map<String, String> result = new HashMap<>(Map.copyOf(headers()));
        Optional.ofNullable(idempotencyKey).ifPresent(idempotencyKey -> result.put("Idempotency-Key", idempotencyKeyType() + idempotencyKey));
        result.putAll(AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));

        return result;
    }

    public final GatewayOrder getGatewayOrder() {
        Map<String, String> params = params();
        List<BasicNameValuePair> paramsList = params.keySet().stream()
                .map(key -> new BasicNameValuePair(key, params.get(key)))
                .collect(Collectors.toUnmodifiableList());
        
        List<BasicNameValuePair> expansionList = expansionFields().stream()
                .map(fieldName -> new BasicNameValuePair("expand[]", fieldName))
                .collect(Collectors.toUnmodifiableList());

        List<BasicNameValuePair> result = new ImmutableList.Builder<BasicNameValuePair>()
                .addAll(paramsList)
                .addAll(expansionList)
                .build();
        
        String payload = URLEncodedUtils.format(result, UTF_8);

        return new GatewayOrder(orderRequestType(), payload, APPLICATION_FORM_URLENCODED_TYPE);
    }
    
    protected Map<String, String> headers() {
        return Collections.emptyMap();
    }
    
    protected List<String> expansionFields() {
        return Collections.emptyList();
    }
    
    protected  String idempotencyKeyType() {
        return orderRequestType().toString();
    }

    protected Map<String, String> params() {
        return Collections.emptyMap();
    }
    
    protected abstract String urlPath();
    protected abstract OrderRequestType orderRequestType();
}
