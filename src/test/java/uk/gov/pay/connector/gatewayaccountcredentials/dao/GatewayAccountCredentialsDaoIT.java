package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials.DELETED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountCredentialsDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private ObjectMapper objectMapper = new ObjectMapper();
    private GatewayAccountDao gatewayAccountDao;

    @BeforeEach
    void setUp() {
        gatewayAccountCredentialsDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsDao.class);
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
    }

    @Test
    void hasActiveCredentialsShouldReturnTrueIfGatewayAccountHasActiveCredentials() {
        long gatewayAccountId = secureRandomLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());
        var gatewayAccountEntity = gatewayAccountDao.findById(gatewayAccountId).get();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountCredentialsEntity.setExternalId(randomUuid());
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);

        boolean result = gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountId);

        assertThat(result, is(true));
    }

    @Test
    void hasActiveCredentialsShouldReturnFalseIfGatewayAccountHasNoActiveCredentials() {
        long gatewayAccountId = secureRandomLong();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withState(CREATED)
                .withPaymentProvider("stripe")
                .withGatewayAccountId(gatewayAccountId)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .withServiceName("a cool service")
                .build());

        boolean result = gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountId);

        assertThat(result, is(false));
    }

    @Test
    void findsCredentialByExternalIdAndGatewayAccount() {
        long gatewayAccountId = secureRandomLong();
        String externalCredentialId = randomUuid();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());
        var gatewayAccountEntity = gatewayAccountDao.findById(gatewayAccountId).get();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountCredentialsEntity.setExternalId(externalCredentialId);
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);

        Optional<GatewayAccountCredentialsEntity> optionalEntity =
                gatewayAccountCredentialsDao.findByExternalIdAndGatewayAccountId(externalCredentialId, gatewayAccountId);

        assertThat(optionalEntity.isPresent(), is(true));
        assertThat(optionalEntity.get().getExternalId(), is(externalCredentialId));
    }

    @Test
    void findByCredentialsKeyValue_shouldFindGatewayAccountCredentialEntity() {
        long gatewayAccountId = secureRandomLong();
        Map<String, Object> credMap = Map.of("some_payment_provider_account_id", "accountid");
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("test provider")
                .withServiceName("service name")
                .withCredentials(credMap)
                .build());

        var gatewayAccountEntity = gatewayAccountDao.findById(gatewayAccountId).get();
        var gatewayAccountCredentialsEntityOne = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider("test provider")
                .withCredentials(credMap)
                .build();
        var gatewayAccountCredentialsEntityTwo = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider("test provider")
                .build();
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntityOne);
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntityTwo);

        Optional<GatewayAccountCredentialsEntity> maybeGatewayAccountCredentials = gatewayAccountCredentialsDao.findByCredentialsKeyValue("some_payment_provider_account_id", "accountid");
        assertThat(maybeGatewayAccountCredentials.isPresent(), is(true));
        Map<String, Object> credentialsMap = maybeGatewayAccountCredentials.get().getCredentials();
        assertThat(credentialsMap, hasEntry("some_payment_provider_account_id", "accountid"));
    }

    @Test
    void shouldPersistHistory() throws Exception {
        GatewayAccountEntity gatewayAccountEntity = createAndPersistAGatewayAccount();

        Map<String, Object> credentials = Map.of(
                ONE_OFF_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-1", CREDENTIALS_USERNAME, "a-merchant-code-1", CREDENTIALS_PASSWORD, "passw0rd1"),
                RECURRING_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-2", CREDENTIALS_USERNAME, "a-merchant-code-2", CREDENTIALS_PASSWORD, "passw0rd1"),
                RECURRING_MERCHANT_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-3", CREDENTIALS_USERNAME, "a-merchant-code-3", CREDENTIALS_PASSWORD, "passw0rd1")
        );

        var gatewayAccountCredentialsEntity = createAndPersistAGatewayAccountCredentialsEntity(gatewayAccountEntity, credentials);

        List<Map<String, Object>> credentialsForAccount = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(gatewayAccountEntity.getId());
        assertThat(credentialsForAccount, hasSize(2));
        long credentialsId = (long) credentialsForAccount.get(1).get("id");

        List<Map<String, Object>> historyRows = app.getDatabaseTestHelper().getGatewayAccountCredentialsHistory(credentialsId);
        assertThat(historyRows, hasSize(1));
        assertThat(historyRows.get(0).get("state"), is("ACTIVE"));
        assertThat(historyRows.get(0).get("history_start_date"), not(nullValue()));
        assertThat(historyRows.get(0).get("history_end_date"), is(nullValue()));
        assertEquals(objectMapper.readValue(historyRows.get(0).get("credentials").toString(), Map.class), credentials);

        gatewayAccountCredentialsEntity.setState(RETIRED);
        WorldpayCredentials worldpayCredentials = (WorldpayCredentials) gatewayAccountCredentialsEntity.getCredentialsObject();
        worldpayCredentials.getRecurringCustomerInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
        worldpayCredentials.getOneOffCustomerInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
        worldpayCredentials.getRecurringMerchantInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
        gatewayAccountCredentialsEntity.setCredentials(worldpayCredentials);

        gatewayAccountCredentialsDao.merge(gatewayAccountCredentialsEntity);

        List<Map<String, Object>> historyRowsAfterUpdate = app.getDatabaseTestHelper().getGatewayAccountCredentialsHistory(credentialsId);
        assertThat(historyRowsAfterUpdate, hasSize(2));
        assertThat(historyRowsAfterUpdate.get(0).get("state"), is("ACTIVE"));
        assertThat(historyRowsAfterUpdate.get(0).get("history_start_date"), not(nullValue()));
        assertThat(historyRowsAfterUpdate.get(0).get("history_end_date"), not(nullValue()));
        assertEquals(objectMapper.readValue(historyRowsAfterUpdate.get(0).get("credentials").toString(), Map.class), credentials);
        assertThat(historyRowsAfterUpdate.get(1).get("state"), is("RETIRED"));
        assertThat(historyRowsAfterUpdate.get(1).get("history_start_date"), not(nullValue()));
        assertThat(historyRowsAfterUpdate.get(1).get("history_end_date"), is(nullValue()));
        Map<String, Object> expectedCredentials = Map.of(
                ONE_OFF_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-1", CREDENTIALS_USERNAME, DELETED, CREDENTIALS_PASSWORD, DELETED),
                RECURRING_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-2", CREDENTIALS_USERNAME, DELETED, CREDENTIALS_PASSWORD, DELETED),
                RECURRING_MERCHANT_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-3", CREDENTIALS_USERNAME, DELETED, CREDENTIALS_PASSWORD, DELETED)
        );
        assertEquals(objectMapper.readValue(historyRowsAfterUpdate.get(1).get("credentials").toString(), Map.class), expectedCredentials);
    }

    private GatewayAccountCredentialsEntity createAndPersistAGatewayAccountCredentialsEntity(
            GatewayAccountEntity gatewayAccountEntity, Map<String, Object> credentials) {
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountCredentialsEntity.setExternalId(randomUuid());
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);
        return gatewayAccountCredentialsEntity;
    }

    private GatewayAccountEntity createAndPersistAGatewayAccount() {
        long gatewayAccountId = secureRandomLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .build());
        return gatewayAccountDao.findById(gatewayAccountId).get();
    }
}
