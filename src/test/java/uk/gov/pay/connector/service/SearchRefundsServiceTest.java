package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.SearchRefundsResponse;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.resources.ChargesPaginationResponseBuilder;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.SearchRefundsResponse.anAllRefundsResponseBuilder;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(MockitoJUnitRunner.class)
public class SearchRefundsServiceTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static final long GATEWAY_ACCOUNT_ID = 1L;

    @Mock
    private RefundDao refundDao;

    @Mock
    private UriInfo uriInfo;

    private SearchRefundsService searchRefundsService;

    @Before
    public void setUp() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        searchRefundsService = new SearchRefundsService(refundDao);
    }

    @Test
    public void getAllRefunds_shouldReturnBadRequestResponse_whenQueryParamsAreInvalid() {
        Long accountId = 1L;
        Long pageNumber = -1L;
        Long displaySize = -2L;

        Response actualResponse = searchRefundsService.getAllRefunds(
                uriInfo,
                accountId,
                pageNumber,
                displaySize);

        Map<String, List<String>> expectedMessage = ImmutableMap.of("message", asList(
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer"));

        assertThat(actualResponse.getEntity(), is(expectedMessage));
        assertThat(actualResponse.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void getAllRefunds_shouldReturnRefundsWhenRefundsExist() throws URISyntaxException {
        Long accountId = 1L;
        Long total = 2L;
        Long pageNumber = 1L;
        Long displaySize = 1L;
        String userExternalId = "7v9ou841qn7jibls7ee0cb6t9j";

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                "sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(accountId);

        List<RefundEntity> refundEntities = Arrays.asList(
                aValidRefundEntity()
                        .withExternalId("someExternalId")
                        .withGatewayAccountEntity(gatewayAccount)
                        .withStatus(RefundStatus.REFUNDED)
                        .withAmount(500L)
                        .withUserExternalId(userExternalId)
                        .build(),
                aValidRefundEntity()
                        .withExternalId("someExternalId")
                        .withGatewayAccountEntity(gatewayAccount)
                        .withStatus(RefundStatus.REFUNDED)
                        .withAmount(500L)
                        .withUserExternalId(userExternalId)
                        .build()
        );

        ChargeSearchParams searchParams = new ChargeSearchParams()
                .withGatewayAccountId(accountId)
                .withDisplaySize(displaySize)
                .withPage(pageNumber);

        given(refundDao.getTotalFor(any(ChargeSearchParams.class))).willReturn(Long.valueOf(refundEntities.size()));
        given(refundDao.findAllBy(any(ChargeSearchParams.class))).willReturn(refundEntities);

        when(uriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://app.com/"));
        when(uriInfo.getPath()).thenReturn("");

        Response response = searchRefundsService.getAllRefunds(uriInfo, 1L, pageNumber, displaySize);

        RefundEntity refundEntity1 = refundEntities.get(0);
        RefundEntity refundEntity2 = refundEntities.get(1);

        List<SearchRefundsResponse> expectedRefunds =
                asList(
                        anAllRefundsResponseBuilder()
                                .withRefundId(refundEntity1.getExternalId())
                                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refundEntity1.getCreatedDate()))
                                .withStatus(String.valueOf(refundEntity1.getStatus()))
                                .withChargeId(refundEntity1.getChargeEntity().getExternalId())
                                .withAmountSubmitted(refundEntity1.getAmount())
                                .withLink("payment_url", GET, new URI("http://app.com/v1/payments/" +
                                        refundEntity1.getChargeEntity().getExternalId()))
                                .build(),
                        anAllRefundsResponseBuilder()
                                .withRefundId(refundEntity2.getExternalId())
                                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(refundEntity2.getCreatedDate()))
                                .withStatus(String.valueOf(refundEntity2.getStatus()))
                                .withChargeId(refundEntity2.getChargeEntity().getExternalId())
                                .withAmountSubmitted(refundEntity2.getAmount())
                                .withLink("payment_url", GET, new URI("http://app.com/v1/payments/" +
                                        refundEntity2.getChargeEntity().getExternalId()))
                                .build());

        Response expectedResponse = new ChargesPaginationResponseBuilder<SearchRefundsResponse>(searchParams, uriInfo)
                .withResponses(expectedRefunds)
                .withTotalCount(total)
                .buildSearchRefundsResponse();


        String actualResponse = (String) response.getEntity();
        boolean firstRefundHasPaymentUrl = actualResponse
                .contains("http://app.com/v1/payments/" + refundEntity1.getChargeEntity().getExternalId());
        boolean secondRefundHasPaymentUrl = actualResponse
                .contains("http://app.com/v1/payments/" + refundEntity1.getChargeEntity().getExternalId());

        assertThat(firstRefundHasPaymentUrl, is(true));
        assertThat(secondRefundHasPaymentUrl, is(true));
        assertThat(response.getEntity(), is(expectedResponse.getEntity()));
        assertThat(response.getEntity(), is(expectedResponse.getEntity()));
    }

    @Test
    public void getAllRefunds_shouldReturnPageNotFoundResponseWhenQueryParamPageExceedsMax() {
        Long accountId = 1L;
        Long pageNumber = 2L;
        Long displaySize = 20L;
        String userExternalId = "7v9ou841qn7jibls7ee0cb6t9j";

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(
                "sandbox", new HashMap<>(), TEST);
        gatewayAccount.setId(accountId);

        List<RefundEntity> refundEntities = Collections.singletonList(
                aValidRefundEntity()
                        .withExternalId("someExternalId")
                        .withGatewayAccountEntity(gatewayAccount)
                        .withStatus(RefundStatus.REFUNDED)
                        .withAmount(500L)
                        .withUserExternalId(userExternalId)
                        .build()
        );

        given(refundDao.getTotalFor(any(ChargeSearchParams.class))).willReturn(Long.valueOf(refundEntities.size()));
        given(refundDao.findAllBy(any(ChargeSearchParams.class))).willReturn(refundEntities);

        when(uriInfo.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://app.com/"));
        when(uriInfo.getPath()).thenReturn("");

        Response response = searchRefundsService.getAllRefunds(uriInfo, 1L, pageNumber, displaySize);

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

    }
}
