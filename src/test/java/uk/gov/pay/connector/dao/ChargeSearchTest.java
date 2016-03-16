package uk.gov.pay.connector.dao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.ZonedDateTime;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.dao.ChargeSearch.aChargeSearch;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

@RunWith(MockitoJUnitRunner.class)
public class ChargeSearchTest {

    private static final Long GATEWAY_ACCOUNT_ID = 12345L;
    private static final String REFERENCE = "reference";
    private static final ZonedDateTime FROM_DATE = ZonedDateTime.parse("2016-01-01T01:00:00Z");
    private static final ZonedDateTime TO_DATE = ZonedDateTime.parse("2026-01-01T01:00:00Z");

    @Mock
    private EntityManager entityManagerMock;

    @Mock
    private TypedQuery queryMock;

    @Test
    public void shouldCreateSearchQueryWithAllTheParametersSpecified() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.reference LIKE :reference " +
                "AND c.createdDate >= :fromDate " +
                "AND c.createdDate <= :toDate " +
                "AND c.status IN :statuses " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatusIn(CREATED, CAPTURE_READY)
                .withCreatedDateFrom(FROM_DATE)
                .withCreatedDateTo(TO_DATE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("reference", "%reference%");
        verify(queryMock).setParameter("statuses", newArrayList(CREATED, CAPTURE_READY));
        verify(queryMock).setParameter("fromDate", FROM_DATE);
        verify(queryMock).setParameter("toDate", TO_DATE);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithOnlyGatewayAccountId() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithReference() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.reference LIKE :reference " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("reference", "%reference%");
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithStatus() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.status IN :statuses " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withStatusIn(CREATED)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("statuses", newArrayList(CREATED));
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithAFromDate() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.createdDate >= :fromDate " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withCreatedDateFrom(FROM_DATE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("fromDate", FROM_DATE);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithAToDate() {

        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.createdDate <= :toDate " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withCreatedDateTo(TO_DATE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("toDate", TO_DATE);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailWhenGatewayAccountIsNotSpecified() {
        aChargeSearch(null).apply(entityManagerMock);
    }
}
