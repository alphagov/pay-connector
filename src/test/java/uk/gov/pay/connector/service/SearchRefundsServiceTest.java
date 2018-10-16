package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.jayway.jsonassert.JsonAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(MockitoJUnitRunner.class)
public class SearchRefundsServiceTest {
    private static final long ACCOUNT_ID = 1L;
    private SearchRefundsService searchRefundsService;
    private GatewayAccountEntity gatewayAccount;
    private static final String EXT_CHARGE_ID = "someExternalId";
    static final String EXT_REFUND_ID = "someExternalRefundId";
    static final Long PAGE_NUMBER = 1L;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private RefundDao refundDao;

    @Mock
    private UriInfo uriInfo;

    @Before
    public void setUp() {
        gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(ACCOUNT_ID);
        searchRefundsService = new SearchRefundsService(refundDao);
    }

    @Test
    public void getAllRefunds_shouldReturnBadRequestResponse_whenQueryParamsAreInvalid() {
        Long pageNumber = -1L;
        Long displaySize = -2L;

        Response actualResponse = searchRefundsService.getAllRefunds(
                uriInfo,
                ACCOUNT_ID,
                pageNumber,
                displaySize);

        Map<String, List<String>> expectedMessage = ImmutableMap.of("message", asList(
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer"));

        assertThat(actualResponse.getEntity(), is(expectedMessage));
        assertThat(actualResponse.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void getAllRefunds_shouldReturnPageNotFoundResponseWhenQueryParamPageExceedsMax() {
        Long pageNumber = 2L;
        Long displaySize = 20L;
        List<RefundEntity> refundEntities = getRefundEntity(1, gatewayAccount);

        when(refundDao.getTotalFor(any(SearchParams.class))).thenReturn(Long.valueOf(refundEntities.size()));

        Response response = searchRefundsService.getAllRefunds(uriInfo, ACCOUNT_ID, pageNumber, displaySize);

        ImmutableMap<String, Object> actualResponse = (ImmutableMap<String, Object>) response.getEntity();
        Response.StatusType statusType = response.getStatusInfo();
        int statusCode = response.getStatus();
        ImmutableMap<String, String> expectedMessage = ImmutableMap.of(
                "message", "the requested page not found");
        assertThat(statusType, is(NOT_FOUND));
        assertThat(statusCode, is(NOT_FOUND.getStatusCode()));
        assertThat(actualResponse, is(expectedMessage));
    }

    @Test
    public void shouldReturnDefaultDisplayWhenQueryParamDisplayExceedsMax() {
        String DEFAULT_DISPLAY_SIZE = "500";
        Long EXCEEDS_DISPLAY_SIZE = 600L;
        List<RefundEntity> refundEntities = getRefundEntity(1, gatewayAccount);

        when(refundDao.getTotalFor(any(SearchParams.class))).thenReturn(EXCEEDS_DISPLAY_SIZE);
        when(refundDao.findAllBy(any(SearchParams.class))).thenReturn(refundEntities);
        when(uriInfo.getBaseUriBuilder()).thenReturn(fromUri("http://app.com/"));
        when(uriInfo.getPath()).thenReturn(format("/v1/api/accounts/%s/refunds", ACCOUNT_ID));
        when(uriInfo.getBaseUri()).thenReturn(fromUri("http://app.com/").build());

        Response response = searchRefundsService.getAllRefunds(uriInfo, ACCOUNT_ID, PAGE_NUMBER, EXCEEDS_DISPLAY_SIZE);

        String body = (String) response.getEntity();

        JsonAssert.with(body)
                .assertThat("$.results.*", hasSize(1))
                .assertThat("$.total", is(600))
                .assertThat("$.count", is(1))
                .assertThat("$.page", is(1))
                .assertThat("$._links.self.href",
                        is(format("http://app.com/v1/api/accounts/%s/refunds?page=%s&display_size=%s",
                                ACCOUNT_ID, PAGE_NUMBER, DEFAULT_DISPLAY_SIZE)))
                .assertThat("$.results[0].links[0].rel", is("self"))
                .assertThat("$.results[0].links[0].href",
                        is(format("http://app.com/v1/api/accounts/%s/charges/%s/refunds/%s",
                                ACCOUNT_ID, EXT_CHARGE_ID, EXT_REFUND_ID)))
                .assertThat("$.results[0].links[1].rel", is("payment_url"))
                .assertThat("$.results[0].links[1].href",
                        is(format("http://app.com/v1/api/accounts/%s/charges/%s", ACCOUNT_ID, EXT_CHARGE_ID)));
    }

    @Test
    public void getAllRefunds_shouldReturnRefundsWhenRefundsExist() {
        Long displaySize = 3L;
        List<RefundEntity> refundEntities = getRefundEntity(3, gatewayAccount);

        when(refundDao.getTotalFor(any(SearchParams.class))).thenReturn(displaySize);
        when(refundDao.findAllBy(any(SearchParams.class))).thenReturn(refundEntities);
        when(uriInfo.getBaseUriBuilder()).thenReturn(fromUri("http://app.com/"));
        when(uriInfo.getPath()).thenReturn(format("/v1/api/accounts/%s/refunds", ACCOUNT_ID));
        when(uriInfo.getBaseUri()).thenReturn(fromUri("http://app.com/").build());

        Response response = searchRefundsService.getAllRefunds(uriInfo, ACCOUNT_ID, PAGE_NUMBER, displaySize);

        String body = (String) response.getEntity();

        JsonAssert.with(body)
                .assertThat("$.results.*", hasSize(3))
                .assertThat("$.total", is(3))
                .assertThat("$.count", is(3))
                .assertThat("$.page", is(1))
                .assertThat("$._links.self.href",
                        is(format("http://app.com/v1/api/accounts/%s/refunds?page=%s&display_size=%s",
                                ACCOUNT_ID, PAGE_NUMBER, displaySize)))
                .assertThat("$.results[0].links[0].rel", is("self"))
                .assertThat("$.results[0].links[0].href",
                        is(format("http://app.com/v1/api/accounts/%s/charges/%s/refunds/%s",
                                ACCOUNT_ID, EXT_CHARGE_ID, EXT_REFUND_ID)))
                .assertThat("$.results[0].links[1].rel", is("payment_url"))
                .assertThat("$.results[0].links[1].href",
                        is(format("http://app.com/v1/api/accounts/%s/charges/%s", ACCOUNT_ID, EXT_CHARGE_ID)));
    }

    private List<RefundEntity> getRefundEntity(int numOfRefunds, GatewayAccountEntity gatewayAccount) {
        ChargeEntity chargeEntity = aValidChargeEntity().withExternalId(EXT_CHARGE_ID).build();
        List<RefundEntity> refundEntities = new ArrayList<>();
        for (int i = 0; i < numOfRefunds; i++) {
            RefundEntity refundEntity = aValidRefundEntity()
                    .withExternalId(EXT_REFUND_ID)
                    .withCharge(chargeEntity)
                    .withGatewayAccountEntity(gatewayAccount)
                    .withStatus(RefundStatus.REFUNDED)
                    .withAmount(500L)
                    .withUserExternalId("7v9ou841qn7jibls7ee0cb6t9j")
                    .build();
            refundEntities.add(refundEntity);
        }
        return refundEntities;
    }
}
