package uk.gov.pay.connector.gatewayaccount.model;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
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
    void getGatewayNameShouldThrowWebApplicationExceptionWhenGatewayAccountCredentialsIsEmpty() {
        gatewayAccountEntity.setGatewayAccountCredentials(new ArrayList<>());
        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayName());
    }

    @Test
    void isAllowGooglePayShouldReturnFalseForWorldpayAccountIfFlagIsEnabledAndMerchantAccountIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    void isAllowGooglePayShouldReturnTrueForWorldpayAccountIfFlagIsEnabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntityWithGatewayMerchantId();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(true));
    }

    @Test
    void isAllowGooglePayShouldReturnFalseForWorldpayAccountIfFlagIsDisabledAndMerchantAccountIdIsAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntityWithGatewayMerchantId();
        gatewayAccountEntity.setAllowGooglePay(false);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(false));
    }

    @Test
    void isAllowGooglePayShouldReturnTrueForStripeAccountIfFlagIsEnabled() {
        GatewayAccountCredentialsEntity credentialsEntityStripe = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("stripe")
                .build();
        gatewayAccountEntity.setAllowGooglePay(true);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityStripe));

        assertThat(gatewayAccountEntity.isAllowGooglePay(), is(true));
    }

    @Test
    void getGatewayMerchantIdShouldReturnIdAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntityWithGatewayMerchantId();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGooglePayMerchantId(), is("some-id"));
    }

    @Test
    void getGatewayMerchantIdShouldReturnNullIfMerchantIdIsNotAvailableOnCredentials() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThat(gatewayAccountEntity.getGooglePayMerchantId(), is(nullValue()));
    }

    @Test
    void getGatewayAccountCredentialsEntityByProviderShouldThrowErrorIfNoCredentialsForPaymentProviderIsAvailable() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("username", "some-user-name"))
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityWorldpay));

        assertThrows(WebApplicationException.class, () -> gatewayAccountEntity.getGatewayAccountCredentialsEntity("sandbox"), "No credentials exists for payment provider");
    }

    @Test
    void getWorldpayGatewayAccountCredentialsEntityByProviderShouldReturnCorrectCredential() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe-accnt-id", "some-id"))
                .withPaymentProvider("stripe").build();

        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "some-user-name")))
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity, credentialsEntityWorldpay));

        GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getGatewayAccountCredentialsEntity("worldpay");
        assertThat(actualCreds.getPaymentProvider(), is("worldpay"));

        var credsObj = (WorldpayCredentials) actualCreds.getCredentialsObject();
        assertThat(credsObj.getOneOffCustomerInitiatedCredentials().isPresent(), is(true));
        assertThat(credsObj.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("some-user-name"));
    }

    @Test
    void getGatewayAccountCredentialsEntityByProviderShouldReturnLatestActiveCredentialIfMultipleExists() {
        GatewayAccountCredentialsEntity credentialsEntityWorldpayLatest = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "latest-creds-user-name")))
                .withActiveStartDate(Instant.now())
                .build();
        GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "old-creds-user-name")))
                .withActiveStartDate(Instant.now().minus(10, DAYS))
                .build();
        gatewayAccountEntitySetCredentialsList(credentialsEntityWorldpay, credentialsEntityWorldpayLatest);

        GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getGatewayAccountCredentialsEntity("worldpay");
        var worldpayCreds = (WorldpayCredentials) actualCreds.getCredentialsObject();
        assertThat(worldpayCreds.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("latest-creds-user-name"));
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
                    .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "some-user-name")))
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity, credentialsEntityWorldpay));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            assertThat(actualCreds.getPaymentProvider(), is("worldpay"));
            var worldpayCreds = (WorldpayCredentials) actualCreds.getCredentialsObject();
            assertThat(worldpayCreds.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("some-user-name"));
        }

        @Test
        void shouldReturnNonRetiredCredential() {
            GatewayAccountCredentialsEntity credentialsEntityRetired = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "retired-creds-user-name")))
                    .withState(RETIRED)
                    .withCreatedDate(Instant.now())
                    .withPaymentProvider("worldpay")
                    .build();
            GatewayAccountCredentialsEntity credentialsEntityActive = aGatewayAccountCredentialsEntity()
                    .withState(ACTIVE)
                    .withCreatedDate(Instant.now().minus(1, MINUTES))
                    .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "active-creds-user-name")))
                    .withPaymentProvider("worldpay")
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityRetired, credentialsEntityActive));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            var worldpayCreds = (WorldpayCredentials) actualCreds.getCredentialsObject();
            assertThat(worldpayCreds.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("active-creds-user-name"));
            assertThat(actualCreds.getState(), is(ACTIVE));
        }

        @Test
        void shouldReturnLatestCredentialIfMultipleExistForPaymentProvider() {
            GatewayAccountCredentialsEntity credentialsEntityLatest = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "latest-creds-user-name")))
                    .withState(CREATED)
                    .withCreatedDate(Instant.now())
                    .withPaymentProvider("worldpay")
                    .build();
            GatewayAccountCredentialsEntity credentialsEntityOld = aGatewayAccountCredentialsEntityOldWithActiveState();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntityLatest, credentialsEntityOld));

            GatewayAccountCredentialsEntity actualCreds = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity("worldpay");
            var worldpayCreds = (WorldpayCredentials) actualCreds.getCredentialsObject();
            assertThat(worldpayCreds.getOneOffCustomerInitiatedCredentials().get().getUsername(), is("latest-creds-user-name"));
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

    private GatewayAccountCredentialsEntity aGatewayAccountCredentialsEntityWithGatewayMerchantId() {
        return aGatewayAccountCredentialsEntity()
                .withPaymentProvider("worldpay")
                .withCredentials(Map.of("gateway_merchant_id", "some-id"))
                .build();
    }

    private void gatewayAccountEntitySetCredentialsList(GatewayAccountCredentialsEntity... entries) {
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(entries));
    }

    private GatewayAccountCredentialsEntity aGatewayAccountCredentialsEntityOldWithActiveState() {
        return aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .withCreatedDate(Instant.now().minus(10, MINUTES))
                .withCredentials(Map.of(ONE_OFF_CUSTOMER_INITIATED, Map.of("username", "old-creds-user-name")))
                .withPaymentProvider("worldpay")
                .build();
    }
}
