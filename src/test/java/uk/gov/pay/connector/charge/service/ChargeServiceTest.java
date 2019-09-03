package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.ChargeResponse.ChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static final long CHARGE_ENTITY_ID = 12345L;
    private static final String[] EXTERNAL_CHARGE_ID = new String[1];
    private static final int RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS = 1;
    private static final int MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS = 10;

    private ChargeCreateRequestBuilder requestBuilder;
    private TelephoneChargeCreateRequest.Builder telephoneRequestBuilder;
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private TokenDao mockedTokenDao;
    @Mock
    private ChargeDao mockedChargeDao;
    @Mock
    private ChargeEventDao mockedChargeEventDao;
    @Mock
    private ChargeEventEntity mockChargeEvent;


    @Mock
    private GatewayAccountDao mockedGatewayAccountDao;
    @Mock
    private CardTypeDao mockedCardTypeDao;
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
    private EventQueue mockedEventQueue;

    @Mock
    private StateTransitionQueue mockedStateTransitionQueue;

    private ChargeService service;

    private GatewayAccountEntity gatewayAccount;
    
    private ChargeEntity returnedChargeEntity;

    @Before
    public void setUp() {
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
                .withCardExpiry("01/19")
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456");

        gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        when(mockedChargeEventDao.persistChargeEventOf(any(), any())).thenReturn(mockChargeEvent);
        
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
                "01/19",
                "visa"
        );
        
        returnedChargeEntity = new ChargeEntity(
                100L,
                ServicePaymentReference.of("Some reference"),
                "Some description",
                AUTHORISATION_SUCCESS,
                "jane.doe@example.com",
                cardDetails,
                externalMetadata,
                gatewayAccount,
                "1PROV",
                SupportedLanguage.ENGLISH
        );
        
        when(mockedChargeDao.findByProviderSessionId("1PROV")).thenReturn(Optional.of(returnedChargeEntity));
        when(mockedChargeDao.findByProviderSessionId("new")).thenReturn(Optional.empty());

        // Populate ChargeEntity with ID when persisting
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(CHARGE_ENTITY_ID);
            EXTERNAL_CHARGE_ID[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(mockedChargeDao).persist(any(ChargeEntity.class));

        when(mockedConfig.getLinks())
                .thenReturn(mockedLinksConfig);

        CaptureProcessConfig mockedCaptureProcessConfig = mock(CaptureProcessConfig.class);
        when(mockedCaptureProcessConfig.getMaximumRetries()).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS);
        when(mockedConfig.getCaptureProcessConfig()).thenReturn(mockedCaptureProcessConfig);
        when(mockedLinksConfig.getFrontendUrl())
                .thenReturn("http://payments.com");

        doAnswer(invocation -> fromUri(SERVICE_HOST))
                .when(this.mockedUriInfo)
                .getBaseUriBuilder();

        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(ChargeEntity.class))).thenReturn(EXTERNAL_AVAILABLE);
        when(mockedConfig.getEmitPaymentStateTransitionEvents()).thenReturn(true);

        service = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedGatewayAccountDao, mockedConfig, mockedProviders, mockedStateTransitionQueue, mockedEventQueue);
    }

    @Test
    public void shouldCreateAChargeWithDefaultLanguageAndDefaultDelayedCapture() {
        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));
        assertThat(createdChargeEntity.getStatus(), is("CREATED"));
        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Pay reference")));
        assertThat(createdChargeEntity.getDescription(), is("This is a description"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
        assertThat(createdChargeEntity.isDelayedCapture(), is(false));
        assertThat(createdChargeEntity.getCorporateSurcharge().isPresent(), is(false));
        assertThat(createdChargeEntity.getWalletType(), is(nullValue()));

        verify(mockedChargeEventDao).persistChargeEventOf(eq(createdChargeEntity), isNull());
    }

    @Test
    public void shouldCreateAChargeWithDelayedCaptureTrue() {
        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(true).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    public void shouldCreateAChargeWithDelayedCaptureFalse() {
        final ChargeCreateRequest request = requestBuilder.withDelayedCapture(false).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
    }

    @Test
    public void shouldCreateAChargeWithExternalMetadata() {
        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.23);
        final ChargeCreateRequest request = requestBuilder.withExternalMetadata(new ExternalMetadata(metadata)).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        assertThat(chargeEntityArgumentCaptor.getValue().getExternalMetadata().get().getMetadata(), equalTo(metadata));
    }

    @Test
    public void shouldCreateAChargeWithNonDefaultLanguage() {
        final ChargeCreateRequest request = requestBuilder.withLanguage(SupportedLanguage.WELSH).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.WELSH));
    }

    @Test
    public void shouldCreateChargeWithZeroAmountIfGatewayAccountAllowsIt() {
        gatewayAccount.setAllowZeroAmount(true);

        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getAmount(), is(0L));
    }

    @Test(expected = ZeroAmountNotAllowedForGatewayAccountException.class)
    public void shouldThrowExceptionWhenCreateChargeWithZeroAmountIfGatewayAccountDoesNotAllowIt() {
        final ChargeCreateRequest request = requestBuilder.withAmount(0).build();

        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedChargeDao, never()).persist(any(ChargeEntity.class));
    }

    @Test
    public void shouldUpdateEmailToCharge() {
        ChargeEntity createdChargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        final String chargeEntityExternalId = createdChargeEntity.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId))
                .thenReturn(Optional.of(createdChargeEntity));

        final String expectedEmail = "test@examplecom";
        PatchRequestBuilder.PatchRequest patchRequest = PatchRequestBuilder.aPatchRequestBuilder(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "email",
                        "value", expectedEmail))
                .withValidOps(singletonList("replace"))
                .withValidPaths(ImmutableSet.of("email"))
                .build();

        service.updateCharge(chargeEntityExternalId, patchRequest);
    }

    @Test
    public void shouldCreateAChargeWithAllPrefilledCardHolderDetails() {
        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", null, "GB");
        cardHolderDetails.setAddress(address);
        final ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
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
    public void shouldCreateAChargeWithPrefilledCardHolderDetailsAndSomeAddressMissing() {
        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
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
    public void shouldCreateAChargeWithNoCountryWhenPrefilledAddressCountryIsMoreThanTwoCharacters() {
        var cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        var address = new PrefilledAddress("Line1", "Line2", "AB1 CD2", "London", "county", "123");
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
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
    public void shouldCreateAChargeWithPrefilledCardHolderDetailsCardholderNameOnly() {
        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        assertThat(createdChargeEntity.getCardDetails(), is(notNullValue()));
        assertThat(createdChargeEntity.getCardDetails().getBillingAddress().isPresent(), is(false));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Joe Bogs"));
    }

    @Test
    public void shouldCreateAChargeWhenPrefilledCardHolderDetailsCardholderNameAndSomeAddressNotPresent() {
        var cardHolderDetails = new PrefilledCardHolderDetails();
        var address = new PrefilledAddress("Line1", null, "AB1 CD2", "London", null, null);
        cardHolderDetails.setAddress(address);

        ChargeCreateRequest request = requestBuilder.withPrefilledCardHolderDetails(cardHolderDetails).build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
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
    public void shouldCreateAChargeWhenPrefilledCardHolderDetailsAreNotPresent() {
        ChargeCreateRequest request = requestBuilder.build();
        service.create(request, GATEWAY_ACCOUNT_ID, mockedUriInfo);
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());
        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getCardDetails(), is(nullValue()));
    }

    @Test
    public void shouldCreateAToken() {
        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(CHARGE_ENTITY_ID));
        assertThat(tokenEntity.getToken(), is(notNullValue()));
    }

    @Test
    public void shouldCreateATelephoneChargeForSuccess() {
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

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getProviderSessionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeForFailureCodeOfP0010() {
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
                .build();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getProviderSessionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldCreateATelephoneChargeForFailureCodeOfP0050() {
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
                .build();

        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));

        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getReference(), is(ServicePaymentReference.of("Some reference")));
        assertThat(createdChargeEntity.getDescription(), is("Some description"));
        assertThat(createdChargeEntity.getStatus(), is("AUTHORISATION ERROR"));
        assertThat(createdChargeEntity.getEmail(), is("jane.doe@example.com"));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
        assertThat(createdChargeEntity.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(createdChargeEntity.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(createdChargeEntity.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(createdChargeEntity.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(createdChargeEntity.getCardDetails().getCardBrand(), is("visa"));
        assertThat(createdChargeEntity.getProviderSessionId(), is("1PROV"));
        assertThat(createdChargeEntity.getExternalMetadata().get().getMetadata(), equalTo(metadata));
        assertThat(createdChargeEntity.getLanguage(), is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void shouldNotFindCharge() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withProviderId("new")
                .withPaymentOutcome(paymentOutcome)
                .build();

        Optional<ChargeResponse> telephoneChargeResponse = service.findCharge(telephoneChargeCreateRequest);

        ArgumentCaptor<String> chargeEntityArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByProviderSessionId(chargeEntityArgumentCaptor.capture());

        String providerId = chargeEntityArgumentCaptor.getValue();
        assertThat(providerId, is("new"));
        assertThat(telephoneChargeResponse.isPresent(), is(false));
    }

    @Test
    public void shouldReturnAResponseForExistingCharge() {
        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();
        
        service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID);

        Optional<ChargeResponse> telephoneChargeResponse = service.findCharge(telephoneChargeCreateRequest);

        ArgumentCaptor<String> chargeEntityArgumentCaptor = forClass(String.class);
        verify(mockedChargeDao).findByProviderSessionId(chargeEntityArgumentCaptor.capture());

        String providerId = chargeEntityArgumentCaptor.getValue();
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
        assertThat(telephoneChargeResponse.get().getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(telephoneChargeResponse.get().getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(telephoneChargeResponse.get().getTelephoneNumber(), is("+447700900796"));
        assertThat(telephoneChargeResponse.get().getChargeId(), is("dummypaymentid123notpersisted"));
        assertThat(telephoneChargeResponse.get().getState().getStatus(), is("success"));
        assertThat(telephoneChargeResponse.get().getState().isFinished(), is(true));
    }

    @Test
    public void shouldCreateATelephoneChargeResponseForSuccess() {

        PaymentOutcome paymentOutcome = new PaymentOutcome("success");

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        ChargeResponse chargeResponse = service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID).get();

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

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
        assertThat(chargeResponse.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getChargeId(), is("dummypaymentid123notpersisted"));
        assertThat(chargeResponse.getState().getStatus(), is("success"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
    }

    @Test
    public void shouldCreateATelephoneChargeResponseForFailure() {

        Supplemental supplemental = new Supplemental("ECKOH01234", "textual message describing error code");
        PaymentOutcome paymentOutcome = new PaymentOutcome("failed", "P0010", supplemental);

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        ChargeResponse chargeResponse = service.create(telephoneChargeCreateRequest, GATEWAY_ACCOUNT_ID).get();

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        assertThat(chargeResponse.getAmount(), is(100L));
        assertThat(chargeResponse.getReference().toString(), is("Some reference"));
        assertThat(chargeResponse.getDescription(), is("Some description"));
        assertThat(chargeResponse.getCreatedDate().toString(), is("2018-02-21T16:04:25Z"));
        assertThat(chargeResponse.getAuthorisedDate().toString(), is("2018-02-21T16:05:33Z"));
        assertThat(chargeResponse.getAuthCode(), is("666"));
        assertThat(chargeResponse.getPaymentOutcome().getStatus(), is("failed"));
        assertThat(chargeResponse.getPaymentOutcome().getCode(), is("P0010"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().getErrorCode(), is("ECKOH01234"));
        assertThat(chargeResponse.getPaymentOutcome().getSupplemental().getErrorMessage(), is("textual message describing error code"));
        assertThat(chargeResponse.getCardDetails().getCardBrand(), is("visa"));
        assertThat(chargeResponse.getCardDetails().getCardHolderName(), is("Jane Doe"));
        assertThat(chargeResponse.getEmail(), is("jane.doe@example.com"));
        assertThat(chargeResponse.getCardDetails().getExpiryDate(), is("01/19"));
        assertThat(chargeResponse.getCardDetails().getLastDigitsCardNumber().toString(), is("1234"));
        assertThat(chargeResponse.getCardDetails().getFirstDigitsCardNumber().toString(), is("123456"));
        assertThat(chargeResponse.getTelephoneNumber(), is("+447700900796"));
        assertThat(chargeResponse.getChargeId(), is("dummypaymentid123notpersisted"));
        assertThat(chargeResponse.getState().getStatus(), is("failed"));
        assertThat(chargeResponse.getState().isFinished(), is(true));
        assertThat(chargeResponse.getState().getMessage(), is("error message"));
        assertThat(chargeResponse.getState().getCode(), is("P0010"));
    }

    @Test
    public void shouldCreateAResponse() throws Exception {
        ChargeResponse response = service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo).get();

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();

        // Then - expected response is returned
        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsCreated() throws Exception {
        shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(CREATED);
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsInProgress() throws Exception {
        shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(ENTERING_CARD_DETAILS);
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsAuthorisationReady_andNoCorporateSurcharge() throws Exception {
        shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(AUTHORISATION_READY);
    }

    @Test
    public void shouldFindChargeForChargeId_withCorporateSurcharge() {
        Long chargeId = 101L;
        Long totalAmount = 1250L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY)
                .withAmount(1000L)
                .withCorporateSurcharge(250L)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(250L));
        assertThat(chargeResponse.getTotalAmount(), is(totalAmount));
        assertThat(chargeResponse.getRefundSummary().getAmountAvailable(), is(totalAmount));
    }

    @Test
    public void shouldFindChargeForChargeId_withFee() {
        Long chargeId = 101L;
        Long amount = 1000L;
        Long fee = 100L;

        ChargeEntity charge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY)
                .withAmount(amount)
                .withFee(fee)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(charge);

        String externalId = charge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();

        assertThat(chargeResponse.getNetAmount(), is(amount - fee));
        assertThat(chargeResponse.getAmount(), is(amount));
    }

    private void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIs(ChargeStatus status) throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(status)
                .withWalletType(WalletType.APPLE_PAY)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponseBuilder chargeResponseWithoutCorporateCardSurcharge = chargeResponseBuilderOf(chargeEntity.get());
        chargeResponseWithoutCorporateCardSurcharge.withWalletType(WalletType.APPLE_PAY);
        chargeResponseWithoutCorporateCardSurcharge.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        chargeResponseWithoutCorporateCardSurcharge.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url", GET, new URI("http://payments.com/secure/" + tokenEntity.getToken()));
        chargeResponseWithoutCorporateCardSurcharge.withLink("next_url_post", POST, new URI("http://payments.com/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(chargeResponseForAccount.isPresent(), is(true));
        final ChargeResponse chargeResponse = chargeResponseForAccount.get();
        assertThat(chargeResponse.getCorporateCardSurcharge(), is(nullValue()));
        assertThat(chargeResponse.getTotalAmount(), is(nullValue()));
        assertThat(chargeResponse, is(chargeResponseWithoutCorporateCardSurcharge.build()));
        assertThat(chargeResponse.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeCannotBeResumed() throws Exception {
        shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeStatusIs(CAPTURED);
    }

    private void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeStatusIs(ChargeStatus status) throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(status)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {
        String externalChargeId = "101abc";
        Optional<ChargeEntity> nonExistingCharge = Optional.empty();

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, GATEWAY_ACCOUNT_ID)).thenReturn(nonExistingCharge);

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(externalChargeId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void shouldUpdateTransactionStatus_whenUpdatingChargeStatusFromInitialStatus() {
        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        when(mockedChargeDao.findByExternalId(createdChargeEntity.getExternalId()))
                .thenReturn(Optional.of(createdChargeEntity));


        service.updateFromInitialStatus(createdChargeEntity.getExternalId(), ENTERING_CARD_DETAILS);

    }

    @Test
    public void shouldFindChargeWithCaptureUrlAndNoNextUrl_whenChargeInAwaitingCaptureRequest() throws Exception {
        Long chargeId = 101L;

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AWAITING_CAPTURE_REQUEST)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("capture", POST, new URI(SERVICE_HOST + "/v1/api/accounts/10/charges/" + externalId + "/capture"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));

    }

    @Deprecated
    private ChargeResponseBuilder chargeResponseBuilderOf(ChargeEntity chargeEntity) {
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
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withEmail(chargeEntity.getEmail())
                .withRefunds(refunds)
                .withSettlement(settlement)
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withLanguage(chargeEntity.getLanguage());
    }

    @Test
    public void shouldBeRetriableGivenChargeHasNotExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS);

        assertThat(service.isChargeRetriable(anyString()), is(true));
    }

    @Test
    public void shouldNotBeRetriableGivenChargeExceededMaxNumberOfCaptureAttempts() {
        when(mockedChargeDao.countCaptureRetriesForChargeExternalId(any())).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS + 1);

        assertThat(service.isChargeRetriable(anyString()), is(false));
    }

    @Test
    public void shouldUpdateChargeEntityAndPersistChargeEventForAValidStateTransition() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());

        service.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        verify(chargeSpy).setStatus(ENTERING_CARD_DETAILS);
        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());
    }

    @Test
    public void shouldOfferStateTransitionMessageForAValidStateTransitionIntoNonLockingState() {
        ChargeEntity chargeSpy = spy(ChargeEntityFixture.aValidChargeEntity().build());
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(chargeEvent.getId()).thenReturn(100L);
        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);

        service.transitionChargeState(chargeSpy, ENTERING_CARD_DETAILS);

        ArgumentCaptor<PaymentStateTransition> paymentStateTransitionArgumentCaptor = ArgumentCaptor.forClass(PaymentStateTransition.class);
        verify(mockedStateTransitionQueue).offer(paymentStateTransitionArgumentCaptor.capture());

        assertThat(paymentStateTransitionArgumentCaptor.getValue().getChargeEventId(), is(100L));
        assertThat(paymentStateTransitionArgumentCaptor.getValue().getStateTransitionEventClass(), is(PaymentStarted.class));
    }

    @Test
    public void shouldNotOfferStateTransitionMessageForAValidStateTransitionIntoLockingState() {
        ChargeEntity chargeSpy = spy(
                ChargeEntityFixture
                        .aValidChargeEntity()
                        .withStatus(ENTERING_CARD_DETAILS)
                        .build()
        );
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(mockedChargeEventDao.persistChargeEventOf(chargeSpy, null)).thenReturn(chargeEvent);

        service.transitionChargeState(chargeSpy, AUTHORISATION_READY);

        verifyNoMoreInteractions(mockedStateTransitionQueue);
    }

    @Test
    public void shouldNotTransitionChargeStateForNonSkippableNonConfirmedCharge() {
        ChargeEntity createdChargeEntity = aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        final String chargeEntityExternalId = createdChargeEntity.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId)).thenReturn(Optional.of(createdChargeEntity));

        thrown.expect(ConflictRuntimeException.class);
        thrown.expectMessage("HTTP 409 Conflict");
        service.markDelayedCaptureChargeAsCaptureApproved(chargeEntityExternalId);
    }
}
