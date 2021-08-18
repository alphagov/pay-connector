package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
public class StripeRequestTest {
    private StripeRefundRequest stripeRefundRequest;

    @Mock
    RefundEntity refundEntity;
    @Mock
    Charge charge;

    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;

    @BeforeEach
    void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName(STRIPE.getName())
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_connect_account_id"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getGatewayTransactionId()).thenReturn("gatewayTransactionId");
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount, gatewayAccountCredentialsEntity);

        stripeRefundRequest = StripeRefundRequest.of(refundGatewayRequest, "charge_id", stripeGatewayConfig);
    }


    @Test
    void shouldSetCorrectAuthorizationHeaderForLiveAccount() {
        gatewayAccount.setType(LIVE);
        when(stripeAuthTokens.getLive()).thenReturn("live");
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer live"));
    }
    
    @Test
    void shouldSetCorrectAuthorizationHeaderForTestAccount() {
        gatewayAccount.setType(TEST);
        when(stripeAuthTokens.getTest()).thenReturn("test");
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer test"));
    }
}
