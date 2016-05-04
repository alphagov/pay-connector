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
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_EXPIRED;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_SUCCEEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

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
                "AND c.createdDate < :toDate " +
                "AND c.status IN :statuses " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withExternalStatus(EXT_SUCCEEDED)
                .withCreatedDateFrom(FROM_DATE)
                .withCreatedDateTo(TO_DATE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("reference", "%reference%");
        verify(queryMock).setParameter("statuses", newArrayList(CAPTURED, CAPTURE_SUBMITTED));
        verify(queryMock).setParameter("fromDate", FROM_DATE);
        verify(queryMock).setParameter("toDate", TO_DATE);
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
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
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);

        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithProvidedLimitAndOffset() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);
        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withOffset(100)
                .withLimit(500)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setFirstResult(100);
        verify(queryMock).setMaxResults(500);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithDefaultLimitAndOffsetIfNotProvided() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);
        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
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
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithExternalStatusMappingToInternalStatuses() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.status IN :statuses " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);
        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withExternalStatus(EXT_EXPIRED)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("statuses", newArrayList(EXPIRED, EXPIRE_CANCEL_PENDING, EXPIRE_CANCEL_FAILED));
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
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
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithAToDate() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.createdDate < :toDate " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);
        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withCreatedDateTo(TO_DATE)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("toDate", TO_DATE);
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldAllowMissingNullOrEmptyParameters() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);
        TypedQuery<ChargeEntity> typedQuery = aChargeSearch(GATEWAY_ACCOUNT_ID)
                .withReferenceLike("  ")
                .withExternalStatus(null)
                .withCreatedDateFrom(null)
                .withCreatedDateTo(null)
                .apply(entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setFirstResult(0);
        verify(queryMock).setMaxResults(100);
        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailWhenGatewayAccountIsNotSpecified() {
        aChargeSearch(null).apply(entityManagerMock);
    }
}
