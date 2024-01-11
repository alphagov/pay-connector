package uk.gov.pay.connector.gateway.stripe;

import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeSdkClientTest {

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    @Mock
    private StripeAuthTokens stripeAuthTokens;

    @Mock
    private StripeSdkWrapper stripeSDKWrapper;

    @InjectMocks
    private StripeSdkClient stripeSDKClient;

    @Captor
    private ArgumentCaptor<RequestOptions> requestOptionsArgumentCaptor;

    @Captor
    ArgumentCaptor<Map<String, Object>> paramsArgumentCaptor;

    private final String TEST_API_KEY = "test-api-key";
    private final String LIVE_API_KEY = "live-api-key";
    public static final String PAYOUT_ID = "payout-id";
    public static final String STRIPE_CONNECT_ACCOUNT_ID = "stripe-account-id";
    public static final String CUSTOMER_ID = "customer-id";
    public static final String STRIPE_REFUND_ID = "a-stripe-refund-id";

    @BeforeEach
    public void setUp() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
    }

    @Test
    void getBalanceTransactionsForPayout_shouldUseTestApiKey() throws Exception {
        when(stripeAuthTokens.getTest()).thenReturn(TEST_API_KEY);
        
        stripeSDKClient.getBalanceTransactionsForPayout(PAYOUT_ID, STRIPE_CONNECT_ACCOUNT_ID, false);

        verify(stripeSDKWrapper).listBalanceTransactions(paramsArgumentCaptor.capture(), requestOptionsArgumentCaptor.capture());

        assertThat(paramsArgumentCaptor.getValue(), hasEntry("payout", PAYOUT_ID));
        RequestOptions requestOptions = requestOptionsArgumentCaptor.getValue();
        assertThat(requestOptions.getApiKey(), is(TEST_API_KEY));
        assertThat(requestOptions.getStripeAccount(), is(STRIPE_CONNECT_ACCOUNT_ID));
    }

    @Test
    void getBalanceTransactionsForPayout_shouldUseLiveApiKey() throws Exception {
        when(stripeAuthTokens.getLive()).thenReturn(LIVE_API_KEY);
        
        stripeSDKClient.getBalanceTransactionsForPayout(PAYOUT_ID, STRIPE_CONNECT_ACCOUNT_ID, true);

        verify(stripeSDKWrapper).listBalanceTransactions(paramsArgumentCaptor.capture(), requestOptionsArgumentCaptor.capture());

        assertThat(paramsArgumentCaptor.getValue(), hasEntry("payout", PAYOUT_ID));
        RequestOptions requestOptions = requestOptionsArgumentCaptor.getValue();
        assertThat(requestOptions.getApiKey(), is(LIVE_API_KEY));
        assertThat(requestOptions.getStripeAccount(), is(STRIPE_CONNECT_ACCOUNT_ID));
    }

    @Test
    void deleteCustomer_shouldUseTestApiKey() throws Exception {
        when(stripeAuthTokens.getTest()).thenReturn(TEST_API_KEY);

        stripeSDKClient.deleteCustomer(CUSTOMER_ID, false);

        verify(stripeSDKWrapper).deleteCustomer(eq(CUSTOMER_ID), requestOptionsArgumentCaptor.capture());
        assertThat(requestOptionsArgumentCaptor.getValue().getApiKey(), is(TEST_API_KEY));
    }

    @Test
    void deleteCustomer_shouldUseLiveApiKey() throws Exception {
        when(stripeAuthTokens.getLive()).thenReturn(LIVE_API_KEY);
        stripeSDKClient.deleteCustomer(CUSTOMER_ID, true);

        verify(stripeSDKWrapper).deleteCustomer(eq(CUSTOMER_ID), requestOptionsArgumentCaptor.capture());
        assertThat(requestOptionsArgumentCaptor.getValue().getApiKey(), is(LIVE_API_KEY));
    }

    @Test
    void getRefund_shouldUseLiveApiKey() throws Exception {
        when(stripeAuthTokens.getLive()).thenReturn(LIVE_API_KEY);
        stripeSDKClient.getRefund(STRIPE_REFUND_ID, true);
        
        verify(stripeSDKWrapper).getRefund(eq(STRIPE_REFUND_ID), paramsArgumentCaptor.capture(), requestOptionsArgumentCaptor.capture());

        Map<String, Object> params = paramsArgumentCaptor.getValue();
        assertThat(params, hasEntry("expand", List.of("charge")));
        assertThat(requestOptionsArgumentCaptor.getValue().getApiKey(), is(LIVE_API_KEY));
    }

    @Test
    void getRefund_shouldUseTestApiKey() throws Exception {
        when(stripeAuthTokens.getTest()).thenReturn(TEST_API_KEY);
        stripeSDKClient.getRefund(STRIPE_REFUND_ID, false);

        verify(stripeSDKWrapper).getRefund(eq(STRIPE_REFUND_ID), paramsArgumentCaptor.capture(), requestOptionsArgumentCaptor.capture());
        
        Map<String, Object> params = paramsArgumentCaptor.getValue();
        assertThat(params, hasEntry("expand", List.of("charge")));
        assertThat(requestOptionsArgumentCaptor.getValue().getApiKey(), is(TEST_API_KEY));
    }
}
