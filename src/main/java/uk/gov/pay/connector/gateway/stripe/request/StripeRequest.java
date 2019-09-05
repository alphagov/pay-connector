package uk.gov.pay.connector.gateway.stripe.request;

import com.google.common.collect.ImmutableList;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public abstract class StripeRequest implements GatewayClientRequest {

    protected GatewayAccountEntity gatewayAccount;
    private String idempotencyKey;
    protected StripeGatewayConfig stripeGatewayConfig;
    protected String stripeConnectAccountId;

    protected StripeRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig) {
        if (gatewayAccount == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest without a gateway account");
        }

        String stripeAccountId = gatewayAccount.getCredentials().get("stripe_account_id");
        if (stripeAccountId == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest with a gateway account with out a stripe account id set");
        }
        
        this.gatewayAccount = gatewayAccount;
        this.idempotencyKey = idempotencyKey;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.stripeConnectAccountId = stripeAccountId;
    }

    public final GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
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
