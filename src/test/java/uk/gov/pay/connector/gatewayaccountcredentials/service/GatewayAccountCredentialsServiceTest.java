package uk.gov.pay.connector.gatewayaccountcredentials.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
public class GatewayAccountCredentialsServiceTest {

    @Mock
    GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @BeforeEach
    void setup() {
        gatewayAccountCredentialsService = new GatewayAccountCredentialsService(mockGatewayAccountCredentialsDao);
    }

    @Test
    public void createCredentialsForSandboxShouldCreateRecordWithActiveState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "sandbox", Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("sandbox"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }

    @Test
    public void createCredentialsForStripeAndWithCredentialsShouldCreateRecordWithActiveState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        when(mockGatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId())).thenReturn(false);

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of("stripe_account_id", "abc"));

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().get("stripe_account_id"), is("abc"));
    }

    @Test
    public void createCredentialsForStripeShouldCreateRecordWithEnteredStateIfAnActiveGatewayAccountCredentialExists() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        when(mockGatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId())).thenReturn(true);

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of("stripe_account_id", "abc"));

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(ENTERED));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().get("stripe_account_id"), is("abc"));
    }

    @Test
    public void createCredentialsForStripeAndWithOutCredentialsShouldCreateRecordWithCreatedState() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"worldpay", "epdq", "smartpay"})
    public void createCredentialsForProvidersShouldCreateRecordWithCreatedState(String paymentProvider) {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, paymentProvider, Map.of());

        verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

        assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is(paymentProvider));
        assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
        assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
    }

    @Test
    public void shouldUpdateGatewayCredentialsIfExactlyOneRecordAndSetToActive() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withState(CREATED)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        var credentials = Map.of("username", "foo");
        gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials);

        assertThat(gatewayAccountEntity.getGatewayAccountCredentials(), hasSize(1));
        GatewayAccountCredentialsEntity updatedCredentials = gatewayAccountEntity.getGatewayAccountCredentials().get(0);
        assertThat(updatedCredentials.getCredentials(), is(credentials));
        assertThat(updatedCredentials.getState(), is(ACTIVE));
        assertThat(updatedCredentials.getActiveStartDate(), is(notNullValue()));
    }

    @Test
    void shouldNotUpdateIfNoCredentialsRecordsExist() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withCredentials(null)
                .withGatewayAccountCredentials(List.of())
                .build();

        var credentials = Map.of("username", "foo");
        gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials);

        assertThat(gatewayAccountEntity.getGatewayAccountCredentials(), hasSize(0));
    }

    @Test
    void shouldThrowExceptionIfMoreThanOneCredentialsRecord() {
        GatewayAccountCredentialsEntity credentialsEntity1 = aGatewayAccountCredentialsEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity2 = aGatewayAccountCredentialsEntity().build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(credentialsEntity1, credentialsEntity2))
                .build();

        var credentials = Map.of("username", "foo");
        assertThrows(WebApplicationException.class, () ->
                gatewayAccountCredentialsService.updateGatewayAccountCredentialsForLegacyEndpoint(gatewayAccountEntity, credentials));
    }

    @Test
    void shouldUpdateGatewayAccountCredentials() {
        long credentialsId = 1;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.of(credentialsEntity));

        JsonNode replaceCredentialsNode = objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        )));
        JsonNode replaceLastUserNode = objectMapper.valueToTree(
                Map.of("path", "last_updated_by_user_external_id",
                        "op", "replace",
                        "value", "new-user-external-id")
        );
        JsonNode replaceStateNode = objectMapper.valueToTree(
                Map.of("path", "state",
                        "op", "replace",
                        "value", "VERIFIED_WITH_LIVE_PAYMENT")
        );

        List<JsonPatchRequest> patchRequests = Stream.of(replaceCredentialsNode, replaceLastUserNode, replaceStateNode)
                .map(JsonPatchRequest::from)
                .collect(Collectors.toList());

        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId, patchRequests);

        assertThat(credentialsEntity.getCredentials(), hasEntry("merchant_id", "new-merchant-id"));
        assertThat(credentialsEntity.getLastUpdatedByUserExternalId(), is("new-user-external-id"));
        assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
    }

    @Test
    void shouldChangeStateToActive_whenCredentialsInCreatedStateUpdated_andOnlyOneCredential() {
        long credentialsId = 1;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.of(credentialsEntity));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId, Collections.singletonList(patchRequest));
        
        assertThat(credentialsEntity.getState(), is(ACTIVE));
    }

    @Test
    void shouldChangeStateToEntered_whenCredentialsInCreatedStateUpdated_andMoreThanOneCredential() {
        long credentialsId = 1;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsBeingUpdated = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(CREATED)
                .build();
        GatewayAccountCredentialsEntity otherCredentials = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsBeingUpdated, otherCredentials));
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.of(credentialsBeingUpdated));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId, Collections.singletonList(patchRequest));

        assertThat(credentialsBeingUpdated.getState(), is(ENTERED));
        assertThat(otherCredentials.getState(), is(ACTIVE));
    }

    @Test
    void shouldNotChangeState_whenCredentialsNotInCreatedState() {
        long credentialsId = 1;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withState(VERIFIED_WITH_LIVE_PAYMENT)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.of(credentialsEntity));

        JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "credentials",
                        "op", "replace",
                        "value", Map.of(
                                "merchant_id", "new-merchant-id"
                        ))));
        gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId, Collections.singletonList(patchRequest));

        assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
    }

    @Test
    void shouldThrowForNotFoundCredentialsId() {
        long credentialsId = 1;
        when(mockGatewayAccountCredentialsDao.findById(credentialsId)).thenReturn(Optional.empty());

        List<JsonPatchRequest> patchRequests = Collections.singletonList(JsonPatchRequest.from(objectMapper.valueToTree(
                Map.of("path", "last_updated_by_user_external_id",
                        "op", "replace",
                        "value", "new-user-external-id")
        )));

        assertThrows(GatewayAccountCredentialsNotFoundException.class, () ->
                gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsId,
                        patchRequests));
    }
}
