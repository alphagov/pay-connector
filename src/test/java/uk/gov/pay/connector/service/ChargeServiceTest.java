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
import static javax.ws.rs.HttpMethod.POST;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.ChargeService.EXPIRY_FAILED;
import static uk.gov.pay.connector.service.ChargeService.EXPIRY_SUCCESS;


@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceTest {

    private static final String SERVICE_HOST = "http://my-service";

    @Mock
    private TokenDao tokenDao;
    @Mock
    private ChargeDao chargeDao;
    @Mock
    private ConnectorConfiguration config;
    @Mock
    private CardCancelService cardCancelService;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private LinksConfig linksConfig;

    private ChargeService service;
    private Map<String, Object> chargeRequest = new HashMap<String, Object>() {{
        put("amount", "100");
        put("return_url", "http://return-service.com");
        put("description", "This is a description");
        put("reference", "Pay reference");
    }};

    @Before
    public void setUp() throws Exception {

        when(config.getLinks())
                .thenReturn(linksConfig);

        when(linksConfig.getFrontendUrl())
                .thenReturn("http://payments.com");

        when(this.uriInfo.getBaseUriBuilder())
                .thenReturn(UriBuilder.fromUri(SERVICE_HOST));

        service = new ChargeService(tokenDao, chargeDao, config, cardCancelService);
    }

    @Test
    public void shouldCreateACharge() throws Exception {

        // Given - existing gateway account
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        // Given - persisting a ChargeEntity, it will be populated with an id
        long chargeEntityId = 12345L;
        final String[] externalChargeId = new String[1];
        doAnswer(invocation -> {
            ChargeEntity chargeEntityBeingPersisted = (ChargeEntity) invocation.getArguments()[0];
            chargeEntityBeingPersisted.setId(chargeEntityId);
            externalChargeId[0] = chargeEntityBeingPersisted.getExternalId();
            return null;
        }).when(chargeDao).persist(any(ChargeEntity.class));

        // When
        ChargeResponse response = service.create(chargeRequest, gatewayAccount, uriInfo);

        // Then - a chargeEntity is created
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = forClass(ChargeEntity.class);
        verify(chargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity createdChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(createdChargeEntity.getId(), is(chargeEntityId));
        assertThat(createdChargeEntity.getStatus(), is("CREATED"));
        assertThat(createdChargeEntity.getGatewayAccount().getId(), is(1L));
        assertThat(createdChargeEntity.getExternalId(), is(externalChargeId[0]));
        assertThat(createdChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(createdChargeEntity.getGatewayAccount().getGatewayName(), is("provider"));
        assertThat(createdChargeEntity.getReference(), is("Pay reference"));
        assertThat(createdChargeEntity.getDescription(), is("This is a description"));
        assertThat(createdChargeEntity.getAmount(), is(100L));
        assertThat(createdChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(createdChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));

        // Then - a TokenEntity is created
        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = forClass(TokenEntity.class);
        verify(tokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(createdChargeEntity.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        // Then - expected response is returned
        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(createdChargeEntity);

        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalChargeId[0]));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/charge/" + externalChargeId[0] + "?chargeTokenId=" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/charge/" + externalChargeId[0]), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(response, is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsCreated() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CREATED)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, uriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(tokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/" + externalId + "/" + tokenEntity.getToken()));

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenChargeStatusIsInProgress() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(ENTERING_CARD_DETAILS)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, uriInfo);

        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);
        verify(tokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeEntity().getId(), is(newCharge.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        ChargeResponse.Builder expectedChargeResponse = chargeResponseBuilderOf(chargeEntity.get());
        expectedChargeResponse.withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/" + externalId));
        expectedChargeResponse.withLink("next_url", GET, new URI("http://payments.com/charge/" + externalId + "?chargeTokenId=" + tokenEntity.getToken()));
        expectedChargeResponse.withLink("next_url_post", POST, new URI("http://payments.com/charge/" + externalId), "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
            put("chargeTokenId", tokenEntity.getToken());
        }});

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse.build()));

    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenChargeCannotBeResumed() throws Exception {

        Long chargeId = 101L;
        Long accountId = 10L;

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeEntity newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .build();

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        String externalId = newCharge.getExternalId();
        when(chargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(chargeEntity);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(externalId, accountId, uriInfo);

        verify(tokenDao, never()).persist(any());

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
        when(gatewayAccount.getId()).thenReturn(accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);

        mockCancelResponse(extChargeId, accountId, Either.right(CancelResponse.successfulCancelResponse(SYSTEM_CANCELLED)));

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
        when(gatewayAccount.getId()).thenReturn(accountId);
        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);

        CancelResponse unsuccessfulResponse = CancelResponse.cancelFailureResponse(ErrorResponse.unexpectedStatusCodeFromGateway("invalid status"));
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
        long accountId = 100L;
        String extChargeId = "ext-id";

        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.getExternalId()).thenReturn(extChargeId);
        when(gatewayAccount.getId()).thenReturn(accountId);
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

    /**
     * TODO To create a matcher rather than using main src to build our assertions
     */
    @Deprecated
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
