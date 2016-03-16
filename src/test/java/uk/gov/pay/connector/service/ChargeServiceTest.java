package uk.gov.pay.connector.service;

import fj.data.Either;
import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.HttpMethod.GET;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_PENDING;


@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceTest {

    public static final String SERVICE_HOST = "http://my-service";
    @Mock
    private TokenDao tokenDao;
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private ConnectorConfiguration config;
    @Mock
    private CardService cardService;

    private UriInfo uriInfo;
    Map<String, Object> chargeRequest;

    private ChargeService service;

    @Before
    public void setUp() throws Exception {
        LinksConfig linksConfig = mock(LinksConfig.class);
        when(config.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getCardDetailsUrl())
                .thenReturn("http://payments.com/{chargeId}/{chargeTokenId}");

        uriInfo = mock(UriInfo.class);

        UriBuilder uriBuilder = UriBuilder.fromUri(SERVICE_HOST);
        when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);

        chargeRequest = new HashMap<String, Object>() {{
            put("amount", "100");
            put("return_url", "http://return-service.com");
            put("description", "This is a description");
            put("reference", "Pay reference");
        }};

        service = new ChargeService(tokenDao, chargeDao, config, cardService);
    }

    @Test
    public void shouldCreateACharge() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        long expectedChargeEntityId = 12345L;

        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(expectedChargeEntityId);
            return null;
        }).when(chargeDao).persist(any(ChargeEntity.class));

        ChargeResponse response = service.create(chargeRequest, gatewayAccount, uriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(chargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity expectedChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(expectedChargeEntity.getId(), is(expectedChargeEntityId));
        assertThat(expectedChargeEntity.getStatus(), is("CREATED"));
        assertThat(expectedChargeEntity.getGatewayAccount().getId(), is(1L));
        assertThat(expectedChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(expectedChargeEntity.getGatewayAccount().getGatewayName(), is("provider"));
        assertThat(expectedChargeEntity.getReference(), is("Pay reference"));
        assertThat(expectedChargeEntity.getDescription(), is("This is a description"));
        assertThat(expectedChargeEntity.getAmount(), is(100L));
        assertThat(expectedChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(expectedChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(tokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeId(), is(expectedChargeEntity.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(expectedChargeEntity);
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/12345"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/12345/" + tokenEntity.getToken()));

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidRedirectUrl() {
        LinksConfig invalidLingsConfig = mock(LinksConfig.class);
        ConnectorConfiguration invalidConfig = mock(ConnectorConfiguration.class);

        when(invalidLingsConfig.getCardDetailsUrl()).thenReturn("blah:asfas/aadw%£>this_is_not_a_url");
        when(invalidConfig.getLinks()).thenReturn(invalidLingsConfig);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        service = new ChargeService(tokenDao, chargeDao, invalidConfig, cardService);

        service.create(chargeRequest, gatewayAccount, uriInfo);
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenTokenExist() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        String tokenValue = "test_token";
        TokenEntity tokenEntity = new TokenEntity(chargeId, tokenValue);

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);
        Optional<TokenEntity> token = Optional.of(tokenEntity);

        when(chargeDao.findByIdAndGatewayAccount(chargeId, accountId)).thenReturn(chargeEntity);
        when(tokenDao.findByChargeId(chargeId)).thenReturn(token);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/101"));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/101/" + tokenEntity.getToken()));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenNoTokenExist() throws Exception {
        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        when(chargeDao.findByIdAndGatewayAccount(chargeId, accountId)).thenReturn(chargeEntity);
        Optional<TokenEntity> nonExistingToken = Optional.empty();
        when(tokenDao.findByChargeId(chargeId)).thenReturn(nonExistingToken);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/101"));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {
        Long chargeId = 101L;
        Long accountId = 10L;
        Optional<ChargeEntity> nonExistingCharge = Optional.empty();
        when(chargeDao.findByIdAndGatewayAccount(chargeId, accountId)).thenReturn(nonExistingCharge);

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void shouldUpdateChargeStatusForAllChargesWithTheGivenStatus() {
        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        when(chargeDao.merge(chargeEntity1)).thenReturn(chargeEntity1);
        when(chargeDao.merge(chargeEntity2)).thenReturn(chargeEntity2);

        service.updateStatus(asList(chargeEntity1, chargeEntity2), ChargeStatus.ENTERING_CARD_DETAILS);

        InOrder inOrder = inOrder(chargeEntity1, chargeEntity2, chargeDao);

        inOrder.verify(chargeDao).merge(chargeEntity1);
        inOrder.verify(chargeEntity1).setStatus(ChargeStatus.ENTERING_CARD_DETAILS);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(ChargeStatus.ENTERING_CARD_DETAILS);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithSuccessfulProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getId()).thenReturn(chargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);
        when(chargeDao.merge(chargeEntity1)).thenReturn(chargeEntity1);
        when(chargeDao.merge(chargeEntity2)).thenReturn(chargeEntity2);

        mockCancelResponse(chargeId, accountId, Either.right(CancelResponse.aSuccessfulCancelResponse()));

        service.expire(asList(chargeEntity1, chargeEntity2));

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeDao).merge(chargeEntity1);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRED);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithUnsuccessfulProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getId()).thenReturn(chargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);
        when(chargeDao.merge(chargeEntity1)).thenReturn(chargeEntity1);
        when(chargeDao.merge(chargeEntity2)).thenReturn(chargeEntity2);

        CancelResponse unsuccessfulResponse = new CancelResponse(false, ErrorResponse.unexpectedStatusCodeFromGateway("invalid status"));
        mockCancelResponse(chargeId, accountId, Either.right(unsuccessfulResponse));

        service.expire(asList(chargeEntity1, chargeEntity2));

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeDao).merge(chargeEntity1);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_FAILED);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithFailedProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getId()).thenReturn(chargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);
        when(chargeDao.merge(chargeEntity1)).thenReturn(chargeEntity1);
        when(chargeDao.merge(chargeEntity2)).thenReturn(chargeEntity2);

        ErrorResponse errorResponse = new ErrorResponse("error-message", ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        mockCancelResponse(chargeId, accountId, Either.left(errorResponse));

        service.expire(asList(chargeEntity1, chargeEntity2));

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeDao).merge(chargeEntity1);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);

        inOrder.verify(chargeDao).merge(chargeEntity2);
        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_FAILED);
    }

    private ChargeResponse.Builder chargeResponseBuilderOf(ChargeEntity chargeEntity) throws URISyntaxException {
        return aChargeResponse()
                .withChargeId(String.valueOf(chargeEntity.getId()))
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withStatus(mapFromStatus(chargeEntity.getStatus()).getValue())
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withReturnUrl(chargeEntity.getReturnUrl());
    }

    private void mockCancelResponse(Long chargeId, Long accountId, Either<ErrorResponse, GatewayResponse> either) {
        when(cardService.doCancel(chargeId, accountId)).thenReturn(either);
    }

}
