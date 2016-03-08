package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.dao.TokenJpaDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.GET;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;

public class ChargeService {

    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);

    ChargeJpaDao chargeDao;
    EventJpaDao eventDao;
    TokenJpaDao tokenDao;
    LinksConfig linksConfig;

    @Inject
    public ChargeService(TokenJpaDao tokenDao, ChargeJpaDao chargeDao, EventJpaDao eventDao, ConnectorConfiguration config) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.eventDao = eventDao;
        this.linksConfig = config.getLinks();
    }

    public ChargeResponse create(Map<String, Object> chargeRequest, GatewayAccountEntity gatewayAccount, UriInfo uriInfo) {

        ChargeEntity chargeEntity =
                new ChargeEntity(new Long(chargeRequest.get("amount").toString()),
                        CREATED.getValue(),
                        null,
                        chargeRequest.get("return_url").toString(),
                        chargeRequest.get("description").toString(),
                        chargeRequest.get("reference").toString(),
                        gatewayAccount);
        chargeDao.persist(chargeEntity);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, CREATED, chargeEntity.getCreatedDate().toLocalDateTime()));
        TokenEntity token = new TokenEntity(chargeEntity.getId(), UUID.randomUUID().toString());
        tokenDao.persist(token);
        ChargeResponse response = buildChargeResponse(uriInfo, chargeEntity, Optional.of(token));
        logger.info("charge = {}", chargeEntity);
        return response;
    }

    public Optional<ChargeResponse> findChargeForAccount(Long chargeId, String accountId, UriInfo uriInfo) {
        return chargeDao.findChargeForAccount(chargeId, accountId)
                .map(chargeEntity -> {
                    Optional<TokenEntity> token = tokenDao.findTokenByChargeId(chargeId);
                    return buildChargeResponse(uriInfo, chargeEntity, token);
                });
    }

    private ChargeResponse buildChargeResponse(UriInfo uriInfo, ChargeEntity charge, Optional<TokenEntity> token) {
        String chargeId = String.valueOf(charge.getId());
        ChargeResponse.Builder responseBuilder = aChargeResponse()
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withReference(charge.getReference())
                .withDescription(charge.getDescription())
                .withStatus(mapFromStatus(charge.getStatus()).getValue())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withProviderName(charge.getGatewayAccount().getGatewayName())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withLink("self", GET, selfUriFor(uriInfo, charge.getGatewayAccount().getId(), chargeId));
        token.ifPresent(tokenEntity -> {
            responseBuilder.withLink("next_url", GET, secureRedirectUriFor(chargeId, tokenEntity.getToken()));
        });
        return responseBuilder.build();
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
                .build(accountId, chargeId);
    }

    private URI secureRedirectUriFor(String chargeId, String tokenId) {
        String secureRedirectLocation = linksConfig.getCardDetailsUrl()
                .replace("{chargeId}", chargeId)
                .replace("{chargeTokenId}", tokenId);
        try {
            return new URI(secureRedirectLocation);
        } catch (URISyntaxException e) {
            logger.error(format("Invalid secure redirect url: %s", secureRedirectLocation), e);
            throw new RuntimeException(e);
        }
    }
}
