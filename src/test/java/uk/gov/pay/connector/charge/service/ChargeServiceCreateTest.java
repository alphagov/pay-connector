package uk.gov.pay.connector.charge.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeException;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationApiNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.service.CardidService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
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
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.time.ZonedDateTime.now;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity.PaymentInstrumentEntityBuilder.aPaymentInstrumentEntity;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

@ExtendWith(MockitoExtension.class)
class ChargeServiceCreateTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final long CHARGE_ENTITY_ID = 12345L;
    private static final String[] EXTERNAL_CHARGE_ID = new String[1];
    private static final String AGREEMENT_ID = "agreement-id";

    private static final String CREDENTIALS_EXTERNAL_ID = "credentials-external-id";
    private static final List<Map<String, Object>> EMPTY_LINKS = new ArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    private ChargeCreateRequestBuilder requestBuilder;
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
    private CaptureProcessConfig mockedCaptureProcessConfig;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;

    @Mock
    private IdempotencyDao mockIdempotencyDao;
    @Mock
    private CardidService mockCardidService;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Mock
    Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;

    @Captor
    ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor;

    @Captor
    private ArgumentCaptor<IdempotencyEntity> idempotencyEntityArgumentCaptor;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
    private ExternalTransactionState externalTransactionState;

    @BeforeEach
    void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("http://return-service.com")
                .withDescription("This is a description")
                .withReference("Pay reference");

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

        gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
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

        when(mockedConfig.getCaptureProcessConfig()).thenReturn(mockedCaptureProcessConfig);
        when(mockedConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        externalTransactionState = new ExternalTransactionState(EXTERNAL_CREATED.getStatus(), false);

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory,
                mapper, mockCardidService, fixedInstantSource);
    }

    @Test
    void shouldReturnAResponseForExistingCharge() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        ExternalMetadata externalMetadata = new ExternalMetadata(
                Map.of(
                        "created_date", "2018-02-21T16:04:25Z",
                        "authorised_date", "2018-02-21T16:05:33Z",
                        "processor_id", "1PROC",
                        "auth_code", "666",
                        "telephone_number", "+447700900796",
                        "status", "success"
                )
        );

        CardDetailsEntity cardDetails = new CardDetailsEntity(
                LastDigitsCardNumber.of("1234"),
                FirstDigitsCardNumber.of("123456"),
                "Jane Doe",
                CardExpiryDate.valueOf("01/19"),
                "visa",
                CardType.valueOf("DEBIT")
        );

        ChargeEntity returnedChargeEntity = aValidChargeEntity()
                .withAmount(100L)
                .withDescription("Some description")
                .withReference(ServicePaymentReference.of("Some reference"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(gatewayAccount.getGatewayName())
                .withEmail("jane.doe@example.com")
                .withExternalMetadata(externalMetadata)
                .withSource(CARD_API)
                .withStatus(AUTHORISATION_SUCCESS)
                .withGatewayTransactionId("1PROV")
                .withCardDetails(cardDetails)
                .withServiceId("a-valid-external-service-id")
                .build();

        when(mockedChargeDao.findByGatewayTransactionIdAndAccount(gatewayAccount.getId(), "1PROV"))
                .thenReturn(Optional.of(returnedChargeEntity));

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        Optional<ChargeResponse> telephoneChargeResponse = chargeService.findCharge(gatewayAccount.getId(), telephoneChargeCreateRequest);

        ArgumentCaptor<String> gatewayTransactionIdArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByGatewayTransactionIdAndAccount(anyLong(), gatewayTransactionIdArgumentCaptor.capture());

        String providerId = gatewayTransactionIdArgumentCaptor.getValue();
        assertThat(providerId, is("1PROV"));
        assertThat(telephoneChargeResponse.isPresent(), is(true));
        assertThat(telephoneChargeResponse.get().getAmount(), is(100L));
        assertThat(telephoneChargeResponse.get().getReference().toString(), is("Some reference"));
        assertThat(telephoneChargeResponse.get().getDescription(), is("Some description"));
        assertThat(telephoneChargeResponse.get().getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(telephoneChargeResponse.get().getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(telephoneChargeResponse.get().getAuthCode(), is("666"));
        assertThat(telephoneChargeResponse.get().getPaymentOutcome().getStatus(), is("success"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getCardBrand(), is("visa"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(telephoneChargeResponse.get().getEmail(), is("jane.doe@example.com"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getExpiryDate().toString(), is("01/19"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(telephoneChargeResponse.get().getTelephoneNumber(), is("+447700900796"));
        assertThat(telephoneChargeResponse.get().getDataLinks(), is(EMPTY_LINKS));
        assertThat(telephoneChargeResponse.get().getDelayedCapture(), is(false));
        assertThat(telephoneChargeResponse.get().getChargeId().length(), is(26));
        assertThat(telephoneChargeResponse.get().getState().getStatus(), is("success"));
        assertThat(telephoneChargeResponse.get().getState().isFinished(), is(true));
    }

    @Test
    void shouldCreateAChargeWithDefaults() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        populateChargeEntity();

        chargeService.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));
        assertThat(createdChargeEntity.getStatus(), is("CREATED"));
        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Pay reference")));
        assertThat(createdChargeEntity.getDescription(), is("This is a description"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
        assertThat(createdChargeEntity.isDelayedCapture(), is(false));
        assertThat(createdChargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(createdChargeEntity.getWalletType(), is(nullValue()));
        assertThat(createdChargeEntity.isMoto(), is(false));
        assertThat(createdChargeEntity.getAuthorisationMode(), is(AuthorisationMode.WEB));
        assertThat(createdChargeEntity.getAgreementPaymentType(), is(nullValue()));

        verify(mockedChargeEventDao).persistChargeEventOf(eq(createdChargeEntity), isNull());
    }

    @Test
    void shouldCreateAChargeWithBlankEmailAddress() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.withEmail("").build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getEmail(), is(nullValue()));
    }

    @Test
    void shouldCreateAChargeWithDelayedCaptureTrue() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(true).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    void shouldCreateAChargeWithDelayedCaptureFalse() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(false).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    void shouldCreateAChargeWithExternalMetadata() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.23);
        final ChargeCreateRequest request = requestBuilder.withExternalMetadata(new ExternalMetadata(metadata)).build();

        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getExternalMetadata().get().getMetadata(), equalTo(metadata));
    }

    @Test
    void shouldCreateAChargeWithNonDefaultLanguage() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.withLanguage(SupportedLanguage.WELSH).build();

        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.WELSH));
    }

    @Test
    void shouldCreateChargeWithZeroAmountIfGatewayAccountAllowsIt() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        gatewayAccount.setAllowZeroAmount(true);

        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();

        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getAmount(), is(0L));
    }

    @Test
    void shouldThrowExceptionWhenCreateChargeWithZeroAmountIfGatewayAccountDoesNotAllowIt() {
        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        
        assertThrows(ZeroAmountNotAllowedForGatewayAccountException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));
        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldCreateMotoChargeIfGatewayAccountAllowsIt() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        gatewayAccount.setAllowMoto(true);
        ChargeCreateRequest request = requestBuilder.withMoto(true).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.isMoto(), is(true));
    }

    @Test
    void shouldThrowExceptionWhenCreateMotoChargeIfGatewayAccountDoesNotAllowIt() {
        ChargeCreateRequest request = requestBuilder.withMoto(true).build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(ChargeException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldCreateAChargeWithSource() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.
                withSource(CARD_API).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_API));
    }

    @Test
    void shouldCreateAChargeWhenGatewayAccountCredentialsHasOneEntry() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.
                withSource(CARD_API).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
    }

    @Test
    void shouldCreateAChargeWhenCredentialEntityFoundForCredentialExternalId() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        when(mockGatewayAccountCredentialsService.getCredentialInUsableState(CREDENTIALS_EXTERNAL_ID, gatewayAccount.getId()))
                .thenReturn(gatewayAccountCredentialsEntity);

        final ChargeCreateRequest request = requestBuilder.withCredentialId(CREDENTIALS_EXTERNAL_ID).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
    }

    @Test
    void shouldCreateAResponse() throws Exception {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockExternalTransactionStateFactory.newExternalTransactionState(any(ChargeEntity.class))).thenReturn(externalTransactionState);
        populateChargeEntity();

        ChargeResponse response = chargeService.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo, null).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();

        // Then - expected response is returned
        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://frontend.test/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://frontend.test/secure"), "application/x-www-form-urlencoded", new HashMap<>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    void shouldCreateACharge_whenAuthorisationApiEnabled_andMotoDisabled() throws Exception {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockExternalTransactionStateFactory.newExternalTransactionState(any(ChargeEntity.class))).thenReturn(externalTransactionState);
        gatewayAccount.setAllowAuthorisationApi(true);
        gatewayAccount.setAllowMoto(false);
        populateChargeEntity();

        ChargeCreateRequest chargeCreateRequest = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withDescription("This is a description")
                .withReference("Pay reference")
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build();

        ChargeResponse response = chargeService.create(chargeCreateRequest, GATEWAY_ACCOUNT_ID, mockedUriInfo, null).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();

        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));
        expectedChargeResponse.withLink("auth_url_post", POST, new URI(SERVICE_HOST + "/v1/api/charges/authorise"), "application/json", new HashMap<>() {{
            put("one_time_token", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    void shouldThrowException_whenAuthorisationApiDisabled_andChargeCreationAttempted_withApiAuthorisation() {
        gatewayAccount.setAllowAuthorisationApi(false);
        ChargeCreateRequest request = requestBuilder.withAuthorisationMode(AuthorisationMode.MOTO_API).build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(AuthorisationApiNotAllowedForGatewayAccountException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    void shouldCreateAToken() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        populateChargeEntity();

        chargeService.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(CHARGE_ENTITY_ID));
        assertThat(tokenEntity.getToken(), is(notNullValue()));
        assertThat(tokenEntity.isUsed(), is(false));
    }

    @Test
    void shouldThrowException_whenGatewayAccountDisabled() {
        gatewayAccount.setDisabled(true);
        ChargeCreateRequest request = requestBuilder.build();

        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        assertThrows(GatewayAccountDisabledException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = Source.class, names = { "CARD_API", "CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO" })
    void shouldThrowException_whenPaymentProviderIsStripeAndAmountUnder30Pence(Source source) {
        GatewayAccountCredentialsEntity stripeGatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider("stripe")
                .withCredentials(Map.of())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();
        ChargeCreateRequest request = requestBuilder
                .withAmount(29)
                .withSource(source)
                .build();
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(stripeGatewayAccountCredentialsEntity);
        assertThrows(ChargeException.class, () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));
        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }
    
    @Test
    void shouldCreateNewChargeAndPersistIdempotency() throws URISyntaxException {
        String idempotencyKey = "idempotency-key";
        ChargeCreateRequest chargeCreateRequest = requestBuilder
                .withAgreementId(AGREEMENT_ID)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withReturnUrl(null)
                .build();

        gatewayAccount.setRecurringEnabled(true);

        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        AgreementEntity agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrument)
                .build();
        Map<String, Object> requestBody = mapper.convertValue(chargeCreateRequest, new TypeReference<>() {
        });

        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        when(mockedAgreementDao.findByExternalIdAndGatewayAccountId(AGREEMENT_ID, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(agreementEntity));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(any(ChargeEntity.class))).thenReturn(externalTransactionState);
        populateChargeEntity();

        ChargeResponse response = chargeService.create(chargeCreateRequest, GATEWAY_ACCOUNT_ID, mockedUriInfo, idempotencyKey).get();

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockIdempotencyDao).persist(idempotencyEntityArgumentCaptor.capture());

        IdempotencyEntity idempotency = idempotencyEntityArgumentCaptor.getValue();
        assertThat(idempotency.getRequestBody(), is(requestBody));

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        ChargeResponse.ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));

        assertThat(response, is(expectedChargeResponse.build()));
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

    private void populateChargeEntity() {
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(CHARGE_ENTITY_ID);
            EXTERNAL_CHARGE_ID[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(mockedChargeDao).persist(any(ChargeEntity.class));
    }

    @Nested
    class TestCardNumberInReference {

        private static final String VALID_CARD_NUMBER = "4242424242424242";

        @Test
        void shouldCreateChargeForCardNumberInReferenceIfFlagIsDisabled() {
            setupMocksToCreateACharge();
            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(false);

            chargeService = getNewChargeService();

            final ChargeCreateRequest request = requestBuilder
                    .withSource(CARD_API)
                    .withReference(VALID_CARD_NUMBER)
                    .build();
            chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

            verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
            assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_API));
        }

        @Test
        void shouldCreateChargeWhenReferenceIsACardNumber_ForAPIPaymentsAndWhenFlagIsEnabled() {
            setupMocksToCreateACharge();

            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(true);

            final ChargeCreateRequest request = requestBuilder
                    .withSource(CARD_API)
                    .withReference(VALID_CARD_NUMBER)
                    .build();

            chargeService = getNewChargeService();
            chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

            verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
            assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_API));
        }

        @ParameterizedTest
        @ValueSource(strings = {"CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO"})
        void shouldCreateChargeWhenReferenceIsACardNumber_ForPaymentPaymentLinkPaymentsAndWhenFlagIsDisabled(String source) {
            setupMocksToCreateACharge();

            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(true);

            final ChargeCreateRequest request = requestBuilder
                    .withSource(Source.from(source).get())
                    .withReference(VALID_CARD_NUMBER)
                    .build();

            chargeService = getNewChargeService();
            chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

            verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
            assertThat(chargeEntityArgumentCaptor.getValue().getSource().name(), equalTo(source));
        }

        @ParameterizedTest
        @ValueSource(strings = {"35680319460", "35680220691985608460"})
        void shouldCreateChargeWhenReferenceLengthIsOutsideMinAndMaxLengthAndPassLuhnCheck_ForPaymentLinkPayments(String cardNumber) {
            setupMocksToCreateACharge();

            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(true);

            final ChargeCreateRequest request = requestBuilder
                    .withSource(CARD_PAYMENT_LINK)
                    .withReference(cardNumber)
                    .build();

            chargeService = getNewChargeService();
            chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

            verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
            assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_PAYMENT_LINK));
        }

        @Test
        void shouldCreateChargeWhenReferencePassesLuhnCheckButIsNotACardNumber_ForPaymentLinkPayment() {
            setupMocksToCreateACharge();

            when(mockCardidService.getCardInformation("4242425554678549")).thenReturn(Optional.empty());
            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(true);

            final ChargeCreateRequest request = requestBuilder
                    .withSource(CARD_PAYMENT_LINK)
                    .withReference("4242425554678549")
                    .build();

            chargeService = getNewChargeService();
            chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

            verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
            assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_PAYMENT_LINK));
        }

        @ParameterizedTest
        @ValueSource(strings = {"42 42 4242 4242 4242", "4242-4242-42-42-4242",
                "430350381314", "4492616950176228242",
                VALID_CARD_NUMBER})
        void shouldNotCreateChargeWhenReferenceIsACardNumber_ForPaymentLinkPaymentsAndWhenFlagIsEnabled(String validCardNumber) {
            Logger root = (Logger) LoggerFactory.getLogger(ChargeService.class);
            root.setLevel(Level.INFO);
            root.addAppender(mockAppender);

            when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
            CardInformation cardInformation = aCardInformation().build();

            when(mockCardidService.getCardInformation(validCardNumber.replaceAll("[ -]", ""))).thenReturn(Optional.of(cardInformation));
            when(mockedConfig.getRejectPaymentLinkPaymentsWithCardNumberInReference()).thenReturn(true);

            final ChargeCreateRequest request = requestBuilder
                    .withSource(CARD_PAYMENT_LINK)
                    .withReference(validCardNumber)
                    .build();

            chargeService = getNewChargeService();

            var exception = assertThrows(ChargeException.class,
                    () -> chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null));

            verifyNoInteractions(mockedChargeDao);
            assertThat(exception.getMessage(), equalTo("Card number entered in a payment link reference"));

            verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents.stream().map(LoggingEvent::getFormattedMessage).collect(Collectors.toList()),
                    hasItems("Card number entered in a payment link reference"));
        }

        private ChargeService getNewChargeService() {
            return new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                    mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                    mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                    mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                    mockTaskQueueService, null, mockIdempotencyDao, mockExternalTransactionStateFactory,
                    mapper, mockCardidService, fixedInstantSource);
        }

        private void setupMocksToCreateACharge() {
            doAnswer(invocation -> fromUri(SERVICE_HOST)).when(mockedUriInfo).getBaseUriBuilder();
            when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
            when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
            when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
            when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
            when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);
        }
    }

}
