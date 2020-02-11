package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;
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
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.it.resources.ChargeEventsResourceIT.SUBMITTED_BY;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;


public class RefundDaoJpaIT extends DaoITestBase {

    private RefundDao refundDao;
    private ChargeDao chargeDao;
    private DatabaseFixtures.TestAccount sandboxAccount;
    private DatabaseFixtures.TestCharge chargeTestRecord;
    private DatabaseFixtures.TestRefund refundTestRecord;

    @Before
    public void setUp() throws Exception {
        setup();
        refundDao = env.getInstance(RefundDao.class);
        chargeDao = env.getInstance(ChargeDao.class);
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
                .withGatewayTransactionId(randomAlphanumeric(10))
                .withChargeExternalId(testCharge.getExternalChargeId())
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
        assertThat(refundEntity.getGatewayTransactionId(), is(refundTestRecord.getGatewayTransactionId()));
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
        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());
        refundEntity.setStatus(REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");
        refundEntity.setChargeExternalId(chargeTestRecord.getExternalChargeId());
        refundEntity.setChargeId(chargeTestRecord.getChargeId());

        refundDao.persist(refundEntity);

        assertNotNull(refundEntity.getId());

        List<Map<String, Object>> refundByIdFound = databaseTestHelper.getRefund(refundEntity.getId());

        assertThat(refundByIdFound.size(), is(1));
        assertThat(refundByIdFound, hasItems(aRefundMatching(refundEntity.getExternalId(), is("test-refund-entity"),
                refundEntity.getChargeExternalId(), refundEntity.getAmount(), refundEntity.getStatus().getValue())));
        assertThat(refundByIdFound.get(0), hasEntry("created_date", java.sql.Timestamp.from(refundEntity.getCreatedDate().toInstant())));
        assertThat(refundByIdFound.get(0), hasEntry("user_external_id", userExternalId));
        assertThat(refundByIdFound.get(0), hasEntry("user_email", userEmail));
        assertThat(refundByIdFound.get(0), hasEntry("charge_external_id", chargeTestRecord.getExternalChargeId()));
    }

    @Test
    public void persist_shouldSearchHistoryByChargeExternalId() {
        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());
        refundEntity.setStatus(REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");
        refundEntity.setGatewayTransactionId(randomAlphanumeric(10));
        refundEntity.setChargeId(chargeTestRecord.getChargeId());

        refundDao.persist(refundEntity);
        List<RefundHistory> refundHistoryList = refundDao.searchHistoryByChargeExternalId(chargeTestRecord.getExternalChargeId());

        assertThat(refundHistoryList.size(), is(1));

        RefundHistory refundHistory = refundHistoryList.get(0);
        assertThat(refundHistory.getId(), is(refundEntity.getId()));
        assertThat(refundHistory.getExternalId(), is(refundEntity.getExternalId()));
        assertThat(refundHistory.getAmount(), is(refundEntity.getAmount()));
        assertThat(refundHistory.getStatus(), is(refundEntity.getStatus()));
        assertThat(refundHistory.getUserEmail(), is(refundEntity.getUserEmail()));
        assertThat(refundHistory.getCreatedDate(), is(refundEntity.getCreatedDate()));
        assertThat(refundHistory.getVersion(), is(refundEntity.getVersion()));
        assertThat(refundHistory.getReference(), is(refundEntity.getReference()));
        assertThat(refundEntity.getUserExternalId(), is(userExternalId));
        assertThat(refundEntity.getUserEmail(), is(refundEntity.getUserEmail()));
        assertThat(refundHistory.getUserExternalId(), is(refundEntity.getUserExternalId()));
        assertThat(refundHistory.getGatewayTransactionId(), is(refundEntity.getGatewayTransactionId()));
        assertThat(refundHistory.getChargeExternalId(), is(chargeTestRecord.getExternalChargeId()));
    }

    @Test
    public void shouldSearchAllHistoryStatusTypesByChargeId() {
        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());

        refundEntity.setReference("test-refund-entity");
        refundEntity.setStatus(CREATED);
        refundEntity.setChargeId(chargeTestRecord.getChargeId());
        refundDao.persist(refundEntity);

        refundEntity.setStatus(REFUND_SUBMITTED);
        refundDao.merge(refundEntity);

        List<RefundHistory> refundHistoryList = refundDao.searchAllHistoryByChargeExternalId(chargeTestRecord.getExternalChargeId());

        assertThat(refundHistoryList.size(), is(2));
    }

    // CREATED to REFUND_SUBMITTED happens synchronously so not needed to return history for CREATED status
    // Causing issues since reference is not populated for CREATED is also being removed as is detected as
    // duplicated.
    @Test
    public void persist_shouldSearchHistoryByChargeId_IgnoringRefundStatusWithStateCreated() {
        RefundEntity refundEntity1 = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());
        refundEntity1.setStatus(CREATED);
        refundEntity1.setChargeId(chargeTestRecord.getChargeId());
        refundDao.persist(refundEntity1);

        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());
        refundEntity.setStatus(REFUND_SUBMITTED);
        refundEntity.setReference("test-refund-entity");
        refundEntity.setChargeId(chargeTestRecord.getChargeId());
        refundDao.persist(refundEntity);

        List<RefundHistory> refundHistoryList = refundDao.searchHistoryByChargeExternalId(chargeTestRecord.getExternalChargeId());

        assertThat(refundHistoryList.size(), is(1));

        RefundHistory refundHistory = refundHistoryList.get(0);
        assertThat(refundHistory.getId(), is(refundEntity.getId()));
        assertThat(refundHistory.getExternalId(), is(refundEntity.getExternalId()));
        assertThat(refundHistory.getAmount(), is(refundEntity.getAmount()));
        assertThat(refundHistory.getStatus(), is(refundEntity.getStatus()));
        assertThat(refundHistory.getCreatedDate(), is(refundEntity.getCreatedDate()));
        assertThat(refundHistory.getVersion(), is(refundEntity.getVersion()));
        assertThat(refundHistory.getReference(), is(refundEntity.getReference()));
        assertThat(userExternalId, is(refundEntity.getUserExternalId()));
        assertThat(refundHistory.getUserEmail(), is(refundEntity.getUserEmail()));
        assertThat(refundHistory.getUserExternalId(), is(refundEntity.getUserExternalId()));
    }

    @Test
    public void getRefundHistoryByRefundExternalIdAndRefundStatus_shouldReturnResultCorrectly() {
        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeTestRecord.getExternalChargeId());
        refundEntity.setStatus(CREATED);
        refundEntity.setReference("test-refund-entity");
        refundEntity.setGatewayTransactionId(randomAlphanumeric(10));
        refundEntity.setChargeId(chargeTestRecord.getChargeId());

        refundDao.persist(refundEntity);
        Optional<RefundHistory> mayBeRefundHistory = refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundEntity.getExternalId(), CREATED);

        RefundHistory refundHistory = mayBeRefundHistory.get();

        assertThat(refundHistory.getId(), is(refundEntity.getId()));
        assertThat(refundHistory.getExternalId(), is(refundEntity.getExternalId()));
        assertThat(refundHistory.getAmount(), is(refundEntity.getAmount()));
        assertThat(refundHistory.getStatus(), is(refundEntity.getStatus()));
        assertThat(refundHistory.getCreatedDate(), is(refundEntity.getCreatedDate()));
        assertThat(refundHistory.getVersion(), is(refundEntity.getVersion()));
        assertThat(refundHistory.getReference(), is(refundEntity.getReference()));
        assertThat(refundHistory.getUserExternalId(), is(refundEntity.getUserExternalId()));
        assertThat(refundHistory.getUserEmail(), is(refundEntity.getUserEmail()));
        assertThat(refundHistory.getGatewayTransactionId(), is(refundEntity.getGatewayTransactionId()));
    }

    @Test
    public void getRefundHistoryByDateRangeShouldReturnResultCorrectly() {

        ZonedDateTime historyDate = ZonedDateTime.parse("2016-01-01T00:00:00Z");

        DatabaseFixtures.TestAccount testAccount = withDatabaseTestHelper(databaseTestHelper).aTestAccount().insert();
        DatabaseFixtures.TestCharge testCharge = withDatabaseTestHelper(databaseTestHelper).aTestCharge().withTestAccount(testAccount).insert();
        DatabaseFixtures.TestRefund testRefund = withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(testCharge)
                .withType(REFUNDED)
                .withCreatedDate(now())
                .withUserEmail(userEmail)
                .withChargeExternalId(testCharge.getExternalChargeId())
                .insert();

        withDatabaseTestHelper(databaseTestHelper)
                .aTestRefundHistory(testRefund)
                .insert(REFUND_SUBMITTED, "ref-2", historyDate.plusMinutes(10), historyDate.plusMinutes(10), SUBMITTED_BY, userEmail)
                .insert(CREATED, "ref-1",  historyDate, historyDate, SUBMITTED_BY, userEmail)
                .insert(REFUNDED, "history-tobe-excluded", historyDate.minusDays(10), historyDate.minusDays(10))
                .insert(REFUNDED, "history-tobe-excluded", historyDate.plusHours(1), historyDate.plusHours(1), SUBMITTED_BY, userEmail);

        List<RefundHistory> refundHistoryList = refundDao.getRefundHistoryByDateRange(historyDate, historyDate.plusMinutes(11), 1, 2);

        assertThat(refundHistoryList.size(), is(2));

        RefundHistory refundHistory = refundHistoryList.get(0);
        assertThat(refundHistory.getStatus(), is(CREATED));
        assertThat(refundHistory.getReference(), is("ref-1"));
        assertThat(refundHistory.getId(), is(testRefund.getId()));
        assertThat(refundHistory.getUserEmail(), is(testRefund.getUserEmail()));

        refundHistory = refundHistoryList.get(1);
        assertThat(refundHistory.getStatus(), is(REFUND_SUBMITTED));
        assertThat(refundHistory.getReference(), is("ref-2"));
        assertThat(refundHistory.getId(), is(testRefund.getId()));
        assertThat(refundHistory.getUserEmail(), is(testRefund.getUserEmail()));
    }

    @Test
    public void findByChargeExternalIdShouldReturnAListOfRefunds() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withReference(randomAlphanumeric(10))
                .withGatewayTransactionId(randomAlphanumeric(10))
                .withChargeExternalId(chargeTestRecord.getExternalChargeId())
                .withTestCharge(chargeTestRecord).insert();

        List<RefundEntity> refundEntityList = refundDao.findRefundsByChargeExternalId(chargeTestRecord.externalChargeId);
        assertThat(refundEntityList.size(), is(2));
        assertThat(refundEntityList.get(0).getChargeExternalId(), is(chargeTestRecord.externalChargeId));
        assertThat(refundEntityList.get(1).getChargeExternalId(), is(chargeTestRecord.externalChargeId));
    }
}
