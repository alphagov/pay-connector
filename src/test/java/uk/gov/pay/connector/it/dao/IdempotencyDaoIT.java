package uk.gov.pay.connector.it.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.persistence.RollbackException;
import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

public class IdempotencyDaoIT extends DaoITestBase {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
    private IdempotencyDao dao;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private GatewayAccountEntity gatewayAccount;

    private String key = "idempotency-key";
    private String resourceExternalId = "resource-external-id";

    private ChargeCreateRequest newChargeCreateRequest;

    @Before
    public void setUp() {
        dao = env.getInstance(IdempotencyDao.class);
        defaultTestAccount = insertTestAccount();
        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(defaultTestAccount.getAccountId());
        newChargeCreateRequest = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withAgreementId("agreement-id")
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withReturnUrl(null)
                .build();
    }

    @Test
    public void shouldFindExistingIdempotencyEntity() {
        databaseTestHelper.insertIdempotency(key, gatewayAccount.getId(), resourceExternalId, mapper.convertValue(newChargeCreateRequest, new TypeReference<>() {}));

        Optional<IdempotencyEntity> optionalEntity = dao.findByGatewayAccountIdAndKey(gatewayAccount.getId(), key);
        assertThat(optionalEntity.isPresent(), is(true));

        IdempotencyEntity entity = optionalEntity.get();
        assertThat(entity.getKey(), is(key));
        assertThat(entity.getGatewayAccount().getId(), is(gatewayAccount.getId()));
        assertThat(entity.getResourceExternalId(), is(resourceExternalId));
        assertThat(entity.getRequestBody().get("amount"), is(100));
    }

    @Test
    public void shouldThrowException_whenGatewayAccountIdAndKeyExists() {
        IdempotencyEntity entity = IdempotencyEntity.from(key, newChargeCreateRequest, gatewayAccount, resourceExternalId);
        dao.persist(entity);

        assertThrows(RollbackException.class, () -> dao.persist(entity));
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