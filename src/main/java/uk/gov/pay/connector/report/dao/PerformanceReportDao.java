package uk.gov.pay.connector.report.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.report.model.domain.GatewayAccountPerformanceReportEntity;
import uk.gov.pay.connector.report.model.domain.PerformanceReportEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.LIVE;

@Transactional
public class PerformanceReportDao extends JpaDao<PerformanceReportEntity> {
  @Inject
    public PerformanceReportDao(final Provider<EntityManager> entityManager) {
      super(entityManager);
    }

  public PerformanceReportEntity aggregateNumberAndValueOfPayments() {
    return (PerformanceReportEntity) entityManager
      .get()
      .createQuery(
        "SELECT new  uk.gov.pay.connector.report.model.domain.PerformanceReportEntity("
        + "   COALESCE(COUNT(c.amount), 0),"
        + "   COALESCE(SUM(c.amount),   0),"
        + "   COALESCE(AVG(c.amount),   0)"
        + " )"
        + " FROM ChargeEntity c"
        + " JOIN GatewayAccountEntity g"
        + " ON c.gatewayAccount.id = g.id"
        + " WHERE c.status = :status"
        + " AND   g.type = :type"
      )
      .setParameter("status", CAPTURED.toString())
      .setParameter("type", LIVE)
      .getSingleResult();
  }

  public Stream<GatewayAccountPerformanceReportEntity> aggregateNumberAndValueOfPaymentsByGatewayAccount() {
    return entityManager
      .get()
      .createQuery(
        "SELECT new uk.gov.pay.connector.report.model.domain.GatewayAccountPerformanceReportEntity("
        + "   COALESCE(COUNT(c.amount), 0),"
        + "   COALESCE(SUM(c.amount),   0),"
        + "   COALESCE(AVG(c.amount),   0),"
        + "   COALESCE(MIN(c.amount),   0),"
        + "   COALESCE(MAX(c.amount),   0),"
        + "   g.id"
        + " )"
        + " FROM ChargeEntity c"
        + " JOIN GatewayAccountEntity g"
        + " ON c.gatewayAccount.id = g.id"
        + " WHERE c.status = :status"
        + " AND   g.type = :type"
        + " GROUP BY g.id"
        + " ORDER BY g.id ASC"
      )
      .setParameter("status", CAPTURED.toString())
      .setParameter("type", LIVE)
      .getResultStream();
  }

  public PerformanceReportEntity aggregateNumberAndValueOfPaymentsForAGivenDay(ZonedDateTime date) {
    ZonedDateTime startDate = date.truncatedTo(ChronoUnit.DAYS);
    ZonedDateTime endDate = startDate.plus(24, ChronoUnit.HOURS);

    return (PerformanceReportEntity) entityManager
      .get()
      .createQuery(
        "SELECT new uk.gov.pay.connector.report.model.domain.PerformanceReportEntity("
        + "   COALESCE(COUNT(c.amount), 0),"
        + "   COALESCE(SUM(c.amount),   0),"
        + "   COALESCE(AVG(c.amount),   0)"
        + " )"
        + " FROM ChargeEntity c"
        + " JOIN GatewayAccountEntity g"
        + " ON c.gatewayAccount.id = g.id"
        + " WHERE c.status = :status"
        + " AND   g.type = :type"
        + " AND   c.createdDate BETWEEN :startDate AND :endDate"
      )
      .setParameter("status", CAPTURED.toString())
      .setParameter("type", LIVE)
      .setParameter("startDate", startDate)
      .setParameter("endDate", endDate)
      .getSingleResult();
  }
}
