package uk.gov.pay.connector.dao;

import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.math.BigDecimal;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.report.PerformanceReportEntity;
import uk.gov.pay.connector.model.domain.report.GatewayAccountPerformanceReportEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.LIVE;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import java.util.List;
import java.util.stream.Stream;
import java.util.Optional;

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
        "SELECT new uk.gov.pay.connector.model.domain.report.PerformanceReportEntity("
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

  public List<GatewayAccountPerformanceReportEntity> aggregateNumberAndValueOfPaymentsByGatewayAccount() {
    return entityManager
      .get()
      .createQuery(
        "SELECT new uk.gov.pay.connector.model.domain.report.GatewayAccountPerformanceReportEntity("
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
      )
      .setParameter("status", CAPTURED.toString())
      .setParameter("type", LIVE)
      .getResultList();
  }

  public PerformanceReportEntity aggregateNumberAndValueOfPaymentsForAGivenDay(ZonedDateTime date) {
    ZonedDateTime startDate = date.truncatedTo(ChronoUnit.DAYS);
    ZonedDateTime endDate = startDate.plus(24, ChronoUnit.HOURS);

    return (PerformanceReportEntity) entityManager
      .get()
      .createQuery(
        "SELECT new uk.gov.pay.connector.model.domain.report.PerformanceReportEntity("
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
