package uk.gov.pay.connector.service;

import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.dao.TokenJpaDao;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.ChargeResponse;

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

import static java.util.Collections.emptyMap;
import static javax.ws.rs.HttpMethod.GET;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;


@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceTest {

    public static final String SERVICE_HOST = "http://my-service";
    @Mock
    private TokenJpaDao tokenDao;
    @Mock
    private ChargeJpaDao chargeDao;
    @Mock
    private EventJpaDao eventDao;
    @Mock
    private ConnectorConfiguration config;

    private UriInfo uriInfo;
    private UriBuilder uriBuilder;
    Map<String, Object> chargeRequest;

    private ChargeService service;

    @Before
    public void setUp() throws Exception {
        LinksConfig linksConfig = mock(LinksConfig.class);
        when(config.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getCardDetailsUrl())
                .thenReturn("http://payments.com/{chargeId}/{chargeTokenId}");

        uriInfo = mock(UriInfo.class);
        uriBuilder = UriBuilder.fromUri(SERVICE_HOST);
        when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);

        chargeRequest = new HashMap<String, Object>() {{
            put("amount", "100");
            put("return_url", "http://return-service.com");
            put("description", "This is a description");
            put("reference", "Pay reference");
        }};

        service = new ChargeService(tokenDao, chargeDao, eventDao, config);
    }

    @Test
    public void shouldCreateACharge() throws Exception {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);

        ChargeResponse response = service.create(chargeRequest, gatewayAccount, uriInfo);

        ChargeEntity chargeEntity = assertChargeDaoInteraction();

        assertEventDaoInteraction(chargeEntity);

        TokenEntity tokenEntity = assertTokenDaoInteraction(chargeEntity);

        ChargeResponse expectedChargeResponse = buildChargeResponse(Optional.of(tokenEntity), chargeEntity);

        assertThat(response, is(expectedChargeResponse));

    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidRedirectUrl() {
        LinksConfig invalidLingsConfig = mock(LinksConfig.class);
        ConnectorConfiguration invalidConfig = mock(ConnectorConfiguration.class);

        when(invalidLingsConfig.getCardDetailsUrl()).thenReturn("blah:asfas/aadw%£>this_is_not_a_url");
        when(invalidConfig.getLinks()).thenReturn(invalidLingsConfig);

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        service = new ChargeService(tokenDao, chargeDao, eventDao, invalidConfig);

        service.create(chargeRequest, gatewayAccount, uriInfo);
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithNextUrlWhenTokenExist() throws Exception {
        long chargeId = 101L;
        String accountId = "10";

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        String returnUrl = "return_url";
        String description = "test_description";
        String reference = "reference";
        ChargeEntity newCharge = new ChargeEntity(chargeId, 1000L, ChargeStatus.CREATED.getValue(),null, returnUrl, description, reference, gatewayAccount);

        String tokenValue = "test_token";
        TokenEntity tokenEntity = new TokenEntity(chargeId, tokenValue);

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);
        Optional<TokenEntity> token = Optional.of(tokenEntity);

        when(chargeDao.findChargeForAccount(chargeId, accountId)).thenReturn(chargeEntity);
        when(tokenDao.findTokenByChargeId(chargeId)).thenReturn(token);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        ChargeResponse expectedChargeResponse = buildChargeResponse(Optional.of(tokenEntity), chargeEntity.get());

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse));
    }

    @Test
    public void shouldFindChargeForChargeIdAndAccountIdWithoutNextUrlWhenNoTokenExist() throws Exception {
        long chargeId = 101L;
        String accountId = "10";

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("provider", new HashMap<>());
        gatewayAccount.setId(1L);
        String returnUrl = "return_url";
        String description = "test_description";
        String reference = "reference";
        ChargeEntity newCharge = new ChargeEntity(chargeId, 1000L, ChargeStatus.CREATED.getValue(),null, returnUrl, description, reference, gatewayAccount);

        Optional<ChargeEntity> chargeEntity = Optional.of(newCharge);

        when(chargeDao.findChargeForAccount(chargeId, accountId)).thenReturn(chargeEntity);
        Optional<TokenEntity> nonExistingToken = Optional.empty();
        when(tokenDao.findTokenByChargeId(chargeId)).thenReturn(nonExistingToken);

        Optional<ChargeResponse> chargeResponseForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        ChargeResponse expectedChargeResponse = buildChargeResponse(nonExistingToken, chargeEntity.get());

        assertThat(chargeResponseForAccount.get(), is(expectedChargeResponse));

    }

    @Test
    public void shouldNotFindAChargeWhenNoChargeForChargeIdAndAccountId() {
        long chargeId = 101L;
        String accountId = "10";
        Optional<ChargeEntity> nonExistingCharge = Optional.empty();
        when(chargeDao.findChargeForAccount(chargeId, accountId)).thenReturn(nonExistingCharge);

        Optional<ChargeResponse> chargeForAccount = service.findChargeForAccount(chargeId, accountId, uriInfo);

        assertThat(chargeForAccount.isPresent(), is(false));
    }

    private ChargeResponse buildChargeResponse(Optional<TokenEntity> tokenEntity, ChargeEntity chargeEntity) throws URISyntaxException {
        ChargeResponse.Builder responseBuilder = aChargeResponse()
                .withChargeId(String.valueOf(chargeEntity.getId()))
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withStatus(mapFromStatus(chargeEntity.getStatus()).getValue())
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withLink("self", GET, new URI(SERVICE_HOST + "/v1/api/accounts/1/charges/"+ getChargeIdAsString(chargeEntity)));
                if(tokenEntity.isPresent()) {
                    responseBuilder.withLink("next_url", GET, new URI("http://payments.com/" + chargeEntity.getId() + "/" + tokenEntity.get().getToken()));
                }

        return responseBuilder.build();
    }

    private String getChargeIdAsString(ChargeEntity chargeEntity) {
        return Optional.ofNullable(chargeEntity.getId())
                .map(String::valueOf)
                .orElse("null");
    }

    private TokenEntity assertTokenDaoInteraction(ChargeEntity expectedChargeEntity) {
        ArgumentCaptor<TokenEntity> tokenEntityArgumentCaptor = ArgumentCaptor.forClass(TokenEntity.class);

        verify(tokenDao).persist(tokenEntityArgumentCaptor.capture());

        TokenEntity tokenEntity = tokenEntityArgumentCaptor.getValue();
        assertThat(tokenEntity.getChargeId(), is(expectedChargeEntity.getId()));
        assertThat(tokenEntity.getToken(), is(notNullValue()));

        return tokenEntity;
    }

    private void assertEventDaoInteraction(ChargeEntity expectedChargeEntity) {
        ArgumentCaptor<ChargeEventEntity> chargeEventEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEventEntity.class);

        verify(eventDao).persist(chargeEventEntityArgumentCaptor.capture());

        ChargeEventEntity chargeEventEntity = chargeEventEntityArgumentCaptor.getValue();
        assertThat(chargeEventEntity.getChargeEntity().getId(), is(expectedChargeEntity.getId()));
        assertThat(chargeEventEntity.getStatus(), is(ChargeStatus.CREATED));
    }

    private ChargeEntity assertChargeDaoInteraction() {
        ArgumentCaptor<ChargeEntity> chargeEntityArgumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(chargeDao).persist(chargeEntityArgumentCaptor.capture());

        ChargeEntity expectedChargeEntity = chargeEntityArgumentCaptor.getValue();
        assertThat(expectedChargeEntity.getId(), is(nullValue()));
        assertThat(expectedChargeEntity.getStatus(), is("CREATED"));
        assertThat(expectedChargeEntity.getGatewayAccount().getId(), is(1L));
        assertThat(expectedChargeEntity.getGatewayAccount().getCredentials(), is(emptyMap()));
        assertThat(expectedChargeEntity.getGatewayAccount().getGatewayName(), is("provider"));
        assertThat(expectedChargeEntity.getReference(), is("Pay reference"));
        assertThat(expectedChargeEntity.getDescription(), is("This is a description"));
        assertThat(expectedChargeEntity.getAmount(), is(100L));
        assertThat(expectedChargeEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(expectedChargeEntity.getCreatedDate(), is(ZonedDateTimeMatchers.within(3, ChronoUnit.SECONDS, ZonedDateTime.now(ZoneId.of("UTC")))));

        return expectedChargeEntity;
    }
}