package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargePatchRequest;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.resources.ChargesApiResource;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ChargeResponse.ChargeResponseBuilder;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.*;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static uk.gov.pay.connector.model.ChargeResponse.aChargeResponse;
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
    public ChargeResponse create(Map<String, String> chargeRequest, GatewayAccountEntity gatewayAccount, UriInfo uriInfo) {
        String email = chargeRequest.get("email") != null ? chargeRequest.get("email") : null;
        ChargeEntity chargeEntity = new ChargeEntity(new Long(chargeRequest.get("amount")),
                chargeRequest.get("return_url"),
                chargeRequest.get("description"),
                chargeRequest.get("reference"),
                gatewayAccount,
                email
                );
        chargeDao.persist(chargeEntity);
        return chargeResponseBuilder(uriInfo, chargeEntity, createNewChargeEntityToken(chargeEntity)).build();
    }

    @Transactional
    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> {
                    if ( !ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().isFinished() ) {
                        return chargeResponseBuilder(uriInfo, chargeEntity, createNewChargeEntityToken(chargeEntity)).build();
                    }
                    return chargeResponseBuilder(uriInfo, chargeEntity).build();
                });
    }

    @Transactional
    public List<ChargeEntity> updateStatus(List<ChargeEntity> chargeEntities, ChargeStatus status) {
        List<ChargeEntity> mergedCharges = new ArrayList<>();
        chargeEntities.stream().forEach(chargeEntity -> {
            logger.info(format("Charge status to update - from=%s, to=%s, charge_external_id=%s",
                    chargeEntity.getStatus(), status, chargeEntity.getExternalId()));
            chargeEntity.setStatus(status);
            ChargeEntity mergedEnt = chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
            mergedCharges.add(mergedEnt);
        });
        return mergedCharges;
    }

    @Transactional
    public ChargeEntity updateCharge(ChargeEntity chargeEntity, ChargePatchRequest chargePatchRequest) {
        switch (chargePatchRequest.getPath()) {
            case ChargesApiResource.EMAIL_KEY:
                chargeEntity.setEmail(chargePatchRequest.getValue());
        }

        chargeDao.merge(chargeEntity);
        return chargeEntity;
    }

    private TokenEntity createNewChargeEntityToken(ChargeEntity chargeEntity) {
        TokenEntity token = TokenEntity.generateNewTokenFor(chargeEntity);
        tokenDao.persist(token);
        return token;
    }

    private ChargeResponseBuilder chargeResponseBuilder(UriInfo uriInfo, ChargeEntity charge, TokenEntity token) {
        return chargeResponseBuilder(uriInfo, charge)
                .withLink("next_url", GET, nextUrl(token.getToken()))
                .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, new HashMap<String, Object>() {{
                    put("chargeTokenId", token.getToken());
                }});
    }

    private ChargeResponseBuilder chargeResponseBuilder(UriInfo uriInfo, ChargeEntity charge) {
        String chargeId = charge.getExternalId();
        return aChargeResponse()
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withReference(charge.getReference())
                .withDescription(charge.getDescription())
                .withState(ChargeStatus.fromString(charge.getStatus()).toExternal())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withProviderName(charge.getGatewayAccount().getGatewayName())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withEmail(charge.getEmail())
                .withLink("self", GET, selfUriFor(uriInfo, charge.getGatewayAccount().getId(), chargeId));
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
