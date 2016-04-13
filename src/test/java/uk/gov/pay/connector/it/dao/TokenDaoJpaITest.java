package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenDaoJpaITest {

    private static final Long GATEWAY_ACCOUNT_ID = 564532435L;
    private static final String RETURN_URL = "http://service.com/success-page";
    private static final String REFERENCE = "Test reference";
    private static final Long AMOUNT = 101L;
    private static final Long CHARGE_ID = 977L;
    private static final String EXTERNAL_CHARGE_ID = "charge977";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenDao tokenDao;
    private ChargeDao chargeDao;
    private DatabaseTestHelper databaseTestHelper;
    private GuicedTestEnvironment env;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    @Before
    public void setUp() throws Exception {

        env = GuicedTestEnvironment
                .from(app.getPersistModule())
                .start();

        tokenDao = env.getInstance(TokenDao.class);
        chargeDao = env.getInstance(ChargeDao.class);
        databaseTestHelper = app.getDatabaseTestHelper();

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .insert();

        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void persist_shouldInsertAToken() {

        ChargeEntity defaultChargeTestEntity = new ChargeEntity();
        defaultChargeTestEntity.setId(defaultTestCharge.getChargeId());

        TokenEntity tokenEntity = TokenEntity.generateNewTokenFor(defaultChargeTestEntity);

        tokenDao.persist(tokenEntity);

        assertThat(databaseTestHelper.getChargeTokenId(defaultChargeTestEntity.getId()), is(tokenEntity.getToken()));
    }

    @Test
    public void findByChargeId_shouldFindToken() {

        String tokenId = "tokenBB2";
        databaseTestHelper.addToken(defaultTestCharge.getChargeId(), tokenId);

        Optional<TokenEntity> tokenOptional = tokenDao.findByChargeId(defaultTestCharge.getChargeId());

        assertThat(tokenOptional.isPresent(), is(true));

        TokenEntity token = tokenOptional.get();

        assertThat(token.getId(), is(notNullValue()));
        assertThat(token.getToken(), is(tokenId));
        assertThat(token.getChargeEntity().getId(), is(defaultTestCharge.getChargeId()));
    }

    @Test
    public void findByChargeId_shouldNotFindToken() {
        Long noExistingChargeId = 9876512L;
        assertThat(tokenDao.findByChargeId(noExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void findByTokenId_shouldFindToken() {

        String tokenId = "qwerty";
        databaseTestHelper.addToken(defaultTestCharge.getChargeId(), tokenId);

        TokenEntity entity = tokenDao.findByTokenId(tokenId).get();

        assertThat(entity.getId(), is(notNullValue()));
        assertThat(entity.getChargeEntity().getId(), is(defaultTestCharge.getChargeId()));
        assertThat(entity.getToken(), is(tokenId));
    }

    @Test
    public void findByTokenId_shouldNotFindToken() {

        String tokenId = "non_existing_tokenId";

        assertThat(tokenDao.findByTokenId(tokenId), is(Optional.empty()));
    }
}
