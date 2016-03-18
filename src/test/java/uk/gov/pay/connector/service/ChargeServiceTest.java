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
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.ChargeService.EXPIRY_FAILED;
import static uk.gov.pay.connector.service.ChargeService.EXPIRY_SUCCESS;


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
    private CardCancelService cardCancelService;

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

        service = new ChargeService(tokenDao, chargeDao, config, cardCancelService);
    }

    @Test
    public void shouldCreateACharge() throws Exception {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        long expectedChargeEntityId = 12345L;
        final String[] externalChargeId = new String[1];

        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(expectedChargeEntityId);
            externalChargeId[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(chargeDao).persist(any(ChargeEntity.class));

        ChargeResponse response = service.create(chargeRequest, gatewayAccount, uriInfo);

        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(chargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity expectedChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(expectedChargeEntity.getId(), is(expectedChargeEntityId));
        assertThat(expectedChargeEntity.getStatus(), is("CREATED"));
        assertThat(expectedChargeEntity.getGatewayAccount().getId(), is(1L));
        assertThat(expectedChargeEntity.getExternalId(), is(externalChargeId[0]));
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
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalChargeId[0]));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/" + externalChargeId[0] + "/" + tokenEntity.getToken()));

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
        service = new ChargeService(tokenDao, chargeDao, invalidConfig, cardCancelService);

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

        String externalId = newCharge.getExternalId();
        when(chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);
        when(tokenDao.findByChargeId(chargeId)).thenReturn(token);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, uriInfo);

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/" + externalId + "/" + tokenEntity.getToken()));

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

        String externalId = newCharge.getExternalId();
        when(chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);
        Optional<TokenEntity> nonExistingToken = Optional.empty();
        when(tokenDao.findByChargeId(chargeId)).thenReturn(nonExistingToken);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, uriInfo);

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {

        String externalChargeId = "101abc";
        Long accountId = 10L;
        Optional<ChargeEntity> nonExistingCharge = Optional.empty();

        when(chargeDao.findByExternalIdAndGatewayAccount(externalChargeId, accountId)).thenReturn(nonExistingCharge);

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(externalChargeId, accountId, uriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    @Test
    public void shouldUpdateChargeStatusForAllChargesWithTheGivenStatus() {
        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);

        service.updateStatus(asList(chargeEntity1, chargeEntity2), ChargeStatus.ENTERING_CARD_DETAILS);

        InOrder inOrder = inOrder(chargeEntity1, chargeEntity2, chargeDao);

        inOrder.verify(chargeEntity1).setStatus(ChargeStatus.ENTERING_CARD_DETAILS);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity1);

        inOrder.verify(chargeEntity2).setStatus(ChargeStatus.ENTERING_CARD_DETAILS);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithSuccessfulProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;
        String extChargeId = "ext-id";

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getExternalId()).thenReturn(extChargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);

        mockCancelResponse(extChargeId, accountId, Either.right(CancelResponse.aSuccessfulCancelResponse()));

        Map<String, Integer> result = service.expire(asList(chargeEntity1, chargeEntity2));
        assertEquals(2, result.get(EXPIRY_SUCCESS).intValue());
        assertEquals(0, result.get(EXPIRY_FAILED).intValue());

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity1);

        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);

        inOrder.verify(chargeEntity2).setStatus(EXPIRED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithUnsuccessfulProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;
        String extChargeId = "ext-id";

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getExternalId()).thenReturn(extChargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);

        CancelResponse unsuccessfulResponse = new CancelResponse(false, ErrorResponse.unexpectedStatusCodeFromGateway("invalid status"));
        mockCancelResponse(extChargeId, accountId, Either.right(unsuccessfulResponse));

        Map<String, Integer> result = service.expire(asList(chargeEntity1, chargeEntity2));
        assertEquals(1, result.get(EXPIRY_SUCCESS).intValue());
        assertEquals(1, result.get(EXPIRY_FAILED).intValue());

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity1);

        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);

        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_FAILED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithFailedProviderCancellation() {
        long chargeId = 10L;
        long accountId = 100L;
        String extChargeId = "ext-id";

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getExternalId()).thenReturn(extChargeId);
        when(gatewayAccount.getId()).thenReturn( accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);

        ErrorResponse errorResponse = new ErrorResponse("error-message", ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        mockCancelResponse(extChargeId, accountId, Either.left(errorResponse));

        Map<String, Integer> result = service.expire(asList(chargeEntity1, chargeEntity2));
        assertEquals(1, result.get(EXPIRY_SUCCESS).intValue());
        assertEquals(1, result.get(EXPIRY_FAILED).intValue());

        InOrder inOrder = inOrder(chargeEntity1, chargeDao, chargeEntity2);
        inOrder.verify(chargeEntity1).setStatus(EXPIRED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity1);

        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_PENDING);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);

        inOrder.verify(chargeEntity2).setStatus(EXPIRE_CANCEL_FAILED);
        inOrder.verify(chargeDao).mergeAndNotifyStatusHasChanged(chargeEntity2);
    }

    private ChargeResponse.Builder chargeResponseBuilderOf(ChargeEntity chargeEntity) throws URISyntaxException {
        return aChargeResponse()
                .withChargeId(chargeEntity.getExternalId())
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withStatus(mapFromStatus(chargeEntity.getStatus()).getValue())
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withReturnUrl(chargeEntity.getReturnUrl());
    }

    private void mockCancelResponse(String extChargeId, Long accountId, Either<ErrorResponse, GatewayResponse> either) {
        when(cardCancelService.doCancel(extChargeId, accountId)).thenReturn(either);
    }

}
