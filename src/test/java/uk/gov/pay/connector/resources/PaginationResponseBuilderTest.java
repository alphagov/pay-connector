package uk.gov.pay.connector.resources;

import com.jayway.jsonassert.JsonAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.SearchParams;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaginationResponseBuilderTest {

    @Mock
    private UriInfo mockUriInfo;

    @Test
    public void shouldBuildChargesPaginationWithExpectedHalResponseLinks() {

        // Only tests hal properties and _links,
        // pending work to convert charge responses in _embedded entities.

        // given
        SearchParams searchParams = new SearchParams()
                .withGatewayAccountId(1L)
                .withExternalState(ExternalChargeState.EXTERNAL_STARTED.getStatus())
                .withDisplaySize(100L)
                .withPage(2L);

        when(mockUriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"));

        when(mockUriInfo.getPath()).thenReturn("/v1/api/accounts/1/charges");

        // when
        Response response = new PaginationResponseBuilder(searchParams, mockUriInfo)
                .withResponses(newArrayList())
                .withTotalCount(500L)
                .buildResponse();

        // then
        JsonAssert.with((String) response.getEntity())
                .assertThat("$.total", is(500))
                .assertThat("$.count", is(0))
                .assertThat("$.page", is(2))
                .assertThat("$._links.next_page.href", is("http://app.com/v1/api/accounts/1/charges?page=3&display_size=100&state=started"))
                .assertThat("$._links.prev_page.href", is("http://app.com/v1/api/accounts/1/charges?page=1&display_size=100&state=started"))
                .assertThat("$._links.last_page.href", is("http://app.com/v1/api/accounts/1/charges?page=5&display_size=100&state=started"))
                .assertThat("$._links.first_page.href", is("http://app.com/v1/api/accounts/1/charges?page=1&display_size=100&state=started"))
                .assertThat("$._links.self.href", is("http://app.com/v1/api/accounts/1/charges?page=2&display_size=100&state=started"))
                .assertThat("$.results.*", hasSize(0));
    }
}
