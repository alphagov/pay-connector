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
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;

public class ChargeService {

    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);

    public static final String EXPIRY_SUCCESS = "expiry-success";
    public static final String EXPIRY_FAILED = "expiry-failed";

    private ChargeDao chargeDao;
    private TokenDao tokenDao;
    private LinksConfig linksConfig;
    private CardCancelService cardCancelService;

    @Inject
    public ChargeService(TokenDao tokenDao, ChargeDao chargeDao, ConnectorConfiguration config, CardCancelService cardCancelService) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.linksConfig = config.getLinks();
        this.cardCancelService = cardCancelService;
    }

    public ChargeResponse create(Map<String, Object> chargeRequest, GatewayAccountEntity gatewayAccount, UriInfo uriInfo) {

        ChargeEntity chargeEntity = new ChargeEntity(new Long(chargeRequest.get("amount").toString()),
                chargeRequest.get("return_url").toString(),
                chargeRequest.get("description").toString(),
                chargeRequest.get("reference").toString(),
                gatewayAccount);
        chargeDao.persist(chargeEntity);
        TokenEntity token = new TokenEntity(chargeEntity.getId(), UUID.randomUUID().toString());
        tokenDao.persist(token);
        ChargeResponse response = buildChargeResponse(uriInfo, chargeEntity, Optional.of(token));
        logger.info("charge = {}", chargeEntity);
        return response;
    }

    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> {
                    Optional<TokenEntity> token = tokenDao.findByChargeId(chargeEntity.getId());
                    return buildChargeResponse(uriInfo, chargeEntity, token);
                });
    }

    @Transactional
    public void updateStatus(List<ChargeEntity> chargeEntities, ChargeStatus status) {
        chargeEntities.stream().forEach(chargeEntity -> {
            chargeEntity.setStatus(status);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        });
    }

    public Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(
                        chargeEntity -> ChargeStatus.AUTHORISATION_SUCCESS.getValue().equals(chargeEntity.getStatus())));
        List<ChargeEntity> nonAuthSuccessCharges = chargesToProcessExpiry.get(Boolean.FALSE);
        updateStatus(nonAuthSuccessCharges, ChargeStatus.EXPIRED);
        int expiredSuccess = nonAuthSuccessCharges.size();

        List<ChargeEntity> authSuccessCharges = chargesToProcessExpiry.get(Boolean.TRUE);
        Pair<Integer, Integer> successFailPair = expireChargesInAuthorisationSuccess(authSuccessCharges);

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + successFailPair.getLeft(), EXPIRY_FAILED, successFailPair.getRight());
    }

    private Pair<Integer, Integer> expireChargesInAuthorisationSuccess(List<ChargeEntity> charges) {
        updateStatus(charges, ChargeStatus.EXPIRE_CANCEL_PENDING);

        List<ChargeEntity> successfullyCancelled = new ArrayList<>();
        List<ChargeEntity> failedCancelled = new ArrayList<>();

        charges.stream().forEach(chargeEntity -> {
            Either<ErrorResponse, GatewayResponse> gatewayResponse = cardCancelService.doCancel(chargeEntity.getExternalId(), chargeEntity.getGatewayAccount().getId());

            if (responseIsNotSuccessful(gatewayResponse)) {
                logUnsuccessfulResponseReasons(chargeEntity, gatewayResponse);
                failedCancelled.add(chargeEntity);
            } else {
                successfullyCancelled.add(chargeEntity);
            }
        });
        updateStatus(successfullyCancelled, ChargeStatus.EXPIRED);
        int expiredSuccess = successfullyCancelled.size();
        updateStatus(failedCancelled, ChargeStatus.EXPIRE_CANCEL_FAILED);
        int expireFailed = failedCancelled.size();
        return Pair.of(expiredSuccess, expireFailed);
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, Either<ErrorResponse, GatewayResponse> gatewayResponse) {
        if (gatewayResponse.isLeft()) {
            logger.error(format("gateway error: %s %s, while cancelling the charge ID %s",
                    gatewayResponse.left().value().getMessage(),
                    gatewayResponse.left().value().getErrorType(),
                    chargeEntity.getId()));
        }

        if (gatewayResponse.isRight()) {
            logger.error(format("gateway unsuccessful response: %s, while cancelling Charge ID: %s",
                    gatewayResponse.right().value().getError(), chargeEntity.getId()));
        }
    }

    private boolean responseIsNotSuccessful(Either<ErrorResponse, GatewayResponse> gatewayResponse) {
        return gatewayResponse.isLeft() || negate(gatewayResponse.right().value().isSuccessful());
    }

    private ChargeResponse buildChargeResponse(UriInfo uriInfo, ChargeEntity charge, Optional<TokenEntity> token) {
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
        token.ifPresent(tokenEntity -> {
            responseBuilder.withLink("next_url", GET, nextUrl(chargeId, tokenEntity.getToken()));
            responseBuilder.withLink("next_url_post", POST, chargeUrl(chargeId), APPLICATION_FORM_URLENCODED, new HashMap<String, Object>() {{
                put("chargeTokenId", tokenEntity.getToken());
            }});
        });

        return responseBuilder.build();
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
                .build(accountId, chargeId);
    }

    private URI nextUrl(String chargeId, String tokenId) {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("charge")
                .path(chargeId)
                .queryParam("chargeTokenId", tokenId)
                .build();
    }

    private URI chargeUrl(String chargeId) {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("charge")
                .path(chargeId)
                .build();
    }
}
