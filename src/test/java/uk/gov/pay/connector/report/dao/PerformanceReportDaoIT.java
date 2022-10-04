package uk.gov.pay.connector.report.dao;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.dao.DaoITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.report.model.domain.GatewayAccountPerformanceReportEntity;
import uk.gov.pay.connector.report.model.domain.PerformanceReportEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.BigDecimalCloseTo.closeTo;

public class PerformanceReportDaoIT extends DaoITestBase {

    private PerformanceReportDao performanceReportDao;
    private DatabaseFixtures.TestAccount testAccountFixture;

    @Before
    public void setUp() {
        performanceReportDao = env.getInstance(PerformanceReportDao.class);
        testAccountFixture = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withType(GatewayAccountType.LIVE)
                .insert();
    }

    private void insertCharge(DatabaseFixtures.TestAccount account, long amount, Instant createdDate) {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(account)
                .withAmount(amount)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .withCreatedDate(createdDate)
                .insert();
    }

    @Ignore
    @Test
    public void shouldAggregateNumberAndValueOfPayments() {
        insertCharge(testAccountFixture, 10L, Instant.now());
        insertCharge(testAccountFixture, 2L, Instant.now());
        PerformanceReportEntity performanceReportEntity = performanceReportDao.aggregateNumberAndValueOfPayments();
        assertThat(performanceReportEntity.getAverageAmount(), is(closeTo(new BigDecimal("6"), ZERO)));
        assertThat(performanceReportEntity.getTotalAmount(), is(closeTo(new BigDecimal("12"), ZERO)));
        assertThat(performanceReportEntity.getTotalVolume(), is(2L));
    }

    @Test
    public void shouldAggregateNumberAndValueOfPaymentsForGatewayAccount() {
        DatabaseFixtures.TestAccount gatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(173L)
                .withType(GatewayAccountType.LIVE)
                .insert();
        DatabaseFixtures.TestAccount anotherGatewayAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(174L)
                .withType(GatewayAccountType.LIVE)
                .insert();
        insertCharge(gatewayAccount, 10L, Instant.now());
        insertCharge(gatewayAccount, 2L, Instant.now());
        insertCharge(anotherGatewayAccount, 6L, Instant.now());
        insertCharge(anotherGatewayAccount, 3L, Instant.now());
        insertCharge(anotherGatewayAccount, 6L, Instant.now());

        List<GatewayAccountPerformanceReportEntity> gatewayAccountPerformanceReportEntities =
                performanceReportDao.aggregateNumberAndValueOfPaymentsByGatewayAccount().collect(Collectors.toList());

        assertThat(gatewayAccountPerformanceReportEntities.size(), is(2));

        GatewayAccountPerformanceReportEntity first = gatewayAccountPerformanceReportEntities.get(0);
        GatewayAccountPerformanceReportEntity second = gatewayAccountPerformanceReportEntities.get(1);

        assertThat(first.getAverageAmount(), is(closeTo(new BigDecimal("6"), ZERO)));
        assertThat(first.getTotalAmount(), is(closeTo(new BigDecimal("12"), ZERO)));
        assertThat(first.getTotalVolume(), is(2L));

        assertThat(second.getAverageAmount(), is(closeTo(new BigDecimal("5"), ZERO)));
        assertThat(second.getTotalAmount(), is(closeTo(new BigDecimal("15"), ZERO)));
        assertThat(second.getTotalVolume(), is(3L));
    }

    @Test
    public void shouldAggregateNumberAndValueOfPaymentsForAGivenDay() {
        Instant oldDate = Instant.parse("2017-11-20T10:00:00Z");
        Instant validDate1 = Instant.parse("2017-11-21T10:00:00Z");
        Instant validDate2 = Instant.parse("2017-11-21T11:00:00Z");
        insertCharge(testAccountFixture, 10L, oldDate);
        insertCharge(testAccountFixture, 2L, validDate1);
        insertCharge(testAccountFixture, 10L, validDate2);
        PerformanceReportEntity performanceReportEntity = performanceReportDao.aggregateNumberAndValueOfPaymentsForAGivenDay(
                ZonedDateTime.ofInstant(validDate1, ZoneOffset.UTC));
        assertThat(performanceReportEntity.getAverageAmount(), is(closeTo(new BigDecimal("6"), ZERO)));
        assertThat(performanceReportEntity.getTotalAmount(), is(closeTo(new BigDecimal("12"), ZERO)));
        assertThat(performanceReportEntity.getTotalVolume(), is(2L));
    }

    @Test
    public void shouldAggregateNumberAndValueOfPaymentsForBetweenGivenDays() {
        Instant oldDate = Instant.parse("2020-07-18T18:00:00Z");
        Instant pickedDate1 = Instant.parse("2020-08-19T10:00:00Z");
        Instant pickedDate2 = Instant.parse("2020-09-19T21:00:00Z");
        Instant newerDate = Instant.parse("2020-10-20T01:00:00Z");
        insertCharge(testAccountFixture, 10L, oldDate);
        insertCharge(testAccountFixture, 2L, pickedDate1);
        insertCharge(testAccountFixture, 2L, pickedDate2);
        insertCharge(testAccountFixture, 10L, newerDate);
        Instant fromDate = Instant.parse("2020-08-01T00:00:00Z");
        Instant toDate = Instant.parse("2020-09-30T00:00:00Z");
        PerformanceReportEntity performanceReportEntity = performanceReportDao
                .aggregateNumberAndValueOfPaymentsForBetweenGivenDates(
                        ZonedDateTime.ofInstant(fromDate, ZoneOffset.UTC),
                        ZonedDateTime.ofInstant(toDate, ZoneOffset.UTC));
        assertThat(performanceReportEntity.getAverageAmount(), is(closeTo(new BigDecimal("2"), ZERO)));
        assertThat(performanceReportEntity.getTotalAmount(), is(closeTo(new BigDecimal("4"), ZERO)));
        assertThat(performanceReportEntity.getTotalVolume(), is(2L));
    }
}
