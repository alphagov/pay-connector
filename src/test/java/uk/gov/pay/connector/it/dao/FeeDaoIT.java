package uk.gov.pay.connector.it.dao;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.fee.dao.FeeDao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class FeeDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private FeeDao feeDao;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    @BeforeEach
    void setUp() {
        feeDao = app.getInstanceFromGuiceContainer(FeeDao.class);
        DatabaseFixtures.TestAccount defaultTestAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        defaultTestCharge = app.getDatabaseFixtures()
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
    }
    
    @Test
    void persist_shouldCreateAFeeWithNullableType() {
        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity defaultChargeTestEntity = chargeEntityFixture.build();
        long chargeId = defaultTestCharge.getChargeId();
        defaultChargeTestEntity.setId(chargeId);
        FeeEntity feeEntity = new FeeEntity(defaultChargeTestEntity, Instant.now(), 100L, null);
        feeDao.persist(feeEntity);
        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(chargeId);

        assertThat(feesForCharge.size(), is(1));
        assertThat(feesForCharge.get(0).get("charge_id"), is(chargeId));
        assertThat(feesForCharge.get(0).get("fee_type"), is(nullValue()));
    }

    @Test
    void persist_shouldCreateAFeeWithTransactionType() {
        ChargeEntityFixture chargeEntityFixture = new ChargeEntityFixture();
        ChargeEntity defaultChargeTestEntity = chargeEntityFixture.build();
        long chargeId = defaultTestCharge.getChargeId();
        defaultChargeTestEntity.setId(chargeId);
        FeeEntity feeEntity = new FeeEntity(defaultChargeTestEntity, Instant.now(), 100L, FeeType.TRANSACTION);
        feeDao.persist(feeEntity);
        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(chargeId);

        assertThat(feesForCharge.size(), is(1));
        assertThat(feesForCharge.get(0).get("charge_id"), is(chargeId));
        assertThat(feesForCharge.get(0).get("fee_type"), is(FeeType.TRANSACTION.getName()));
    }
}
