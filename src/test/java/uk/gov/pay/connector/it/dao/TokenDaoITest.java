package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.PayDBIException;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@Deprecated
public class TokenDaoITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenDao tokenDao;
    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setUp() throws Exception {
        tokenDao = new TokenDao(app.getJdbi());
        databaseTestHelper = app.getDatabaseTestHelper();
    }

    @Test
    public void shouldInsertAToken() {
        String tokenId = "tokenId";
        tokenDao.insertNewToken("12345", tokenId);

        assertThat(databaseTestHelper.getChargeTokenId("12345"), is(tokenId));
    }

    @Test
    public void shouldFindByChargeId() {

        String chargeId = "54321";
        String tokenId = "tokenB";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findByChargeId(chargeId), is(tokenId));
    }

    @Test
    public void shouldNotFindByChargeId() {
        String noExistingChargeId = "98765";
        assertThat(tokenDao.findByChargeId(noExistingChargeId), is(nullValue()));
    }

    @Test
    public void shouldDeleteATokenByTokenId() {

        String chargeId = "0987";
        String tokenId = "tokenC";
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

    @Test
    public void shouldFindChargeByTokenId() {

        String chargeId = "1111";
        String tokenId = "tokenD";
        databaseTestHelper.addToken(chargeId, tokenId);

        assertThat(tokenDao.findChargeByTokenId(tokenId).get(), is(chargeId));
    }

    @Test
    public void shouldNotFindChargeByTokenId() {
        String noExistingTokenId = "non_existing_tokenId";
        assertThat(tokenDao.findChargeByTokenId(noExistingTokenId).isPresent(), is(false));
    }
}
