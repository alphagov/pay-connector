package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.cqrs.SuccessfulChargeEvent;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

@Transactional
public class SuccessfulChargeEventDao extends JpaDao<SuccessfulChargeEvent> {
    
    @Inject
    public SuccessfulChargeEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<SuccessfulChargeEvent> find(Long gatewayAccountId, String fromDate, String toDate) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<SuccessfulChargeEvent> cq = cb.createQuery(SuccessfulChargeEvent.class);
        Root<SuccessfulChargeEvent> charge = cq.from(SuccessfulChargeEvent.class);

        List<Predicate> predicates = new ArrayList<Predicate>() {{
            add(cb.equal(charge.get("gatewayAccountId"), gatewayAccountId));
            add(cb.greaterThanOrEqualTo(charge.get("createdDate"), fromDate));
            add(cb.lessThan(charge.get("createdDate"), toDate));
        }};
        
        cq.select(charge).where(predicates.toArray(new Predicate[]{})).orderBy(cb.desc(charge.get("createdDate")));
        Query query = entityManager.get().createQuery(cq);

        return query.getResultList();
    }
}
