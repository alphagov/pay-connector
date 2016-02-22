package uk.gov.pay.connector.app;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventJpaDao;

public class DataAccessModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ChargeJpaDao.class).in(Singleton.class);
        bind(EventJpaDao.class).in(Singleton.class);
    }
}
