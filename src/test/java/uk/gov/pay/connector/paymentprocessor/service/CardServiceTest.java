package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import org.mockito.Mock;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;

public abstract class CardServiceTest {

    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    protected PaymentProviders mockedProviders = mock(PaymentProviders.class);
    @Mock
    protected MetricRegistry mockMetricRegistry;
    protected ChargeDao mockedChargeDao = mock(ChargeDao.class);
    protected ChargeService chargeService;
    protected ChargeEventDao mockedChargeEventDao = mock(ChargeEventDao.class);
    protected CardTypeDao mockedCardTypeDao = mock(CardTypeDao.class);

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .withEvents(List.of(
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.CREATED, Optional.of(ZonedDateTime.now().minusHours(3)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_SUCCESS, Optional.of(ZonedDateTime.now()), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.ENTERING_CARD_DETAILS, Optional.of(ZonedDateTime.now().minusHours(2)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_TIMEOUT, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_ERROR, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_3DS_REQUIRED, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_CANCELLED, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_REJECTED, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty()),
                        new ChargeEventEntity(new ChargeEntity(), ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR, Optional.of(ZonedDateTime.now().minusHours(1)), Optional.empty())
                ))
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
