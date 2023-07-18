package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;

import javax.persistence.RollbackException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

public class IdempotencyDaoIT extends DaoITestBase {
    private IdempotencyDao dao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private GatewayAccountEntity gatewayAccount;
    private String key = "idempotency-key";
    private String resourceExternalId = "resource-external-id";

    @Before
    public void setUp() {
        dao = env.getInstance(IdempotencyDao.class);
        defaultTestAccount = insertTestAccount();
        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
    }

    @Test
    public void shouldFindExistingIdempotencyEntity() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        databaseTestHelper.insertIdempotency(key, gatewayAccount.getId(), resourceExternalId, requestBody);

        Optional<IdempotencyEntity> optionalEntity = dao.findByGatewayAccountIdAndKey(gatewayAccount.getId(), key);
        assertThat(optionalEntity.isPresent(), is(true));

        IdempotencyEntity entity = optionalEntity.get();
        assertThat(entity.getKey(), is(key));
        assertThat(entity.getGatewayAccount().getId(), is(gatewayAccount.getId()));
        assertThat(entity.getResourceExternalId(), is(resourceExternalId));
        assertThat(entity.getRequestBody().get("foo"), is("bar"));
    }

    @Test
    public void shouldThrowException_whenGatewayAccountIdAndKeyExists() {
        Map<String, Object> requestBody = Map.of("foo", "bar"); 
        IdempotencyEntity entity = new IdempotencyEntity(key, gatewayAccount,
                resourceExternalId, requestBody, Instant.now());
        dao.persist(entity);

        assertThrows(RollbackException.class, () -> dao.persist(entity));
    }

    @Test
    public void shouldReturnTrueIfIdempotencyExistsForResourceExternalId() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        databaseTestHelper.insertIdempotency(key, gatewayAccount.getId(), resourceExternalId, requestBody);

        boolean idempotencyExists = dao.idempotencyExistsByResourceExternalId(resourceExternalId);
        assertThat(idempotencyExists, is(true));
    }

    @Test
    public void shouldReturnFalseIfIdempotencyDoesNotExistForResourceExternalId() {
        String resourceExternalIdNotExisting = "resource-external-id-not-existing";

        boolean idempotencyExists = dao.idempotencyExistsByResourceExternalId(resourceExternalIdNotExisting);
        assertThat(idempotencyExists, is(false));
    }

    @Test
    public void shouldDeleteOnlyIdempotencyEntitiesMoreThan24HoursOld() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        String expiringKey = "expiring-idempotency-key";
        String newKey = "new-idempotency-key";
        Instant nowMinus25Hours = Instant.now().minus(25, ChronoUnit.HOURS);
        
        databaseTestHelper.insertIdempotency(expiringKey, nowMinus25Hours, gatewayAccount.getId(), resourceExternalId, requestBody);
        databaseTestHelper.insertIdempotency(newKey, gatewayAccount.getId(), resourceExternalId, requestBody);

        Instant idempotencyKeyExpiryDate = Instant.now().minus(86400, ChronoUnit.SECONDS);
        dao.deleteIdempotencyKeysOlderThanSpecifiedDateTime(idempotencyKeyExpiryDate);
        
        Optional<IdempotencyEntity> optionalOldEntity = dao.findByGatewayAccountIdAndKey(gatewayAccount.getId(),expiringKey);
        assertThat(optionalOldEntity.isPresent(), is(false));

        Optional<IdempotencyEntity> optionalNewEntity = dao.findByGatewayAccountIdAndKey(gatewayAccount.getId(), newKey);
        assertThat(optionalNewEntity.isPresent(), is(true));
    }
    
    private DatabaseFixtures.TestAccount insertTestAccount() {
        return DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
    }

    @After
    public void clear() {
        databaseTestHelper.truncateAllData();
    }
}
