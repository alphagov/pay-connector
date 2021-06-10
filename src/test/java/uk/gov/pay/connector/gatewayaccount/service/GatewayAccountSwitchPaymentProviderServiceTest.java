package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountSwitchPaymentProviderServiceTest {

    private GatewayAccountSwitchPaymentProviderService gatewayAccountSwitchPaymentProviderService;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountSwitchPaymentProviderRequest request;

    @Mock
    private GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @Before
    public void setUp() {
        gatewayAccountSwitchPaymentProviderService = new GatewayAccountSwitchPaymentProviderService(mockGatewayAccountDao, mockGatewayAccountCredentialsDao);
        gatewayAccountEntity = aGatewayAccountEntity().build();
        request = new GatewayAccountSwitchPaymentProviderRequest(randomUuid(), randomUuid());
    }

    @Test
    public void shouldThrowExceptionWhenCredentialIsMissing() {
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
        var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, null));
        assertThat(thrown.getMessage(), is("Account has no credential to switch to/from"));
    }

    @Test
    public void shouldThrowExceptionWhenNoActiveCredentialFound() {
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withState(RETIRED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
        var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
        assertThat(thrown.getMessage(), is(format("Account credential with ACTIVE state not found.", request.getGatewayAccountCredentialId())));
    }

    @Test
    public void shouldThrowExceptionWhenCredentialsNonExistent() {
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
        var thrown = assertThrows(NotFoundException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
        assertThat(thrown.getMessage(), is(format("Account credential with id [%s] not found.", request.getGatewayAccountCredentialId())));
    }

    @Test
    public void shouldThrowExceptionWhenCredentialNotCorrectState() {
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withExternalId(request.getGatewayAccountCredentialId())
                .withState(ENTERED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
        var thrown = assertThrows(BadRequestException.class, () -> gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request));
        assertThat(thrown.getMessage(), is(format("Credential with id [%s] is not in correct state.", request.getGatewayAccountCredentialId())));
    }

    @Test
    public void shouldMergeCredentials() {
        var gatewayAccountCredentialsEntity1 = aGatewayAccountCredentialsEntity()
                .withState(ACTIVE)
                .build();
        var gatewayAccountCredentialsEntity2 = aGatewayAccountCredentialsEntity()
                .withExternalId(request.getGatewayAccountCredentialId())
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity1, gatewayAccountCredentialsEntity2));
        ArgumentCaptor<GatewayAccountCredentialsEntity> credentialArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        ArgumentCaptor<GatewayAccountEntity> gatewayArgumentCaptor = ArgumentCaptor.forClass(GatewayAccountEntity.class);
        gatewayAccountSwitchPaymentProviderService.switchPaymentProviderForAccount(gatewayAccountEntity, request);
        verify(mockGatewayAccountCredentialsDao, times(2)).merge(credentialArgumentCaptor.capture());
        verify(mockGatewayAccountDao).merge(gatewayArgumentCaptor.capture());

        List<GatewayAccountCredentialsEntity> credentialList = credentialArgumentCaptor.getAllValues();

        Optional<GatewayAccountCredentialsEntity> activeCredential = credentialList.stream().filter(credential -> ACTIVE.equals(credential.getState())).findFirst();
        assertThat(activeCredential.get().getExternalId(), is(gatewayAccountCredentialsEntity2.getExternalId()));

        Optional<GatewayAccountCredentialsEntity> retiredCredential = credentialList.stream().filter(credential -> RETIRED.equals(credential.getState())).findFirst();
        assertThat(retiredCredential.get().getExternalId(), is(gatewayAccountCredentialsEntity1.getExternalId()));
    }
}