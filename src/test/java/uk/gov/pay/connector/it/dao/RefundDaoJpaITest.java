package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;

public class RefundDaoJpaITest extends DaoITestBase {

    private RefundDao refundDao;

    private DatabaseFixtures.TestAccount sandboxAccount;
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

        this.sandboxAccount = testAccount.insert();
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
    public void findByProviderAndReference_shouldFindRefund() {
        Optional<RefundEntity> refundEntityOptional = refundDao.findByProviderAndReference(sandboxAccount.getPaymentProvider(), refundTestRecord.getReference());

        assertThat(refundEntityOptional.isPresent(), is(true));

        RefundEntity refundEntity = refundEntityOptional.get();

        assertNotNull(refundEntity.getId());
        assertThat(refundEntity.getExternalId(), is(refundTestRecord.getExternalRefundId()));
        assertThat(refundEntity.getAmount(), is(refundTestRecord.getAmount()));
        assertThat(refundEntity.getStatus(), is(refundTestRecord.getStatus()));
        assertNotNull(refundEntity.getChargeEntity());
        assertThat(refundEntity.getChargeEntity().getId(), is(refundTestRecord.getTestCharge().getChargeId()));
        assertThat(refundEntity.getCreatedDate(), is(refundTestRecord.getCreatedDate()));
        assertNotNull(refundEntity.getVersion());
    }

    @Test
    public void findByProviderAndReference_shouldNotFindRefundIfProviderDoesNotMatch() {

        Optional<RefundEntity> refundEntityOptional = refundDao.findByProviderAndReference("worldpay", refundTestRecord.getReference());

        assertThat(refundEntityOptional, is(Optional.empty()));
    }

    @Test
    public void findByProviderAndReference_shouldNotFindRefundIfReferenceDoesNotMatch() {
        String noExistingReference = "refund_0";
        assertThat(refundDao.findByProviderAndReference(sandboxAccount.getPaymentProvider(), noExistingReference).isPresent(), is(false));
    }

    @Test
         public void persist_shouldCreateARefund() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L);
        refundEntity.setStatus(RefundStatus.REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");

        refundDao.persist(refundEntity);

        assertNotNull(refundEntity.getId());

        List<Map<String, Object>> refundByIdFound = databaseTestHelper.getRefund(refundEntity.getId());

        assertThat(refundByIdFound.size(), is(1));
        assertThat(refundByIdFound, hasItems(aRefundMatching(refundEntity.getExternalId(), is("test-refund-entity"),
                refundEntity.getChargeEntity().getId(), refundEntity.getAmount(), refundEntity.getStatus().getValue())));
        assertThat(refundByIdFound.get(0), hasEntry("created_date", java.sql.Timestamp.from(refundEntity.getCreatedDate().toInstant())));
    }

    @Test
    public void persist_shouldSearchHistoryByChargeId() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L);
        refundEntity.setStatus(RefundStatus.REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");

        refundDao.persist(refundEntity);
        List<RefundHistory> refundHistoryList = refundDao.searchHistoryByChargeId(chargeEntity.getId());


        assertThat(refundHistoryList.size(), is(1));

        RefundHistory refundHistory = refundHistoryList.get(0);
        assertThat(refundHistory.getId(), is(refundEntity.getId()));
        assertThat(refundHistory.getExternalId(), is(refundEntity.getExternalId()));
        assertThat(refundHistory.getAmount(), is(refundEntity.getAmount()));
        assertThat(refundHistory.getStatus(), is(refundEntity.getStatus()));
        assertNotNull(refundHistory.getChargeEntity());
        assertThat(refundHistory.getChargeEntity().getId(), is(refundEntity.getChargeEntity().getId()));
        assertThat(refundHistory.getCreatedDate(), is(refundEntity.getCreatedDate()));
        assertThat(refundHistory.getVersion(), is(refundEntity.getVersion()));
        assertThat(refundHistory.getReference(), is(refundEntity.getReference()));
    }
}
