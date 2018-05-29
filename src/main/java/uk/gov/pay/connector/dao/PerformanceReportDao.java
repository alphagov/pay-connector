package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.report.PerformanceReportEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Transactional
public class PerformanceReportDao extends JpaDao<PerformanceReportEntity> {
    @Inject
    public PerformanceReportDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public PerformanceReportEntity aggregateNumberAndValueOfPayments() {
            return (PerformanceReportEntity) entityManager.get()
                    .createQuery("SELECT NEW uk.gov.pay.connector.model.domain.report.PerformanceReportEntity(COUNT(c.amount), SUM(c.amount), AVG(c.amount)) FROM ChargeEntity c WHERE c.status = :status")
                    .setParameter("status", CAPTURED.toString())
                    .setMaxResults(1)
                    .getSingleResult();
    }
}
