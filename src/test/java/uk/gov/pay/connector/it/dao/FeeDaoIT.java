package uk.gov.pay.connector.it.dao;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.fee.dao.FeeDao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class FeeDaoIT extends DaoITestBase {
    private FeeDao feeDao;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    @Before
    public void setUp() {
        feeDao = env.getInstance(FeeDao.class);
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

    @After
    public void truncate() {
        databaseTestHelper.truncateAllData();
    }
    
    @Test
    public void persist_shouldCreateAFeeWithNullableType() {
        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity defaultChargeTestEntity = chargeEntityFixture.build();
        long chargeId = defaultTestCharge.getChargeId();
        defaultChargeTestEntity.setId(chargeId);
        FeeEntity feeEntity = new FeeEntity(defaultChargeTestEntity, Instant.now(), 100L, null);
        feeDao.persist(feeEntity);
        List<Map<String, Object>> feesForCharge = databaseTestHelper.getFeesByChargeId(chargeId);

        assertThat(feesForCharge.size(), is(1));
        assertThat(feesForCharge.get(0).get("charge_id"), is(chargeId));
        assertThat(feesForCharge.get(0).get("fee_type"), is(nullValue()));
    }

    @Test
    public void persist_shouldCreateAFeeWithTransactionType() {
        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity defaultChargeTestEntity = chargeEntityFixture.build();
        long chargeId = defaultTestCharge.getChargeId();
        defaultChargeTestEntity.setId(chargeId);
        FeeEntity feeEntity = new FeeEntity(defaultChargeTestEntity, Instant.now(), 100L, FeeType.TRANSACTION);
        feeDao.persist(feeEntity);
        List<Map<String, Object>> feesForCharge = databaseTestHelper.getFeesByChargeId(chargeId);

        assertThat(feesForCharge.size(), is(1));
        assertThat(feesForCharge.get(0).get("charge_id"), is(chargeId));
        assertThat(feesForCharge.get(0).get("fee_type"), is(FeeType.TRANSACTION.getName()));
    }
}
