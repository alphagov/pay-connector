package uk.gov.pay.connector.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IJpaDao {
    <T> void persist(T object);

    <T, ID> Optional<T> findById(Class<T> clazz, ID id);

    <T> T merge(T object);

    <T> void remove(T object);

    <T, ID> void removeById(Class<T> clazz, ID id);

    <T> List<T> findAll(Class clazz);

    <T> List<T> find(Class<T> clazz, String namedQuery, Map<String, Object> paramsMap);
}
