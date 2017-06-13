package uk.gov.pay.connector.service;

public interface StatusMapper<V> {

   InterpretedStatus from(V value);

}
