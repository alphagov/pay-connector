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
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.lenient;
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

    private List<ChargeEventEntity> generateList(ChargeStatus... chargeStatus) {
        var list = new ArrayList<ChargeEventEntity>();
        ZonedDateTime latestDateTime = ZonedDateTime.now();

        for(ChargeStatus status : chargeStatus) {
            ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
            lenient().when(chargeEventEntity.getUpdated()).thenReturn(latestDateTime);
            lenient().when(chargeEventEntity.getStatus()).thenReturn(status);

            list.add(chargeEventEntity);
        }

        return list;
    }

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        List<ChargeEventEntity> eventEntities = generateList(ChargeStatus.CREATED,
                ChargeStatus.AUTHORISATION_SUCCESS,
                ChargeStatus.ENTERING_CARD_DETAILS,
                ChargeStatus.AUTHORISATION_TIMEOUT,
                ChargeStatus.AUTHORISATION_ERROR,
                ChargeStatus.AUTHORISATION_3DS_REQUIRED,
                ChargeStatus.AUTHORISATION_CANCELLED,
                ChargeStatus.AUTHORISATION_REJECTED,
                ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR);

        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .withEvents(eventEntities)
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
