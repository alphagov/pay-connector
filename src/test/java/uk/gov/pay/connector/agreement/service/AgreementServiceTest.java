package uk.gov.pay.connector.agreement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundException;
import uk.gov.pay.connector.agreement.exception.RecurringCardPaymentsNotAllowedException;
import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.charge.exception.PaymentInstrumentNotActiveException;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.charge.AgreementCancelledByService;
import uk.gov.pay.connector.events.model.charge.AgreementCancelledByUser;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AgreementServiceTest {

    private static final String SERVICE_ID = "TestAgreementServiceID";

    private static final long GATEWAY_ACCOUNT_ID = 10L;

    private static final String REFERENCE_ID = "test";

    private GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

    private AgreementDao mockedAgreementDao = mock(AgreementDao.class);

    private GatewayAccountDao mockedGatewayAccountDao = mock(GatewayAccountDao.class);

    private LedgerService mockedLedgerService = mock(LedgerService.class);

    private AgreementService agreementService;

    @BeforeEach
    public void setUp() {
        String instantExpected = "2022-03-03T10:15:30Z";
        Clock clock = Clock.fixed(Instant.parse(instantExpected), ZoneOffset.UTC);
        agreementService = new AgreementService(mockedAgreementDao, mockedGatewayAccountDao, mockedLedgerService, clock);
    }

    @Test
    public void shouldCreateAnAgreementWhenRecurringEnabled() {
        var description = "a valid description";
        var userIdentifier = "a-valid-user-reference";
        when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
        when(gatewayAccount.isLive()).thenReturn(false);
        when(gatewayAccount.isRecurringEnabled()).thenReturn(true);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, description, userIdentifier);
        Optional<AgreementResponse> response = agreementService.create(agreementCreateRequest, GATEWAY_ACCOUNT_ID);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().getReference(), is(REFERENCE_ID));
        assertThat(response.get().getServiceId(), is(SERVICE_ID));
        assertThat(response.get().getDescription(), is(description));
        assertThat(response.get().getUserIdentifier(), is(userIdentifier));
    }

    @Test
    public void createAnAgreement_ShouldThrowRecurringCardPaymentsNotAllowedExceptionWhenRecurringDisabled() {
        var description = "a valid description";
        var userIdentifier = "a-valid-user-reference";
        when(gatewayAccount.isRecurringEnabled()).thenReturn(false);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, description, userIdentifier);
        assertThrows(RecurringCardPaymentsNotAllowedException.class, () -> agreementService.create(agreementCreateRequest, GATEWAY_ACCOUNT_ID));
    }
    
    @Test
    public void cancelAnAgreement_ThrowsAgreementNotFound() {
        var agreementId = "an-external-id";
        when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockedAgreementDao.findByExternalId(agreementId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());
        assertThrows(AgreementNotFoundException.class, () -> agreementService.cancel(agreementId, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
    }

    @Test
    public void cancelAnAgreement_ThrowsPaymentInstrumentNotActiveWhenNoPaymentInstrument() {
        var agreementId = "an-external-id";
        var agreementWithoutPaymentInstrument = new AgreementEntity.AgreementEntityBuilder()
                .withPaymentInstrument(null)
                .build();
        when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockedAgreementDao.findByExternalId(agreementId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreementWithoutPaymentInstrument));
        assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancel(agreementId, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
    }

    @ParameterizedTest()
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    public void cancelAnAgreement_ThrowsPaymentInstrumentNotActiveWhenNonActiveStates(PaymentInstrumentStatus status) {
        var agreementId = "an-external-id";
        var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withStatus(status)
                .build();
        var agreement = new AgreementEntity.AgreementEntityBuilder()
                .withPaymentInstrument(paymentInstrument)
                .build();
        when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockedAgreementDao.findByExternalId(agreementId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
        assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancel(agreementId, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
    }

    @Test
    public void cancelAnAgreementWithUserDetails_ShouldMoveToCancelStatusForActivePaymentInstrument() {
        var agreementId = "an-external-id";
        var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        var agreement = new AgreementEntity.AgreementEntityBuilder()
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .build();
        var cancelRequest = new AgreementCancelRequest("valid-user-external-id", "valid@email.test");
        when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockedAgreementDao.findByExternalId(agreementId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
        agreementService.cancel(agreementId, GATEWAY_ACCOUNT_ID, cancelRequest);
        verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByUser.class));
        assertThat(paymentInstrument.getPaymentInstrumentStatus(), is(PaymentInstrumentStatus.CANCELLED));
    }

    @Test
    public void cancelAnAgreementWithoutUserDetails_ShouldMoveToCancelStatusForActivePaymentInstrument() {
        var agreementId = "an-external-id";
        var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        var agreement = new AgreementEntity.AgreementEntityBuilder()
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .build();
        when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockedAgreementDao.findByExternalId(agreementId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
        agreementService.cancel(agreementId, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest());
        verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByService.class));
        assertThat(paymentInstrument.getPaymentInstrumentStatus(), is(PaymentInstrumentStatus.CANCELLED));
    }
}
