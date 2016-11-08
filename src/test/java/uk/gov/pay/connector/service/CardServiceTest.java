package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.*;

import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {
    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);

    protected ChargeCardDetailsService mockChargeCardDetailsService = mock(ChargeCardDetailsService.class);
    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);
    protected CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .build();
        entity.setChargeCardDetailsEntity(new ChargeCardDetailsEntity(entity));
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
