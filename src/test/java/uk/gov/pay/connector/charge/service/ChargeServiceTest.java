package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.eclipse.persistence.exceptions.QueryException;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.Event;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.PaymentCreatedEvent;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;

import javax.ws.rs.WebApplicationException;
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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.ChargeResponse.ChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
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

    @Mock
    private TokenDao mockedTokenDao;
    @Mock
    private ChargeDao mockedChargeDao;
    @Mock
    private ChargeEventDao mockedChargeEventDao;
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
    private EventQueue eventQueue;

    private ChargeService service;

    private GatewayAccountEntity gatewayAccount;

    @Before
    public void setUp() {
        requestBuilder = ChargeCreateRequestBuilder
                .aChargeCreateRequest()
                .withAmount(100L)
                .withReturnUrl("http://return-service.com")
                .withDescription("This is a description")
                .withReference("Pay reference");

        gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

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

        service = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedGatewayAccountDao, mockedConfig, mockedProviders, eventQueue);
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

        verify(mockedChargeEventDao).persistChargeEventOf(createdChargeEntity);
    }

    @Test
    public void createChargeShouldPutPaymentCreatedEventOnQueue() throws Exception {
        // ACT
        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);
        
        // ASSERT
        verify(eventQueue).emitEvent(any(PaymentCreatedEvent.class));
    }

    @Test(expected = WebApplicationException.class)
    public void createChargeThrowsWebApplicationExceptionIfEmittingPaymentCreatedEventFails() throws Exception {
        doThrow(new QueueException("Queue badness")).when(eventQueue).emitEvent(any(Event.class));
        
        service.create(requestBuilder.build(), GATEWAY_ACCOUNT_ID, mockedUriInfo);
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
        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        Address address = new Address("Line1", "Line2", "AB1 CD2", "London", null, "GB");
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
        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        cardHolderDetails.setCardHolderName("Joe Bogs");
        Address address = new Address("Line1", null, "AB1 CD2", "London", null, null);
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
        PrefilledCardHolderDetails cardHolderDetails = new PrefilledCardHolderDetails();
        Address address = new Address("Line1", null, "AB1 CD2", "London", null, null);
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
    
    
}
