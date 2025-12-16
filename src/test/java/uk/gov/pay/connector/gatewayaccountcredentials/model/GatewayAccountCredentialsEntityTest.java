package uk.gov.pay.connector.gatewayaccountcredentials.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.SandboxCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertThat(((WorldpayCredentials) credentials).getOneOffCustomerInitiatedCredentials().isEmpty(), is(true));
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
        assertThat(worldpayCredentials.hasCredentials(), is(true));
        assertThat(worldpayCredentials.getOneOffCustomerInitiatedCredentials().isPresent(), is(true));
        assertThat(worldpayCredentials.getOneOffCustomerInitiatedCredentials().get().getMerchantCode(), is("a-merchant-code"));
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
        assertThat(stripeCredentials.hasCredentials(), is(true));
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
        assertThat(epdqCredentials.hasCredentials(), is(true));
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
        assertThat(credentials.hasCredentials(), is(true));
    }

    @Test
    void getCredentialsObject_shouldReturnSandboxCredentialsForSmartpay() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(SMARTPAY.getName())
                .build();
        GatewayCredentials credentials = credentialsEntity.getCredentialsObject();
        assertThat(credentials, isA(SandboxCredentials.class));
        assertThat(credentials.hasCredentials(), is(true));
    }

    @Test
    void getCredentialsObject_shouldThrowForUnrecognisedProvider() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("foo")
                .build();
        assertThrows(PaymentGatewayName.Unsupported.class, credentialsEntity::getCredentialsObject);
    }

    @Test
    void setCredentials_shouldSerializeWorldpayCredentialsToMapForWritingToDatabase() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        var worldpayCredentials = (WorldpayCredentials) credentialsEntity.getCredentialsObject();
        worldpayCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("one-off-merchant-code", "one-off-username", "one-off-password"));
        worldpayCredentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("cit-merchant-code", "cit-username", "cit-password"));
        worldpayCredentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials("mit-merchant-code", "mit-username", "mit-password"));
        worldpayCredentials.setGooglePayMerchantId("google-pay-merchant-id");

        credentialsEntity.setCredentials(worldpayCredentials);

        var retrivedWorldpayCredentials = (WorldpayCredentials) credentialsEntity.getCredentialsObject();

        assertThat(retrivedWorldpayCredentials.hasCredentials(), is(true));
        assertThat(retrivedWorldpayCredentials.getGooglePayMerchantId().orElse(null), is("google-pay-merchant-id"));

        assertTrue(retrivedWorldpayCredentials.getOneOffCustomerInitiatedCredentials().isPresent());
        assertThat(retrivedWorldpayCredentials.getOneOffCustomerInitiatedCredentials().get().getMerchantCode(), is("one-off-merchant-code"));
        assertThat(retrivedWorldpayCredentials.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("one-off-username"));
        assertThat(retrivedWorldpayCredentials.getOneOffCustomerInitiatedCredentials().get().getPassword(), is("one-off-password"));

        assertTrue(retrivedWorldpayCredentials.getRecurringCustomerInitiatedCredentials().isPresent());
        assertThat(retrivedWorldpayCredentials.getRecurringCustomerInitiatedCredentials().get().getMerchantCode(), is("cit-merchant-code"));
        assertThat(retrivedWorldpayCredentials.getRecurringCustomerInitiatedCredentials().get().getUsername(), is("cit-username"));
        assertThat(retrivedWorldpayCredentials.getRecurringCustomerInitiatedCredentials().get().getPassword(), is("cit-password"));

        assertTrue(retrivedWorldpayCredentials.getRecurringMerchantInitiatedCredentials().isPresent());
        assertThat(retrivedWorldpayCredentials.getRecurringMerchantInitiatedCredentials().get().getMerchantCode(), is("mit-merchant-code"));
        assertThat(retrivedWorldpayCredentials.getRecurringMerchantInitiatedCredentials().get().getUsername(), is("mit-username"));
        assertThat(retrivedWorldpayCredentials.getRecurringMerchantInitiatedCredentials().get().getPassword(), is("mit-password"));

    }

    @Test
    void setCredentials_shouldSerializeWorldpayCredentialsToMapForWritingToDatabase_shouldNotIncludeNullValues() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(WORLDPAY.getName())
                .build();

        var worldpayCredentials = (WorldpayCredentials) credentialsEntity.getCredentialsObject();
        credentialsEntity.setCredentials(worldpayCredentials);

        var retrivedWorldpayCredentials = (WorldpayCredentials) credentialsEntity.getCredentialsObject();

        assertThat(retrivedWorldpayCredentials.hasCredentials(), is(false));
        assertThat(retrivedWorldpayCredentials.getGooglePayMerchantId().isPresent(), is(false));
        assertTrue(retrivedWorldpayCredentials.getOneOffCustomerInitiatedCredentials().isEmpty());
        assertTrue(retrivedWorldpayCredentials.getRecurringCustomerInitiatedCredentials().isEmpty());
        assertTrue(retrivedWorldpayCredentials.getRecurringMerchantInitiatedCredentials().isEmpty());
    }

    @Test
    void setCredentials_shouldSerializeEpdqCredentialsToMapForWritingToDatabase() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(EPDQ.getName())
                .build();

        var epdqCredentials = (EpdqCredentials) credentialsEntity.getCredentialsObject();
        epdqCredentials.setMerchantId("a-merchant-id");
        epdqCredentials.setUsername("a-username");
        epdqCredentials.setPassword("a-password");
        epdqCredentials.setShaInPassphrase("a-sha-in-passphrase");
        epdqCredentials.setShaOutPassphrase("a-sha-out-passphrase");

        credentialsEntity.setCredentials(epdqCredentials);

        var retrivedEpdqCredentials = (EpdqCredentials) credentialsEntity.getCredentialsObject();
        assertThat(retrivedEpdqCredentials.hasCredentials(), is(true));
        assertThat(retrivedEpdqCredentials.getMerchantId(), is("a-merchant-id"));
        assertThat(retrivedEpdqCredentials.getUsername(), is("a-username"));
        assertThat(retrivedEpdqCredentials.getPassword(), is("a-password"));
        assertThat(retrivedEpdqCredentials.getShaInPassphrase(), is("a-sha-in-passphrase"));
        assertThat(retrivedEpdqCredentials.getShaOutPassphrase(), is("a-sha-out-passphrase"));
    }

    @Test
    void setCredentials_shouldSerializeStripeCredentialsToMapForWritingToDatabase() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(STRIPE.getName())
                .build();

        var stripeAccountId = "a-stripe-account-id";
        var stripeCredentials = (StripeCredentials) credentialsEntity.getCredentialsObject();
        stripeCredentials.setStripeAccountId(stripeAccountId);

        credentialsEntity.setCredentials(stripeCredentials);

        var retrivedStripeCredentials = (StripeCredentials) credentialsEntity.getCredentialsObject();
        assertThat(retrivedStripeCredentials.hasCredentials(), is(true));
        assertThat(retrivedStripeCredentials.getStripeAccountId(), is(stripeAccountId));
    }
}
