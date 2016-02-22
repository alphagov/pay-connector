package uk.gov.pay.connector.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    @Mock
    private EntityManager entityManagerMock;

    @Test
    public void shouldCreateSearchQueryWithAllTheParametersSpecified() {

        Long gatewayAccount = 12345L;
        String reference = "reference";
        String status = "STATUS";
        ZonedDateTime fromDate = ZonedDateTime.parse("2016-01-01T01:00:00Z");
        ZonedDateTime toDate = ZonedDateTime.parse("2026-01-01T01:00:00Z");

        TypedQuery queryMock = Mockito.mock(TypedQuery.class);
        when(entityManagerMock.createQuery("select c from ChargeEntity c " +
                "where c.gatewayAccount.id = :gatewayAccountId " +
                "and c.reference like :reference " +
                "and c.status = :status " +
                "and c.createdDate between :fromDate and :toDate", ChargeEntity.class)).thenReturn(queryMock);

        TypedQuery<ChargeEntity> typedQuery  = new ChargeSearchQueryBuilder()
                .withGatewayAccountId(gatewayAccount)
                .withReferenceLike(reference)
                .withStatus(status)
                .withCreatedDateBetween(fromDate, toDate)
                .build(() -> entityManagerMock);

        assertThat(typedQuery, is(queryMock));
        verify(entityManagerMock).createQuery("select c from ChargeEntity c " +
                "where c.gatewayAccount.id = :gatewayAccountId " +
                "and c.reference like :reference " +
                "and c.status = :status " +
                "and c.createdDate between :fromDate and :toDate", ChargeEntity.class);
        verify(queryMock).setParameter("gatewayAccountId", gatewayAccount);
        verify(queryMock).setParameter("reference", "%reference%");
        verify(queryMock).setParameter("status", status);
        verify(queryMock).setParameter("fromDate", fromDate);
        verify(queryMock).setParameter("toDate", toDate);

        verifyNoMoreInteractions(queryMock, entityManagerMock);
    }
}