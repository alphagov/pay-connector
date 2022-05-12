package uk.gov.pay.connector.charge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.AgreementIdAndSaveInstrumentMandatoryInputException;
import uk.gov.pay.connector.charge.exception.AgreementNotFoundException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;

import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceCreateAgreementTest {
    private static final long GATEWAY_ACCOUNT_ID = 10L;

    private ChargeCreateRequestBuilder requestBuilder;

    @Mock
    private TokenDao mockedTokenDao;

    @Mock
    private ChargeDao mockedChargeDao;

    @Mock
    private ChargeEventDao mockedChargeEventDao;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private GatewayAccountDao mockedGatewayAccountDao;

    @Mock
    private CardTypeDao mockedCardTypeDao;

    @Mock
    private AgreementDao mockedAgreementDao;

    @Mock
    private ConnectorConfiguration mockedConfig;

    @Mock
    private UriInfo mockedUriInfo;

    @Mock
    private PaymentProviders mockedProviders;

    @Mock
    private EventService mockEventService;

    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;

    @Mock
    private StateTransitionService mockStateTransitionService;

    @Mock
    private RefundService mockedRefundService;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private TaskQueueService mockTaskQueueService;

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;

    @BeforeEach
    void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("http://return-service.com")
                .withDescription("This is a description")
                .withReference("Pay reference");

        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider("sandbox")
                .withCredentials(Map.of())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        gatewayAccount.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService);
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdNotProvidedAlongWithSavePaymentInstrumentToAgreement() {
        final ChargeCreateRequest request = requestBuilder.withAmount(1000).withSavePaymentInstrumentToAgreement(true).build();
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(AgreementIdAndSaveInstrumentMandatoryInputException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo));

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdNotPresentInDb() {
        String UNKNOWN_AGREEMENT_ID = "unknownId";
        final ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(UNKNOWN_AGREEMENT_ID).withSavePaymentInstrumentToAgreement(true).build();
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockedAgreementDao.findByExternalId(UNKNOWN_AGREEMENT_ID)).thenReturn(Optional.empty());

        assertThrows(AgreementNotFoundException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo));

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

}
