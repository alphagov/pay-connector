package uk.gov.pay.connector.charge.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.IdempotencyKeyUsedException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.ws.rs.core.UriInfo;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder.aChargeCreateRequest;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceIdempotencyTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final String AGREEMENT_ID = "agreement-id";
    private static final ObjectMapper mapper = new ObjectMapper();

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
    private LinksConfig mockedLinksConfig;

    @Mock
    private PaymentProviders mockedProviders;

    @Mock
    private PaymentProvider mockedPaymentProvider;

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
    private CaptureProcessConfig mockedCaptureProcessConfig;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    
    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Mock
    Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;

    @BeforeEach
    void setUp() {
        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        when(mockedConfig.getLinks()).thenReturn(mockedLinksConfig);

        when(mockedConfig.getCaptureProcessConfig()).thenReturn(mockedCaptureProcessConfig);
        when(mockedConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory,
                mapper, null, fixedInstantSource);
    }

    @Test
    void shouldReturnEmptyOptional_whenNoExistingIdempotencyEntryFound() {
        String idempotencyKey = "idempotency-key";
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(100L)
                .withDescription("This is a description")
                .withReference("Pay reference")
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withReturnUrl(null)
                .build();
        
        when(mockIdempotencyDao.findByGatewayAccountIdAndKey(GATEWAY_ACCOUNT_ID, idempotencyKey)).thenReturn(Optional.empty());

        assertFalse(chargeService.checkForChargeCreatedWithIdempotencyKey(chargeCreateRequest, GATEWAY_ACCOUNT_ID,
                idempotencyKey, mockedUriInfo).isPresent());
    }

    @Test
    void shouldReturnExistingCharge_whenIdempotencyExistsAndMatches() {
        String existingChargeExternalId = "existing-id";
        String idempotencyKey = "idempotency-key";
        ChargeCreateRequest chargeCreateRequest = aChargeCreateRequest()
                .withAmount(100L)
                .withDescription("This is a description")
                .withReference("Pay reference")
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withReturnUrl(null)
                .build();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(existingChargeExternalId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();
        Map<String, Object> requestBody = mapper.convertValue(chargeCreateRequest, new TypeReference<>() {
        });
        IdempotencyEntity idempotencyEntity = new IdempotencyEntity(idempotencyKey, gatewayAccount,
                existingChargeExternalId, requestBody, Instant.now());

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockIdempotencyDao.findByGatewayAccountIdAndKey(GATEWAY_ACCOUNT_ID, idempotencyKey)).thenReturn(Optional.of(idempotencyEntity));
        when(mockedChargeDao.findByExternalId(existingChargeExternalId)).thenReturn(Optional.of(chargeEntity));

        ChargeResponse response = chargeService.checkForChargeCreatedWithIdempotencyKey(chargeCreateRequest, GATEWAY_ACCOUNT_ID, idempotencyKey, mockedUriInfo).get();
        
        assertThat(response.getChargeId(), is(existingChargeExternalId));
    }

    @Test
    void shouldThrowException_whenIdempotencyExistsAndRequestBodyNotMatch() {
        Logger root = (Logger) LoggerFactory.getLogger(ChargeService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String existingChargeExternalId = "existing-id";
        String idempotencyKey = "an-idempotency-key";
        ChargeCreateRequest newChargeCreateRequest = aChargeCreateRequest()
                .withAmount(100L)
                .withDescription("This is a description")
                .withReference("Pay reference")
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();

        ChargeCreateRequest existingChargeCreateRequest = aChargeCreateRequest()
                .withAmount(100L)
                .withDescription("This is a description")
                .withReference("Pay reference")
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withExternalMetadata(new ExternalMetadata(Map.of("invoice-number", "123")))
                .build();

        Map<String, Object> requestBody = mapper.convertValue(existingChargeCreateRequest, new TypeReference<>() {
        });
        IdempotencyEntity idempotencyEntity = new IdempotencyEntity(idempotencyKey, gatewayAccount,
                existingChargeExternalId, requestBody, Instant.now());
        
        when(mockIdempotencyDao.findByGatewayAccountIdAndKey(GATEWAY_ACCOUNT_ID, idempotencyKey)).thenReturn(Optional.of(idempotencyEntity));

        assertThrows(IdempotencyKeyUsedException.class, () -> chargeService.checkForChargeCreatedWithIdempotencyKey(newChargeCreateRequest, GATEWAY_ACCOUNT_ID, idempotencyKey, mockedUriInfo).get());

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent value = loggingEventArgumentCaptor.getValue();
        assertThat(value.getMessage(), is("Idempotency-Key [an-idempotency-key] was already used to create a charge with a different request body. Existing payment external id: existing-id"));
    }
}
