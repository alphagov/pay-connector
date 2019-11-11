package uk.gov.pay.connector.charge.resource;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FrontendChargeResponse;
import uk.gov.pay.connector.charge.model.NewChargeStatusRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.charge.model.FrontendChargeResponse.aFrontendChargeResponse;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.common.service.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.common.validator.ApiValidators.validateChargePatchParams;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargesFrontendResource {

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final CardTypeDao cardTypeDao;
    private final Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;

    @Inject
    public ChargesFrontendResource(ChargeDao chargeDao, ChargeService chargeService, CardTypeDao cardTypeDao, Worldpay3dsFlexJwtService worldpay3dsFlexJwtService) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.cardTypeDao = cardTypeDao;
        this.worldpay3dsFlexJwtService = worldpay3dsFlexJwtService;
    }

    @GET
    @Path("/v1/frontend/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {

        return chargeDao.findByExternalId(chargeId)
                .map(charge -> Response.ok(buildChargeResponse(uriInfo, charge)).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path("/v1/frontend/charges/{chargeId}/worldpay/3ds-flex/ddc")
    @Produces(APPLICATION_JSON)
    public Response getWorldpay3dsFlexDdcJwt(@PathParam("chargeId") String chargeId) {

        ChargeEntity chargeEntity = chargeService.findChargeById(chargeId);
        GatewayAccount gatewayAccount = GatewayAccount.valueOf(chargeEntity.getGatewayAccount());
        Worldpay3dsFlexCredentials = chargeEntity.getGatewayAccount().getWorldpay3dsFlexCredentials();
        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, chargeEntity.getCreatedDate());

        return Response.ok().entity(Map.of("jwt", token)).build();
    }

    @PATCH
    @Path("/v1/frontend/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response patchCharge(@PathParam("chargeId") String chargeId, Map<String, String> chargePatchMap, @Context UriInfo uriInfo) {
        PatchRequestBuilder.PatchRequest chargePatchRequest;

        try {
            chargePatchRequest = aPatchRequestBuilder(chargePatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(ImmutableSet.of(EMAIL_KEY))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + chargePatchMap.toString());
        }

        if (!validateChargePatchParams(chargePatchRequest)) {
            logger.error("Charge {}: InvalidPatchParameters", chargeId);
            return badRequestResponse("Invalid patch parameters" + chargePatchMap.toString());
        }

        return chargeService.updateCharge(chargeId, chargePatchRequest)
                .map(chargeEntity -> Response.ok(buildChargeResponse(uriInfo, chargeEntity)).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PUT
    @Path("/v1/frontend/charges/{chargeId}/status")
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response updateChargeStatus(
            @PathParam("chargeId") String chargeId,
            @Valid @NotNull NewChargeStatusRequest newChargeStatusRequest) {
        ChargeStatus newChargeStatus;

        try {
            newChargeStatus = ChargeStatus.fromString(newChargeStatusRequest.getNewStatus());
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(e.getMessage());
        }

        return chargeService.updateFromInitialStatus(chargeId, newChargeStatus)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> getInvalidStatusResponse(chargeId, newChargeStatus));
    }

    private Response getInvalidStatusResponse(String chargeId, ChargeStatus newChargeStatus) {
        return badRequestResponse("charge with id: " + chargeId +
                " cannot be updated to the new status: " + newChargeStatus.getValue());
    }

    private Optional<String> findCardBrandLabel(String cardBrand) {
        if (cardBrand == null) {
            return Optional.empty();
        }

        return cardTypeDao.findByBrand(cardBrand)
                .stream()
                .findFirst()
                .map(CardTypeEntity::getLabel);
    }

    private ChargeResponse buildChargeResponse(UriInfo uriInfo, ChargeEntity charge) {
        String chargeId = charge.getExternalId();

        FrontendChargeResponse.FrontendChargeResponseBuilder responseBuilder = aFrontendChargeResponse()
                .withStatus(charge.getStatus())
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withEmail(charge.getEmail())
                .withFee(charge.getFeeAmount().orElse(null))
                .withNetAmount(charge.getNetAmount().orElse(null))
                .withGatewayAccount(charge.getGatewayAccount())
                .withLanguage(charge.getLanguage())
                .withDelayedCapture(charge.isDelayedCapture())
                .withLink("self", GET, locationUriFor("/v1/frontend/charges/{chargeId}", uriInfo, chargeId))
                .withLink("cardAuth", POST, locationUriFor("/v1/frontend/charges/{chargeId}/cards", uriInfo, chargeId))
                .withLink("cardCapture", POST, locationUriFor("/v1/frontend/charges/{chargeId}/capture", uriInfo, chargeId))
                .withWalletType(charge.getWalletType());

        if (charge.getCardDetails() != null) {
            var persistedCard = charge.getCardDetails().toCard();
            persistedCard.setCardBrand(findCardBrandLabel(charge.getCardDetails().getCardBrand()).orElse(""));
            responseBuilder.withCardDetails(persistedCard);
        }
        
        if (charge.get3dsDetails() != null) {
            var auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(charge.get3dsDetails().getPaRequest());
            auth3dsData.setIssuerUrl(charge.get3dsDetails().getIssuerUrl());
            auth3dsData.setHtmlOut(charge.get3dsDetails().getHtmlOut());
            auth3dsData.setMd(charge.get3dsDetails().getMd());

            if (charge.getGatewayAccount().getGatewayName().equals(WORLDPAY.getName())) {
                worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(charge).ifPresent(
                        auth3dsData::setWorldpayChallengeJwt);
            }
            
            responseBuilder.withAuth3dsData(auth3dsData);
        }
        
        charge.getCorporateSurcharge().ifPresent(surcharge -> {
            if (surcharge > 0) {
                responseBuilder
                        .withCorporateCardSurcharge(surcharge)
                        .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
            }
        });

        return responseBuilder
                .build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path)
                .build(chargeId);
    }
}
