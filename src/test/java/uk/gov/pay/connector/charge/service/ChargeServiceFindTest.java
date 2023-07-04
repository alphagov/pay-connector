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
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.fee.model.Fee;
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
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CAPTURABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceFindTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static ObjectMapper objectMapper = new ObjectMapper();

    private TelephoneChargeCreateRequest.Builder telephoneRequestBuilder;

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
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    
    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Captor ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor;

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;

    @BeforeEach
    void setUp() {
        telephoneRequestBuilder = new TelephoneChargeCreateRequest.Builder()
                .withAmount(100L)
                .withReference("Some reference")
                .withDescription("Some description")
                .withCreatedDate("2018-02-21T16:04:25Z")
                .withAuthorisedDate("2018-02-21T16:05:33Z")
                .withProcessorId("1PROC")
                .withProviderId("1PROV")
                .withAuthCode("666")
                .withNameOnCard("Jane Doe")
                .withEmailAddress("jane.doe@example.com")
                .withTelephoneNumber("+447700900796")
                .withCardType("visa")
                .withCardExpiry(CardExpiryDate.valueOf("01/19"))
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456");

        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        var gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider("sandbox")
                .withCredentials(Map.of())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        gatewayAccount.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);

        when(mockedConfig.getLinks()).thenReturn(mockedLinksConfig);

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService,
                mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory, objectMapper);
    }
    @Test
    void shouldNotFindCharge() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withProviderId("new")
                .withPaymentOutcome(paymentOutcome)
                .build();

        Optional<ChargeResponse> telephoneChargeResponse = chargeService.findCharge(1234L, telephoneChargeCreateRequest);

        ArgumentCaptor<String> gatewayTransactionIdArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByGatewayTransactionIdAndAccount(anyLong(), gatewayTransactionIdArgumentCaptor.capture());

        String providerId = gatewayTransactionIdArgumentCaptor.getValue();
        assertThat(providerId, is("new"));
        assertThat(telephoneChargeResponse.isPresent(), is(false));
    }

    @ParameterizedTest
    @EnumSource(names = {
            "CREATED",
            "ENTERING_CARD_DETAILS",
            "AUTHORISATION_READY"     
    })
    void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(ChargeStatus status) throws URISyntaxException {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(status)
                .withWalletType(WalletType.APPLE_PAY)
                .build();

        String externalId = newCharge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(newCharge))
                .thenReturn(new ExternalTransactionState(status.toExternal().getStatus(), false));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.ChargeResponseBuilder chargeResponseWithoutCorporateCardSurcharge = chargeResponseBuilderOf(newCharge);
        chargeResponseWithoutCorporateCardSurcharge.withWalletType(WalletType.APPLE_PAY);
        chargeResponseWithoutCorporateCardSurcharge.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        chargeResponseWithoutCorporateCardSurcharge.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url", GET, new URI("http://frontend.test/secure/" + tokenEntity.getToken()));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url_post", POST, new URI("http://frontend.test/secure"),
                "application/x-www-form-urlencoded", Map.of("chargeTokenId", tokenEntity.getToken()));

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(nullValue()));
        assertThat(chargeResponse.getTotalAmount(), is(nullValue()));
        assertThat(chargeResponse, is(chargeResponseWithoutCorporateCardSurcharge.build()));
        assertThat(chargeResponse.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    void shouldFindChargeForChargeId_withCorporateSurcharge() {
        Long chargeId = 101L;
        Long totalAmount = 1250L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY)
                .withAmount(1000L)
                .withCorporateSurcharge(250L)
                .build();

        String externalId = newCharge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(250L));
        assertThat(chargeResponse.getTotalAmount(), is(totalAmount));
        assertThat(chargeResponse.getRefundSummary().getAmountAvailable(), is(totalAmount));
    }

    @Test
    void shouldFindChargeForChargeId_withFee() {
        Long chargeId = 101L;
        Long amount = 1000L;
        Long fee = 100L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(amount)
                .withFee(Fee.of(null, fee))
                .build();

        String externalId = charge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(charge));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        
        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getNetAmount(), is(amount - fee));
        assertThat(chargeResponse.getAmount(), is(amount));
    }

    @Test
    void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeCannotBeResumed() throws URISyntaxException {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);

        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .build();

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(newCharge))
                .thenReturn(new ExternalTransactionState(EXTERNAL_SUCCESS.getStatus(), true));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(newCharge);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {
        String externalChargeId = "101abc";

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());

        Optional<ChargeResponse> chargeForAccount = chargeService.findChargeForAccount(externalChargeId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    void shouldFindChargeWithAuthUrl_whenChargeCreatedWithAuthorisationApi() throws URISyntaxException {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CREATED)
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build();

        String externalId = newCharge.getExternalId();

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(newCharge))
                .thenReturn(new ExternalTransactionState(EXTERNAL_CREATED.getStatus(), false));

        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo).get();

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(newCharge);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("auth_url_post", POST, new URI(SERVICE_HOST + "/v1/api/charges/authorise"), "application/json", new HashMap<>() {{
            put("one_time_token", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    void shouldFindChargeWithCaptureUrlAndNoNextUrl_whenChargeInAwaitingCaptureRequest() throws URISyntaxException {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AWAITING_CAPTURE_REQUEST)
                .build();

        String externalId = newCharge.getExternalId();

        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(newCharge))
                .thenReturn(new ExternalTransactionState(EXTERNAL_CAPTURABLE.getStatus(), false));

        Optional<ChargeResponse> chargeResponseForAccount = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(newCharge);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("capture", POST, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/capture"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    void shouldFindCharge_fromDbIfExists() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(chargeEntity));

        Optional<Charge> charge = chargeService.findCharge(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID);

        verifyNoInteractions(ledgerService);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
    }

    @Test
    void shouldFindCharge_fromLedgerIfNotExists() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(chargeEntity.getExternalId());
        transaction.setAmount(chargeEntity.getAmount());
        transaction.setCreatedDate(Instant.now().toString());
        transaction.setGatewayAccountId(String.valueOf(GATEWAY_ACCOUNT_ID));
        transaction.setLive(true);
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForGatewayAccount(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(transaction));

        Optional<Charge> charge = chargeService.findCharge(chargeEntity.getExternalId(), GATEWAY_ACCOUNT_ID);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
    }

    @Test
    void findByProviderAndTransactionIdFromDbOrLedger_fromDbIfExists() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox",
                chargeEntity.getExternalId()
        )).thenReturn(Optional.of(chargeEntity));

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        verifyNoInteractions(ledgerService);

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
    }

    @Test
    void findByProviderAndTransactionIdFromDbOrLedger_fromLedgerIfNotExists() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setTransactionId(chargeEntity.getExternalId());
        transaction.setAmount(chargeEntity.getAmount());
        transaction.setCreatedDate(Instant.now().toString());
        transaction.setGatewayAccountId(String.valueOf(GATEWAY_ACCOUNT_ID));
        transaction.setLive(true);
        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox",
                chargeEntity.getExternalId()
        )).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForProviderAndGatewayTransactionId("sandbox",
                chargeEntity.getExternalId())).thenReturn(Optional.of(transaction));

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        assertThat(charge.isPresent(), is(true));
        final Charge result = charge.get();
        assertThat(result.getAmount(), is(chargeEntity.getAmount()));
        assertThat(result.getExternalId(), is(chargeEntity.getExternalId()));
    }

    @Test
    void findByProviderAndTransactionIdFromDbOrLedger_shouldReturnEmptyOptionalIfChargeIsNotInDbOrLedger() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        when(mockedChargeDao.findByProviderAndTransactionId(
                "sandbox", chargeEntity.getExternalId()
        )).thenReturn(Optional.empty());

        when(ledgerService.getTransactionForProviderAndGatewayTransactionId("sandbox",
                chargeEntity.getExternalId())).thenReturn(Optional.empty());

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger("sandbox",
                chargeEntity.getExternalId());

        assertThat(charge.isPresent(), is(false));
    }

    private ChargeResponse.ChargeResponseBuilder chargeResponseBuilderOf(ChargeEntity chargeEntity) {
        ChargeResponse.RefundSummary refunds = new ChargeResponse.RefundSummary();
        refunds.setAmountAvailable(chargeEntity.getAmount());
        refunds.setAmountSubmitted(0L);
        refunds.setStatus(EXTERNAL_AVAILABLE.getStatus());

        ChargeResponse.SettlementSummary settlement = new ChargeResponse.SettlementSummary();
        settlement.setCapturedTime(null);
        settlement.setCaptureSubmitTime(null);

        ExternalChargeState externalChargeState = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal();
        return aChargeResponseBuilder()
                .withChargeId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()))
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getPaymentProvider())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withEmail(chargeEntity.getEmail())
                .withRefunds(refunds)
                .withSettlement(settlement)
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withLanguage(chargeEntity.getLanguage())
                .withMoto(chargeEntity.isMoto())
                .withAuthorisationMode(chargeEntity.getAuthorisationMode());
    }

}
