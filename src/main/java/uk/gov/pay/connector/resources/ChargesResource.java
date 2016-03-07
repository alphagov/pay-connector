package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.F;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.ChargeJpaDao;
import uk.gov.pay.connector.dao.ChargeSearchQuery;
import uk.gov.pay.connector.dao.GatewayAccountJpaDao;
import uk.gov.pay.connector.dao.TokenJpaDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.model.ChargeResponse.Builder;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.util.ChargesCSVGenerator;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.valueOfExternalStatus;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiValidators.validateGatewayAccountReference;
import static uk.gov.pay.connector.model.ChargeResponse.Builder.aChargeResponse;
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

    private ChargeJpaDao chargeDao;
    private TokenJpaDao tokenDao;
    private GatewayAccountJpaDao gatewayAccountDao;
    private ChargeService chargeService;
    private LinksConfig linksConfig;

    private static final Logger logger = LoggerFactory.getLogger(ChargesResource.class);

    @Inject
    public ChargesResource(ChargeJpaDao chargeDao, TokenJpaDao tokenDao, GatewayAccountJpaDao gatewayAccountDao, ChargeService chargeService, ConnectorConfiguration configuration) {
        this.chargeDao = chargeDao;
        this.tokenDao = tokenDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeService = chargeService;
        this.linksConfig = configuration.getLinks();
    }

    @GET
    @Path(CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam("accountId") String accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(Long.valueOf(chargeId), accountId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
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

        return gatewayAccountDao.findById(Long.valueOf(accountId)).map(
                gatewayAccountEntity -> {
                    logger.info("Creating new charge of {}.", chargeRequest);
                    ChargeResponse response = chargeService.create(chargeRequest, gatewayAccountEntity, uriInfo);
                    logger.info("responseData = {}", response);
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElse(notFoundResponse(logger, "Unknown gateway account: " + accountId));
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

    private F<Boolean, Response> listChargesResponse(final String accountId, String reference, String status, String fromDate, String toDate, Function<List<ChargeEntity>, Response> responseFunction) {
        return success -> {

            List<ChargeEntity> charges = getChargesForCriteriaJpa(accountId, reference, status, fromDate, toDate);

            if (charges.isEmpty()) {
                logger.info("no charges found for given filter");
                if (!gatewayAccountDao.findById(Long.valueOf(accountId)).isPresent()) {
                    return notFoundResponse(logger, format("account with id %s not found", accountId));
                }
            }

            return responseFunction.apply(charges);
        };
    }

    private Function<List<ChargeEntity>, Response> jsonResponse() {
        return charges -> ok(ImmutableMap.of("results", charges.stream()
                .map(charge -> aChargeResponse()
                        .withChargeId(String.valueOf(charge.getId()))
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

    private List<ChargeEntity> getChargesForCriteriaJpa(String accountId, String reference, String status, String fromDate, String toDate) {
        ExternalChargeStatus chargeStatus = null;
        if (StringUtils.isNotBlank(status)) {
            chargeStatus = valueOfExternalStatus(status);
        }

        ChargeSearchQuery searchQuery = new ChargeSearchQuery(new Long(accountId));
        searchQuery.withReferenceLike(reference);
        searchQuery.withExternalStatus(chargeStatus);
        searchQuery.withCreatedDateFrom(fromDate);
        searchQuery.withCreatedDateTo(toDate);

        return chargeDao.findAllBy(searchQuery);
    }

    private static F<String, Response> handleError =
            errorMessage -> badRequestResponse(logger, errorMessage);
}
