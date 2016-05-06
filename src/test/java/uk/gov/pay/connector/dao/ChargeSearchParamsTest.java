package uk.gov.pay.connector.dao;

import org.junit.Test;

import java.time.ZonedDateTime;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;

public class ChargeSearchParamsTest {

    @Test
    public void shouldBuildQueryParamsForChargeSearch() throws Exception {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(5L)
                .withExternalChargeState(singletonList(EXTERNAL_CREATED))
                .withGatewayAccountId(111L)
                .withPage(2L)
                .withReferenceLike("ref")
                .withFromDate(ZonedDateTime.parse("2012-06-30T12:30:40Z[GMT]"))
                .withToDate(ZonedDateTime.parse("2012-07-30T12:30:40Z[GMT]"));

        String expectedQueryString =
                "&reference=ref" +
                "&from_date=2012-06-30T12:30:40Z[GMT]" +
                "&to_date=2012-07-30T12:30:40Z[GMT]" +
                "&page=2" +
                "&display_size=5" +
                "&state=created";

        assertEquals("query params string mismatch", expectedQueryString, params.buildQueryParams());
    }

    @Test
    public void shouldSetPageAToOneWhenValueLessThanZero() {
        ChargeSearchParams params = new ChargeSearchParams()
                .withDisplaySize(500L)
                .withExternalChargeState(singletonList(EXTERNAL_CREATED))
                .withGatewayAccountId(111L)
                .withPage(-1L)
                .withReferenceLike("ref")
                .withFromDate(ZonedDateTime.parse("2012-06-30T12:30:40Z[GMT]"))
                .withToDate(ZonedDateTime.parse("2012-07-30T12:30:40Z[GMT]"));

        String expectedQueryString =
                "&reference=ref" +
                "&from_date=2012-06-30T12:30:40Z[GMT]" +
                "&to_date=2012-07-30T12:30:40Z[GMT]" +
                "&page=1" +
                "&display_size=500" +
                "&state=created";

        assertEquals("query params string mismatch", expectedQueryString, params.buildQueryParams());

    }
}