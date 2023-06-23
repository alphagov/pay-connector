package uk.gov.pay.connector.gatewayaccountcredentials.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.CredentialsNotFoundBadRequestException;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsInUsableStateException;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("Gateway account credentials service")
public class GatewayAccountCredentialsServiceTest {

    @Mock
    GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @BeforeEach
    void setup() {
        gatewayAccountCredentialsService = new GatewayAccountCredentialsService(mockGatewayAccountCredentialsDao, objectMapper);
    }

    @Nested
    @DisplayName("createGatewayAccountCredentials")
    class CreateGatewayAccountCredentialsTest {

        @Test
        void createCredentialsForSandboxShouldCreateRecordWithActiveState() {
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
        void createCredentialsForStripeAndWithCredentialsShouldCreateRecordWithActiveState() {
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
        void createCredentialsForStripeShouldCreateRecordWithCreatedStateIfAnActiveGatewayAccountCredentialExists() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(LIVE)
                    .build();
            when(mockGatewayAccountCredentialsDao.hasActiveCredentials(gatewayAccountEntity.getId())).thenReturn(true);

            ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
            gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of("stripe_account_id", "abc"));

            verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

            assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
            assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
            assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
            assertThat(gatewayAccountCredentialsEntity.getCredentials().get("stripe_account_id"), is("abc"));
        }

        @Test
        void createCredentialsForStripeAndWithOutCredentialsShouldCreateRecordWithCreatedState() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(LIVE)
                    .build();

            ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
            gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of());

            verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

            assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
            assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
            assertThat(gatewayAccountCredentialsEntity.getState(), is(CREATED));
            assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
        }

        @Test
        void createCredentialsForStripeTestWithOutCredentialsShouldCreateRecordWithActiveState() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(TEST)
                    .build();

            ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
            gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "stripe", Map.of());

            verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

            assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
            assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
            assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
            assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
        }

        @Test
        void createCredentialsForSandboxWithOutCredentialsShouldCreateRecordWithActiveState() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(TEST)
                    .build();

            ArgumentCaptor<GatewayAccountCredentialsEntity> argumentCaptor = ArgumentCaptor.forClass(GatewayAccountCredentialsEntity.class);
            gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity, "sandbox", Map.of());

            verify(mockGatewayAccountCredentialsDao).persist(argumentCaptor.capture());
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = argumentCaptor.getValue();

            assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("sandbox"));
            assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
            assertThat(gatewayAccountCredentialsEntity.getState(), is(ACTIVE));
            assertThat(gatewayAccountCredentialsEntity.getCredentials().isEmpty(), is(true));
        }

        @ParameterizedTest
        @ValueSource(strings = {"worldpay", "epdq"})
        void createCredentialsForProvidersShouldCreateRecordWithCreatedState(String paymentProvider) {
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
    }

    @Nested
    @DisplayName("updateGatewayAccountCredentials")
    class UpdateGatewayAccountCredentialsTest {
        @Test
        void shouldUpdateGatewayAccountCredentials() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));


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

            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, patchRequests);

            verify(mockGatewayAccountCredentialsDao, times(2)).merge(credentialsEntity);
            assertThat(credentialsEntity.getCredentials(), hasEntry("merchant_id", "new-merchant-id"));
            assertThat(credentialsEntity.getLastUpdatedByUserExternalId(), is("new-user-external-id"));
            assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
        }

        @Test
        void shouldUpdateGatewayAccountCredentialsForWorldpayRecurringMerchantDetails() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonNode replaceRecurringCIT = objectMapper.valueToTree(
                    Map.of("path", "credentials/worldpay/recurring_customer_initiated",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_code", "new-merchant-code-cit"
                            )));
            JsonNode replaceRecurringMIT = objectMapper.valueToTree(
                    Map.of("path", "credentials/worldpay/recurring_merchant_initiated",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_code", "new-merchant-code-mit"
                            )));

            List<JsonPatchRequest> patchRequests = Stream.of(replaceRecurringCIT, replaceRecurringMIT)
                    .map(JsonPatchRequest::from)
                    .collect(Collectors.toList());

            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, patchRequests);

            assertThat((Map<String, Object>)credentialsEntity.getCredentials().get("recurring_customer_initiated"), hasEntry("merchant_code", "new-merchant-code-cit"));
            assertThat((Map<String, Object>)credentialsEntity.getCredentials().get("recurring_merchant_initiated"), hasEntry("merchant_code", "new-merchant-code-mit"));
        }

        @Test
        void shouldChangeStateToActive_whenCredentialsInCreatedStateUpdated_andOnlyOneCredential() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id"
                            ))));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }

        @Test
        void shouldChangeStateToEntered_whenCredentialsInCreatedStateUpdated_andMoreThanOneCredential() {
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

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id"
                            ))));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsBeingUpdated, Collections.singletonList(patchRequest));

            assertThat(credentialsBeingUpdated.getState(), is(ENTERED));
            assertThat(otherCredentials.getState(), is(ACTIVE));
        }

        @Test
        void shouldNotChangeState_whenCredentialsNotInCreatedState() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(VERIFIED_WITH_LIVE_PAYMENT)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id"
                            ))));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(VERIFIED_WITH_LIVE_PAYMENT));
        }

        @Test
        void shouldNotChangeStateWhenCredentialsAreEmpty() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of()
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(CREATED));
        }

        @Test
        void shouldNotChangeStateForWorldpayLiveAccount_whenCredentialsAreSetButNotFlexCredentials() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(WORLDPAY.getName())
                    .withType(LIVE)
                    .withWorldpay3dsFlexCredentialsEntity(null)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id")
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(CREATED));
        }

        @Test
        void shouldNotChangeStateForWorldpayLiveAccount_whenCredentialsAreNotConfiguredButFlexCredentialsAre() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(WORLDPAY.getName())
                    .withType(LIVE)
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of()
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(CREATED));
        }

        @Test
        void shouldChangeStateForWorldpayLiveAccount_whenBothCredentialsAreFlexCredentialsConfigured() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(WORLDPAY.getName())
                    .withType(LIVE)
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id")
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }

        @Test
        void shouldChangeStateForWorldpayTestAccount_whenCredentialsAreConfiguredAndNotFlexCredentials() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(WORLDPAY.getName())
                    .withType(TEST)
                    .withWorldpay3dsFlexCredentialsEntity(null)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id")
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }

        @Test
        void shouldChangeStateForWorldpayLiveMOTOAccount_whenCredentialsAreConfiguredAndNotFlexCredentials() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(WORLDPAY.getName())
                    .withType(LIVE)
                    .withAllowMoto(true)
                    .withWorldpay3dsFlexCredentialsEntity(null)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            JsonPatchRequest patchRequest = JsonPatchRequest.from(objectMapper.valueToTree(
                    Map.of("path", "credentials",
                            "op", "replace",
                            "value", Map.of(
                                    "merchant_id", "new-merchant-id")
                    )));
            gatewayAccountCredentialsService.updateGatewayAccountCredentials(credentialsEntity, Collections.singletonList(patchRequest));

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }
    }

    @Nested
    @DisplayName("Update state post flex credentials update")
    class UpdateStatePostFlexCredentialsUpdate {
        @Test
        void shouldSetCredentialsStateToActiveWhenFlexAndIntegrationCredentialsAreSetForALiveAccount() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .withType(LIVE)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }

        @Test
        void shouldNotSetCredentialsStateToActiveWhenFlexCredentialsAreNotSetForALiveAccount() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(null)
                    .withType(LIVE)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialsEntity.getState(), is(CREATED));
        }

        @Test
        void shouldSetCredentialsStateToActiveEvenWhenFlexCredentialsAreNotSetForATestAccount() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(null)
                    .withType(TEST)
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialsEntity.getState(), is(ACTIVE));
        }

        @Test
        void shouldNotUpdateStateWhenIntegrationCredentialsAreNotSet() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withCredentials(null)
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialsEntity.getState(), is(CREATED));
        }

        @Test
        void shouldChangeStateToEnteredForAWorldpayCredential_ifMultipleCredentialsExists() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity credentialToUpdate = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(CREATED)
                    .build();
            GatewayAccountCredentialsEntity activeCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialToUpdate, activeCredentials));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialToUpdate.getState(), is(ENTERED));
            assertThat(activeCredentials.getState(), is(ACTIVE));
        }

        @Test
        void shouldChangeStateToEnteredForLatestWorldpayCredential_ifMultipleWorldpayCredentialsExists() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity activeCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.now().minus(10, MINUTES))
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(ACTIVE)
                    .build();
            GatewayAccountCredentialsEntity latestCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.now())
                    .withCredentials(Map.of("username", "some-user-name"))
                    .withState(CREATED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(activeCredentials, latestCredentials));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(activeCredentials.getState(), is(ACTIVE));
            assertThat(latestCredentials.getState(), is(ENTERED));
        }

        @Test
        void shouldNotChangeStateToEnteredForAWorldpayCredential_ifMultipleCredentialsExistsAndWorldpayIntegrationCredentialsAreNotSet() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity credentialToUpdate = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCredentials(null)
                    .withState(CREATED)
                    .build();
            GatewayAccountCredentialsEntity activeCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialToUpdate, activeCredentials));

            gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);

            assertThat(credentialToUpdate.getState(), is(CREATED));
            assertThat(activeCredentials.getState(), is(ACTIVE));
        }

        @Test
        void shouldThrowErrorIfWorldpayCredentialIsNotFoundOnGatewayAccount() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity activeCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(STRIPE.getName())
                    .withState(ACTIVE)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(activeCredentials));

            WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
                gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);
            });

            assertEquals("HTTP 500 Internal Server Error", exception.getMessage());
        }

        @Test
        void shouldThrowErrorIfOnlyNonRetiredWorldpayCredentialsExists() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build())
                    .build();
            GatewayAccountCredentialsEntity activeCredentials = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(WORLDPAY.getName())
                    .withState(RETIRED)
                    .build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of(activeCredentials));

            WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
                gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);
            });

            assertEquals("HTTP 500 Internal Server Error", exception.getMessage());
        }
    }

    @DisplayName("findByCredentialsKeyValue")
    @Nested
    class FindByCredentialsKeyValueTest {
        @Test
        void shouldReturnStripeGatewayAccountForGatewayAccountCredentials() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayName(STRIPE.getName())
                    .build();
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withCredentials(Map.of("stripe_account_id", "stripeAccountId"))
                    .withPaymentProvider(STRIPE.getName())
                    .build();

            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

            gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId");

            assertThat(gatewayAccountCredentialsEntity.getPaymentProvider(), is("stripe"));
            assertThat(gatewayAccountCredentialsEntity.getGatewayAccountEntity(), is(gatewayAccountEntity));
        }

        @Test
        void shouldThrowRuntimeExceptionIfGatewayAccountNotFound() {
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withCredentials(Map.of("stripe_account_id", "stripeAccountId"))
                    .withPaymentProvider(STRIPE.getName())
                    .build();

            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

            assertThrows(GatewayAccountNotFoundException.class,
                    () -> gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId"));
        }

        @Test
        void shouldThrowRuntimeExceptionIfGatewayAccountCredentialsNotFound() {
            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(eq("stripe_account_id"), eq("stripeAccountId"))).thenReturn(Optional.empty());

            assertThrows(GatewayAccountCredentialsNotFoundException.class,
                    () -> gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue("stripe_account_id", "stripeAccountId"));
        }
    }

    @DisplayName("getCurrentOrActiveCredential")
    @Nested
    class GetCurrentOrActiveCredentialTest {
        @Test
        void shouldThrowNoCredentialsInUsableStateExceptionIfGatewayAccountCredentialInCreatedState() {
            GatewayAccountCredentialsEntity credentialsEntityWorldpay = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider("worldpay")
                    .withState(CREATED)
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(credentialsEntityWorldpay))
                    .build();

            assertThrows(NoCredentialsInUsableStateException.class,
                    () -> gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccountEntity));
        }

        @Test
        void shouldThrowWebApplicationExceptionIfNoCredentialsFound() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of())
                    .build();

            assertThrows(WebApplicationException.class,
                    () -> gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccountEntity));
        }
    }

    @DisplayName("findCredentialFromCharge")
    @Nested
    class FindCredentialFromChargeTest {
        @Test
        void shouldReturnGatewayAccountCredentialForMatchedCredentialExternalId() {
            GatewayAccountCredentialsEntity aGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .build();
            GatewayAccountCredentialsEntity aGatewayAccountCredentialsEntityTwo = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(aGatewayAccountCredentialsEntityOne, aGatewayAccountCredentialsEntityTwo))
                    .build();
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntityOne).build());
            String expectedCredentialsExternalId = aGatewayAccountCredentialsEntityOne.getExternalId();
            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(true));
            assertThat(gatewayAccountCredentialsEntity.get().getExternalId(), is(expectedCredentialsExternalId));
        }

        @Test
        void shouldReturnEmptyOptionalIfNoGatewayAccountCredentialsFoundInGatewayAccountEntity() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            gatewayAccountEntity.setGatewayAccountCredentials(List.of());
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withGatewayAccountCredentialsEntity(null).build());
            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(false));
        }

        @Test
        void shouldReturnGatewayAccountCredentialByPaymentProviderIfNoMatchForCredentialExternalId() {
            GatewayAccountCredentialsEntity worldpayGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .build();
            GatewayAccountCredentialsEntity stripeGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(worldpayGatewayAccountCredentialsEntityOne, stripeGatewayAccountCredentialsEntityOne))
                    .build();
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withPaymentProvider(WORLDPAY.getName()).build());
            String chargeCredentialExternalId = charge.getCredentialExternalId().get();
            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(true));
            assertThat(gatewayAccountCredentialsEntity.get().getPaymentProvider(), is("worldpay"));
            assertThat(chargeCredentialExternalId.equals(gatewayAccountCredentialsEntity.get().getExternalId()), is(false));
        }

        @Test
        void shouldReturnGatewayAccountCredentialByPaymentProviderIfCredentialExternalIdNotProvided() {
            GatewayAccountCredentialsEntity worldpayGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .build();
            GatewayAccountCredentialsEntity stripeGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(worldpayGatewayAccountCredentialsEntityOne, stripeGatewayAccountCredentialsEntityOne))
                    .build();
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withPaymentProvider(WORLDPAY.getName()).build());
            charge.setCredentialExternalId(null);
            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(true));
            assertThat(gatewayAccountCredentialsEntity.get().getPaymentProvider(), is("worldpay"));
        }

        @Test
        void shouldReturnGatewayAccountCredentialCreatedBeforeChargeIfMultipleCredentialsExistWhenSearchingByPaymentProvider() {
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withCreatedDate(Instant.parse("2021-08-16T10:00:00Z")).withPaymentProvider(WORLDPAY.getName()).build());
            GatewayAccountCredentialsEntity worldpayGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.parse("2021-08-15T13:00:00Z"))
                    .build();
            GatewayAccountCredentialsEntity stripeGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.parse("2021-08-17T15:00:00Z"))
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(worldpayGatewayAccountCredentialsEntityOne, stripeGatewayAccountCredentialsEntityOne))
                    .build();

            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(true));
            assertThat(gatewayAccountCredentialsEntity.get().getPaymentProvider(), is("worldpay"));
            assertThat(gatewayAccountCredentialsEntity.get().getCreatedDate(), is(Instant.parse("2021-08-15T13:00:00Z")));
        }

        @Test
        void shouldReturnFirstGatewayAccountCredentialIfNoneCreatedBeforeChargeWhenSearchingByPaymentProvider() {
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withCreatedDate(Instant.parse("2021-08-16T10:00:00Z")).withPaymentProvider(WORLDPAY.getName()).build());
            GatewayAccountCredentialsEntity worldpayGatewayAccountCredentialsEntityOne = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.parse("2021-08-17T13:00:00Z"))
                    .build();
            GatewayAccountCredentialsEntity worldpayGatewayAccountCredentialsEntityTwo = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withCreatedDate(Instant.parse("2021-08-18T15:00:00Z"))
                    .build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withGatewayAccountCredentials(List.of(worldpayGatewayAccountCredentialsEntityOne, worldpayGatewayAccountCredentialsEntityTwo))
                    .build();

            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(true));
            assertThat(gatewayAccountCredentialsEntity.get().getPaymentProvider(), is("worldpay"));
            assertThat(gatewayAccountCredentialsEntity.get().getCreatedDate(), is(Instant.parse("2021-08-17T13:00:00Z")));
        }

        @Test
        void shouldReturnEmptyOptionalIfNoGatewayAccountCredentialsFoundWhenSearchingByPaymentProvider() {
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity stripeGatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(List.of(stripeGatewayAccountCredentialsEntity));
            Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().withPaymentProvider(WORLDPAY.getName()).build());
            Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity);

            assertThat(gatewayAccountCredentialsEntity.isPresent(), is(false));
        }

    }

    @Nested
    @DisplayName("activateCredentialIfNotYetActive")
    class ActivateCredentialIfNotYetActiveTest {
        @Test
        void shouldUpdateStateToActiveIfNotActive() {
            String stripeCredentialId = "credentialId";
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .withState(CREATED)
                    .withCredentials(Map.of(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .thenReturn(Optional.of(gatewayAccountCredentialsEntity));
            gatewayAccountCredentialsService.activateCredentialIfNotYetActive(stripeCredentialId);

            verify(mockGatewayAccountCredentialsDao, times(1)).merge(any(GatewayAccountCredentialsEntity.class));
        }

        @Test
        void shouldUpdateStateToEnteredIfActiveCredentialAlreadyExists() {
            String stripeCredentialId = "credentialId";
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity updatableEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .withState(CREATED)
                    .withCredentials(Map.of(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build();
            GatewayAccountCredentialsEntity existingEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .withState(ACTIVE)
                    .withCredentials(Map.of(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(List.of(updatableEntity, existingEntity));
            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .thenReturn(Optional.of(updatableEntity));
            gatewayAccountCredentialsService.activateCredentialIfNotYetActive(stripeCredentialId);

            assertThat(updatableEntity.getState(), is(ENTERED));
            verify(mockGatewayAccountCredentialsDao, times(1)).merge(any(GatewayAccountCredentialsEntity.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACTIVE", "RETIRED", "VERIFIED_WITH_LIVE_PAYMENT", "ENTERED"})
        void shouldNotUpdateStateToActive(String state) {
            var credentialState = GatewayAccountCredentialState.valueOf(state);
            String stripeCredentialId = "credentialId";
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(STRIPE.getName())
                    .withState(credentialState)
                    .withCredentials(Map.of(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));
            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .thenReturn(Optional.of(gatewayAccountCredentialsEntity));
            gatewayAccountCredentialsService.activateCredentialIfNotYetActive(stripeCredentialId);

            verify(mockGatewayAccountCredentialsDao, never()).merge(any(GatewayAccountCredentialsEntity.class));
        }

        @Test
        void shouldNotUpdateNonExistentCredentials() {
            String stripeCredentialId = "credentialId";
            when(mockGatewayAccountCredentialsDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeCredentialId))
                    .thenReturn(Optional.empty());
            gatewayAccountCredentialsService.activateCredentialIfNotYetActive(stripeCredentialId);

            verify(mockGatewayAccountCredentialsDao, never()).merge(any(GatewayAccountCredentialsEntity.class));
        }
    }

    @Nested
    @DisplayName("getCredentialInUsableState")
    class GetCredentialInUsableState {

        @Test
        void shouldReturnCredentialForExternalId() {
            String credentialExternalId = "credential-external-id";
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withExternalId(credentialExternalId)
                    .build();
            when(mockGatewayAccountCredentialsDao.findByExternalIdAndGatewayAccountId(credentialExternalId, 1L))
                    .thenReturn(Optional.of(gatewayAccountCredentialsEntity));

            GatewayAccountCredentialsEntity credentialsEntity =
                    gatewayAccountCredentialsService.getCredentialInUsableState(credentialExternalId, 1L);

            assertThat(credentialExternalId, is(credentialsEntity.getExternalId()));
        }

        @Test
        void shouldThrowExceptionIfCredentialNotFoundForExternalId() {
            String credentialExternalId = "credential-external-id";
            when(mockGatewayAccountCredentialsDao.findByExternalIdAndGatewayAccountId(credentialExternalId, 1L))
                    .thenReturn(Optional.empty());

            var exception = assertThrows(CredentialsNotFoundBadRequestException.class, () -> {
                gatewayAccountCredentialsService.getCredentialInUsableState(credentialExternalId, 1L);
            });

            assertThat(exception.getMessage(), is("Credentials not found for gateway account [1] and credential_external_id [credential-external-id]"));
        }

        @Test
        void shouldThrowExceptionIfCredentialIsNotInUsableState() {
            String credentialExternalId = "credential-external-id";
            GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                    .aGatewayAccountCredentialsEntity()
                    .withExternalId(credentialExternalId)
                    .withState(CREATED)
                    .build();

            when(mockGatewayAccountCredentialsDao.findByExternalIdAndGatewayAccountId(credentialExternalId, 1L))
                    .thenReturn(Optional.of(gatewayAccountCredentialsEntity));

            var exception = assertThrows(NoCredentialsInUsableStateException.class, () -> {
                gatewayAccountCredentialsService.getCredentialInUsableState(credentialExternalId, 1L);
            });

            assertThat(exception.getMessage(), is("Payment provider details are not configured on this account"));
        }
    }
}
