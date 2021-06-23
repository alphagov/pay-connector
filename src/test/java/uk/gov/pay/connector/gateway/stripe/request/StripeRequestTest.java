package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@RunWith(MockitoJUnitRunner.class)
public class StripeRequestTest {
    private StripeRefundRequest stripeRefundRequest;
    private String chargeExternalId = "chargeExternalId";

    @Mock
    RefundEntity refundEntity;
    @Mock
    Charge charge;

    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    ChargeDao chargeDao;

    @Before
    public void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .withCredentials(Map.of("stripe_account_id", "stripe_connect_account_id"))
                .build();
        when(charge.getGatewayTransactionId()).thenReturn("gatewayTransactionId");
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getLive()).thenReturn("live");
        when(stripeAuthTokens.getTest()).thenReturn("test");
        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount);

        stripeRefundRequest = StripeRefundRequest.of(refundGatewayRequest, "charge_id", stripeGatewayConfig);
    }


    @Test
    public void shouldSetCorrectAuthorizationHeaderForLiveAccount() {
        gatewayAccount.setType(LIVE);
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer live"));
    }
    
    @Test
    public void shouldSetCorrectAuthorizationHeaderForTestAccount() {
        gatewayAccount.setType(TEST);
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer test"));
    }
}
