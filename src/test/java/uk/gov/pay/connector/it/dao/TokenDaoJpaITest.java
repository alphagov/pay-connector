package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.TokenJpaDao;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenDaoJpaITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenJpaDao tokenDao;
    private DatabaseTestHelper databaseTestHelper;
    private GuicedTestEnvironment env;

    @Before
    public void setUp() throws Exception {

        env = GuicedTestEnvironment
                .from(app.getPersistModule())
                .start();

        tokenDao = env.getInstance(TokenJpaDao.class);
        databaseTestHelper = app.getDatabaseTestHelper();
    }

    @After
    public void tearDown() {
        env.stop();
    }

    @Test
    public void shouldInsertAToken() {

        String tokenId = "tokenIdA";
        String chargeId = "123456";
        tokenDao.insertNewToken(chargeId, tokenId);

        assertThat(databaseTestHelper.getChargeTokenId(chargeId), is(tokenId));
    }

    @Test
    public void shouldFindByChargeId() {

        String chargeId = "10101";
        String tokenId = "tokenBB";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findByChargeId(chargeId), is(tokenId));
    }

    @Test
    public void shouldNotFindByChargeId() {
        String noExistingChargeId = "987651";
        assertThat(tokenDao.findByChargeId(noExistingChargeId), is(nullValue()));
    }

    @Test
    public void shouldFindTokenByChargeId() {

        Long chargeId = 101012L;
        String tokenId = "tokenBB2";
        databaseTestHelper.addToken(String.valueOf(chargeId), tokenId);

        Optional<TokenEntity> tokenOptional = tokenDao.findTokenByChargeId(chargeId);

        assertThat(tokenOptional.isPresent(), is(true));

        TokenEntity token = tokenOptional.get();

        assertThat(token.getId(), is(notNullValue()));
        assertThat(token.getToken(), is(tokenId));
        assertThat(token.getChargeId(), is(chargeId));
    }

    @Test
    public void shouldNotFindTokenByChargeId() {
        Long noExistingChargeId = 9876512L;
        assertThat(tokenDao.findTokenByChargeId(noExistingChargeId).isPresent(), is(false));
    }

    @Test
    public void shouldFindChargeByTokenId() {

        String chargeId = "11112";
        String tokenId = "tokenD2";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findChargeByTokenId(tokenId).get(), is(chargeId));
    }

    @Test
    public void shouldFindByTokenId() {

        String chargeId = "987654";
        String tokenId = "qwerty";
        databaseTestHelper.addToken(chargeId, tokenId);

        TokenEntity entity = tokenDao.findByTokenId(tokenId).get();

        assertThat(entity.getId(), is(notNullValue()));
        assertThat(entity.getChargeId(), is(Long.valueOf(chargeId)));
        assertThat(entity.getToken(), is(tokenId));
    }

    @Test
    public void shouldNotFindByTokenId() {

        String tokenId = "sdfgh";

        assertThat(tokenDao.findByTokenId(tokenId), is(Optional.empty()));
    }

    @Test
    public void shouldNotFindChargeByTokenId() {
        String noExistingTokenId = "non_existing_tokenId";
        assertThat(tokenDao.findChargeByTokenId(noExistingTokenId).isPresent(), is(false));
    }

    @Test
    public void shouldDeleteATokenByTokenId() {

        String chargeId = "09871";
        String tokenId = "tokenC1";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(databaseTestHelper.getChargeTokenId(chargeId), is(tokenId));

        tokenDao.deleteByTokenId(tokenId);

        assertThat(databaseTestHelper.getChargeTokenId(chargeId), is(nullValue()));
    }
}
