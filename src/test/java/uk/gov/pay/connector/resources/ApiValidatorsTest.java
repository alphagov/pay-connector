package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;

import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.resources.ApiValidators.validateChargePatchParams;
import static uk.gov.pay.connector.resources.ApiValidators.validateFromDateIsBeforeToDate;

public class ApiValidatorsTest {

    @Test
    public void shouldValidateEmailLength_WhenPatchingAnEmail() throws Exception {

        PatchRequestBuilder.PatchRequest request = PatchRequestBuilder.aPatchRequestBuilder(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "email",
                        "value","test@examplecom"))
                .withValidOps(singletonList("replace"))
                .withValidPaths(singletonList("email"))
                .build();
        assertThat(validateChargePatchParams(request), is(true));
    }

    @Test
    public void shouldInvalidateEmailLength_WhenPatchingAnEmail() throws Exception {

        PatchRequestBuilder.PatchRequest request = PatchRequestBuilder.aPatchRequestBuilder(
                ImmutableMap.of(
                        "op", "replace",
                        "path", "email",
                        "value",randomAlphanumeric(255) +"@examplecom"))
                .withValidOps(singletonList("replace"))
                .withValidPaths(singletonList("email"))
                .build();
        assertThat(validateChargePatchParams(request), is(false));
    }

    @Test
    public void validateFromDateIsBeforeToDate_fromDateBeforeToDate() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result = validateFromDateIsBeforeToDate("from_date",  "2017-11-23T12:00:00Z",
                "to_date", "2017-11-23T15:00:00Z");

        assertThat(result.isRight(), is(true));
        assertThat(result.right().value(), is(Pair.of(ZonedDateTime.parse("2017-11-23T12:00:00Z"), ZonedDateTime.parse("2017-11-23T15:00:00Z"))));
    }

    @Test
    public void validateFromDateIsBeforeToDate_fromDateAfterToDate() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result =  validateFromDateIsBeforeToDate("from_date", "2017-11-23T15:00:00Z",
                "to_date", "2017-11-23T12:00:00Z");

        assertThat(result.isLeft(), is(true));
        assertThat(result.left().value().get(0), is("query param 'to_date' must be later than 'from_date'"));
    }

    @Test
    public void validateFromDateIsBeforeToDate_fromDateIsMissing() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result = validateFromDateIsBeforeToDate("from_date", null,
                "to_date", "2017-11-23T15:00:00Z");

        assertThat(result.isLeft(), is(true));
        assertThat(result.left().value().get(0), is("query param 'from_date' not in correct format"));
    }

    @Test
    public void validateFromDateIsBeforeToDate_toDateIsMissing() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result = validateFromDateIsBeforeToDate("from_date", "2017-11-23T12:00:00Z",
                "to_date", null);

        assertThat(result.isLeft(), is(true));
        assertThat(result.left().value().get(0), is("query param 'to_date' not in correct format"));
    }

    @Test
    public void validateFromDateIsBeforeToDate_fromDateIsInIncorrectFormat() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result = validateFromDateIsBeforeToDate("from_date", "Thu, 23 Nov 2017 12:00:00 GMT",
                "to_date", "2017-11-23T15:00:00Z");

        assertThat(result.isLeft(), is(true));
        assertThat(result.left().value().get(0), is("query param 'from_date' not in correct format"));
    }

    @Test
    public void validateFromDateIsBeforeToDate_toDateIsInIncorrectFormat() {
        Either<List<String>, Pair<ZonedDateTime, ZonedDateTime>> result = validateFromDateIsBeforeToDate("from_date", "2017-11-23T12:00:00Z",
                "to_date", "2017/11/23 03:00:00 PM");

        assertThat(result.isLeft(), is(true));
        assertThat(result.left().value().get(0), is("query param 'to_date' not in correct format"));
    }

}
