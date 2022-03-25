package uk.gov.pay.connector.charge.resource;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.charge.exception.TelephonePaymentNotificationsNotAllowedException;
import uk.gov.pay.connector.charge.model.AuthMode;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.stripe.json.Card;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithGatewayTransactionNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class ChargesApiResource {
    public static final String EMAIL_KEY = "email";
    public static final String AMOUNT_KEY = "amount";
    public static final String LANGUAGE_KEY = "language";
    public static final String DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String DESCRIPTION_KEY = "description";
    private static final String REFERENCE_KEY = "reference";
    public static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = ImmutableMap.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255,
            EMAIL_KEY, 254
    );
    private static final String ACCOUNT_ID = "accountId";
    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 10_000_000;
    private final ChargeService chargeService;
    private final ChargeExpiryService chargeExpiryService;
    private final GatewayAccountService gatewayAccountService;
    private final CardAuthoriseService cardAuthoriseService;
    private final AgreementService agreementService;

    @Inject
    public ChargesApiResource(ChargeService chargeService,
                              ChargeExpiryService chargeExpiryService,
                              GatewayAccountService gatewayAccountService,
                              CardAuthoriseService cardAuthoriseService,
                              AgreementService agreementService) {
        this.chargeService = chargeService;
        this.chargeExpiryService = chargeExpiryService;
        this.gatewayAccountService = gatewayAccountService;
        this.cardAuthoriseService = cardAuthoriseService;
        this.agreementService = agreementService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam(ACCOUNT_ID) Long accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(chargeId, accountId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/charges")
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(
            @PathParam(ACCOUNT_ID) Long accountId,
            @NotNull @Valid ChargeCreateRequest chargeRequest,
            @Context UriInfo uriInfo
    ) {
        logger.info("Creating new charge - {}", chargeRequest.toStringWithoutPersonalIdentifiableInformation());

        return chargeService.create(chargeRequest, accountId, uriInfo)
                .map(response -> {
                    // TODO(sfount): should throw an error if this isn't all correct (before creating charge)
                    if (chargeRequest.getAuthMode() == AuthMode.API && chargeRequest.getAgreementId() != null) {
                        // there's no reason to get and get the charge, it can just be returned
                        chargeService.findChargeEntity(response.getChargeId())
                                        .ifPresent(charge -> {
                                             agreementService.find(charge.getAgreementId())
                                                             .ifPresent(agreement -> {
                                                                 cardAuthoriseService.doAuthorise(charge.getExternalId(), agreement.getPaymentInstrument());
                                                             });
                                        });
                    }
                    
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @POST
    @Path("v1/api/accounts/{accountId}/telephone-charges")
    @Produces(APPLICATION_JSON)
    public Response createNewTelephoneCharge(
            @PathParam(ACCOUNT_ID) Long accountId,
            @NotNull @Valid TelephoneChargeCreateRequest telephoneChargeCreateRequest,
            @Context UriInfo uriInfo
    ) {
        GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        if (!gatewayAccount.isAllowTelephonePaymentNotifications()) {
            throw new TelephonePaymentNotificationsNotAllowedException(gatewayAccount.getId());
        }
        
        return chargeService.findCharge(accountId, telephoneChargeCreateRequest)
                .map(response -> Response.status(200).entity(response).build())
                .orElseGet(() -> Response.status(201).entity(chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount)).build());
    }

    @POST
    @Path("/v1/tasks/expired-charges-sweep")
    @Produces(APPLICATION_JSON)
    public Response expireCharges(@Context UriInfo uriInfo) {
        Map<String, Integer> resultMap = chargeExpiryService.sweepAndExpireChargesAndTokens();
        return successResponseWithEntity(resultMap);
    }

    @GET
    @Path("/v1/api/charges/gateway_transaction/{gatewayTransactionId}")
    @Produces(APPLICATION_JSON)
    public Response getChargeForGatewayTransactionId(@PathParam("gatewayTransactionId") String gatewayTransactionId, @Context UriInfo uriInfo) {
        return chargeService.findChargeByGatewayTransactionId(gatewayTransactionId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
                .orElseGet(() -> responseWithGatewayTransactionNotFound(gatewayTransactionId));
    }
}
