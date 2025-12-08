package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
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
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.time.ZonedDateTime.now;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;

@ExtendWith(MockitoExtension.class)
class ChargeServiceCreateTelephonePaymentTest {

    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final long CHARGE_ENTITY_ID = 12345L;
    private static final String[] EXTERNAL_CHARGE_ID = new String[1];
    private static final List<Map<String, Object>> EMPTY_LINKS = new ArrayList<>();
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

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;

    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    @Captor
    private ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

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

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory,
                objectMapper, null, fixedInstantSource);
    }

    @Test
    void shouldCreateATelephoneChargeForSuccess() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockGatewayAccountCredentialsService).getCurrentOrActiveCredential(gatewayAccount);

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("CAPTURE SUBMITTED"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC),
                is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate().toString(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getCardDetails().getCardType(), is(nullValue()));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    void shouldCreateATelephoneChargeForFailureCodeOfP0010() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0010", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "failed",
                "code", "P0010",
                "error_code", "ECKOH01234",
                "error_message", "textual message describing error code"
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC),
                is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    void shouldCreateATelephoneChargeForFailureCodeOfP0050() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", "1PROC",
                "auth_code", "666",
                "telephone_number", "+447700900796",
                "status", "failed",
                "code", "P0050",
                "error_code", "ECKOH01234",
                "error_message", "textual message describing error code"
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC),
                is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    void shouldCreateATelephoneChargeAndTruncateMetaDataOver50Characters() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        String stringGreaterThan50 = StringUtils.repeat("*", 51);
        String stringOf50 = StringUtils.repeat("*", 50);

        Supplemental supplemental = new Supplemental(stringGreaterThan50, stringGreaterThan50);
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", stringOf50,
                "auth_code", stringOf50,
                "telephone_number", stringOf50,
                "status", "failed",
                "code", "P0050",
                "error_code", stringOf50,
                "error_message", stringOf50
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withProcessorId(stringGreaterThan50)
                .withAuthCode(stringGreaterThan50)
                .withTelephoneNumber(stringGreaterThan50)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC),
                is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate().toString(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    void shouldCreateATelephoneChargeAndNotTruncateMetaDataOf50Characters() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        String stringOf50 = StringUtils.repeat("*", 50);

        Supplemental supplemental = new Supplemental(stringOf50, stringOf50);
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0050", supplemental);

        Map<String, Object> metadata = Map.of(
                "created_date", "2018-02-21T16:04:25Z",
                "authorised_date", "2018-02-21T16:05:33Z",
                "processor_id", stringOf50,
                "auth_code", stringOf50,
                "telephone_number", stringOf50,
                "status", "failed",
                "code", "P0050",
                "error_code", stringOf50,
                "error_message", stringOf50
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withProcessorId(stringOf50)
                .withAuthCode(stringOf50)
                .withTelephoneNumber(stringOf50)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(ZonedDateTime.ofInstant(createdChargeEntity.getCreatedDate(), ZoneOffset.UTC),
                is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneOffset.UTC))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate().toString(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    void shouldCreateAnExternalTelephoneChargeWithSource() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeEntityArgumentCaptor.getValue().getSource(), equalTo(CARD_EXTERNAL_TELEPHONE));
    }

    @Test
    void shouldCreateATelephoneChargeResponseForSuccess() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        ChargeResponse chargeResponse = chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        verify(mockGatewayAccountCredentialsService).getCurrentOrActiveCredential(gatewayAccount);

        assertThat(chargeResponse.getAmount(), is(100L));
        assertThat(chargeResponse.getReference().toString(), is("Some reference"));
        assertThat(chargeResponse.getDescription(), is("Some description"));
        assertThat(chargeResponse.getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(chargeResponse.getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(chargeResponse.getAuthCode(), is("666"));
        assertThat(chargeResponse.getPaymentOutcome().getStatus(), is("success"));
        assertThat(chargeResponse.getCardDetails().getCardBrand(), is("visa"));
        assertThat(chargeResponse.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(chargeResponse.getEmail(), is("jane.doe@example.com"));
        assertThat(chargeResponse.getCardDetails().getExpiryDate().toString(), is("01/19"));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getDataLinks(), is(EMPTY_LINKS));
        assertThat(chargeResponse.getDelayedCapture(), is(false));
        assertThat(chargeResponse.getChargeId().length(), is(26));
        assertThat(chargeResponse.getState().getStatus(), is("success"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
    }

    @Test
    void shouldCreateATelephoneChargeResponseForFailure() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0010", supplemental);

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .withCardExpiry(null)
                .build();

        ChargeResponse chargeResponse = chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeResponse.getAmount(), is(100L));
        assertThat(chargeResponse.getReference().toString(), is("Some reference"));
        assertThat(chargeResponse.getDescription(), is("Some description"));
        assertThat(chargeResponse.getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(chargeResponse.getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(chargeResponse.getAuthCode(), is("666"));
        assertThat(chargeResponse.getPaymentOutcome().getStatus(), is("failed"));
        assertThat(chargeResponse.getPaymentOutcome().getCode().get(), is("P0010"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().get().getErrorCode().get(), is("ECKOH01234"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().get().getErrorMessage().get(), is("textual message describing error code"));
        assertThat(chargeResponse.getCardDetails().getCardBrand(), is("visa"));
        assertThat(chargeResponse.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(chargeResponse.getEmail(), is("jane.doe@example.com"));
        assertThat(chargeResponse.getCardDetails().getExpiryDate(), is(nullValue()));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getDataLinks(), is(EMPTY_LINKS));
        assertThat(chargeResponse.getDelayedCapture(), is(false));
        assertThat(chargeResponse.getChargeId().length(), is(26));
        assertThat(chargeResponse.getState().getStatus(), is("failed"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
        assertThat(chargeResponse.getState().getMessage(), is("Payment method rejected"));
        assertThat(chargeResponse.getState().getCode(), is("P0010"));
    }

    @Test
    void shouldCreateATelephoneChargeWhenGatewayAccountCredentialsHasOneEntry() {
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount))
                .thenReturn(gatewayAccountCredentialsEntity);

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        populateChargeEntity();

        chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getPaymentProvider(), is("sandbox"));
    }

    private void populateChargeEntity() {
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(CHARGE_ENTITY_ID);
            EXTERNAL_CHARGE_ID[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(mockedChargeDao).persist(any(ChargeEntity.class));
    }

}
