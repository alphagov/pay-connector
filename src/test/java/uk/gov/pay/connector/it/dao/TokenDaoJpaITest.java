package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenDaoJpaITest extends DaoITestBase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenDao tokenDao;

    private DatabaseFixtures.TestCharge defaultTestCharge;

    @Before
    public void setUp() throws Exception {
        tokenDao = env.getInstance(TokenDao.class);
        DatabaseFixtures.TestAccount defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
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
