package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
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
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.ChargeResponse.ChargeResponseBuilder;
import static uk.gov.pay.connector.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;


@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 1L;
    private static final long CHARGE_ENTITY_ID = 12345L;
    private static final String[] EXTERNAL_CHARGE_ID = new String[1];

    private static final Map<String, String> CHARGE_REQUEST = new HashMap<String, String>() {{
        put("amount", "100");
        put("return_url", "http://return-service.com");
        put("description", "This is a description");
        put("reference", "Pay reference");
    }};

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
    private PaymentRequestDao mockedPaymentRequestDao;
    @Mock
    private ChargeStatusUpdater mockedChargeStatusUpdater;

    private ChargeService service;

    @Before
    public void setUp() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        // Populate ChargeEntity with ID when persisting
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(CHARGE_ENTITY_ID);
            EXTERNAL_CHARGE_ID[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(mockedChargeDao).persist(any(ChargeEntity.class));

        when(mockedConfig.getLinks())
                .thenReturn(mockedLinksConfig);

        when(mockedLinksConfig.getFrontendUrl())
                .thenReturn("http://payments.com");

        doAnswer(invocation -> fromUri(SERVICE_HOST))
                .when(this.mockedUriInfo)
                .getBaseUriBuilder();

        when(mockedProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.getExternalChargeRefundAvailability(any(ChargeEntity.class))).thenReturn(EXTERNAL_AVAILABLE);

        service = new ChargeService(mockedTokenDao, mockedChargeDao, mockedChargeEventDao,
                mockedCardTypeDao, mockedGatewayAccountDao, mockedConfig, mockedProviders,
                mockedPaymentRequestDao, mockedChargeStatusUpdater);
    }

    @Test
    public void shouldCreateACharge() throws Exception {
        service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(CHARGE_ENTITY_ID));
        assertThat(createdChargeEntity.getStatus(), is("CREATED"));
        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdChargeEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdChargeEntity.getReference(), is("Pay reference"));
        assertThat(createdChargeEntity.getDescription(), is("This is a description"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));

        verify(mockedChargeEventDao).persistChargeEventOf(createdChargeEntity, Optional.empty());
    }

    @Test
    public void shouldCreateAPaymentRequest() throws Exception {
        service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<PaymentRequestEntity> paymentRequestEntityArgumentCaptor = forClass(PaymentRequestEntity.class);
        verify(mockedPaymentRequestDao).persist(paymentRequestEntityArgumentCaptor.capture());

        PaymentRequestEntity createdPaymentRequestEntity = paymentRequestEntityArgumentCaptor.getValue();
        assertThat(createdPaymentRequestEntity.getGatewayAccount().getId(), is(GATEWAY_ACCOUNT_ID));
        assertThat(createdPaymentRequestEntity.getExternalId(), is(EXTERNAL_CHARGE_ID[0]));
        assertThat(createdPaymentRequestEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdPaymentRequestEntity.getGatewayAccount().getGatewayName(), is("sandbox"));
        assertThat(createdPaymentRequestEntity.getReference(), is("Pay reference"));
        assertThat(createdPaymentRequestEntity.getDescription(), is("This is a description"));
        assertThat(createdPaymentRequestEntity.getAmount(), is(100L));
        assertThat(createdPaymentRequestEntity.getReturnUrl(), is("http://return-service.com"));
        assertThat(createdPaymentRequestEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));
    }

    @Test
    public void shouldUpdateEmailToChargeTransaction() {
        ChargeEntity createdChargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        final String chargeEntityExternalId = createdChargeEntity.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeEntityExternalId))
                .thenReturn(Optional.of(createdChargeEntity));

        final ChargeTransactionEntity chargeTransactionEntity = ChargeTransactionEntity.from(createdChargeEntity);
        final PaymentRequestEntity paymentRequestEntity = PaymentRequestEntity.from(createdChargeEntity, chargeTransactionEntity);
        when(mockedPaymentRequestDao.findByExternalId(chargeEntityExternalId))
                .thenReturn(Optional.of(paymentRequestEntity));

        final String expectedEmail = "test@examplecom";
        PatchRequestBuilder.PatchRequest patchRequest = PatchRequestBuilder.aPatchRequestBuilder(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "email",
                        "value", expectedEmail))
                .withValidOps(singletonList("replace"))
                .withValidPaths(singletonList("email"))
                .build();

        assertThat(paymentRequestEntity.getChargeTransaction().getEmail(), is(nullValue()));

        service.updateEmail(chargeEntityExternalId, patchRequest);

        verify(mockedPaymentRequestDao).findByExternalId(chargeEntityExternalId);
        String emailFromTransaction = paymentRequestEntity.getChargeTransaction().getEmail();
        assertThat(emailFromTransaction, is(expectedEmail));
    }

    @Test
    public void shouldCreateAToken() throws Exception {
        service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(CHARGE_ENTITY_ID));
        assertThat(tokenEntity.getToken(), is(notNullValue()));
    }

    @Test
    public void shouldCreateAResponse() throws Exception {
        ChargeResponse response = service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo).get();

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();

        // Then - expected response is returned
        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + EXTERNAL_CHARGE_ID[0]));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + EXTERNAL_CHARGE_ID[0] + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsCreated() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CREATED)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsInProgress() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(ENTERING_CARD_DETAILS)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, mockedUriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(mockedTokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId + "/refunds"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/secure/" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/secure"), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));

    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeCannotBeResumed() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, mockedUriInfo);

        verify(mockedTokenDao, never()).persist(any());

        ChargeResponseBuilder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("refunds", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId + "/refunds"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {

        String externalChargeId = "101abc";
        Long accountId = 10L;
        Optional<ChargeEntity> nonExistingCharge = Optional.empty();

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId)).thenReturn(nonExistingCharge);

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(externalChargeId, accountId, mockedUriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void whenCreatingCharge_shouldCreateTransactionEntity() throws Exception {
        service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<PaymentRequestEntity> argumentCaptor = forClass(PaymentRequestEntity.class);
        verify(mockedPaymentRequestDao).persist(argumentCaptor.capture());

        PaymentRequestEntity paymentRequestEntity = argumentCaptor.getValue();
        assertThat(paymentRequestEntity.getTransactions().size(), is(1));
        TransactionEntity transactionEntity = paymentRequestEntity.getTransactions().get(0);
        assertThat(transactionEntity.getAmount(), is(100L));
        assertThat(transactionEntity.getStatus(), is(ChargeStatus.CREATED));
        assertThat(transactionEntity.getOperation(), is(TransactionOperation.CHARGE));
    }

    @Test
    public void shouldUpdateTransactionStatus_whenUpdatingChargeStatusFromInitialStatus() throws Exception {
        service.create(CHARGE_REQUEST, GATEWAY_ACCOUNT_ID, mockedUriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(mockedChargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();

        when(mockedChargeDao.findByExternalId(createdChargeEntity.getExternalId()))
                .thenReturn(Optional.of(createdChargeEntity));

        final PaymentRequestEntity paymentRequestEntity = PaymentRequestEntity.from(createdChargeEntity, ChargeTransactionEntity.from(createdChargeEntity));
        when(mockedPaymentRequestDao.findByExternalId(createdChargeEntity.getExternalId()))
                .thenReturn(Optional.of(paymentRequestEntity));

        service.updateFromInitialStatus(createdChargeEntity.getExternalId(), ChargeStatus.ENTERING_CARD_DETAILS);

        verify(mockedChargeStatusUpdater)
                .updateChargeTransactionStatus(paymentRequestEntity.getExternalId(), ChargeStatus.ENTERING_CARD_DETAILS);
    }

    @Deprecated
    private ChargeResponseBuilder chargeResponseBuilderOf(ChargeEntity chargeEntity) throws URISyntaxException {
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
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(chargeEntity.getCreatedDate()))
                .withEmail(chargeEntity.getEmail())
                .withRefunds(refunds)
                .withSettlement(settlement)
                .withReturnUrl(chargeEntity.getReturnUrl());
    }

}
