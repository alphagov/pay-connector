package uk.gov.pay.connector.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Customer;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;

import java.util.Map;

/**
 * This class is just a thin wrapper around the Stripe SDK to allow us to mock out the static stripe methods in our
 * tests. It should not contain any logic that requires unit tests.
 */
public class StripeSdkWrapper {

    Iterable<BalanceTransaction> listBalanceTransactions(Map<String, Object> params, RequestOptions requestOptions)
            throws StripeException {
        return BalanceTransaction.list(params, requestOptions).autoPagingIterable(params, requestOptions);
    }

    void deleteCustomer(String customerId, RequestOptions requestOptions) throws StripeException {
        Customer.retrieve(customerId, requestOptions).delete(requestOptions);
    }

    Refund getRefund(String stripeRefundId, Map<String, Object> params, RequestOptions requestOptions) throws StripeException {
        return Refund.retrieve(stripeRefundId, params, requestOptions);
    }

    void createTransfer(Map<String, Object> params, RequestOptions requestOptions) throws StripeException {
        Transfer.create(params, requestOptions);
    }
}
