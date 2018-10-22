package uk.gov.pay.connector.it.dao;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.FromDate;
import uk.gov.pay.connector.charge.model.ToDate;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;


public class RefundDaoJpaITest extends DaoITestBase {

    private RefundDao refundDao;
    private DatabaseFixtures.TestAccount sandboxAccount;
    private DatabaseFixtures.TestCharge chargeTestRecord;
    private DatabaseFixtures.TestRefund refundTestRecord;


    @Before
    public void setUp() {
        refundDao = env.getInstance(RefundDao.class);
        databaseTestHelper.deleteAllCardTypes();

        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount();

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURED);

        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withReference(randomAlphanumeric(10))
                .withTestCharge(testCharge);

        this.sandboxAccount = testAccount.insert();
        this.chargeTestRecord = testCharge.insert();
        this.refundTestRecord = testRefund.insert();
    }


    @Test
    public void findById_shouldFindRefund() {
        Optional<RefundEntity> refundEntityOptional = refundDao.findById(RefundEntity.class, refundTestRecord.getId());

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
        assertThat(refundDao.findById(RefundEntity.class, noExistingRefundId).isPresent(), is(false));
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
    public void findAllBy_shouldFindAllRefundsByQueryParamsWithAccountId() {
        SearchParams searchParams = new SearchParams().withGatewayAccountId(sandboxAccount.getAccountId());
        addSuccessfulRefundsToAccount("refund1", now().minusMinutes(30));
        addSuccessfulRefundsToAccount("refund2", now().minusMinutes(30));
        
        List<RefundEntity> refunds = refundDao.findAllBy(searchParams);
        assertThat(refunds.size(), is(2));
    }

    @Test
    public void findAllBy_shouldFindByDateRange() {
        FromDate fromDate = FromDate.of("2016-01-02T01:00:00Z");
        ToDate toDate = ToDate.of("2016-01-03T01:00:00Z");
        addSuccessfulRefundsToAccount("oldRefund", fromDate.getRawValue().minusMinutes(1L));
        addSuccessfulRefundsToAccount("inRangeRefund1", toDate.getRawValue().minusMinutes(1L));
        addSuccessfulRefundsToAccount("inRangeRefund2", fromDate.getRawValue().plusMinutes(1L));
        addSuccessfulRefundsToAccount("futureRefund", toDate.getRawValue().plusMinutes(1L));
        SearchParams searchParams = new SearchParams()
                .withGatewayAccountId(sandboxAccount.getAccountId())
                .withFromDate(fromDate)
                .withToDate(toDate);
        List<RefundEntity> refunds = refundDao.findAllBy(searchParams);
        assertThat(refunds.size(), is(2));
        assertThat(refunds.get(0).getExternalId(), is("inRangeRefund1"));
        assertThat(refunds.get(1).getExternalId(), is("inRangeRefund2"));
    }

    private void addSuccessfulRefundsToAccount(String externalRefundId, ZonedDateTime zonedDateTime) {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withExternalRefundId(externalRefundId)
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUNDED)
                .withCreatedDate(zonedDateTime)
                .insert();
    }

    @Test
    public void findBetweenDatesWithStatusIn_shouldFindRefundWithMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUNDED)
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<RefundStatus> statuses = Lists.newArrayList(REFUND_SUBMITTED, REFUNDED);

        List<RefundEntity> refunds = refundDao.findByAccountBetweenDatesWithStatusIn(
                sandboxAccount.getAccountId(),
                now().minusMinutes(45),
                now().minusMinutes(15),
                statuses);

        assertThat(refunds.size(), is(1));
        assertThat(refunds.get(0).getReference(), is("reference"));
    }

    @Test
    public void findBetweenDatesWithStatusIn_shouldFindNoneWithNonMatchingAccountButMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUND_SUBMITTED)
                .withCreatedDate(now().minusMinutes(30))
                .insert();

        ArrayList<RefundStatus> statuses = Lists.newArrayList(REFUND_SUBMITTED);

        List<RefundEntity> refunds = refundDao.findByAccountBetweenDatesWithStatusIn(
                100_000L,
                now().minusMinutes(45),
                now().minusMinutes(15),
                statuses);

        assertThat(refunds.size(), is(0));
    }

    @Test
    public void findBetweenDatesWithStatusIn_shouldFindNoneWithMatchingAccountButNonMatchingStatusInsideRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUNDED)
                .withCreatedDate(now().minusMinutes(20))
                .insert();

        ArrayList<RefundStatus> statuses = Lists.newArrayList(CREATED, REFUND_SUBMITTED, REFUND_ERROR);

        List<RefundEntity> refunds = refundDao.findByAccountBetweenDatesWithStatusIn(
                sandboxAccount.getAccountId(),
                now().minusMinutes(30),
                now().minusMinutes(10),
                statuses);

        assertThat(refunds.size(), is(0));
    }

    @Test
    public void findBetweenDatesWithStatusIn_shouldFindNoneWithMatchingAccountAndMatchingStatusBeforeRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUND_SUBMITTED)
                .withCreatedDate(now().minusMinutes(90))
                .insert();

        ArrayList<RefundStatus> statuses = Lists.newArrayList(REFUND_SUBMITTED, REFUNDED, REFUND_ERROR);

        List<RefundEntity> refunds = refundDao.findByAccountBetweenDatesWithStatusIn(
                sandboxAccount.getAccountId(),
                now().minusMinutes(60),
                now().minusMinutes(30),
                statuses);

        assertThat(refunds.size(), is(0));
    }

    @Test
    public void findBetweenDatesWithStatusIn_shouldFindNoneWithMatchingAccountAndMatchingStatusAfterRange() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(chargeTestRecord)
                .withReference("reference")
                .withRefundStatus(REFUNDED)
                .withCreatedDate(now().minusMinutes(15))
                .insert();

        ArrayList<RefundStatus> statuses = Lists.newArrayList(REFUND_SUBMITTED, REFUNDED, REFUND_ERROR);

        List<RefundEntity> refunds = refundDao.findByAccountBetweenDatesWithStatusIn(
                sandboxAccount.getAccountId(),
                now().minusMinutes(60),
                now().minusMinutes(30),
                statuses);

        assertThat(refunds.size(), is(0));
    }

    @Test
    public void persist_shouldCreateARefund() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L, userExternalId);
        refundEntity.setStatus(REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");

        refundDao.persist(refundEntity);

        assertNotNull(refundEntity.getId());

        List<Map<String, Object>> refundByIdFound = databaseTestHelper.getRefund(refundEntity.getId());

        assertThat(refundByIdFound.size(), is(1));
        assertThat(refundByIdFound, hasItems(aRefundMatching(refundEntity.getExternalId(), is("test-refund-entity"),
                refundEntity.getChargeEntity().getId(), refundEntity.getAmount(), refundEntity.getStatus().getValue())));
        assertThat(refundByIdFound.get(0), hasEntry("created_date", java.sql.Timestamp.from(refundEntity.getCreatedDate().toInstant())));
        assertThat(refundByIdFound.get(0), hasEntry("user_external_id", userExternalId));
    }

    @Test
    public void persist_shouldSearchHistoryByChargeId() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L, userExternalId);
        refundEntity.setStatus(REFUND_SUBMITTED);
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
        assertThat(refundEntity.getUserExternalId(), is(userExternalId));
        assertThat(refundHistory.getUserExternalId(), is(refundEntity.getUserExternalId()));
    }

    // CREATED to REFUND_SUBMITTED happens synchronously so not needed to return history for CREATED status
    // Causing issues since reference is not populated for CREATED is also being removed as is detected as
    // duplicated.
    @Test
    public void persist_shouldSearchHistoryByChargeId_IgnoringRefundStatusWithStateCreated() {
        ChargeEntity chargeEntity = new ChargeEntity();
        chargeEntity.setId(chargeTestRecord.getChargeId());

        RefundEntity refundEntity1 = new RefundEntity(chargeEntity, 100L, userExternalId);
        refundEntity1.setStatus(CREATED);

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 100L, userExternalId);
        refundEntity.setStatus(REFUND_SUBMITTED);
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
        assertThat(userExternalId, is(refundEntity.getUserExternalId()));
        assertThat(refundHistory.getUserExternalId(), is(refundEntity.getUserExternalId()));
    }
}
