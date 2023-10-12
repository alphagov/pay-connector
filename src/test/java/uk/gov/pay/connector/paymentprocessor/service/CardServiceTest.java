package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import org.mockito.Mock;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;

public abstract class CardServiceTest {

    protected final PaymentProvider mockedPaymentProvider = mock(PaymentProvider.class);
    @Mock
    protected PaymentProviders mockedProviders;
    @Mock
    protected MetricRegistry mockMetricRegistry;
    @Mock
    protected ChargeDao mockedChargeDao;
    @Mock
    protected ChargeEventDao mockedChargeEventDao;
    @Mock
    protected CardTypeDao mockedCardTypeDao;

    protected ChargeEntity createNewChargeWith(Long chargeId, ChargeStatus status) {
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .withEvents(List.of(
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.CREATED).withUpdated(ZonedDateTime.now().minusHours(3)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_SUCCESS).withUpdated(ZonedDateTime.now()).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.ENTERING_CARD_DETAILS).withUpdated(ZonedDateTime.now().minusHours(2)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_TIMEOUT).withUpdated(ZonedDateTime.now().minusHours(1)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_ERROR).withUpdated(ZonedDateTime.now().minusHours(1)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED).withUpdated(ZonedDateTime.now().minusHours(1)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_CANCELLED).withUpdated(ZonedDateTime.now().minusHours(1)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_REJECTED).withUpdated(ZonedDateTime.now().minusHours(1)).build(),
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR).withUpdated(ZonedDateTime.now().minusHours(1)).build()
                ))
                .build();
        entity.setCardDetails(new CardDetailsEntity());
        return entity;
    }

    protected ChargeEntity createNewChargeWithFees(String provider, Long chargeId, ChargeStatus status, String gatewayTransactionId) {
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withId(chargeId)
                .withStatus(status)
                .withFee(Fee.of(FeeType.TRANSACTION, 50L))
                .withFee(Fee.of(FeeType.RADAR, 40L))
                .withFee(Fee.of(FeeType.THREE_D_S, 30L))
                .build();
        entity.setGatewayTransactionId(gatewayTransactionId);
        return entity;
    }

    protected ChargeEntity createNewChargeWith(String provider, Long chargeId, ChargeStatus status, String gatewayTransactionId) {
        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        ChargeEntity entity = ChargeEntityFixture
                .aValidChargeEntity()
                .withPaymentProvider(provider)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withId(chargeId)
                .withStatus(status)
                .withEvents(List.of(
                        aChargeEventEntity().withChargeEntity(new ChargeEntity()).withStatus(status).withUpdated(ZonedDateTime.now().minusHours(1)).build()
                ))
                .build();
        entity.setGatewayTransactionId(gatewayTransactionId);
        return entity;
    }
}
