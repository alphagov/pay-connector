package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundEntity;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;

public class RefundDaoJpaITest extends DaoITestBase {

    private RefundDao refundDao;

    private DatabaseFixtures.TestCharge chargeTestRecord;
    private DatabaseFixtures.TestRefund refundTestRecord;

    @Before
    public void setUp() throws Exception {
        refundDao = env.getInstance(RefundDao.class);

        databaseTestHelper.deleteAllCardTypes();

        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount();

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURE_SUBMITTED);

        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(testCharge);

        testAccount.insert();
        this.chargeTestRecord = testCharge.insert();
        this.refundTestRecord = testRefund.insert();
    }

    @Test
    public void findById_shouldFindRefund() {
        Optional<RefundEntity> refundEntityOptional = refundDao.findById(refundTestRecord.getId());

        assertThat(refundEntityOptional.isPresent(), is(true));

        RefundEntity refundEntity = refundEntityOptional.get();

        assertNotNull(refundEntity.getId());
        assertThat(refundEntity.getAmount(), is(refundTestRecord.getAmount()));
        assertThat(refundEntity.getStatus(), is(refundTestRecord.getStatus()));
        assertNotNull(refundEntity.getChargeEntity());
        assertThat(refundEntity.getChargeEntity().getId(), is(refundTestRecord.getTestCharge().getChargeId()));
        assertThat(refundEntity.getCreatedDate(), is(refundTestRecord.getCreatedDate()));
        assertNotNull(refundEntity.getVersion());
    }

    @Test
    public void findById_shouldNotFindRefund() {
        long noExistingRefundId = 0L;
        assertThat(refundDao.findById(noExistingRefundId).isPresent(), is(false));
    }

    @Test
    public void findByExternalId_shouldFindRefund() {
        Optional<RefundEntity> refundEntityOptional = refundDao.findByExternalId(refundTestRecord.getExternalRefundId());

        assertThat(refundEntityOptional.isPresent(), is(true));

        RefundEntity refundEntity = refundEntityOptional.get();

        assertNotNull(refundEntity.getId());
        assertThat(refundEntity.getAmount(), is(refundTestRecord.getAmount()));
        assertThat(refundEntity.getStatus(), is(refundTestRecord.getStatus()));
        assertNotNull(refundEntity.getChargeEntity());
        assertThat(refundEntity.getChargeEntity().getId(), is(refundTestRecord.getTestCharge().getChargeId()));
        assertThat(refundEntity.getCreatedDate(), is(refundTestRecord.getCreatedDate()));
        assertNotNull(refundEntity.getVersion());
    }

    @Test
    public void findByExternalId_shouldNotFindRefund() {
        String noExistingExternalRefundId = "refund_0";
        assertThat(refundDao.findByExternalId(noExistingExternalRefundId).isPresent(), is(false));
    }

    @Test
    public void persist_shouldCreateARefund() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L);

        refundDao.persist(refundEntity);

        assertNotNull(refundEntity.getId());

        java.util.List<Map<String, Object>> refundByIdFound = databaseTestHelper.getRefund(refundEntity.getId());

        assertThat(refundByIdFound, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("external_id", refundEntity.getExternalId()),
                        org.hamcrest.Matchers.hasEntry("amount", (Object) refundEntity.getAmount()),
                        org.hamcrest.Matchers.hasEntry("status", refundEntity.getStatus().toString()),
                        org.hamcrest.Matchers.hasEntry("charge_id", (Object) refundEntity.getChargeEntity().getId()),
                        org.hamcrest.Matchers.hasEntry("created_date", (Object) java.sql.Timestamp.from(refundEntity.getCreatedDate().toInstant()))
                )));
    }
}
