package uk.gov.pay.connector.app;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.dao.TokenJpaDao;

public class DataAccessModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ChargeJpaDao.class).in(Singleton.class);
        bind(EventJpaDao.class).in(Singleton.class);
        bind(TokenJpaDao.class).in(Singleton.class);
        bind(GatewayAccountJpaDao.class).in(Singleton.class);
    }
}
