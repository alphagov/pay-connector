package uk.gov.pay.connector.agreement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import uk.gov.pay.connector.events.model.agreement.AgreementCancelledByService;
import uk.gov.pay.connector.events.model.agreement.AgreementCancelledByUser;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AgreementServiceTest {

    private static final String SERVICE_ID = "a-valid-service-id";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final String REFERENCE_ID = "test";
    public static final String VALID_DESCRIPTION = "a valid description";
    public static final String VALID_USER_REFERENCE = "a-valid-user-reference";
    public static final String VALID_AGREEMENT_ID = "a-valid-agreement-id";
    private static final String INSTANT_EXPECTED = "2022-03-03T10:15:30Z";
    
    private final GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
    private final AgreementDao mockedAgreementDao = mock(AgreementDao.class);
    private final GatewayAccountDao mockedGatewayAccountDao = mock(GatewayAccountDao.class);
    private final LedgerService mockedLedgerService = mock(LedgerService.class);
    private final TaskQueueService mockedTaskQueueService = mock(TaskQueueService.class);
    private AgreementService agreementService;
    
    @BeforeEach
    public void setUp() {
        InstantSource instantSource = InstantSource.fixed(Instant.parse(INSTANT_EXPECTED));
        agreementService = new AgreementService(mockedAgreementDao, mockedGatewayAccountDao, mockedLedgerService, instantSource, mockedTaskQueueService);
    }
    
    @Nested
    class ByGatewayAccountId {
        
        @Nested
        class CreateAgreement {
            
            @Test
            public void shouldBeSuccessful_whenRecurringEnabled() {
                when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
                when(gatewayAccount.isLive()).thenReturn(false);
                when(gatewayAccount.isRecurringEnabled()).thenReturn(true);
                when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                Optional<AgreementResponse> response = agreementService.createByGatewayAccountId(agreementCreateRequest, GATEWAY_ACCOUNT_ID);

                assertThat(response.isPresent(), is(true));
                assertThat(response.get().getReference(), is(REFERENCE_ID));
                assertThat(response.get().getServiceId(), is(SERVICE_ID));
                assertThat(response.get().getDescription(), is(VALID_DESCRIPTION));
                assertThat(response.get().getUserIdentifier(), is(VALID_USER_REFERENCE));
            }

            @Test
            public void shouldThrowException_whenGatewayAccountNotFound() {
                when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());
                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                assertThrows(GatewayAccountNotFoundException.class, () -> agreementService.createByGatewayAccountId(agreementCreateRequest, GATEWAY_ACCOUNT_ID));
            }

            @Test
            public void shouldThrowRecurringCardPaymentsNotAllowedException_whenRecurringDisabled() {
                when(gatewayAccount.isRecurringEnabled()).thenReturn(false);
                when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                assertThrows(RecurringCardPaymentsNotAllowedException.class, () -> agreementService.createByGatewayAccountId(agreementCreateRequest, GATEWAY_ACCOUNT_ID));
            }
        }

        @Nested
        class CancelAgreement {
            @Test
            public void shouldThrowException_whenGatewayAccountNotFound() {
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());
                assertThrows(AgreementNotFoundException.class, () -> agreementService.cancelByGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
            }

            @Test
            public void shouldThrowPaymentInstrumentNotActiveWhenNoPaymentInstrument() {
                var agreementWithoutPaymentInstrument = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(null)
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreementWithoutPaymentInstrument));
                assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancelByGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
            }

            @ParameterizedTest()
            @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
            public void shouldThrowPaymentInstrumentNotActive_WhenNonActiveStates(PaymentInstrumentStatus status) {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(status)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
                assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancelByGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest()));
            }

            @Test
            public void shouldCancelAgreementSuccessfully_whenUserDetailsProvided() {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(PaymentInstrumentStatus.ACTIVE)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                        .build();
                var cancelRequest = new AgreementCancelRequest("valid-user-external-id", "valid@email.test");
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
                agreementService.cancelByGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID, cancelRequest);
                verify(mockedTaskQueueService).addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
                verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByUser.class));
                assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.CANCELLED));
                assertThat(agreement.getCancelledDate(), is(Instant.parse(INSTANT_EXPECTED)));
            }

            @Test
            public void shouldCancelAgreementSuccessfully_whenUserDetailsNotProvided() {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(PaymentInstrumentStatus.ACTIVE)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreement));
                agreementService.cancelByGatewayAccountId(VALID_AGREEMENT_ID, GATEWAY_ACCOUNT_ID, new AgreementCancelRequest());
                verify(mockedTaskQueueService).addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
                verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByService.class));
                assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.CANCELLED));
                assertThat(agreement.getCancelledDate(), is(Instant.parse(INSTANT_EXPECTED)));
            }
        }
    }
    
    @Nested
    class ByServiceIdAndAccountType {

        @Nested
        class CreateAgreement {
            
            @Test
            public void shouldBeSuccessful_whenRecurringEnabled() {
                when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
                when(gatewayAccount.isLive()).thenReturn(false);
                when(gatewayAccount.isRecurringEnabled()).thenReturn(true);
                when(mockedGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(gatewayAccount));

                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                Optional<AgreementResponse> response = agreementService.createByServiceIdAndAccountType(agreementCreateRequest, SERVICE_ID, GatewayAccountType.TEST);

                assertThat(response.isPresent(), is(true));
                assertThat(response.get().getReference(), is(REFERENCE_ID));
                assertThat(response.get().getServiceId(), is(SERVICE_ID));
                assertThat(response.get().getDescription(), is(VALID_DESCRIPTION));
                assertThat(response.get().getUserIdentifier(), is(VALID_USER_REFERENCE));
            }

            @Test
            public void shouldThrowNotFoundException_whenGatewayAccountDoesNotExist() {
                when(mockedGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.empty());
                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                assertThrows(GatewayAccountNotFoundException.class, () -> agreementService.createByServiceIdAndAccountType(agreementCreateRequest, SERVICE_ID, GatewayAccountType.TEST));
            }

            @Test
            public void shouldThrowRecurringCardPaymentsNotAllowedException_whenRecurringDisabled() {
                when(gatewayAccount.isRecurringEnabled()).thenReturn(false);
                when(mockedGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(gatewayAccount));
                AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, VALID_USER_REFERENCE);
                assertThrows(RecurringCardPaymentsNotAllowedException.class, () -> agreementService.createByServiceIdAndAccountType(agreementCreateRequest, SERVICE_ID, GatewayAccountType.TEST));
            }
        }

        @Nested
        class CancelAgreement {
            @Test
            public void shouldThrowAgreementNotFoundException() {
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.empty());
                assertThrows(AgreementNotFoundException.class, () -> agreementService.cancelByServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST, new AgreementCancelRequest()));
            }

            @Test
            public void shouldThrowPaymentInstrumentNotActiveWhenNoPaymentInstrument() {
                var agreementWithoutPaymentInstrument = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(null)
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(agreementWithoutPaymentInstrument));
                assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancelByServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST, new AgreementCancelRequest()));
            }

            @ParameterizedTest()
            @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
            public void shouldThrowPaymentInstrumentNotActive_WhenNonActiveStates(PaymentInstrumentStatus status) {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(status)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(agreement));
                assertThrows(PaymentInstrumentNotActiveException.class, () -> agreementService.cancelByServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST, new AgreementCancelRequest()));
            }

            @Test
            public void shouldCancelAgreementSuccessfully_whenUserDetailsProvided() {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(PaymentInstrumentStatus.ACTIVE)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                        .build();
                var cancelRequest = new AgreementCancelRequest("valid-user-external-id", "valid@email.test");
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(agreement));
                agreementService.cancelByServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST, cancelRequest);
                verify(mockedTaskQueueService).addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
                verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByUser.class));
                assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.CANCELLED));
                assertThat(agreement.getCancelledDate(), is(Instant.parse(INSTANT_EXPECTED)));
            }

            @Test
            public void shouldCancelAgreementSuccessfully_whenUserDetailsNotProvided() {
                var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                        .withStatus(PaymentInstrumentStatus.ACTIVE)
                        .build();
                var agreement = new AgreementEntity.AgreementEntityBuilder()
                        .withPaymentInstrument(paymentInstrument)
                        .withGatewayAccount(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                        .build();
                when(gatewayAccount.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
                when(mockedAgreementDao.findByExternalIdAndServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST)).thenReturn(Optional.of(agreement));
                agreementService.cancelByServiceIdAndAccountType(VALID_AGREEMENT_ID, SERVICE_ID, GatewayAccountType.TEST,new AgreementCancelRequest());
                verify(mockedTaskQueueService).addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
                verify(mockedLedgerService).postEvent(Mockito.any(AgreementCancelledByService.class));
                assertThat(paymentInstrument.getStatus(), is(PaymentInstrumentStatus.CANCELLED));
                assertThat(agreement.getCancelledDate(), is(Instant.parse(INSTANT_EXPECTED)));
            }
        }
    }
}
