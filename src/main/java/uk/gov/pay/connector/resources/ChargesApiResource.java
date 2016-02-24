package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.F;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.*;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.dao.IGatewayAccountDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeEventExternal;
import uk.gov.pay.connector.util.ChargesCSVGenerator;
import uk.gov.pay.connector.util.ResponseBuilder;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static fj.data.Either.reduce;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.valueOfExternalStatus;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.ApiValidators.validateGatewayAccountReference;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCDateString;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesApiResource {
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
    public static final String CREATED_DATE = "created_date";
    public static final String FROM_DATE_KEY = "from_date";
    public static final String TO_DATE_KEY = "to_date";
    private final String TEXT_CSV = "text/csv";

    private IChargeDao chargeDao;
    private ITokenDao tokenDao;
    private IGatewayAccountDao gatewayAccountDao;
    private IEventDao eventDao;
    private LinksConfig linksConfig;

    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public ChargesApiResource(IChargeDao chargeDao, ITokenDao tokenDao, IGatewayAccountDao gatewayAccountDao, IEventDao eventDao, LinksConfig linksConfig) {
        this.chargeDao = chargeDao;
        this.tokenDao = tokenDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.eventDao = eventDao;
        this.linksConfig = linksConfig;
    }

    @GET
    @Path(CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("accountId") String accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        Optional<Map<String, Object>> maybeCharge = chargeDao.findChargeForAccount(chargeId, accountId);

        return maybeCharge
                .map(charge -> {
                    URI selfUri = selfUriFor(uriInfo, accountId, chargeId);
                    String tokenId = tokenDao.findByChargeId(chargeId);
                    Map<String, Object> responseData = getResponseData(chargeId, tokenId, charge, selfUri);

                    return ResponseUtil.entityResponse(responseData);
                })
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));

    }

    @GET
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getChargesJson(@PathParam("accountId") String accountId,
                                   @QueryParam(REFERENCE_KEY) String reference,
                                   @QueryParam(STATUS_KEY) String status,
                                   @QueryParam(FROM_DATE_KEY) String fromDate,
                                   @QueryParam(TO_DATE_KEY) String toDate,
                                   @Context UriInfo uriInfo) {

        Optional<String> errorMessageOptional = ApiValidators.validateDateQueryParams(ImmutableList.of(
                Pair.of(FROM_DATE_KEY, fromDate),
                Pair.of(TO_DATE_KEY, toDate)
        ));
        return errorMessageOptional
                .map(errorMessage -> badRequestResponse(logger, errorMessage))
                .orElseGet(() ->
                        reduce(validateGatewayAccountReference(accountId)
                                .bimap(handleError, listChargesResponse(accountId, reference, status, fromDate, toDate, jsonResponse()))));
    }

    @GET
    @Path(CHARGES_API_PATH)
    @Produces(TEXT_CSV)
    public Response getChargesCsv(@PathParam("accountId") String accountId,
                                  @QueryParam(REFERENCE_KEY) String reference,
                                  @QueryParam(STATUS_KEY) String status,
                                  @QueryParam(FROM_DATE_KEY) String fromDate,
                                  @QueryParam(TO_DATE_KEY) String toDate,
                                  @Context UriInfo uriInfo) {

        Optional<String> errorMessageOptional = ApiValidators.validateDateQueryParams(ImmutableList.of(
                Pair.of(FROM_DATE_KEY, fromDate),
                Pair.of(TO_DATE_KEY, toDate)
        ));
        return errorMessageOptional
                .map(errorMessage -> Response.status(BAD_REQUEST).entity(errorMessage).build())
                .orElseGet(() ->
                        reduce(validateGatewayAccountReference(accountId)
                                .bimap(handleError, listChargesResponse(accountId, reference, status, fromDate, toDate, csvResponse()))));

    }

    @POST
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(@PathParam("accountId") String accountId, Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(logger, missingFields.get());
        }

        Optional<List<String>> invalidSizeFields = checkInvalidSizeFields(chargeRequest);
        if (invalidSizeFields.isPresent()) {
            return fieldsInvalidSizeResponse(logger, invalidSizeFields.get());
        }

        if (gatewayAccountDao.idIsMissing(accountId)) {
            return notFoundResponse(logger, "Unknown gateway account: " + accountId);
        }

        logger.info("Creating new charge of {}.", chargeRequest);
        String chargeId = chargeDao.saveNewCharge(accountId, chargeRequest);
        String tokenId = UUID.randomUUID().toString();
        tokenDao.insertNewToken(chargeId, tokenId);

        Optional<Map<String, Object>> maybeCharge = chargeDao.findById(chargeId);

        return maybeCharge
                .map(charge -> {
                    URI selfUri = selfUriFor(uriInfo, accountId, chargeId);
                    Map<String, Object> responseData = getResponseData(chargeId, tokenId, charge, selfUri);

                    logger.info("charge = {}", charge);
                    logger.info("responseData = {}", responseData);

                    return entityCreatedResponse(selfUri, responseData);
                })
                .orElseGet(() -> responseWithChargeNotFound(logger, chargeId));
    }

    @GET
    @Path(CHARGE_EVENTS_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getEvents(@PathParam("accountId") Long accountId, @PathParam("chargeId") Long chargeId) {
        List<ChargeEvent> events = eventDao.findEvents(accountId, chargeId);
        List<ChargeEventExternal> eventsExternal = transformToExternalStatus(events);
        List<ChargeEventExternal> nonRepeatingExternalChargeEvents = getNonRepeatingChargeEvents(eventsExternal);

        ImmutableMap<String, Object> responsePayload = ImmutableMap.of("charge_id", chargeId, "events", nonRepeatingExternalChargeEvents);
        return ok().entity(responsePayload).build();
    }

    private List<ChargeEventExternal> transformToExternalStatus(List<ChargeEvent> events) {
        List<ChargeEventExternal> externalEvents = events
                .stream()
                .map(event ->
                        new ChargeEventExternal(event.getChargeId(), mapFromStatus(event.getStatus()), event.getUpdated()))
                .collect(toList());

        return externalEvents;
    }

    private List<ChargeEventExternal> getNonRepeatingChargeEvents(List<ChargeEventExternal> externalEvents) {
        ListIterator<ChargeEventExternal> iterator = externalEvents.listIterator();
        ChargeEventExternal prev = null;
        while (iterator.hasNext()) {
            ChargeEventExternal current = iterator.next();
            if (current.equals(prev)) { // remove any immediately repeating statuses
                iterator.remove();
            }
            prev = current;
        }
        return externalEvents;
    }

    private Map<String, Object> getResponseData(String chargeId, String tokenId, Map<String, Object> charge, URI selfUri) {
        ResponseBuilder responseBuilder = new ResponseBuilder()
                .withCharge(convertStatusToExternalStatus(newHashMap(charge)))
                .withLink("self", GET, selfUri);

        if (!isEmpty(tokenId)) {
            URI nextUrl = secureRedirectUriFor(chargeId, tokenId);
            responseBuilder.withLink("next_url", GET, nextUrl);
        }

        return responseBuilder.build();
    }

    private Map<String, Object> convertStatusToExternalStatus(Map<String, Object> data) {
        ExternalChargeStatus externalState = mapFromStatus(data.get(STATUS_KEY).toString());
        data.put(STATUS_KEY, externalState.getValue());
        return data;
    }

    private URI selfUriFor(UriInfo uriInfo, String accountId, String chargeId) {
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

    private F<Boolean, Response> listChargesResponse(final String accountId, String reference, String status, String fromDate, String toDate, Function<Object, Response> buildResponseFunction) {
        return success -> {
            List<Map<String, Object>> charges = getChargesForCriteria(accountId, reference, status, fromDate, toDate);

            if (charges.isEmpty()) {
                logger.info("no charges found for given filter");
                return gatewayAccountDao.findById(accountId)
                        .map(x -> buildResponseFunction.apply(charges))
                        .orElseGet(() -> notFoundResponse(logger, format("account with id %s not found", accountId)));
            }
            return buildResponseFunction.apply(charges);
        };
    }

    private Function<Object, Response> jsonResponse() {
        return charges -> ok(ImmutableMap.of("results", charges)).build();
    }

    private Function<Object, Response> csvResponse() {
        return charges -> ok(ChargesCSVGenerator.generate((List<Map<String, Object>>) charges)).build();
    }

    private List<Map<String, Object>> getChargesForCriteria(String accountId, String reference, String status, String fromDate, String toDate) {
        ExternalChargeStatus chargeStatus = null;
        if (StringUtils.isNotBlank(status)) {
            chargeStatus = valueOfExternalStatus(status);
        }
        List<Map<String, Object>> charges = chargeDao.findAllBy(accountId, reference, chargeStatus, fromDate, toDate);
        charges.forEach(charge -> {
            charge.put(STATUS_KEY, mapFromStatus(charge.get(STATUS_KEY).toString()).getValue());
            charge.put(CREATED_DATE, toUTCDateString((ZonedDateTime) charge.get(CREATED_DATE)));
        });
        return charges;
    }

    private static F<String, Response> handleError =
            errorMessage -> badRequestResponse(logger, errorMessage);


}
