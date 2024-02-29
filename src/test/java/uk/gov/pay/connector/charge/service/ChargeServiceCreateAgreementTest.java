package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.exception.RecurringCardPaymentsNotAllowedException;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.AgreementMissingPaymentInstrumentException;
import uk.gov.pay.connector.charge.exception.AgreementNotFoundBadRequestException;
import uk.gov.pay.connector.charge.exception.IncorrectAuthorisationModeForSavePaymentToAgreementException;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeException;
import uk.gov.pay.connector.charge.exception.PaymentInstrumentNotActiveException;
import uk.gov.pay.connector.charge.exception.SavePaymentInstrumentToAgreementRequiresAgreementIdException;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.paymentprocessor.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceCreateAgreementTest {
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final String SERVICE_HOST = "https://service-host.test/";
    private static final String FRONTEND_URL = "https://frontend.test/";
    private static final String AGREEMENT_ID = "agreement-id";
    private static ObjectMapper objectMapper = new ObjectMapper();

    private ChargeCreateRequestBuilder requestBuilder;

    @Mock
    private TokenDao mockTokenDao;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeEventDao mockChargeEventDao;

    @Mock
    private LedgerService mockLedgerService;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @Mock
    private CardTypeDao mockCardTypeDao;

    @Mock
    private AgreementDao mockAgreementDao;

    @Mock
    private ConnectorConfiguration mockConfig;

    @Mock
    private UriInfo mockedUriInfo;

    @Mock
    private LinksConfig mockLinksConfig;

    @Mock
    private PaymentProviders mockProviders;

    @Mock
    protected PaymentProvider mockPaymentProvider;

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
    
    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;

    @Mock
    private AgreementEntity mockAgreementEntity;

    @Mock
    private PaymentInstrumentEntity mockPaymentInstrumentEntity;

    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Captor
    private ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @BeforeEach
    void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("https://return-url.test/")
                .withDescription("This is a description")
                .withReference("This is a reference");

        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);
        gatewayAccount.setRecurringEnabled(true);

        gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(PaymentGatewayName.SANDBOX.getName())
                .withCredentials(Collections.emptyMap())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        when(mockConfig.getLinks()).thenReturn(mockLinksConfig);

        chargeService = new ChargeService(mockTokenDao, mockChargeDao, mockChargeEventDao, mockCardTypeDao, mockAgreementDao, mockGatewayAccountDao,
                mockConfig, mockProviders, mockStateTransitionService, mockLedgerService, mockedRefundService, mockEventService,
                mockGatewayAccountCredentialsService,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao,
                mockExternalTransactionStateFactory, objectMapper, null);
    }

    @Test
    void shouldCreateChargeWithSavePaymentInstrumentToAgreement() {
        when(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockAgreementEntity));
        when(mockedUriInfo.getBaseUriBuilder()).thenReturn(fromUri(SERVICE_HOST));
        when(mockLinksConfig.getFrontendUrl()).thenReturn(FRONTEND_URL);
        when(mockProviders.byName(PaymentGatewayName.SANDBOX)).thenReturn(mockPaymentProvider);
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), eq(Collections.emptyList()))).thenReturn(EXTERNAL_AVAILABLE);

        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(AGREEMENT_ID).withSavePaymentInstrumentToAgreement(true).build();

        Optional<ChargeResponse> chargeResponse = chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        assertThat(chargeResponse.isPresent(), is(true));
        assertThat(chargeResponse.get().getLink("next_url"), is(notNullValue()));
        assertThat(chargeResponse.get().getLink("next_url_post"), is(notNullValue()));

        verify(mockChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getAgreement().isPresent(), is(true));
        assertThat(createdChargeEntity.getAgreement().get(), is(mockAgreementEntity));
        assertThat(createdChargeEntity.isSavePaymentInstrumentToAgreement(), is(true));
    }

    @Test
    void shouldCreateChargeWithAuthorisationModeAgreement() {
        when(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockAgreementEntity));
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.of(mockPaymentInstrumentEntity));
        when(mockPaymentInstrumentEntity.getStatus()).thenReturn(PaymentInstrumentStatus.ACTIVE);
        when(mockedUriInfo.getBaseUriBuilder()).thenReturn(fromUri(SERVICE_HOST));
        when(mockProviders.byName(PaymentGatewayName.SANDBOX)).thenReturn(mockPaymentProvider);
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), eq(Collections.emptyList()))).thenReturn(EXTERNAL_AVAILABLE);

        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(AGREEMENT_ID).withAuthorisationMode(AuthorisationMode.AGREEMENT).build();

        Optional<ChargeResponse> chargeResponse = chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        assertThat(chargeResponse.isPresent(), is(true));
        assertThat(chargeResponse.get().getDataLinks().stream().anyMatch(link -> "next_url".equals(link.get("rel"))), is(false));
        assertThat(chargeResponse.get().getDataLinks().stream().anyMatch(link -> "next_url_post".equals(link.get("rel"))), is(false));

        verify(mockChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getAgreement().isPresent(), is(true));
        assertThat(createdChargeEntity.getAgreement().get(), is(mockAgreementEntity));
        assertThat(createdChargeEntity.getAuthorisationMode(), is(AuthorisationMode.AGREEMENT));
        assertThat(createdChargeEntity.isSavePaymentInstrumentToAgreement(), is(false));
        assertThat(createdChargeEntity.getPaymentInstrument(), is(Optional.of(mockPaymentInstrumentEntity)));
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdNotProvidedAlongWithAuthorisationModeAgreement() {
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAuthorisationMode(AuthorisationMode.AGREEMENT).build();

        var thrown = assertThrows(MissingMandatoryAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Missing mandatory attribute: agreement_id"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdAndSavePaymentInstrumentToAgreementTrueAndAuthorisationModeAgreement() {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId("agreement ID")
                .withSavePaymentInstrumentToAgreement(true)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();

        assertThrows(IncorrectAuthorisationModeForSavePaymentToAgreementException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenMotoTrueAndAuthorisationModeAgreement() {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId("agreement ID")
                .withMoto(true)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();

        var thrown = assertThrows(UnexpectedAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: moto"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailPresentAndAuthorisationModeAgreement() {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId("agreement ID")
                .withEmail("test@test.test")
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();

        var thrown = assertThrows(UnexpectedAttributeException.class, 
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: email"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenPrefilledCardholderDetailsPresentAndAuthorisationModeAgreement() {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId("agreement ID")
                .withPrefilledCardHolderDetails(new PrefilledCardHolderDetails())
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();

        var thrown = assertThrows(UnexpectedAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: prefilled_cardholder_details"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdProvidedWithSavePaymentInstrumentToAgreementFalse() {
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(AGREEMENT_ID).withSavePaymentInstrumentToAgreement(false).build();

        var thrown = assertThrows(UnexpectedAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: agreement_id"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementIdNotProvidedAlongWithSavePaymentInstrumentToAgreementTrue() {
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withSavePaymentInstrumentToAgreement(true).build();

        assertThrows(SavePaymentInstrumentToAgreementRequiresAgreementIdException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EXCLUDE, names = "AGREEMENT")
    void shouldThrowExceptionWhenAgreementIdProvidedWithoutSavePaymentInstrumentToAgreementOrAuthorisationModeAgreement(AuthorisationMode authorisationMode) {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(authorisationMode)
                .build();

        var thrown = assertThrows(UnexpectedAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: agreement_id"));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EXCLUDE, names = {"WEB", "AGREEMENT"})
    void shouldThrowExceptionWhenSavePaymentInstrumentToAgreementTrueWithAgreementIdAndAuthorisationModeNotWebOrAgreement(AuthorisationMode authorisationMode) {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withAgreementId("agreement-id")
                .withSavePaymentInstrumentToAgreement(true)
                .withAuthorisationMode(authorisationMode)
                .build();

        var thrown = assertThrows(UnexpectedAttributeException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Unexpected attribute: agreement_id"));
        
        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EXCLUDE, names = {"WEB", "AGREEMENT"})
    void shouldThrowExceptionWhenSavePaymentInstrumentToAgreementTrueWithNoAgreementIdAndAuthorisationModeNotWebOrAgreement(AuthorisationMode authorisationMode) {
        gatewayAccount.setAllowMoto(true);
        gatewayAccount.setAllowAuthorisationApi(true);

        ChargeCreateRequest request = requestBuilder
                .withAmount(1000)
                .withSavePaymentInstrumentToAgreement(true)
                .withAuthorisationMode(authorisationMode)
                .build();

        assertThrows(IncorrectAuthorisationModeForSavePaymentToAgreementException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementNotPresentInDbForSavePaymentInstrumentToAgreement() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        String UNKNOWN_AGREEMENT_ID = "unknownId";
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(UNKNOWN_AGREEMENT_ID).withSavePaymentInstrumentToAgreement(true).build();
        when(mockAgreementDao.findByExternalId(UNKNOWN_AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThrows(AgreementNotFoundBadRequestException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementNotPresentInDbForAuthorisationModeAgreement() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        String unknownAgreementId = "unknownId";
        final ChargeCreateRequest request = requestBuilder.withAmount(1000)
                .withAgreementId(unknownAgreementId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(AgreementNotFoundBadRequestException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenAgreementHasNoPaymentInstrumentForAuthorisationModeAgreement() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockAgreementEntity));
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.empty());

        final ChargeCreateRequest request = requestBuilder.withAmount(1000)
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(AgreementMissingPaymentInstrumentException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @ParameterizedTest
    @EnumSource(mode = EXCLUDE, names = "ACTIVE")
    void shouldThrowExceptionWhenAgreementHasPaymentInstrumentInIncorrectStateForAuthorisationModeAgreement(PaymentInstrumentStatus paymentInstrumentStatus) {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockAgreementDao.findByExternalId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockAgreementEntity));
        when(mockAgreementEntity.getPaymentInstrument()).thenReturn(Optional.of(mockPaymentInstrumentEntity));
        when(mockPaymentInstrumentEntity.getStatus()).thenReturn(paymentInstrumentStatus);

        final ChargeCreateRequest request = requestBuilder.withAmount(1000)
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(PaymentInstrumentNotActiveException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }
    
    @Test 
    void shouldThrowExceptionWhenRecurringNotEnabledForGatewayAccountForAuthorisationModeAgreement() {
        gatewayAccount.setRecurringEnabled(false);
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAuthorisationMode(AuthorisationMode.AGREEMENT).build();
        
        var thrown = assertThrows(RecurringCardPaymentsNotAllowedException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));
        
        assertThat(thrown.getMessage(), is("Attempt to use authorisation mode 'agreement' for gateway account 10, which does not have recurring card payments enabled"));
        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
        }

    @Test
    void shouldThrowExceptionWhenRecurringNotEnabledForSavePaymentInstrumentToAgreement() {
        gatewayAccount.setRecurringEnabled(false);
        ChargeCreateRequest request = requestBuilder.withAmount(1000).withAgreementId(AGREEMENT_ID).withSavePaymentInstrumentToAgreement(true).build();
        
        var thrown = assertThrows(RecurringCardPaymentsNotAllowedException.class,
                () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        assertThat(thrown.getMessage(), is("Attempt to save payment instrument to agreement for gateway account 10, which does not have recurring card payments enabled"));
        verify(mockChargeDao, never()).persist(any(ChargeEntity.class));
    }
}
