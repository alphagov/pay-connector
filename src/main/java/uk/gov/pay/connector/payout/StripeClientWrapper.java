package uk.gov.pay.connector.payout;

import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.net.RequestOptions;

import java.util.List;
import java.util.Map;

class StripeClientWrapper {

    Iterable<BalanceTransaction> getBalanceTransactionsForPayout(String payoutId, String stripeAccountId, String apiKey) throws StripeException {
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .setStripeAccount(stripeAccountId)
                .build();

        Map<String, Object> params = Map.of(
                "payout", payoutId,
                "expand", List.of("data.source", "data.source.source_transfer"));
        
        return BalanceTransaction.list(params, requestOptions).autoPagingIterable(params, requestOptions);
    }
}
