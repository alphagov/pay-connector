package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class GatewayAccountEntityTest {

    private GatewayAccountEntity gatewayAccountEntity;
    private List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities;

    @BeforeEach
    void setUp() {
        gatewayAccountCredentialsEntities = new ArrayList<>();
        gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .build();
    }

    @Test
    void shouldReturnSandboxAsPaymentProviderNameForSingleGatewayAccountCredentialEntity() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .build();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        assertThat(gatewayAccountEntity.getGatewayName(), is("sandbox"));
    }

    @Test
    void shouldReturnStripeAsPaymentProviderNameForLatestGatewayAccountCredentialEntity() {
        GatewayAccountCredentialsEntity latestActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .withState(ACTIVE)
                .build();

        GatewayAccountCredentialsEntity earlierActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(ACTIVE)
                .build();

        GatewayAccountCredentialsEntity latestRetiredGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(GatewayAccountCredentialState.RETIRED)
                .build();

        latestActiveGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-08-04T10:00:00Z"));
        earlierActiveGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-01-03T10:00:00Z"));
        latestRetiredGatewayAccountCredential.setActiveStartDate(Instant.parse("2021-09-04T10:00:00Z"));
        gatewayAccountCredentialsEntities.add(latestActiveGatewayAccountCredential);
        gatewayAccountCredentialsEntities.add(earlierActiveGatewayAccountCredential);
        gatewayAccountCredentialsEntities.add(latestRetiredGatewayAccountCredential);
        gatewayAccountEntity.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);

        assertThat(gatewayAccountEntity.getGatewayName(), is("stripe"));
    }

    @Test
    void shouldThrowWebApplicationExceptionWhenGatewayAccountCredentialsIsEmpty() {
        gatewayAccountEntity.setGatewayAccountCredentials(new ArrayList<>());
        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayName());
    }

    @Test
    public void getCredentialsByProviderShouldReturnCorrectCredential() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe-accnt-id", "some-id"))
                .withPaymentProvider("stripe").build();
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "some-user-name"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity, credentialsEntityWorldpay));

        Map<String, String> actualCreds = gatewayAccountEntity.getCredentials("worldpay");
        assertThat(actualCreds, hasEntry("username", "some-user-name"));
    }

    @Test
    public void getCredentialsByProviderShouldReturnLatestActiveCredentialIfMultipleExists() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpayLatest = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "latest-creds-user-name"))
                .withActiveStartDate(Instant.now())
                .build();
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "old-creds-user-name"))
                .withActiveStartDate(Instant.now().minus(10, DAYS))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay, credentialsEntityWorldpayLatest));

        Map<String, String> actualCreds = gatewayAccountEntity.getCredentials("worldpay");
        assertThat(actualCreds, hasEntry("username", "latest-creds-user-name"));
    }

    @Test
    public void getCredentialsByProviderShouldThrowErrorIfNoCredentialsAreAvailable() {
        gatewayAccountEntity.setGatewayAccountCredentials(List.of());
        assertThrows(WebApplicationException.class, () -> {
            gatewayAccountEntity.getCredentials("worldpay");
        });
    }

    @Test
    public void getCredentialsByProviderShouldThrowErrorIfNoCredentialsForPaymentProviderIsAvailable() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "some-user-name"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThrows(WebApplicationException.class, () -> {
            gatewayAccountEntity.getCredentials("sandbox");
        }, "No credentials exists for payment provider");
    }

    @Test
    public void isAllowGooglePayShouldReturnFalseIfFlagIsEnabledAndMerchantAccountIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    public void isAllowGooglePayShouldReturnTrueIfFlagIsEnabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(true));
    }

    @Test
    public void isAllowGooglePayShouldReturnFalseIfFlagIsDisabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setAllowGooglePay(false);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    public void getGatewayMerchantIdShouldReturnIdAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is("some-id"));
    }

    @Test
    public void getGatewayMerchantIdShouldReturnNullIfMerchantIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is(nullValue()));
    }
}
