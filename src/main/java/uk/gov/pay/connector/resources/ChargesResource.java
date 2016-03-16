package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import fj.F;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.util.ChargesCSVGenerator;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.*;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.dao.ChargeSearch.aChargeSearch;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.valueOfExternalStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.ApiValidators.validateGatewayAccountReference;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesResource {
    private static final String AMOUNT_KEY = "amount";
    private static final String DESCRIPTION_KEY = "description";
    private static final String RETURN_URL_KEY = "return_url";
    private static final String REFERENCE_KEY = "reference";
    private static final String[] REQUIRED_FIELDS = {AMOUNT_KEY, DESCRIPTION_KEY, REFERENCE_KEY, RETURN_URL_KEY};
    private static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = ImmutableMap.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255
    );

    private static final String STATUS_KEY = "status";
    public static final String FROM_DATE_KEY = "from_date";
    public static final String TO_DATE_KEY = "to_date";
    private final String TEXT_CSV = "text/csv";

    private ChargeDao chargeDao;
    private GatewayAccountDao gatewayAccountDao;
    private ChargeService chargeService;
    private CardService cardService;

    private static final int ONE_HOUR = 3600;
    private static final String CHARGE_EXPIRY_WINDOW = "CHARGE_EXPIRY_WINDOW_SECONDS";
    protected static final ArrayList<ChargeStatus> NON_TERMINAL_STATUSES = Lists.newArrayList(
                    CREATED,
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUBMITTED,
                    AUTHORISATION_SUCCESS);

    private static final Logger logger = LoggerFactory.getLogger(ChargesResource.class);

    @Inject
    public ChargesResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao, ChargeService chargeService) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeService = chargeService;
    }

    @GET
    @Path(CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("accountId") Long accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(chargeId, accountId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
    }

    @GET
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getChargesJson(@PathParam("accountId") Long accountId,
                                   @QueryParam(REFERENCE_KEY) String reference,
                                   @QueryParam(STATUS_KEY) String status,
                                   @QueryParam(FROM_DATE_KEY) String fromDate,
                                   @QueryParam(TO_DATE_KEY) String toDate,
                                   @Context UriInfo uriInfo) {
        return ApiValidators
                .validateDateQueryParams(ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate)))
                .map(errorMessage -> badRequestResponse(logger, errorMessage))
                .orElseGet(() -> reduce(validateGatewayAccountReference(gatewayAccountDao, accountId)
                        .bimap(handleError, listCharges(accountId, reference, status, fromDate, toDate, jsonResponse()))));
    }

    @GET
    @Path(CHARGES_API_PATH)
    @Produces(TEXT_CSV)
    public Response getChargesCsv(@PathParam("accountId") Long accountId,
                                  @QueryParam(REFERENCE_KEY) String reference,
                                  @QueryParam(STATUS_KEY) String status,
                                  @QueryParam(FROM_DATE_KEY) String fromDate,
                                  @QueryParam(TO_DATE_KEY) String toDate,
                                  @Context UriInfo uriInfo) {
        return ApiValidators
                .validateDateQueryParams(ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate)))
                .map(errorMessage -> status(BAD_REQUEST).entity(errorMessage).build())
                .orElseGet(() -> reduce(validateGatewayAccountReference(gatewayAccountDao, accountId)
                        .bimap(handleError, listCharges(accountId, reference, status, fromDate, toDate, csvResponse()))));
    }

    private F<Boolean, Response> listCharges(Long accountId, String reference, String status, String fromDate, String toDate, Function<List<ChargeEntity>, Response> responseFunction) {
        return success -> {
            List<ChargeEntity> charges = chargeDao.findAllBy(aChargeSearch(accountId)
                    .withReferenceLike(reference)
                    .withExternalStatus(parseStatus(status))
                    .withCreatedDateFrom(parseDate(fromDate))
                    .withCreatedDateTo(parseDate(toDate)));
            return responseFunction.apply(charges);
        };
    }

    @POST
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(@PathParam("accountId") Long accountId, Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(logger, missingFields.get());
        }

        Optional<List<String>> invalidSizeFields = checkInvalidSizeFields(chargeRequest);
        if (invalidSizeFields.isPresent()) {
            return fieldsInvalidSizeResponse(logger, invalidSizeFields.get());
        }

        return gatewayAccountDao.findById(accountId).map(
                gatewayAccountEntity -> {
                    logger.info("Creating new charge of {}.", chargeRequest);
                    ChargeResponse response = chargeService.create(chargeRequest, gatewayAccountEntity, uriInfo);
                    logger.info("responseData = {}", response);
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse(logger, "Unknown gateway account: " + accountId));
    }

    @POST
    @Path(EXPIRE_CHARGES)
    public Response expireCharges(@Context UriInfo uriInfo) {
        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(getExpiryDate(), NON_TERMINAL_STATUSES);
        logger.info(format("%s charges found expiring since %s", charges.size(), getExpiryDate()));
        chargeService.expire(charges);
        return noContentResponse();
    }

    private ZonedDateTime getExpiryDate() {
        //default expiry window, can be overridden by env var
        int chargeExpiryWindowSeconds = ONE_HOUR;
        if (StringUtils.isNotBlank(System.getenv(CHARGE_EXPIRY_WINDOW))) {
            chargeExpiryWindowSeconds = Integer.parseInt(System.getenv(CHARGE_EXPIRY_WINDOW));
        }
        logger.info("Charge expiry window size in seconds: " +chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
    }

    private ExternalChargeStatus parseStatus(String status) {
        ExternalChargeStatus chargeStatus = null;
        if (isNotBlank(status)) {
            chargeStatus = valueOfExternalStatus(status);
        }
        return chargeStatus;
    }

    private ZonedDateTime parseDate(String date) {
        ZonedDateTime parse = null;
        if (isNotBlank(date)) {
            parse = ZonedDateTime.parse(date);
        }
        return parse;
    }

    private Optional<List<String>> checkMissingFields(Map<String, Object> inputData) {
        List<String> missing = Arrays.stream(REQUIRED_FIELDS)
                .filter(field -> !inputData.containsKey(field))
                .collect(Collectors.toList());

        return missing.isEmpty()
                ? Optional.<List<String>>empty()
                : Optional.of(missing);
    }

    private Optional<List<String>> checkInvalidSizeFields(Map<String, Object> inputData) {
        List<String> invalidSize = MAXIMUM_FIELDS_SIZE.entrySet().stream()
                .filter(entry -> !isFieldSizeValid(inputData, entry.getKey(), entry.getValue()))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());

        return invalidSize.isEmpty()
                ? Optional.<List<String>>empty()
                : Optional.of(invalidSize);
    }

    private boolean isFieldSizeValid(Map<String, Object> chargeRequest, String fieldName, int fieldSize) {
        String value = chargeRequest.get(fieldName).toString();
        return value.length() <= fieldSize;
    }

    private Function<List<ChargeEntity>, Response> jsonResponse() {
        return charges -> ok(ImmutableMap.of("results", charges.stream()
                .map(charge -> aChargeResponse()
                        .withChargeId(charge.getExternalId())
                        .withAmount(charge.getAmount())
                        .withReference(charge.getReference())
                        .withDescription(charge.getDescription())
                        .withStatus(mapFromStatus(charge.getStatus()).getValue())
                        .withGatewayTransactionId(charge.getGatewayTransactionId())
                        .withCreatedDate(charge.getCreatedDate())
                        .withReturnUrl(charge.getReturnUrl()).build())
                .collect(Collectors.toList()))).build();
    }

    private Function<List<ChargeEntity>, Response> csvResponse() {
        return charges -> ok(ChargesCSVGenerator.generate(charges)).build();
    }

    private static F<String, Response> handleError =
            errorMessage -> notFoundResponse(logger, errorMessage);
}
