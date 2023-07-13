package uk.gov.pay.connector.gatewayaccountcredentials.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.SandboxCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class GatewayAccountCredentialsEntityTest {

    @Test
    void getCredentialsObject_shouldReturnEmptyWorldpayCredentials_whenCredentialsEmpty() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(WORLDPAY.getName())
                .withCredentials(Map.of())
                .build();
        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, not(nullValue()));
        assertThat(credentials, isA(WorldpayCredentials.class));
        assertThat(((WorldpayCredentials) credentials).getOneOffCustomerInitiatedCredentials(), is(nullValue()));
    }

    @Test
    void getCredentialsObject_shouldReturnPopulatedWorldpayCredentials() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(WORLDPAY.getName())
                .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of(
                        "merchant_code", "a-merchant-code",
                        "username", "a-username",
                        "password", "a-password")))
                .build();

        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, isA(WorldpayCredentials.class));
        var worldpayCredentials = (WorldpayCredentials) credentials;
        assertThat(worldpayCredentials.getOneOffCustomerInitiatedCredentials(), not(nullValue()));
        assertThat(worldpayCredentials.getOneOffCustomerInitiatedCredentials().getMerchantCode(), is("a-merchant-code"));
    }

    @Test
    void getCredentialsObject_shouldReturnEmptyStripeCredentials_whenCredentialsEmpty() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(STRIPE.getName())
                .withCredentials(Map.of())
                .build();
        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, not(nullValue()));
        assertThat(credentials, isA(StripeCredentials.class));
        assertThat(((StripeCredentials) credentials).getStripeAccountId(), is(nullValue()));
    }

    @Test
    void getCredentialsObject_shouldReturnPopulatedStripeCredentials() {
        String stripeAccountId = "a-stripe-account";
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(STRIPE.getName())
                .withCredentials(Map.of(STRIPE_ACCOUNT_ID_KEY, stripeAccountId))
                .build();

        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, isA(StripeCredentials.class));
        StripeCredentials stripeCredentials = (StripeCredentials) credentials;
        assertThat(stripeCredentials.getStripeAccountId(), is(stripeAccountId));
    }

    @Test
    void getCredentialsObject_shouldReturnEmptyEpdqCredentials_whenCredentialsEmpty() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(EPDQ.getName())
                .withCredentials(Map.of())
                .build();
        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, not(nullValue()));
        assertThat(credentials, isA(EpdqCredentials.class));
        assertThat(((EpdqCredentials) credentials).getMerchantId(), is(nullValue()));
    }

    @Test
    void getCredentialsObject_shouldReturnPopulatedEpdqCredentials() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(EPDQ.getName())
                .withCredentials(Map.of("merchant_id", "a-merchant-id"))
                .build();

        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, isA(EpdqCredentials.class));
        EpdqCredentials epdqCredentials = (EpdqCredentials) credentials;
        assertThat(epdqCredentials.getMerchantId(), is("a-merchant-id"));
    }

    @Test
    void getCredentialsObject_shouldReturnSandboxCredentials() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(SANDBOX.getName())
                .withCredentials(Map.of())
                .build();
        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, isA(SandboxCredentials.class));
    }

    @Test
    void getCredentialsObject_shouldThrowForUnrecognisedProvider() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("foo")
                .build();
        assertThrows(PaymentGatewayName.Unsupported.class, credentialsEntity::getCredentialsObject);
    }

    @Test
    void getCredentialsObject_shouldThrowForUnsupportedProvider() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(SMARTPAY.getName())
                .build();
        assertThrows(IllegalArgumentException.class, credentialsEntity::getCredentialsObject);
    }
}
