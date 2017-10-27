package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
import javax.ws.rs.*;
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
import static uk.gov.pay.connector.model.FrontendChargeResponse.aFrontendChargeResponse;
import static uk.gov.pay.connector.model.builder.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.ApiValidators.validateChargePatchParams;
import static uk.gov.pay.connector.resources.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesFrontendResource {

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private static final String NEW_STATUS = "new_status";
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final CardTypeDao cardTypeDao;

    @Inject
    public ChargesFrontendResource(ChargeDao chargeDao, ChargeService chargeService, CardTypeDao cardTypeDao) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.cardTypeDao = cardTypeDao;
    }

    @GET
    @Path(FRONTEND_CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {

        Optional<ChargeEntity> maybeCharge = chargeDao.findByExternalId(chargeId);
        logger.debug("charge from DB: " + maybeCharge);

        return maybeCharge
                .map(charge -> Response.ok(buildChargeResponse(uriInfo, charge)).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PATCH
    @Path(FRONTEND_CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response patchCharge(@PathParam("chargeId") String chargeId, Map<String, String> chargePatchMap, @Context UriInfo uriInfo) {
        PatchRequestBuilder.PatchRequest chargePatchRequest;

        try {
            chargePatchRequest = aPatchRequestBuilder(chargePatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(Collections.singletonList(EMAIL_KEY))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + chargePatchMap.toString());
        }

        if (!validateChargePatchParams(chargePatchRequest)) {
            return badRequestResponse("Invalid patch parameters" + chargePatchMap.toString());
        }

        return chargeService.updateCharge(chargeId, chargePatchRequest)
                .map(chargeEntity -> Response.ok(buildChargeResponse(uriInfo, chargeEntity)).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PUT
    @Path(FRONTEND_CHARGE_STATUS_API_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response updateChargeStatus(@PathParam("chargeId") String chargeId, Map newStatusMap) {
        if (invalidInput(newStatusMap)) {
            return fieldsMissingResponse(ImmutableList.of(NEW_STATUS));
        }

        String chargeStatusString = newStatusMap.get(NEW_STATUS).toString();
        if (!ChargeStatus.isValid(chargeStatusString)) {
            String msg = "charge status not recognized: " + chargeStatusString;
            logger.error(msg);
            return badRequestResponse(msg);
        }

        ChargeStatus newChargeStatus = ChargeStatus.fromString(newStatusMap.get(NEW_STATUS).toString());
        if (!isValidStateTransition(newChargeStatus)) {
            return getInvalidStatusResponse(chargeId, newChargeStatus);
        }

        return chargeService.updateStatus(chargeId, newChargeStatus)
                .map(chargeEntity -> Response.noContent().build())
                .orElseGet(() -> getInvalidStatusResponse(chargeId, newChargeStatus));
    }

    private Response getInvalidStatusResponse(String chargeId, ChargeStatus newChargeStatus) {
        return badRequestResponse("charge with id: " + chargeId +
                " cannot be updated to the new status: " + newChargeStatus.getValue());
    }

    private boolean invalidInput(Map newStatusMap) {
        return newStatusMap == null || newStatusMap.get(NEW_STATUS) == null;
    }

    private boolean isValidStateTransition(ChargeStatus newChargeStatus) {
        return newChargeStatus.equals(ENTERING_CARD_DETAILS);
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
        PersistedCard persistedCard = null;
        if (charge.getCardDetails() != null) {
            persistedCard = charge.getCardDetails().toCard();
            persistedCard.setCardBrand(findCardBrandLabel(charge.getCardDetails().getCardBrand()).orElse(""));
        }

        ChargeResponse.Auth3dsData auth3dsData = null;
        if (charge.get3dsDetails() != null) {
            auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(charge.get3dsDetails().getPaRequest());
            auth3dsData.setIssuerUrl(charge.get3dsDetails().getIssuerUrl());
        }

        return aFrontendChargeResponse()
                .withStatus(charge.getStatus())
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(charge.getCreatedDate()))
                .withReturnUrl(charge.getReturnUrl())
                .withEmail(charge.getEmail())
                .withChargeCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withGatewayAccount(charge.getGatewayAccount())
                .withLink("self", GET, locationUriFor(FRONTEND_CHARGE_API_PATH, uriInfo, chargeId))
                .withLink("cardAuth", POST, locationUriFor(FRONTEND_CHARGE_AUTHORIZE_API_PATH, uriInfo, chargeId))
                .withLink("cardCapture", POST, locationUriFor(FRONTEND_CHARGE_CAPTURE_API_PATH, uriInfo, chargeId)).build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path)
                .build(chargeId);
    }
}
