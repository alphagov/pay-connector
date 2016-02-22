package uk.gov.pay.connector.dao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import java.time.ZonedDateTime;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChargeSearchQueryBuilderTest {

    private static final Long GATEWAY_ACCOUNT_ID = 12345L;
    private static final String REFERENCE = "reference";
    private static final String STATUS = "STATUS";
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
                "AND c.status IN (:statuses) " +
                "AND c.createdDate BETWEEN :fromDate AND :toDate " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery  = new ChargeSearchQueryBuilder(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .withStatus(STATUS)
                .withCreatedDateBetween(FROM_DATE, TO_DATE)
                .buildWith(() -> entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("reference", "%reference%");
        verify(queryMock).setParameter("statuses", STATUS);
        verify(queryMock).setParameter("fromDate", FROM_DATE);
        verify(queryMock).setParameter("toDate", TO_DATE);

        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }

    @Test
    public void shouldCreateAQueryWithGatewayAccountAndReference() {
        String expectedTypedQuery = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId " +
                "AND c.reference LIKE :reference " +
                "ORDER BY c.id DESC";

        when(entityManagerMock.createQuery(expectedTypedQuery, ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery  = new ChargeSearchQueryBuilder(GATEWAY_ACCOUNT_ID)
                .withReferenceLike(REFERENCE)
                .buildWith(() -> entityManagerMock);

        assertThat(typedQuery, is(queryMock));

        verify(entityManagerMock).createQuery(expectedTypedQuery, ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", GATEWAY_ACCOUNT_ID);
        verify(queryMock).setParameter("reference", "%reference%");

        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }
}