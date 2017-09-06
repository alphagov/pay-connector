package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import uk.gov.pay.connector.service.ChargeExpiryService;
import uk.gov.pay.connector.service.ChargeService;
import uk.gov.pay.connector.service.search.SearchService;
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
import static java.util.Arrays.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.model.TransactionType.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.resources.ApiValidators.validateGatewayAccountReference;
import static uk.gov.pay.connector.service.ChargeExpiryService.EXPIRABLE_STATUSES;
import static uk.gov.pay.connector.service.search.SearchService.TYPE.CHARGE;
import static uk.gov.pay.connector.service.search.SearchService.TYPE.TRANSACTION;
import static uk.gov.pay.connector.util.ResponseUtil.*;

@Path("/")
public class ChargesApiResource {
    static final String AMOUNT_KEY = "amount";
    private static final String DESCRIPTION_KEY = "description";
    private static final String RETURN_URL_KEY = "return_url";
    private static final String REFERENCE_KEY = "reference";
    public static final String EMAIL_KEY = "email";
    private static final String[] REQUIRED_FIELDS = {AMOUNT_KEY, DESCRIPTION_KEY, REFERENCE_KEY, RETURN_URL_KEY};
    static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = ImmutableMap.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255,
            EMAIL_KEY, 254
    );

    static int MIN_AMOUNT = 1;
    static int MAX_AMOUNT = 10000000;

    private static final String STATE_KEY = "state";
    private static final String CARD_BRAND_KEY = "card_brand";
    private static final String FROM_DATE_KEY = "from_date";
    private static final String TO_DATE_KEY = "to_date";
    private static final String ACCOUNT_ID = "accountId";
    private static final String PAGE = "page";
    private static final String DISPLAY_SIZE = "display_size";

    private static final Set<String> CHARGE_REQUEST_KEYS_THAT_MAY_HAVE_PII = Collections.singleton("description");

    private final ChargeDao chargeDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final ChargeService chargeService;
    private final ConnectorConfiguration configuration;
    private SearchService searchService;
    private final ChargeExpiryService chargeExpiryService;

    private static final int ONE_HOUR = 3600;
    private static final String CHARGE_EXPIRY_WINDOW = "CHARGE_EXPIRY_WINDOW_SECONDS";

    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);

    @Inject
    public ChargesApiResource(ChargeDao chargeDao, GatewayAccountDao gatewayAccountDao,
                              ChargeService chargeService, SearchService searchService,
                              ChargeExpiryService chargeExpiryService, ConnectorConfiguration configuration) {
        this.chargeDao = chargeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeService = chargeService;
        this.searchService = searchService;
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
                                   @QueryParam(EMAIL_KEY) String email,
                                   @QueryParam(REFERENCE_KEY) String reference,
                                   @QueryParam(STATE_KEY) String state,
                                   @QueryParam("transaction_type") String transactionType,
                                   @QueryParam("payment_states") String paymentStates,
                                   @QueryParam("refund_states") String refundStates,
                                   @QueryParam(CARD_BRAND_KEY) String cardBrand,
                                   @QueryParam(FROM_DATE_KEY) String fromDate,
                                   @QueryParam(TO_DATE_KEY) String toDate,
                                   @QueryParam(PAGE) Long pageNumber,
                                   @QueryParam(DISPLAY_SIZE) Long displaySize,
                                   @HeaderParam("features") String features,
                                   @Context UriInfo uriInfo) {

        List<Pair<String, String>> inputDatePairMap = ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate));
        List<Pair<String, Long>> nonNegativePairMap = ImmutableList.of(Pair.of(PAGE, pageNumber), Pair.of(DISPLAY_SIZE, displaySize));

        boolean isFeatureTransactionsEnabled = isFeatureTransactionsWithRefundsEnabled(features);


        return ApiValidators
                .validateQueryParams(inputDatePairMap, nonNegativePairMap) //TODO - improvement, get the entire searchparam object into the validateQueryParams
                .map(ResponseUtil::badRequestResponse)
                .orElseGet(() -> {
                    ChargeSearchParams searchParams = new ChargeSearchParams()
                            .withGatewayAccountId(accountId)
                            .withEmailLike(email)
                            .withReferenceLike(reference)
                            .withCardBrand(cardBrand)
                            .withFromDate(parseDate(fromDate))
                            .withToDate(parseDate(toDate))
                            .withDisplaySize(displaySize != null ? displaySize : configuration.getTransactionsPaginationConfig().getDisplayPageSize())
                            .withPage(pageNumber != null ? pageNumber : 1);

                    if (isFeatureTransactionsEnabled) {
                        searchParams
                                .withTransactionType(valueFrom(transactionType))
                                .addExternalChargeStates(paymentStates)
                                .addExternalRefundStates(refundStates);
                    } else {
                        searchParams
                                .withExternalState(state);
                    }
                    return reduce(validateGatewayAccountReference(gatewayAccountDao, accountId)
                            .bimap(handleError, listCharges(searchParams, isFeatureTransactionsEnabled, uriInfo)));
                }); // always the first page if its missing
    }

    private boolean isFeatureTransactionsWithRefundsEnabled(String features) {
        return isNotBlank(features) && stream(features.split(",")).anyMatch("REFUNDS_IN_TX_LIST"::equals);
    }

    @POST
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response createNewCharge(@PathParam(ACCOUNT_ID) Long accountId, Map<String, String> chargeRequest, @Context UriInfo uriInfo) {
        Optional<List<String>> missingFields = checkMissingFields(chargeRequest);
        if (missingFields.isPresent()) {
            return fieldsMissingResponse(missingFields.get());
        }

        Optional<List<String>> invalidSizeFields = checkInvalidSizeFields(chargeRequest);
        if (invalidSizeFields.isPresent()) {
            return fieldsInvalidSizeResponse(invalidSizeFields.get());
        }

        Optional<List<String>> invalidFields = ApiValidators.validateChargeParams(chargeRequest);
        if (invalidFields.isPresent()) {
            return fieldsInvalidResponse(invalidFields.get());
        }

        return gatewayAccountDao.findById(accountId).map(
                gatewayAccountEntity -> {
                    logger.info("Creating new charge - {}", stringifyChargeRequestWithoutPii(chargeRequest));
                    ChargeResponse response = chargeService.create(chargeRequest, gatewayAccountEntity, uriInfo);
                    return created(response.getLink("self")).entity(response).build();
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @POST
    @Path(CHARGES_EXPIRE_CHARGES_TASK_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response expireCharges(@Context UriInfo uriInfo) {
        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(getExpiryDate(), EXPIRABLE_STATUSES);
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

    private Optional<List<String>> checkMissingFields(Map<String, String> inputData) {
        List<String> missing = stream(REQUIRED_FIELDS)
                .filter(field -> !inputData.containsKey(field))
                .collect(Collectors.toList());

        return missing.isEmpty()
                ? Optional.empty()
                : Optional.of(missing);
    }

    private F<Boolean, Response> listCharges(ChargeSearchParams searchParams, boolean isFeatureTransactionsEnabled, UriInfo uriInfo) {
        if (isFeatureTransactionsEnabled) {
            return success -> searchService.ofType(TRANSACTION).search(searchParams, uriInfo);
        }
        return success -> searchService.ofType(CHARGE).search(searchParams, uriInfo);
    }

    private Optional<List<String>> checkInvalidSizeFields(Map<String, String> inputData) {
        List<String> invalidSize = MAXIMUM_FIELDS_SIZE.entrySet().stream()
                .filter(entry -> !isFieldSizeValid(inputData, entry.getKey(), entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return invalidSize.isEmpty()
                ? Optional.empty()
                : Optional.of(invalidSize);
    }

    private boolean isFieldSizeValid(Map<String, String> chargeRequest, String fieldName, int fieldSize) {
        Optional<String> value = Optional.ofNullable(chargeRequest.get(fieldName));
        return !value.isPresent() || value.get().length() <= fieldSize; //already checked that mandatory fields are already there
    }

    private static String stringifyChargeRequestWithoutPii(Map<String, String> map) {
        return map.entrySet().stream()
                .filter(entry -> !CHARGE_REQUEST_KEYS_THAT_MAY_HAVE_PII.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()))
                .toString();
    }

    private static F<String, Response> handleError =
            ResponseUtil::notFoundResponse;
}
