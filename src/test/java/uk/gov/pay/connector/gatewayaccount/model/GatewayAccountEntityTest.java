package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
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
        gatewayAccountEntity.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);
    }

    @Test
    void getGatewayNameShouldReturnPaymentProviderOfOnlyCredentialAvailable() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .build();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        assertThat(gatewayAccountEntity.getGatewayName(), is("sandbox"));
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
    void getGatewayNameShouldReturnPaymentProviderOfFirstNonRetiredCredentialIfNoActiveCredentialExist() {
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

        assertThat(gatewayAccountEntity.getGatewayName(), is("worldpay"));
    }

    @Test
    void getGatewayNameShouldReturnPaymentProviderOfRetiredCredentialIfOnlyRetiredCredentialExist() {
        GatewayAccountCredentialsEntity retiredCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .withState(RETIRED)
                .withCreatedDate(Instant.parse("2021-01-15T10:00:00Z"))
                .build();

        GatewayAccountCredentialsEntity latestRetiredCredential = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withState(RETIRED)
                .withCreatedDate(Instant.parse("2021-02-28T10:00:00Z"))
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(retiredCredential,
                retiredCredential, latestRetiredCredential));

        assertThat(gatewayAccountEntity.getGatewayName(), is("stripe"));
    }

    @Test
    void getGatewayNameshouldThrowWebApplicationExceptionWhenGatewayAccountCredentialsIsEmpty() {
        gatewayAccountEntity.setGatewayAccountCredentials(new ArrayList<>());
        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayName());
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
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is("some-id"));
    }

    @Test
    void getGatewayMerchantIdShouldReturnNullIfMerchantIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGatewayMerchantId(), is(nullValue()));
    }

    @Test
    void getGatewayAccountCredentialsEntityByProviderShouldThrowErrorIfNoCredentialsForPaymentProviderIsAvailable() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "some-user-name"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThrows(WebApplicationException.class, () -> {
            gatewayAccountEntity.getGatewayAccountCredentialsEntity("sandbox");
        }, "No credentials exists for payment provider");
    }

    @Test
    void getGatewayAccountCredentialsEntityByProviderShouldReturnCorrectCredential() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe-accnt-id", "some-id"))
                .withPaymentProvider("stripe").build();
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "some-user-name"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity, credentialsEntityWorldpay));

        GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getGatewayAccountCredentialsEntity("worldpay");
        assertThat(actualCreds.getPaymentProvider(), is("worldpay"));
        assertThat(actualCreds.getCredentials(), hasEntry("username", "some-user-name"));
    }

    @Test
    void getGatewayAccountCredentialsEntityByProviderShouldThrowErrorIfNoCredentialsAreAvailable() {
        gatewayAccountEntity.setGatewayAccountCredentials(List.of());
        assertThrows(WebApplicationException.class, () -> {
            gatewayAccountEntity.getGatewayAccountCredentialsEntity("worldpay");
        });
    }

    @Nested
    @DisplayName("Test get latest non-retired credential")
    class TestGetRecentNonRetiredGatewayAccountCredentialsEntity {
        @Test
        void shouldReturnCorrectCredentialForPaymentProvider() {
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of("stripe-accnt-id", "some-id"))
                    .withPaymentProvider("stripe").build();
            GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider("worldpay")
                    .withCredentials(Map.of("username", "some-user-name"))
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity, credentialsEntityWorldpay));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            assertThat(actualCreds.getPaymentProvider(), is("worldpay"));
            assertThat(actualCreds.getCredentials(), hasEntry("username", "some-user-name"));
        }

        @Test
        void shouldReturnNonRetiredCredential() {
            GatewayAccountCredentialsEntity credentialsEntityRetired = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of("username", "retired-creds-user-name"))
                    .withState(RETIRED)
                    .withCreatedDate(Instant.now())
                    .withPaymentProvider("worldpay")
                    .build();
            GatewayAccountCredentialsEntity credentialsEntityActive = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withCreatedDate(Instant.now().minus(1, MINUTES))
                    .withCredentials(Map.of("username", "active-creds-user-name"))
                    .withPaymentProvider("worldpay")
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityRetired, credentialsEntityActive));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            assertThat(actualCreds.getCredentials(), hasEntry("username", "active-creds-user-name"));
            assertThat(actualCreds.getState(), is(ACTIVE));
        }

        @Test
        void shouldReturnLatestCredentialIfMultipleExistForPaymentProvider() {
            GatewayAccountCredentialsEntity credentialsEntityLatest = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of("username", "latest-creds-user-name"))
                    .withState(CREATED)
                    .withCreatedDate(Instant.now())
                    .withPaymentProvider("worldpay")
                    .build();
            GatewayAccountCredentialsEntity credentialsEntityOld = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withCreatedDate(Instant.now().minus(10, MINUTES))
                    .withCredentials(Map.of("username", "old-creds-user-name"))
                    .withPaymentProvider("worldpay")
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityLatest, credentialsEntityOld));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            assertThat(actualCreds.getCredentials(), hasEntry("username", "latest-creds-user-name"));
            assertThat(actualCreds.getState(), is(CREATED));
        }

        @Test
        void shouldThrowErrorIfNoCredentialsAreAvailable() {
            gatewayAccountEntity.setGatewayAccountCredentials(List.of());
            assertThrows(WebApplicationException.class, () -> {
                gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            });
        }

    }
}
