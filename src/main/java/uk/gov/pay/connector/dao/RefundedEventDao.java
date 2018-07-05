package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.cqrs.RefundedEvent;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Transactional
public class RefundedEventDao extends JpaDao<RefundedEvent> {
    
    @Inject
    public RefundedEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<RefundedEvent> find(Long gatewayAccountId, ZonedDateTime fromDate, ZonedDateTime toDate) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<RefundedEvent> cq = cb.createQuery(RefundedEvent.class);
        Root<RefundedEvent> refund = cq.from(RefundedEvent.class);

        List<Predicate> predicates = new ArrayList<Predicate>() {{
            add(cb.equal(refund.get("gatewayAccountId"), gatewayAccountId));
            add(cb.greaterThanOrEqualTo(refund.get("createdDate"), fromDate));
            add(cb.lessThan(refund.get("createdDate"), toDate));
        }};

        cq.select(refund).where(predicates.toArray(new Predicate[]{})).orderBy(cb.desc(refund.get("createdDate")));
        Query query = entityManager.get().createQuery(cq);

        return query.getResultList();
    }
}
