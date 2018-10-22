package uk.gov.pay.connector.dao;

import org.junit.Test;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.charge.model.CardHolderName;
import uk.gov.pay.connector.charge.model.DisplaySize;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.FromDate;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PageNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.ToDate;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.TransactionType.PAYMENT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;

public class SearchParamsTest {

    @Test
    public void buildQueryParams_chargeSearch_withAllParameters() {

        String expectedQueryString =
                "reference=ref" +
                        "&email=user" +
                        "&from_date=2012-06-30T12:30:40Z[UTC]" +
                        "&to_date=2012-07-30T12:30:40Z[UTC]" +
                        "&page=2" +
                        "&display_size=5" +
                        "&state=created" +
                        "&first_digits_card_number=123456" +
                        "&last_digits_card_number=1234" +
                        "&cardholder_name=bla" +
                        "&card_brand=visa";
          

        SearchParams params = new SearchParams()
                .withDisplaySize(DisplaySize.of(5L))
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withCardBrand("visa")
                .withGatewayAccountId(111L)
                .withPage(PageNumber.of(2L))
                .withLastDigitsCardNumber(LastDigitsCardNumber.of("1234"))
                .withFirstDigitsCardNumber(FirstDigitsCardNumber.of("123456"))
                .withReferenceLike(ServicePaymentReference.of("ref"))
                .withEmailLike("user")
                .withCardHolderNameLike(CardHolderName.of("bla"))
                .withFromDate(FromDate.of("2012-06-30T12:30:40Z[UTC]"))
                .withToDate(ToDate.of("2012-07-30T12:30:40Z[UTC]"));

        assertThat(params.buildQueryParams(), is(expectedQueryString));
        assertThat(params.getGatewayAccountId(), is(111L));
        assertThat(params.getDisplaySize().getRawValue(), is(5L));
        assertThat(params.getPage().getRawValue(), is(2L));
    }

    @Test
    public void buildQueryParamsWithPiiRedaction_chargeSearch_withAllParameters()  {

        String expectedQueryString =
                "reference=ref" +
                        "&email=*****" +
                        "&from_date=2012-06-30T12:30:40Z[UTC]" +
                        "&to_date=2012-07-30T12:30:40Z[UTC]" +
                        "&page=2" +
                        "&display_size=5" +
                        "&state=created" +
                        "&card_brand=visa";

        SearchParams params = new SearchParams()
                .withDisplaySize(DisplaySize.of(5L))
                .withExternalState(EXTERNAL_CREATED.getStatus())
                .withCardBrand("visa")
                .withGatewayAccountId(111L)
                .withPage(PageNumber.of(2L))
                .withReferenceLike(ServicePaymentReference.of("ref"))
                .withEmailLike("user")
                .withFromDate(FromDate.of("2012-06-30T12:30:40Z[UTC]"))
                .withToDate(ToDate.of("2012-07-30T12:30:40Z[UTC]"));

        assertThat(params.buildQueryParamsWithPiiRedaction(), is(expectedQueryString));
    }


    @Test
    public void getInternalStates_chargeSearch_shouldPopulateAllInternalChargeStates_FromExternalFailedState() {

        SearchParams params = new SearchParams()
                .withExternalState(ExternalChargeState.EXTERNAL_FAILED_CANCELLED.getStatus());

        assertThat(params.buildQueryParams(), is("state=failed"));
        assertThat(params.getInternalStates(),  hasSize(11));
        assertThat(params.getInternalStates(), containsInAnyOrder(
                USER_CANCEL_READY, USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, USER_CANCELLED, EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, EXPIRED, EXPIRE_CANCEL_FAILED,
                AUTHORISATION_REJECTED, AUTHORISATION_CANCELLED, AUTHORISATION_ABORTED));
    }

    @Test
    public void getInternalStates_shouldSetInternalStatesDirectlyToSearchParams() {

        // So internal methods can call findAll with specific states of a charge
        SearchParams params = new SearchParams()
                .withInternalStates(asList(CAPTURED, USER_CANCELLED));

        assertThat(params.getInternalStates(), hasSize(2));
        assertThat(params.getInternalStates(), containsInAnyOrder(CAPTURED, USER_CANCELLED));
    }

    @Test
    public void buildQueryParams_transactionsSearch_withAllParameters()  {

        String expectedQueryString =
                "transaction_type=payment" +
                        "&reference=ref" +
                        "&email=user@example.com" +
                        "&from_date=2012-06-30T12:30:40Z[UTC]" +
                        "&to_date=2012-07-30T12:30:40Z[UTC]" +
                        "&page=2" +
                        "&display_size=5" +
                        "&payment_states=created" +
                        "&refund_states=submitted" +
                        "&first_digits_card_number=695943" +
                        "&last_digits_card_number=6749" +
                        "&cardholder_name=abc" +
                        "&card_brand=visa" +
                        "&card_brand=master-card";

        SearchParams params = new SearchParams()
                .withDisplaySize(DisplaySize.of(5L))
                .withTransactionType(PAYMENT)
                .withFirstDigitsCardNumber(FirstDigitsCardNumber.of("695943"))
                .withLastDigitsCardNumber(LastDigitsCardNumber.of("6749"))
                .withCardHolderNameLike(CardHolderName.of("abc"))
                .addExternalChargeStates(singletonList("created"))
                .addExternalRefundStates(singletonList("submitted"))
                .withCardBrands(asList("visa", "master-card"))
                .withGatewayAccountId(111L)
                .withPage(PageNumber.of(2L))
                .withReferenceLike(ServicePaymentReference.of("ref"))
                .withEmailLike("user@example.com")
                .withFromDate(FromDate.of("2012-06-30T12:30:40Z[UTC]"))
                .withToDate(ToDate.of("2012-07-30T12:30:40Z[UTC]"));

        assertThat(params.buildQueryParams(), is(expectedQueryString));
    }

    @Test
    public void buildQueryParams_transactionsSearch_withNonTransactionType_andStateForChargeAndRefund()  {

        String expectedQueryString =
                "reference=ref" +
                        "&email=user@example.com" +
                        "&page=2" +
                        "&payment_states=success" +
                        "&refund_states=success";

        SearchParams params = new SearchParams()
                .addExternalChargeStates(singletonList("success"))
                .addExternalRefundStates(singletonList("success"))
                .withGatewayAccountId(111L)
                .withPage(PageNumber.of(2L))
                .withReferenceLike(ServicePaymentReference.of("ref"))
                .withEmailLike("user@example.com");

        assertThat(params.buildQueryParams(), is(expectedQueryString));
    }

    @Test
    public void getInternalChargeStatuses_transactionsSearch_MultipleChargeStates() {

        String expectedQueryString = "payment_states=success,failed";

        SearchParams params = new SearchParams()
                .addExternalChargeStates(singletonList("success"))
                .addExternalChargeStates(singletonList("failed"))
                .withGatewayAccountId(111L);

        assertThat(params.buildQueryParams(), is(expectedQueryString));
        assertThat(params.getInternalChargeStatuses(),  hasSize(16));
        assertThat(params.getInternalChargeStatuses(), containsInAnyOrder(
                USER_CANCEL_READY, USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, USER_CANCELLED, EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, EXPIRED, EXPIRE_CANCEL_FAILED,
                AUTHORISATION_REJECTED, AUTHORISATION_CANCELLED, AUTHORISATION_ABORTED, CAPTURE_APPROVED, CAPTURE_APPROVED_RETRY, CAPTURE_READY, CAPTURED, CAPTURE_SUBMITTED));

    }

    @Test
    public void getInternalChargeStatuses_transactionsSearch_MultipleChargeStatesV2() {

        String expectedQueryString = "payment_states=timedout,declined,cancelled";

        SearchParams params = new SearchParams()
                .addExternalChargeStatesV2(singletonList("declined"))
                .addExternalChargeStatesV2(singletonList("timedout"))
                .addExternalChargeStatesV2(singletonList("cancelled"))
                .withGatewayAccountId(111L);

        assertThat(params.buildQueryParams(), is(expectedQueryString));
        assertThat(params.getInternalChargeStatuses(),  hasSize(15));
        assertThat(params.getInternalChargeStatuses(), containsInAnyOrder(
                USER_CANCEL_READY, USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, USER_CANCELLED, EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, EXPIRED, EXPIRE_CANCEL_FAILED,
                AUTHORISATION_REJECTED, AUTHORISATION_CANCELLED, AUTHORISATION_ABORTED, SYSTEM_CANCEL_ERROR, SYSTEM_CANCEL_READY, SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED
        ));

    }

    @Test
    public void getInternalChargeStatuses_transactionsSearch_shouldPopulateAllInternalChargeStates_FromExternalFailedState() {

        SearchParams params = new SearchParams()
                .addExternalChargeStates(singletonList(ExternalChargeState.EXTERNAL_FAILED_CANCELLED.getStatus()));

        assertThat(params.buildQueryParams(), is("payment_states=failed"));
        assertThat(params.getInternalChargeStatuses(),  hasSize(11));
        assertThat(params.getInternalChargeStatuses(), containsInAnyOrder(
                USER_CANCEL_READY, USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, USER_CANCELLED, EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, EXPIRED, EXPIRE_CANCEL_FAILED,
                AUTHORISATION_REJECTED, AUTHORISATION_CANCELLED, AUTHORISATION_ABORTED));
    }
}
