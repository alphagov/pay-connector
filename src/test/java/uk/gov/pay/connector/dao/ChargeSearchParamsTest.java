package uk.gov.pay.connector.dao;

import org.junit.Test;

import java.time.ZonedDateTime;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;

public class ChargeSearchParamsTest {

    public static final String FROM_DATE = "2012-06-30T12:30:40Z[GMT]";
    public static final String TO_DATE = "2012-07-30T12:30:40Z[GMT]";
    public static final String EXPECTED_QUERY_STRING = "&reference=ref" +
            "&from_date="+FROM_DATE+
            "&to_date="+TO_DATE+
            "&page=%s" +
            "&display_size=%s" +
            "&state=created";

    @Test
    public void shouldBuildQueryParamsForChargeSearch() throws Exception {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(5L)
                .withExternalChargeState(singletonList(EXTERNAL_CREATED))
                .withGatewayAccountId(111L)
                .withPage(2L)
                .withReferenceLike("ref")
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        assertEquals("query params string mismatch", format(EXPECTED_QUERY_STRING, 2, 5), params.buildQueryParams());
    }

    @Test
    public void shouldSetPageAToOneWhenValueLessThanZero() {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(500L)
                .withExternalChargeState(singletonList(EXTERNAL_CREATED))
                .withGatewayAccountId(111L)
                .withPage(-1L)
                .withReferenceLike("ref")
                .withFromDate(ZonedDateTime.parse(FROM_DATE))
                .withToDate(ZonedDateTime.parse(TO_DATE));

        assertEquals("query params string mismatch", format(EXPECTED_QUERY_STRING, 1, 500), params.buildQueryParams());

    }
}
