package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.GatewayAccountCredentialsNotConfiguredException;
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
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
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
    void getGatewayNameShouldReturnPaymentProviderOfLatestActiveCredentialIfMultipleCredentialsExists() {
        GatewayAccountCredentialsEntity latestActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .withState(ACTIVE)
                .withActiveStartDate(Instant.parse("2021-08-30T10:00:00Z"))
                .build();

        GatewayAccountCredentialsEntity earlierActiveGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(ACTIVE)
                .withActiveStartDate(Instant.parse("2021-01-30T10:00:00Z"))
                .build();

        GatewayAccountCredentialsEntity latestRetiredGatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(GatewayAccountCredentialState.RETIRED)
                .withActiveStartDate(Instant.parse("2021-09-30T10:00:00Z"))
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(latestActiveGatewayAccountCredential,
                earlierActiveGatewayAccountCredential, latestRetiredGatewayAccountCredential));

        assertThat(gatewayAccountEntity.getGatewayName(), is("stripe"));
    }

    @Test
    void getGatewayNameShouldReturnPaymentProviderOfFirstNonCreatedCredentialIfNoActiveCredentialExist() {
        GatewayAccountCredentialsEntity retiredCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .withState(RETIRED)
                .withCreatedDate(Instant.parse("2021-01-15T10:00:00Z"))
                .build();

        GatewayAccountCredentialsEntity earliestCreatedCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(CREATED)
                .withCreatedDate(Instant.parse("2021-02-28T10:00:00Z"))
                .build();

        GatewayAccountCredentialsEntity latestCreatedCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withState(GatewayAccountCredentialState.CREATED)
                .withCreatedDate(Instant.parse("2021-10-30T10:00:00Z"))
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(retiredCredential,
                earliestCreatedCredential, latestCreatedCredential));

        assertThat(gatewayAccountEntity.getGatewayName(), is("stripe"));
    }

    @Test
    void getGatewayNameShouldThrowWebApplicationExceptionWhenGatewayAccountCredentialsIsEmpty() {
        gatewayAccountEntity.setGatewayAccountCredentials(new ArrayList<>());
        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayName());
    }

    @Test
    void getGatewayNameShouldThrowGatewayAccountCredentialsNotConfiguredExceptionForGatewayAccountCredentialsInCreatedState() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));
        assertThrows(GatewayAccountCredentialsNotConfiguredException.class, () -> gatewayAccountEntity.getGatewayName());
    }

    @Test
    void getCredentialsByProviderShouldReturnCorrectCredential() {
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
    void getCredentialsByProviderShouldReturnLatestActiveCredentialIfMultipleExists() {
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
    void getCredentialsByProviderShouldThrowErrorIfNoCredentialsAreAvailable() {
        gatewayAccountEntity.setGatewayAccountCredentials(List.of());
        assertThrows(WebApplicationException.class, () -> {
            gatewayAccountEntity.getCredentials("worldpay");
        });
    }

    @Test
    void getCredentialsByProviderShouldThrowErrorIfNoCredentialsForPaymentProviderIsAvailable() {
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
    void isAllowGooglePayShouldReturnFalseIfFlagIsEnabledAndMerchantAccountIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    void isAllowGooglePayShouldReturnTrueIfFlagIsEnabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(true));
    }

    @Test
    void isAllowGooglePayShouldReturnFalseIfFlagIsDisabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setAllowGooglePay(false);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    void getGatewayMerchantIdShouldReturnIdAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(ACTIVE)
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is("some-id"));
    }

    @Test
    void getGatewayMerchantIdShouldReturnNullIfMerchantIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is(nullValue()));
    }
}
