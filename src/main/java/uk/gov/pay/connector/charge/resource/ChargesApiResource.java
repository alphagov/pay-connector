package uk.gov.pay.connector.charge.resource;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.CardHolderName;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.SearchService;
import uk.gov.pay.connector.common.model.CommaDelimitedSetParameter;
import uk.gov.pay.connector.common.validator.ApiValidators;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.TransactionSearchStrategyTransactionType.inferTransactionTypeFrom;
import static uk.gov.pay.connector.charge.service.SearchService.TYPE.CHARGE;
import static uk.gov.pay.connector.charge.service.SearchService.TYPE.TRANSACTION;
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
    private static final String CARDHOLDER_NAME_KEY = "cardholder_name";
    private static final String LAST_DIGITS_CARD_NUMBER_KEY = "last_digits_card_number";
    private static final String FIRST_DIGITS_CARD_NUMBER_KEY = "first_digits_card_number";
    public static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = ImmutableMap.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255,
            EMAIL_KEY, 254
    );
    private static final String STATE_KEY = "state";
    private static final String PAYMENT_STATES_KEY = "payment_states";
    private static final String REFUND_STATES_KEY = "refund_states";
    private static final String CARD_BRAND_KEY = "card_brand";
    private static final String FROM_DATE_KEY = "from_date";
    private static final String TO_DATE_KEY = "to_date";
    private static final String ACCOUNT_ID = "accountId";
    private static final String PAGE = "page";
    private static final String DISPLAY_SIZE = "display_size";
    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 10_000_000;
    private final GatewayAccountDao gatewayAccountDao;
    private final ChargeService chargeService;
    private final ConnectorConfiguration configuration;
    private final ChargeExpiryService chargeExpiryService;
    private SearchService searchService;

    @Inject
    public ChargesApiResource(GatewayAccountDao gatewayAccountDao,
                              ChargeService chargeService, SearchService searchService,
                              ChargeExpiryService chargeExpiryService, ConnectorConfiguration configuration) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.chargeService = chargeService;
        this.searchService = searchService;
        this.chargeExpiryService = chargeExpiryService;
        this.configuration = configuration;
    }


    @GET
    @Path("/v1/api/accounts/{accountId}/charges/{chargeId}")
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam(ACCOUNT_ID) Long accountId, @PathParam("chargeId") String chargeId, @Context UriInfo uriInfo) {
        return chargeService.findChargeForAccount(chargeId, accountId, uriInfo)
                .map(chargeResponse -> Response.ok(chargeResponse).build())
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/charges")
    @Timed
    @Produces(APPLICATION_JSON)
    public Response getChargesJson(@PathParam(ACCOUNT_ID) Long accountId,
                                   @QueryParam(EMAIL_KEY) String email,
                                   @QueryParam(REFERENCE_KEY) String reference,
                                   @QueryParam(CARDHOLDER_NAME_KEY) String cardHolderName,
                                   @QueryParam(LAST_DIGITS_CARD_NUMBER_KEY) String lastDigitsCardNumber,
                                   @QueryParam(FIRST_DIGITS_CARD_NUMBER_KEY) String firstDigitsCardNumber,
                                   @QueryParam(STATE_KEY) String state,
                                   @QueryParam(PAYMENT_STATES_KEY) CommaDelimitedSetParameter paymentStates,
                                   @QueryParam(REFUND_STATES_KEY) CommaDelimitedSetParameter refundStates,
                                   @QueryParam(CARD_BRAND_KEY) List<String> cardBrands,
                                   @QueryParam(FROM_DATE_KEY) String fromDate,
                                   @QueryParam(TO_DATE_KEY) String toDate,
                                   @QueryParam(PAGE) Long pageNumber,
                                   @QueryParam(DISPLAY_SIZE) Long displaySize,
                                   @HeaderParam("features") CommaDelimitedSetParameter features,
                                   @Context UriInfo uriInfo) {

        List<Pair<String, String>> inputDatePairMap = ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate));
        List<Pair<String, Long>> nonNegativePairMap = ImmutableList.of(Pair.of(PAGE, pageNumber), Pair.of(DISPLAY_SIZE, displaySize));

        boolean isFeatureTransactionsEnabled = features != null && features.has("REFUNDS_IN_TX_LIST");

        return ApiValidators
                .validateQueryParams(inputDatePairMap, nonNegativePairMap) //TODO - improvement, get the entire searchparam object into the validateQueryParams
                .map(ResponseUtil::badRequestResponse)
                .orElseGet(() -> {
                    SearchParams searchParams = new SearchParams()
                            .withGatewayAccountId(accountId)
                            .withEmailLike(email)
                            .withCardHolderNameLike(cardHolderName != null ? CardHolderName.of(cardHolderName) : null)
                            .withLastDigitsCardNumber(LastDigitsCardNumber.ofNullable(lastDigitsCardNumber))
                            .withFirstDigitsCardNumber(FirstDigitsCardNumber.ofNullable(firstDigitsCardNumber))
                            .withReferenceLike(reference != null ? ServicePaymentReference.of(reference) : null)
                            .withCardBrands(removeBlanks(cardBrands))
                            .withFromDate(parseDate(fromDate))
                            .withToDate(parseDate(toDate))
                            .withDisplaySize(displaySize != null ? displaySize : configuration.getTransactionsPaginationConfig().getDisplayPageSize())
                            .withPage(pageNumber != null ? pageNumber : 1);

                    if (isFeatureTransactionsEnabled) {
                        searchParams
                                .withTransactionType(inferTransactionTypeFrom(toList(paymentStates), toList(refundStates)))
                                .addExternalChargeStates(toList(paymentStates))
                                .addExternalRefundStates(toList(refundStates));
                    } else {
                        searchParams.withExternalState(state);
                    }
                    return gatewayAccountDao.findById(accountId)
                            .map(gatewayAccount -> listCharges(searchParams, isFeatureTransactionsEnabled, uriInfo))
                            .orElseGet(() -> notFoundResponse(format("account with id %s not found", accountId)));
                }); // always the first page if its missing
    }

    @GET
    @Path("/v2/api/accounts/{accountId}/charges")
    @Timed
    @Produces(APPLICATION_JSON)
    public Response getChargesJsonV2(@PathParam(ACCOUNT_ID) Long accountId,
                                     @QueryParam(EMAIL_KEY) String email,
                                     @QueryParam(REFERENCE_KEY) String reference,
                                     @QueryParam(CARDHOLDER_NAME_KEY) String cardHolderName,
                                     @QueryParam(LAST_DIGITS_CARD_NUMBER_KEY) String lastDigitsCardNumber,
                                     @QueryParam(FIRST_DIGITS_CARD_NUMBER_KEY) String firstDigitsCardNumber,
                                     @QueryParam(PAYMENT_STATES_KEY) CommaDelimitedSetParameter paymentStates,
                                     @QueryParam(REFUND_STATES_KEY) CommaDelimitedSetParameter refundStates,
                                     @QueryParam(CARD_BRAND_KEY) List<String> cardBrands,
                                     @QueryParam(FROM_DATE_KEY) String fromDate,
                                     @QueryParam(TO_DATE_KEY) String toDate,
                                     @QueryParam(PAGE) Long pageNumber,
                                     @QueryParam(DISPLAY_SIZE) Long displaySize,
                                     @Context UriInfo uriInfo) {

        List<Pair<String, String>> inputDatePairMap = ImmutableList.of(Pair.of(FROM_DATE_KEY, fromDate), Pair.of(TO_DATE_KEY, toDate));
        List<Pair<String, Long>> nonNegativePairMap = ImmutableList.of(Pair.of(PAGE, pageNumber), Pair.of(DISPLAY_SIZE, displaySize));
        //Client using v2 API will have the feature flag enabled by default
        boolean isFeatureTransactionsEnabled = true;

        return ApiValidators
                .validateQueryParams(inputDatePairMap, nonNegativePairMap) //TODO - improvement, get the entire searchparam object into the validateQueryParams
                .map(ResponseUtil::badRequestResponse)
                .orElseGet(() -> {
                    SearchParams searchParams = new SearchParams()
                            .withGatewayAccountId(accountId)
                            .withEmailLike(email)
                            .withCardHolderNameLike(cardHolderName != null ? CardHolderName.of(cardHolderName) : null)
                            .withLastDigitsCardNumber(LastDigitsCardNumber.ofNullable(lastDigitsCardNumber))
                            .withFirstDigitsCardNumber(FirstDigitsCardNumber.ofNullable(firstDigitsCardNumber))
                            .withReferenceLike(reference != null ? ServicePaymentReference.of(reference) : null)
                            .withCardBrands(removeBlanks(cardBrands))
                            .withFromDate(parseDate(fromDate))
                            .withToDate(parseDate(toDate))
                            .withDisplaySize(displaySize != null ? displaySize : configuration.getTransactionsPaginationConfig().getDisplayPageSize())
                            .withPage(pageNumber != null ? pageNumber : 1)
                            .withTransactionType(inferTransactionTypeFrom(toList(paymentStates), toList(refundStates)))
                            .addExternalChargeStatesV2(toList(paymentStates))
                            .addExternalRefundStates(toList(refundStates));

                    return gatewayAccountDao.findById(accountId)
                            .map(gatewayAccount -> listCharges(searchParams, isFeatureTransactionsEnabled, uriInfo))
                            .orElseGet(() -> notFoundResponse(format("account with id %s not found", accountId)));
                }); // always the first page if its missing
    }

    private List<String> toList(CommaDelimitedSetParameter commaDelimitedSetParameter) {
        if (commaDelimitedSetParameter != null) {
            return commaDelimitedSetParameter.stream().collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> removeBlanks(List<String> cardBrands) {
        return cardBrands.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
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
                .map(response -> created(response.getLink("self")).entity(response).build())
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
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

    private ZonedDateTime parseDate(String date) {
        ZonedDateTime parse = null;
        if (isNotBlank(date)) {
            parse = ZonedDateTime.parse(date);
        }
        return parse;
    }

    private Response listCharges(SearchParams searchParams, boolean isFeatureTransactionsEnabled, UriInfo uriInfo) {
        long startTime = System.nanoTime();
        try {
            if (isFeatureTransactionsEnabled) {
                return searchService.ofType(TRANSACTION).search(searchParams, uriInfo);
            }
            return searchService.ofType(CHARGE).search(searchParams, uriInfo);
        } finally {
            long endTime = System.nanoTime();
            logger.info("Charge Search - is feature transactions enabled [{}] took [{}] params [{}]",
                    isFeatureTransactionsEnabled,
                    (endTime - startTime) / 1000000000.0,
                    searchParams.buildQueryParamsWithPiiRedaction());
        }
    }
}
