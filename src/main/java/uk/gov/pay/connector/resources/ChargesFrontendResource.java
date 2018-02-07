package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.PATCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
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
import static uk.gov.pay.connector.model.FrontendChargeResponse.aFrontendChargeResponse;
import static uk.gov.pay.connector.model.builder.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_AUTHORIZE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_CAPTURE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.FRONTEND_CHARGE_STATUS_API_PATH;
import static uk.gov.pay.connector.resources.ApiValidators.validateChargePatchParams;
import static uk.gov.pay.connector.resources.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.fieldsMissingResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;

@Path("/")
public class ChargesFrontendResource {

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private static final String NEW_STATUS = "new_status";
    private final ChargeService chargeService;
    private final CardTypeDao cardTypeDao;
    private final PaymentRequestDao paymentRequestDao;

    @Inject
    public ChargesFrontendResource(ChargeService chargeService, CardTypeDao cardTypeDao, PaymentRequestDao paymentRequestDao) {
        this.chargeService = chargeService;
        this.cardTypeDao = cardTypeDao;
        this.paymentRequestDao = paymentRequestDao;
    }

    @GET
    @Path(FRONTEND_CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    @JsonView(GatewayAccountEntity.Views.FrontendView.class)
    public Response getCharge(@PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<PaymentRequestEntity> paymentRequestEntity = paymentRequestDao.findByExternalId(chargeId);
        return paymentRequestEntity
                .map(paymentRequest -> Response.ok(buildChargeResponse(uriInfo, paymentRequest)).build())
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

        return chargeService.updateEmail(chargeId, chargePatchRequest)
                .map(paymentRequestEntity -> Response.ok(buildChargeResponse(uriInfo, paymentRequestEntity)).build())
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

        ChargeStatus newChargeStatus;
        try {
            newChargeStatus = ChargeStatus.fromString(newStatusMap.get(NEW_STATUS).toString());
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(e.getMessage());
        }

        if (!isValidStateTransition(newChargeStatus)) {
            return getInvalidStatusResponse(chargeId, newChargeStatus);
        }

        return chargeService.updateFromInitialStatus(chargeId, newChargeStatus)
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

    private ChargeResponse buildChargeResponse(UriInfo uriInfo, PaymentRequestEntity paymentRequest) {
        String externalId = paymentRequest.getExternalId();
        PersistedCard persistedCard = null;
        ChargeTransactionEntity chargeTransaction = paymentRequest.getChargeTransaction();
        CardEntity cardEntity = chargeTransaction.getCard();
        if (cardEntity != null) {
            persistedCard = PersistedCard.from(cardEntity, findCardBrandLabel(cardEntity.getCardBrand()).orElse(""));
        }

        ChargeResponse.Auth3dsData auth3dsData = null;
        Card3dsEntity card3ds = chargeTransaction.getCard3ds();
        if (card3ds != null) {
            auth3dsData = ChargeResponse.Auth3dsData.from(card3ds);
        }

        return aFrontendChargeResponse()
                .withStatus(chargeTransaction.getStatus().getValue())
                .withChargeId(externalId)
                .withAmount(chargeTransaction.getAmount())
                .withDescription(paymentRequest.getDescription())
                .withGatewayTransactionId(chargeTransaction.getGatewayTransactionId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(chargeTransaction.getCreatedDate()))
                .withReturnUrl(paymentRequest.getReturnUrl())
                .withEmail(chargeTransaction.getEmail())
                .withChargeCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withGatewayAccount(paymentRequest.getGatewayAccount())
                .withLink("self", GET, locationUriFor(FRONTEND_CHARGE_API_PATH, uriInfo, externalId))
                .withLink("cardAuth", POST, locationUriFor(FRONTEND_CHARGE_AUTHORIZE_API_PATH, uriInfo, externalId))
                .withLink("cardCapture", POST, locationUriFor(FRONTEND_CHARGE_CAPTURE_API_PATH, uriInfo, externalId)).build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path)
                .build(chargeId);
    }
}
