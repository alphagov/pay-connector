package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.dao.Worldpay3dsFlexCredentialsDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.ws.rs.WebApplicationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class Worldpay3dsFlexCredentialsServiceTest {

    private Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService;
    @Mock
    private Worldpay3dsFlexCredentialsDao mockWorldpay3dsFlexCredentialsDao;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @BeforeEach
    public void setup() {
        worldpay3dsFlexCredentialsService = new Worldpay3dsFlexCredentialsService(
                mockWorldpay3dsFlexCredentialsDao,
                mockGatewayAccountCredentialsService);
    }

    @Test
    void shouldUpdateFlexCredentialsAndCredentialsState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

        Worldpay3dsFlexCredentialsRequest worldpay3dsFlexCredentialsRequest
                = new Worldpay3dsFlexCredentialsRequest();

        worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsFlexCredentialsRequest,
                gatewayAccountEntity);

        verify(mockWorldpay3dsFlexCredentialsDao).merge(any(Worldpay3dsFlexCredentialsEntity.class));
        verify(mockGatewayAccountCredentialsService).updateStateForCredentials(credentialsEntity);
    }

    @Test
    void shouldThrowErrorWhenGatewayCredentialToUpdateStateIsNotFound() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        Worldpay3dsFlexCredentialsRequest worldpay3dsFlexCredentialsRequest
                = new Worldpay3dsFlexCredentialsRequest();

        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsFlexCredentialsRequest,
                    gatewayAccountEntity);
        });

        assertEquals("HTTP 500 Internal Server Error", exception.getMessage());

        verifyNoInteractions(mockGatewayAccountCredentialsService);
    }
}
