package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_RESOURCE;

public class ChargeService {

    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);

    private ChargeDao chargeDao;
    private TokenDao tokenDao;
    private LinksConfig linksConfig;

    @Inject
    public ChargeService(TokenDao tokenDao, ChargeDao chargeDao, ConnectorConfiguration config) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.linksConfig = config.getLinks();
    }

    @Transactional
    public ChargeResponse create(Map<String, Object> chargeRequest, GatewayAccountEntity gatewayAccount, UriInfo uriInfo) {

        ChargeEntity chargeEntity = new ChargeEntity(new Long(chargeRequest.get("amount").toString()),
                chargeRequest.get("return_url").toString(),
                chargeRequest.get("description").toString(),
                chargeRequest.get("reference").toString(),
                gatewayAccount);
        chargeDao.persist(chargeEntity);
        ChargeResponse response =
                chargeResponseBuilder(uriInfo, chargeEntity, createNewChargeEntityToken(chargeEntity)).build();
        logger.info("charge = {}", chargeEntity);
        return response;
    }

    @Transactional
    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> {
                    if (chargeEntity.hasExternalStatus(EXT_CREATED) || chargeEntity.hasExternalStatus(EXT_IN_PROGRESS)) {
                        return chargeResponseBuilder(uriInfo, chargeEntity, createNewChargeEntityToken(chargeEntity)).build();
                    }
                    return chargeResponseBuilder(uriInfo, chargeEntity).build();
                });
    }

    @Transactional
    public void updateStatus(List<ChargeEntity> chargeEntities, ChargeStatus status) {
        chargeEntities.stream().forEach(chargeEntity -> {
            chargeEntity.setStatus(status);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        });
    }

    private TokenEntity createNewChargeEntityToken(ChargeEntity chargeEntity) {
        TokenEntity token = TokenEntity.generateNewTokenFor(chargeEntity);
        tokenDao.persist(token);
        return token;
    }

    private ChargeResponse.Builder chargeResponseBuilder(UriInfo uriInfo, ChargeEntity charge, TokenEntity token) {
        return chargeResponseBuilder(uriInfo, charge)
                .withLink("next_url", GET, nextUrl(token.getToken()))
                .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, new HashMap<String, Object>() {{
                    put("chargeTokenId", token.getToken());
                }});
    }

    private ChargeResponse.Builder chargeResponseBuilder(UriInfo uriInfo, ChargeEntity charge) {
        String chargeId = charge.getExternalId();
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
        return responseBuilder;
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_RESOURCE)
                .build(accountId, chargeId);
    }

    private URI nextUrl(String tokenId) {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("secure")
                .path(tokenId)
                .build();
    }

    private URI nextUrl() {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("secure")
                .build();
    }
}
