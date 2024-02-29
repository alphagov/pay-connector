package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.pay.connector.paymentprocessor.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.paymentprocessor.model.ChargeCardDetailsEntity;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
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
    @Mock
    private EventService eventService;
    @Mock
    private ChargeService chargeService;
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private PaymentInstrumentService paymentInstrumentService;
    @Mock
    private TaskQueueService taskQueueService;
    @InjectMocks
    private CardService cardService;

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
        entity.setChargeCardDetails(new ChargeCardDetailsEntity(null));
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

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithoutTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));

        cardService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_REJECTED, AUTHORISATION_3DS, null,
                null, null, null);

        verify(chargeSpy, never()).setGatewayTransactionId(anyString());
        verify(chargeSpy).setStatus(AUTHORISATION_REJECTED);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithTransactionId() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));

        cardService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_SUCCESS, AUTHORISATION_3DS, "transaction-id",
                null, null, null);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).setStatus(AUTHORISATION_SUCCESS);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationIf3dsRequiredAgainAndTransactionId() {
        final Auth3dsRequiredEntity mockedAuth3dsRequiredEntity = mock(Auth3dsRequiredEntity.class);
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .withAuth3dsDetailsEntity(mockedAuth3dsRequiredEntity)
                .build());

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        when(mockedAuth3dsRequiredEntity.getThreeDsVersion()).thenReturn("2.1.0");

        cardService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS, "transaction-id",
                mockedAuth3dsRequiredEntity, ProviderSessionIdentifier.of("provider-session-identifier"), null);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).getChargeCardDetails().set3dsRequiredDetails(mockedAuth3dsRequiredEntity);
        verify(chargeSpy).getChargeCardDetails().setProviderSessionId("provider-session-identifier");
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
        verify(eventService).emitAndRecordEvent(any(Gateway3dsInfoObtained.class));
    }

    @Test
    void shouldUpdateChargePost3dsAuthorisationWithGatewayRecurringAuthToken_whenSavePaymentInstrumentToAgreementTrue() {
        ChargeEntity chargeSpy = spy(aValidChargeEntity()
                .withStatus(AUTHORISATION_3DS_READY)
                .withSavePaymentInstrumentToAgreement(true)
                .build());

        Map<String, String> recurringAuthToken = Map.of("token", "foo");
        PaymentInstrumentEntity paymentInstrument = new PaymentInstrumentEntity();

        final String chargeEntityExternalId = chargeSpy.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(chargeSpy));
        when(paymentInstrumentService.createPaymentInstrument(chargeSpy, recurringAuthToken)).thenReturn(paymentInstrument);

        cardService.updateChargePost3dsAuthorisation(chargeSpy.getExternalId(), AUTHORISATION_SUCCESS, AUTHORISATION_3DS, "transaction-id",
                null, null, recurringAuthToken);

        verify(chargeSpy).setGatewayTransactionId("transaction-id");
        verify(chargeSpy).setStatus(AUTHORISATION_SUCCESS);
        verify(chargeSpy).setPaymentInstrument(paymentInstrument);
        verify(paymentInstrumentService).createPaymentInstrument(chargeSpy, recurringAuthToken);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }
}
