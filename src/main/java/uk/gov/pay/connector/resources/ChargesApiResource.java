package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import fj.F;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.ChargeExpiryService;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static fj.data.Either.reduce;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.model.ChargeResponse.aChargeResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.ApiValidators.validateGatewayAccountReference;
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

    private static final String STATE_KEY = "state";
    private static final String FROM_DATE_KEY = "from_date";
    private static final String TO_DATE_KEY = "to_date";
    private static final String ACCOUNT_ID = "accountId";
    private static final String PAGE = "page";
    private static final String DISPLAY_SIZE = "display_size";

    private final ChargeDao chargeDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final ChargeService chargeService;
    private final ConnectorConfiguration configuration;
    private final ChargeExpiryService chargeExpiryService;

    private static final int ONE_HOUR = 3600;
    private static final String CHARGE_EXPIRY_WINDOW = "CHARGE_EXPIRY_WINDOW_SECONDS";
    private static final ArrayList<ChargeStatus> NON_TERMINAL_STATUSES = Lists.newArrayList(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_SUCCESS);

    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);

    @Inject
    public ChargesApiResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao,
                              ChargeService chargeService, ChargeExpiryService chargeExpiryService,
                              ConnectorConfiguration configuration) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeService = chargeService;
        this.chargeExpiryService = chargeExpiryService;
        this.configuration = configuration;
    }

    @GET
    @Path(CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam(ACCOUNT_ID) Long accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(chargeId, accountId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getChargesJson(@PathParam(ACCOUNT_ID) Long accountId,
                                   @QueryParam(REFERENCE_KEY) String reference,
                                   @QueryParam(STATE_KEY) String state,
                                   @QueryParam(FROM_DATE_KEY) String fromDate,
                                   @QueryParam(TO_DATE_KEY) String toDate,
                                   @QueryParam(PAGE) Long pageNumber,
                                   @QueryParam(DISPLAY_SIZE) Long displaySize,
                                   @Context UriInfo uriInfo) {

        List<Pair<String, String>> inputDatePairMap = ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate));
        List<Pair<String, Long>> nonNegativePairMap = ImmutableList.of(Pair.of(PAGE, pageNumber), Pair.of(DISPLAY_SIZE, displaySize));

        return ApiValidators
                .validateQueryParams(inputDatePairMap, nonNegativePairMap) //TODO - improvement, get the entire searchparam object into the validateQueryParams
                .map(ResponseUtil::badRequestResponse)
                .orElseGet(() -> reduce(validateGatewayAccountReference(gatewayAccountDao, accountId)
                        .bimap(handleError,
                                listCharges(new ChargeSearchParams()
                                        .withGatewayAccountId(accountId)
                                        .withReferenceLike(reference)
                                        .withExternalChargeState(state)
                                        .withFromDate(parseDate(fromDate))
                                        .withToDate(parseDate(toDate))
                                        .withDisplaySize(displaySize != null ? displaySize : configuration.getTransactionsPaginationConfig().getDisplayPageSize())
                                        .withPage(pageNumber != null ? pageNumber : 1), uriInfo)))); // always the first page if its missing
    }

    @POST
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(@PathParam(ACCOUNT_ID) Long accountId, Map<String, Object> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(missingFields.get());
        }

        Optional<List<String>> invalidSizeFields = checkInvalidSizeFields(chargeRequest);
        if (invalidSizeFields.isPresent()) {
            return fieldsInvalidSizeResponse(invalidSizeFields.get());
        }

        return gatewayAccountDao.findById(accountId).map(
                gatewayAccountEntity -> {
                    logger.info("Creating new charge - {}", chargeRequest);
                    ChargeResponse response = chargeService.create(chargeRequest, gatewayAccountEntity, uriInfo);
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @POST
    @Path(CHARGES_EXPIRE_CHARGES_TASK_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response expireCharges(@Context UriInfo uriInfo) {
        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(getExpiryDate(), NON_TERMINAL_STATUSES);
        logger.info(format("Charges found for expiry - number_of_charges=%s, since_date=%s", charges.size(), getExpiryDate()));
        Map<String, Integer> resultMap = chargeExpiryService.expire(charges);
        return successResponseWithEntity(resultMap);
    }

    private ZonedDateTime getExpiryDate() {
        //default expiry window, can be overridden by env var
        int chargeExpiryWindowSeconds = ONE_HOUR;
        if (StringUtils.isNotBlank(System.getenv(CHARGE_EXPIRY_WINDOW))) {
            chargeExpiryWindowSeconds = Integer.parseInt(System.getenv(CHARGE_EXPIRY_WINDOW));
        }
        logger.debug("Charge expiry window size in seconds: " + chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
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
                ? Optional.empty()
                : Optional.of(missing);
    }

    private F<Boolean, Response> listCharges(ChargeSearchParams searchParams, UriInfo uriInfo) {
        Long totalCount = chargeDao.getTotalFor(searchParams);
        if (totalCount > 0) {
            double lastPage = Math.ceil(new Double(totalCount) / searchParams.getDisplaySize());
            if (searchParams.getPage() > lastPage || searchParams.getPage() < 1) {
                return success -> notFoundResponse("the requested page not found");
            }
        }

        List<ChargeEntity> charges = chargeDao.findAllBy(searchParams);
        List<ChargeResponse> chargesResponse =
                charges.stream()
                        .map(charge -> aChargeResponse()
                                .withChargeId(charge.getExternalId())
                                .withAmount(charge.getAmount())
                                .withReference(charge.getReference())
                                .withDescription(charge.getDescription())
                                .withState(ChargeStatus.fromString(charge.getStatus()).toExternal())
                                .withGatewayTransactionId(charge.getGatewayTransactionId())
                                .withCreatedDate(charge.getCreatedDate())
                                .withReturnUrl(charge.getReturnUrl())
                                .withProviderName(charge.getGatewayAccount().getGatewayName())
                                .build()
                        ).collect(Collectors.toList());

        return success ->
                new ChargesPaginationResponseBuilder(searchParams, uriInfo)
                        .withChargeResponses(chargesResponse)
                        .withTotalCount(totalCount)
                        .buildResponse();
    }

    private Optional<List<String>> checkInvalidSizeFields(Map<String, Object> inputData) {
        List<String> invalidSize = MAXIMUM_FIELDS_SIZE.entrySet().stream()
                .filter(entry -> !isFieldSizeValid(inputData, entry.getKey(), entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return invalidSize.isEmpty()
                ? Optional.empty()
                : Optional.of(invalidSize);
    }

    private boolean isFieldSizeValid(Map<String, Object> chargeRequest, String fieldName, int fieldSize) {
        String value = chargeRequest.get(fieldName).toString();
        return value.length() <= fieldSize;
    }

    private static F<String, Response> handleError =
            ResponseUtil::notFoundResponse;
}
