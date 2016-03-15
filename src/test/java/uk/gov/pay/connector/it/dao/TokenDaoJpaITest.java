package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenDaoJpaITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenDao tokenDao;
    private DatabaseTestHelper databaseTestHelper;
    private GuicedTestEnvironment env;

    @Before
    public void setUp() throws Exception {

        env = GuicedTestEnvironment
                .from(app.getPersistModule())
                .start();

        tokenDao = env.getInstance(TokenDao.class);
        databaseTestHelper = app.getDatabaseTestHelper();
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void persist_shouldInsertAToken() {

        String tokenId = "tokenIdA";
        Long chargeId = 123456L;
        tokenDao.persist(new TokenEntity(chargeId, tokenId));

        assertThat(databaseTestHelper.getChargeTokenId(chargeId), is(tokenId));
    }

    @Test
    public void findByChargeId_shouldFindToken() {

        Long chargeId = 101012L;
        String tokenId = "tokenBB2";
        databaseTestHelper.addToken(chargeId, tokenId);

        Optional<TokenEntity> tokenOptional = tokenDao.findByChargeId(chargeId);

        assertThat(tokenOptional.isPresent(), is(true));

        TokenEntity token = tokenOptional.get();

        assertThat(token.getId(), is(notNullValue()));
        assertThat(token.getToken(), is(tokenId));
        assertThat(token.getChargeId(), is(chargeId));
    }

    @Test
    public void findByChargeId_shouldNotFindToken() {
        Long noExistingChargeId = 9876512L;
        assertThat(tokenDao.findByChargeId(noExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void findByTokenId_shouldFindToken() {

        Long chargeId = 987654L;
        String tokenId = "qwerty";
        databaseTestHelper.addToken(chargeId, tokenId);

        TokenEntity entity = tokenDao.findByTokenId(tokenId).get();

        assertThat(entity.getId(), is(notNullValue()));
        assertThat(entity.getChargeId(), is(chargeId));
        assertThat(entity.getToken(), is(tokenId));
    }

    @Test
    public void findByTokenId_shouldNotFindToken() {

        String tokenId = "non_existing_tokenId";

        assertThat(tokenDao.findByTokenId(tokenId), is(Optional.empty()));
    }
}
