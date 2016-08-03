package uk.gov.pay.connector.resources;

import com.jayway.jsonassert.JsonAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChargesPaginationResponseBuilderTest {

    @Mock
    private UriInfo mockUriInfo;

    @Test
    public void shouldBuildChargesPaginationWithExpectedHalResponseLinks() throws Exception {

        // Only tests hal properties and _links,
        // pending work to convert charge responses in _embedded entities.

        // given
        ChargeSearchParams searchParams = new ChargeSearchParams()
                .withGatewayAccountId(1L)
                .withInternalChargeStatuses(newArrayList(ChargeStatus.CAPTURED, ChargeStatus.AUTHORISATION_ERROR))
                .withDisplaySize(100L)
                .withPage(2L);

        when(mockUriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"));

        // when
        Response response = new ChargesPaginationResponseBuilder(searchParams, mockUriInfo)
                .withChargeResponses(newArrayList())
                .withTotalCount(500L)
                .buildResponse();

        // then
        Object entity = response.getEntity();
        System.out.println("entity = " + entity);
        JsonAssert.with((String) entity)
                .assertThat("$.total", is(500))
                .assertThat("$.count", is(0))
                .assertThat("$.page", is(2))
                .assertThat("$._links.next_page.href", is("http://app.com/v1/api/accounts/1/charges?page=3&display_size=100"))
                .assertThat("$._links.prev_page.href", is("http://app.com/v1/api/accounts/1/charges?page=1&display_size=100"))
                .assertThat("$._links.last_page.href", is("http://app.com/v1/api/accounts/1/charges?page=5&display_size=100"))
                .assertThat("$._links.first_page.href", is("http://app.com/v1/api/accounts/1/charges?page=1&display_size=100"))
                .assertThat("$._links.self.href", is("http://app.com/v1/api/accounts/1/charges?page=2&display_size=100"))
                .assertThat("$.results.*", hasSize(0));
    }
}
