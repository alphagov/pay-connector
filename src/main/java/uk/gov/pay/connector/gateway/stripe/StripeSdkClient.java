package uk.gov.pay.connector.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StripeSdkClient {

    private final StripeGatewayConfig stripeGatewayConfig;

    private final StripeSdkWrapper stripeSDKWrapper;

    @Inject
    public StripeSdkClient(StripeGatewayConfig stripeGatewayConfig, StripeSdkWrapper stripeSDKWrapper) {
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.stripeSDKWrapper = stripeSDKWrapper;
    }

    public Iterable<BalanceTransaction> getBalanceTransactionsForPayout(String payoutId, String stripeAccountId, boolean live) throws StripeException {
        String apiKey = getStripeApiKey(live);
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .setStripeAccount(stripeAccountId)
                .build();

        Map<String, Object> params = Map.of(
                "payout", payoutId,
                "expand", List.of("data.source", "data.source.source_transfer"));

        return stripeSDKWrapper.listBalanceTransactions(params, requestOptions);
    }

    public void deleteCustomer(String customerId, boolean live) throws StripeException {
        String apiKey = getStripeApiKey(live);
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .build();

        stripeSDKWrapper.deleteCustomer(customerId, requestOptions);
    }

    private String getStripeApiKey(boolean live) {
        return live ? stripeGatewayConfig.getAuthTokens().getLive() : stripeGatewayConfig.getAuthTokens().getTest();
    }

    public Refund getRefund(String stripeRefundId, boolean live) throws StripeException {
        String apiKey = getStripeApiKey(live);
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .build();
        var expandList = List.of("charge");
        Map<String, Object> params = new HashMap<>();
        params.put("expand", expandList);

        return stripeSDKWrapper.getRefund(stripeRefundId, params, requestOptions);
    }

    public void createTransfer(Map<String, Object> transferRequest, boolean live) throws StripeException {
        String apiKey = getStripeApiKey(live);
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .build();
        stripeSDKWrapper.createTransfer(transferRequest, requestOptions);
    }
}
