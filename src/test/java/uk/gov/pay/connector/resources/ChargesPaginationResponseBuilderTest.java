package uk.gov.pay.connector.resources;

import com.jayway.jsonassert.JsonAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.SearchRefundsResponse;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.SearchRefundsResponse.anAllRefundsResponseBuilder;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(MockitoJUnitRunner.class)
public class ChargesPaginationResponseBuilderTest {

    @Mock
    private UriInfo mockUriInfo;

    @Test
    public void shouldBuildChargesPaginationWithExpectedHalResponseLinks(){

        // Only tests hal properties and _links,
        // pending work to convert charge responses in _embedded entities.

        // given
        ChargeSearchParams searchParams = new ChargeSearchParams()
                .withGatewayAccountId(1L)
                .withExternalState(ExternalChargeState.EXTERNAL_STARTED.getStatus())
                .withDisplaySize(100L)
                .withPage(2L);

        when(mockUriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"),
                UriBuilder.fromUri("http://app.com"), UriBuilder.fromUri("http://app.com"));

        when(mockUriInfo.getPath()).thenReturn("/v1/api/accounts/1/charges");

        // when
        Response response = new ChargesPaginationResponseBuilder(searchParams, mockUriInfo)
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


    @Test
    public void shouldBuildRefundsPaginationWithExpectedHalResponseLinks() {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity( "sandbox", new HashMap<>(), TEST);
        Long accountId = gatewayAccount.getId();

        RefundEntity refund = aValidRefundEntity()
                .withExternalId("someExternalId")
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(RefundStatus.REFUNDED)
                .withAmount(500L)
                .build();


        ChargeSearchParams searchParams = new ChargeSearchParams()
                .withGatewayAccountId(accountId)
                .withExternalState(ExternalChargeState.EXTERNAL_SUCCESS.getStatus())
                .withDisplaySize(100L)
                .withPage(2L);

        when(mockUriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri(""));

        when(mockUriInfo.getPath()).thenReturn("");

        List<SearchRefundsResponse> refundResponses = Arrays.asList(
                anAllRefundsResponseBuilder()
                .withRefundId(refund.getExternalId())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refund.getCreatedDate()))
                .withStatus(String.valueOf(refund.getStatus()))
                .withChargeId(refund.getChargeEntity().getExternalId())
                .withAmountSubmitted(refund.getAmount())
                .build());
        
        
        Response response = new ChargesPaginationResponseBuilder<SearchRefundsResponse>(searchParams, mockUriInfo)
                .withResponses(refundResponses)
                .withTotalCount(500L)
                .buildSearchRefundsResponse();

        // then
        JsonAssert.with((String) response.getEntity())
                .assertThat("$.total", is(500))
                .assertThat("$.count", is(1))
                .assertThat("$.page", is(2))
                .assertThat("$.results.*", hasSize(1));
    }
}
