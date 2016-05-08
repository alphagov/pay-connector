package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.*;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;

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
                    if (chargeEntity.hasExternalStatus(LEGACY_EXT_CREATED) || chargeEntity.hasExternalStatus(LEGACY_EXT_IN_PROGRESS)) {
                        return chargeResponseBuilder(uriInfo, chargeEntity, createNewChargeEntityToken(chargeEntity)).build();
                    }
                    return chargeResponseBuilder(uriInfo, chargeEntity).build();
                });
    }

    @Transactional
    public List<ChargeEntity> updateStatus(List<ChargeEntity> chargeEntities, ChargeStatus status) {
        List<ChargeEntity> mergedCharges = new ArrayList<ChargeEntity>();
        chargeEntities.stream().forEach(chargeEntity -> {
            chargeEntity.setStatus(status);
            logger.info("charge status to update: "+chargeEntity.getStatus() + "for Charge ID: "+chargeEntity.getId());
            ChargeEntity mergedEnt = chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
            mergedCharges.add(mergedEnt);
        });
        return mergedCharges;
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
                .withState(ChargeStatus.fromString(charge.getStatus()).toExternal())
                .withStatus(ChargeStatus.fromString(charge.getStatus()).toLegacy().getValue())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withProviderName(charge.getGatewayAccount().getGatewayName())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withLink("self", GET, selfUriFor(uriInfo, charge.getGatewayAccount().getId(), chargeId));
        return responseBuilder;
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
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
