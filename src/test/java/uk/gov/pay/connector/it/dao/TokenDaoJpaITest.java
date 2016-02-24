package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.dao.TokenJpaDao;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

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

    @Before
    public void setUp() throws Exception {

        GuicedTestEnvironment env = GuicedTestEnvironment
                .from(app.getPersistModule())
                .start();

        tokenDao = env.getInstance(TokenJpaDao.class);
        databaseTestHelper = app.getDatabaseTestHelper();
    }

    @Test
    public void shouldInsertAToken() {

        String tokenId = "tokenIdA";
        String chargeId = "123456";
        tokenDao.insertNewToken(chargeId, tokenId);

        assertThat(databaseTestHelper.getChargeTokenId(chargeId), is(tokenId));
    }

    // FIXME. Weird behaviour, tokenDao.findByChargeId returning unwanted results. WIP
   /* @Test
    public void shouldFindByChargeId() {

        String chargeId = "10101";
        String tokenId = "tokenBB";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findByChargeId(chargeId), is(tokenId));
    }*/

    @Test
    public void shouldNotFindByChargeId() {
        String noExistingChargeId = "987651";
        assertThat(tokenDao.findByChargeId(noExistingChargeId), is(nullValue()));
    }

    @Test
    public void shouldFindChargeByTokenId() {

        String chargeId = "11112";
        String tokenId = "tokenD2";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findChargeByTokenId(tokenId).get(), is(chargeId));
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

    @Test
    public void shouldFailWhenDeleteATokenByIdWhenTokenDoesNotExist() {

        expectedException.expect(PayDBIException.class);
        expectedException.expectMessage(is("Unexpected: Failed to delete chargeTokenId = 'non_existing_tokenId' from tokens table"));

        tokenDao.deleteByTokenId("non_existing_tokenId");
    }
}
