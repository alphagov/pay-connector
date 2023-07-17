package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.it.dao.DaoITestBase;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountCredentialsDaoIT extends DaoITestBase {
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        gatewayAccountCredentialsDao = env.getInstance(GatewayAccountCredentialsDao.class);
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
    }

    @After
    public void truncate() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void hasActiveCredentialsShouldReturnTrueIfGatewayAccountHasActiveCredentials() {
        long gatewayAccountId = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
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
    public void hasActiveCredentialsShouldReturnFalseIfGatewayAccountHasNoActiveCredentials() {
        long gatewayAccountId = nextLong();
        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withState(CREATED)
                .withPaymentProvider("stripe")
                .withGatewayAccountId(gatewayAccountId)
                .build();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .withServiceName("a cool service")
                .build());

        boolean result = gatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountId);

        assertThat(result, is(false));
    }

    @Test
    public void findsCredentialByExternalIdAndGatewayAccount() {
        long gatewayAccountId = nextLong();
        String externalCredentialId = randomUuid();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
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
    public void findByCredentialsKeyValue_shouldFindGatewayAccountCredentialEntity() {
        long gatewayAccountId = nextLong();
        Map<String, Object> credMap = Map.of("some_payment_provider_account_id", "accountid");
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
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
    public void shouldPersistHistory() {
        long gatewayAccountId = nextLong();
        String externalCredentialId = randomUuid();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
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

        List<Map<String, Object>> credentialsForAccount = databaseTestHelper.getGatewayAccountCredentialsForAccount(gatewayAccountId);
        assertThat(credentialsForAccount, hasSize(2));
        long credentialsId = (long) credentialsForAccount.get(1).get("id");
        
        List<Map<String, Object>> historyRows = databaseTestHelper.getGatewayAccountCredentialsHistory(credentialsId);
        assertThat(historyRows, hasSize(1));
        assertThat(historyRows.get(0).get("state"), is("ACTIVE"));
        assertThat(historyRows.get(0).get("history_start_date"), not(nullValue()));
        assertThat(historyRows.get(0).get("history_end_date"), is(nullValue()));
        
        gatewayAccountCredentialsEntity.setState(RETIRED);
        gatewayAccountCredentialsDao.merge(gatewayAccountCredentialsEntity);

        List<Map<String, Object>> historyRowsAfterUpdate = databaseTestHelper.getGatewayAccountCredentialsHistory(credentialsId);
        assertThat(historyRowsAfterUpdate, hasSize(2));
        assertThat(historyRowsAfterUpdate.get(0).get("state"), is("ACTIVE"));
        assertThat(historyRowsAfterUpdate.get(0).get("history_start_date"), not(nullValue()));
        assertThat(historyRowsAfterUpdate.get(0).get("history_end_date"), not(nullValue()));
        assertThat(historyRowsAfterUpdate.get(1).get("state"), is("RETIRED"));
        assertThat(historyRowsAfterUpdate.get(1).get("history_start_date"), not(nullValue()));
        assertThat(historyRowsAfterUpdate.get(1).get("history_end_date"), is(nullValue()));
    }
}
