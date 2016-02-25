package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;
import java.util.Map;
import java.util.Optional;

//TODO: rename this when we can get rid of Jpa
public class JpaDao<T> {

    private static final String QUERY_SELECT_ALL = "SELECT o FROM %s o ORDER BY o.id";

    protected final Provider<EntityManager> entityManager;

    public JpaDao(Provider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public <T> void persist(final T object) {
        entityManager.get().persist(object);
    }

    @Transactional
    public <T, ID> Optional<T> findById(final Class<T> clazz, final ID id) {
        return Optional.ofNullable(entityManager.get().find(clazz, id));
    }

    @Transactional
    public <T> T merge(final T object) {
        return entityManager.get().merge(object);
    }

    @Transactional
    public <T> void remove(final T object) {
        entityManager.get().remove(object);
    }

    @Transactional
    public <T, ID> void removeById(final Class<T> clazz, final ID id) {
        remove(findById(clazz, id));
    }

    @Transactional
    public <T> List<T> findAll(final Class clazz) {
        final String query = String.format(QUERY_SELECT_ALL, clazz.getSimpleName());
        return entityManager.get().createQuery(query).getResultList();
    }

    @Transactional
    public <T> List<T> find(final Class<T> clazz, final String namedQuery, final Map<String, Object> paramsMap) {
        final Query query = fillNamedParametersQuery(clazz, namedQuery, paramsMap);
        return query.getResultList();
    }

    private Query fillNamedParametersQuery(final Class clazz, final String namedQuery, final Map<String, Object> paramsMap) {
        final Query query = entityManager.get().createNamedQuery(namedQuery, clazz);
        paramsMap.entrySet().forEach((param) -> query.setParameter(param.getKey(), param.getValue()));
        return query;
    }
}
