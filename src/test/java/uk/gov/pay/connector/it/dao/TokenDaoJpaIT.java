package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresent;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenDaoJpaIT extends DaoITestBase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private TokenDao tokenDao;
    private ChargeDao chargeDao;

    private DatabaseFixtures.TestCharge defaultTestCharge;

    @Before
    public void setUp() {
        databaseTestHelper.truncateAllData();
        tokenDao = env.getInstance(TokenDao.class);
        chargeDao = env.getInstance(ChargeDao.class);
        DatabaseFixtures.TestAccount defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    @Test
    public void persist_shouldInsertAToken() {

        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity defaultChargeTestEntity = chargeEntityFixture.build();
        defaultChargeTestEntity.setId(defaultTestCharge.getChargeId());

        TokenEntity tokenEntity = TokenEntity.generateNewTokenFor(defaultChargeTestEntity);

        tokenDao.persist(tokenEntity);

        assertThat(databaseTestHelper.getChargeTokenId(defaultChargeTestEntity.getId()), is(tokenEntity.getToken()));
    }

    @Test
    public void findByTokenId_shouldFindUnusedToken() {

        String tokenId = "qwerty";
        databaseTestHelper.addToken(defaultTestCharge.getChargeId(), tokenId);

        TokenEntity entity = tokenDao.findByTokenId(tokenId).get();

        assertThat(entity.getId(), is(notNullValue()));
        assertThat(entity.getChargeEntity().getId(), is(defaultTestCharge.getChargeId()));
        assertThat(entity.getToken(), is(tokenId));
        assertThat(entity.isUsed(), is(false));
    }

    @Test
    public void findByTokenId_shouldFindUsedToken() {

        String tokenId = "qwerty";
        databaseTestHelper.addToken(defaultTestCharge.getChargeId(), tokenId, true);

        TokenEntity entity = tokenDao.findByTokenId(tokenId).get();

        assertThat(entity.getId(), is(notNullValue()));
        assertThat(entity.getChargeEntity().getId(), is(defaultTestCharge.getChargeId()));
        assertThat(entity.getToken(), is(tokenId));
        assertThat(entity.isUsed(), is(true));
    }

    @Test
    public void findByTokenId_shouldNotFindToken() {

        String tokenId = "non_existing_tokenId";

        assertThat(tokenDao.findByTokenId(tokenId), is(Optional.empty()));
    }
    
    @Test
    public void deleteByCutOffDate_shouldDeleteOlderTokens() {
        ZonedDateTime today = ZonedDateTime.now(ZoneId.of("UTC"));

        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity chargeTestEntity = chargeEntityFixture.build();
        chargeTestEntity.setId(defaultTestCharge.getChargeId());
        
        TokenEntity presentDayToken = TokenEntity.generateNewTokenFor(chargeTestEntity);
        presentDayToken.setCreatedDate(today);
        presentDayToken.setToken("present-day-token");
        tokenDao.persist(presentDayToken);
        
        TokenEntity olderThanExpiryThreshold = TokenEntity.generateNewTokenFor(chargeTestEntity);
        olderThanExpiryThreshold.setCreatedDate(today.minusDays(8));
        olderThanExpiryThreshold.setToken("old-token");
        tokenDao.persist(olderThanExpiryThreshold);

        final ZonedDateTime expiryThreshold = today.minusDays(7);
        tokenDao.deleteTokensOlderThanSpecifiedDate(expiryThreshold);

        assertThat(tokenDao.findByTokenId("old-token"), isEmpty());
        assertThat(tokenDao.findByTokenId("present-day-token"), isPresent());
    }
}
