package uk.gov.pay.connector.it.dao;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import javax.persistence.RollbackException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class IdempotencyDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app);
    private IdempotencyDao idempotencyDao;
    private GatewayAccountDao gatewayAccountDao;
    private String key = "idempotency-key";
    private String resourceExternalId = "resource-external-id";

    @BeforeEach
    void setUp() {
        idempotencyDao = app.getInstanceFromGuiceContainer(IdempotencyDao.class);
    }

    @Test
    void shouldFindExistingIdempotencyEntity() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        app.getDatabaseTestHelper().insertIdempotency(key, Long.parseLong(testBaseExtension.getAccountId()), resourceExternalId, requestBody);

        Optional<IdempotencyEntity> optionalEntity = idempotencyDao.findByGatewayAccountIdAndKey(Long.parseLong(testBaseExtension.getAccountId()), key);
        assertThat(optionalEntity.isPresent(), is(true));

        IdempotencyEntity entity = optionalEntity.get();
        assertThat(entity.getKey(), is(key));
        assertThat(entity.getGatewayAccount().getId(), is(Long.parseLong(testBaseExtension.getAccountId())));
        assertThat(entity.getResourceExternalId(), is(resourceExternalId));
        assertThat(entity.getRequestBody().get("foo"), is("bar"));
    }

    @Test
    void shouldThrowException_whenGatewayAccountIdAndKeyExists() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
        long gatewayAccountId = RandomUtils.nextLong();
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .build());
        
        IdempotencyEntity entity = new IdempotencyEntity(key, gatewayAccountDao.findById(gatewayAccountId).get(),
                resourceExternalId, requestBody, Instant.now());
        idempotencyDao.persist(entity);

        assertThrows(RollbackException.class, () -> idempotencyDao.persist(entity));
    }

    @Test
    void shouldReturnTrueIfIdempotencyExistsForResourceExternalId() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        app.getDatabaseTestHelper().insertIdempotency(key, Long.parseLong(testBaseExtension.getAccountId()), resourceExternalId, requestBody);

        boolean idempotencyExists = idempotencyDao.idempotencyExistsByResourceExternalId(resourceExternalId);
        assertThat(idempotencyExists, is(true));
    }

    @Test
    void shouldReturnFalseIfIdempotencyDoesNotExistForResourceExternalId() {
        String resourceExternalIdNotExisting = "resource-external-id-not-existing";

        boolean idempotencyExists = idempotencyDao.idempotencyExistsByResourceExternalId(resourceExternalIdNotExisting);
        assertThat(idempotencyExists, is(false));
    }

    @Test
    void shouldDeleteOnlyIdempotencyEntitiesMoreThan24HoursOld() {
        Map<String, Object> requestBody = Map.of("foo", "bar");
        String expiringKey = "expiring-idempotency-key";
        String newKey = "new-idempotency-key";
        Instant nowMinus25Hours = Instant.now().minus(25, ChronoUnit.HOURS);
        
        app.getDatabaseTestHelper().insertIdempotency(expiringKey, nowMinus25Hours, Long.parseLong(testBaseExtension.getAccountId()), resourceExternalId, requestBody);
        app.getDatabaseTestHelper().insertIdempotency(newKey, Long.parseLong(testBaseExtension.getAccountId()), resourceExternalId, requestBody);

        Instant idempotencyKeyExpiryDate = Instant.now().minus(86400, ChronoUnit.SECONDS);
        idempotencyDao.deleteIdempotencyKeysOlderThanSpecifiedDateTime(idempotencyKeyExpiryDate);
        
        Optional<IdempotencyEntity> optionalOldEntity = idempotencyDao.findByGatewayAccountIdAndKey(Long.parseLong(testBaseExtension.getAccountId()),expiringKey);
        assertThat(optionalOldEntity.isPresent(), is(false));

        Optional<IdempotencyEntity> optionalNewEntity = idempotencyDao.findByGatewayAccountIdAndKey(Long.parseLong(testBaseExtension.getAccountId()), newKey);
        assertThat(optionalNewEntity.isPresent(), is(true));
    }
}
