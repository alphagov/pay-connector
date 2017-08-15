package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.model.TransactionDto;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.List;

public class TransactionDao {

    private final Provider<EntityManager> entityManager;

    @Inject
    public TransactionDao(Provider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    public List<TransactionDto> findAllBy(ChargeSearchParams searchParams) {
        return entityManager.get().createQuery(
                "SELECT NEW uk.gov.pay.connector.model.TransactionDto('charge', c.externalId, c.reference, c.status, c.email, c.gatewayAccount.id, c.gatewayTransactionId, c.createdDate, c.cardDetails, c.amount) FROM ChargeEntity c " +
                        "UNION SELECT 'refund', r.chargeEntity.externalId, r.chargeEntity.reference, r.status,  r.chargeEntity.email, r.chargeEntity.gatewayAccount.id,r.chargeEntity.gatewayTransactionId, r.createdDate, r.chargeEntity.cardDetails, r.amount FROM RefundEntity r", TransactionDto.class)
                .getResultList();
    }

    /*public Long getTotalFor(ChargeSearchParams params) {


        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);
        List<Predicate> predicates = buildParamPredicates(params, cb, charge);

        cq.select(cb.count(charge));
        cq.where(predicates.toArray(new Predicate[]{}));
        return entityManager.get().createQuery(cq).getSingleResult();
    }*/

}
