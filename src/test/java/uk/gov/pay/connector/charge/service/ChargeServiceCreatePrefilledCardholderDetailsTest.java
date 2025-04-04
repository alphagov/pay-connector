package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;
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
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;

import jakarta.ws.rs.core.UriInfo;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jakarta.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class ChargeServiceCreatePrefilledCardholderDetailsTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

    @Captor
    private ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

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
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");

        chargeService = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedAgreementDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory,
                objectMapper, null, fixedInstantSource);
    }

    @Test
    void shouldCreateAChargeWithAllPrefilledCardHolderDetails() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", null, "GB");
        cardHolderDetails.setAddress(address);
        final ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCountry(), is("GB"));
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is("Line2"));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
    }

    @Test
    void shouldCreateAChargeWithPrefilledCardHolderDetailsAndSomeAddressMissing() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);


        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is(nullValue()));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    void shouldCreateAChargeWithNoCountryWhenPrefilledAddressCountryIsMoreThanTwoCharacters() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", "county", "123");
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is("Line2"));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is("county"));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    void shouldCreateAChargeWithPrefilledCardHolderDetailsCardholderNameOnly() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(false));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
    }

    @Test
    void shouldCreateAChargeWhenPrefilledCardHolderDetailsCardholderNameAndSomeAddressNotPresent() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        var cardHolderDetails = new PrefilledCardHolderDetails();
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(true));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is(nullValue()));
        AddressEntity addressEntity = createdChargeEntity.getCardDetails().getBillingAddress().get();
        assertThat(addressEntity.getLine1(), is("Line1"));
        assertThat(addressEntity.getLine2(), is(nullValue()));
        assertThat(addressEntity.getPostcode(), is("AB1 CD2"));
        assertThat(addressEntity.getCity(), is("London"));
        assertThat(addressEntity.getCounty(), is(nullValue()));
        assertThat(addressEntity.getCountry(), is(nullValue()));
    }

    @Test
    void shouldCreateAChargeWhenPrefilledCardHolderDetailsAreNotPresent() {
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockedUriInfo).getBaseUriBuilder();
        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        when(mockGatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount)).thenReturn(gatewayAccountCredentialsEntity);

        ChargeCreateRequest request = requestBuilder.build();
        chargeService.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo, null);

        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getCardDetails(), is(nullValue()));
    }

}
