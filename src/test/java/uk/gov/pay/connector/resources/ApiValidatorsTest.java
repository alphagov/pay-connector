package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.resources.ApiValidators.validateChargePatchParams;

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

}