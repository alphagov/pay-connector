package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.chargeevents.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {

    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);
    protected MetricRegistry mockMetricRegistry;
    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);
    protected ChargeEventDao mockedChargeEventDao = mock(ChargeEventDao.class);
    protected CardTypeDao mockedCardTypeDao = mock(CardTypeDao.class);

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .build();
        entity.setCardDetails(new CardDetailsEntity());
        return entity;

    }

    protected ChargeEntity createNewChargeWith(String provider, Long chargeId, ChargeStatus status, String gatewayTransactionId) {
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.setGatewayName(provider);
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withId(chargeId)
                .withStatus(status)
                .build();
        entity.setGatewayTransactionId(gatewayTransactionId);
        return entity;
    }
}
