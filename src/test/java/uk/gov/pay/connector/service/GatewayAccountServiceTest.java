package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.DigitalWalletNotSupportedGatewayException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountWithoutAnActiveCredentialException;
import uk.gov.pay.connector.gatewayaccount.exception.MissingWorldpay3dsFlexCredentialsEntityException;
import uk.gov.pay.connector.gatewayaccount.exception.NotSupportedGatewayAccountException;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_DISABLED;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_DISABLED_REASON;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_RECURRING_ENABLED;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@ExtendWith(MockitoExtension.class)
public class GatewayAccountServiceTest {
    private static final String BAD_REQUEST_MESSAGE = "HTTP 400 Bad Request";

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;
    
    @Mock
    private CardTypeDao mockCardTypeDao;
    
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    
    @Mock
    private GatewayAccountEntity getMockGatewayAccountEntity1;
    
    @Mock
    private GatewayAccountEntity getMockGatewayAccountEntity2;

    @Mock
    private Worldpay3dsFlexCredentialsEntity mockWorldpay3dsFlexCredentialsEntity;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    private GatewayAccountService gatewayAccountService;
    
    private static final Long GATEWAY_ACCOUNT_ID = 100L;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        gatewayAccountService = new GatewayAccountService(mockGatewayAccountDao, mockCardTypeDao,
                mockGatewayAccountCredentialsService);
        lenient().when(mockGatewayAccountEntity.getType()).thenReturn("test");
        lenient().when(getMockGatewayAccountEntity1.getType()).thenReturn("test");
        lenient().when(getMockGatewayAccountEntity1.getServiceName()).thenReturn("service one");
        lenient().when(getMockGatewayAccountEntity2.getType()).thenReturn("test");
        lenient().when(getMockGatewayAccountEntity2.getServiceName()).thenReturn("service two");
    }

    @Test
    public void shouldGetGatewayAccount() {
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccountEntity> gatewayAccountEntity = gatewayAccountService.getGatewayAccount(GATEWAY_ACCOUNT_ID);

        assertThat(gatewayAccountEntity.get(), is(this.mockGatewayAccountEntity));
    }

    @Test
    public void shouldSearchGatewayAccounts() {
        GatewayAccountSearchParams gatewayAccountSearchParams = new GatewayAccountSearchParams();
        when(mockGatewayAccountDao.search(gatewayAccountSearchParams))
                .thenReturn(Arrays.asList(getMockGatewayAccountEntity1, getMockGatewayAccountEntity2));

        List<GatewayAccountResponse> gatewayAccounts = gatewayAccountService.searchGatewayAccounts(gatewayAccountSearchParams);

        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getServiceName(), is("service one"));
        assertThat(gatewayAccounts.get(1).getServiceName(), is("service two"));
    }

    @Test
    public void shouldUpdateNotifySettingsWhenUpdate() {
        Map<String, String> settings = Map.of(
                "api_token", "anapitoken",
                "template_id", "atemplateid");
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "notify_settings",
                "value", settings)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setNotifySettings(settings);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateNotifySettingsWhenRemove() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of("op", "replace",
                "path", "notify_settings")));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setNotifySettings(null);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateEmailCollectionMode() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "email_collection_mode",
                "value", "off")));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        assertThat(optionalGatewayAccount.isPresent(), is(true));
        InOrder inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        inOrder.verify(mockGatewayAccountEntity).setEmailCollectionMode(EmailCollectionMode.OFF);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }
    
    @Test
    public void shouldUpdateCorporateCreditCardSurchargeAmount() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setCorporateCreditCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateCorporateDebitCardSurchargeAmount() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "corporate_debit_card_surcharge_amount",
                "value", 100)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setCorporateDebitCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidDebitCardSurchargeAmount() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 100)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setCorporatePrepaidDebitCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldNotAllowDigitalWalletForUnsupportedGateways() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "add",
                "path", "allow_apple_pay",
                "value", "true")));

        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("epdq");
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        assertThrows(DigitalWalletNotSupportedGatewayException.class,
                () -> gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request));
    }

    @Test
    public void shouldUpdateAllowZeroAmountTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_zero_amount",
                "value", true)));


        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowZeroAmount(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowZeroAmountFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_zero_amount",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowZeroAmount(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateBlockPrepaidCardsTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "block_prepaid_cards",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setBlockPrepaidCards(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateBlockPrepaidCardsFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "block_prepaid_cards",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setBlockPrepaidCards(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowMotoTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_moto",
                "value", true)));


        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowMoto(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowMotoFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_moto",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowMoto(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateMotoMaskCardNumberInputTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "moto_mask_card_number_input",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setMotoMaskCardNumberInput(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateMotoMaskCardNumberInputFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "moto_mask_card_number_input",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setMotoMaskCardNumberInput(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateMotoMaskCardSecurityCodeInputTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "moto_mask_card_security_code_input",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setMotoMaskCardSecurityCodeInput(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateMotoMaskCardSecurityCodeInputFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "moto_mask_card_security_code_input",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setMotoMaskCardSecurityCodeInput(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowTelephonePaymentNotificationsToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_telephone_payment_notifications",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowTelephonePaymentNotifications(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowTelephonePaymentNotificationsToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_telephone_payment_notifications",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowTelephonePaymentNotifications(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateSendPayerIpAddressToGatewayToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_payer_ip_address_to_gateway",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendPayerIpAddressToGateway(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateSendPayerIpAddressToGatewayToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_payer_ip_address_to_gateway",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendPayerIpAddressToGateway(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateSendPayerEmailToGatewayToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_payer_email_to_gateway",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendPayerEmailToGateway(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateSendPayerEmailToGatewayToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_payer_email_to_gateway",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendPayerEmailToGateway(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateProviderSwitchEnabledToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "provider_switch_enabled",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        when(mockGatewayAccountEntity.getId()).thenReturn(GATEWAY_ACCOUNT_ID);
        when(mockGatewayAccountCredentialsService.hasActiveCredentials(GATEWAY_ACCOUNT_ID)).thenReturn(true);
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setProviderSwitchEnabled(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void enableProviderSwitchShouldThrowErrorIfAnActiveCredentialDoesNotExist() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "provider_switch_enabled",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        var thrown = assertThrows(
                GatewayAccountWithoutAnActiveCredentialException.class,
                () -> gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request)
        );
        assertThat(thrown.getMessage(),
                is(BAD_REQUEST_MESSAGE));
    }

    @Test
    public void shouldUpdateSendReferenceToGatewayToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_reference_to_gateway",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendReferenceToGateway(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateSendReferenceToGatewayToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "send_reference_to_gateway",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setSendReferenceToGateway(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateIntegrationVersion3ds() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "integration_version_3ds",
                "value", 2
        )));
        
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setIntegrationVersion3ds(2);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldGetGatewayAccountByExternalId() {
        String externalId = randomUuid();
        when(mockGatewayAccountDao.findByExternalId(externalId)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccountEntity> gatewayAccountEntity = gatewayAccountService.getGatewayAccountByExternal(externalId);

        assertThat(gatewayAccountEntity.get(), is(this.mockGatewayAccountEntity));
    }

    @Test
    public void shouldUpdateWorldpayExemptionEngineEnabledToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
                "value", false)));
        when(mockGatewayAccountEntity.getGatewayName()).thenReturn(WORLDPAY.getName());
        when(mockGatewayAccountEntity.getWorldpay3dsFlexCredentialsEntity()).thenReturn(Optional.of(mockWorldpay3dsFlexCredentialsEntity));
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        InOrder inOrder = inOrder(mockWorldpay3dsFlexCredentialsEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockWorldpay3dsFlexCredentialsEntity).setExemptionEngineEnabled(false);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateWorldpayExemptionEngineEnabledToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
                "value", true)));
        when(mockGatewayAccountEntity.getGatewayName()).thenReturn(WORLDPAY.getName());
        when(mockGatewayAccountEntity.getWorldpay3dsFlexCredentialsEntity()).thenReturn(Optional.of(mockWorldpay3dsFlexCredentialsEntity));
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        InOrder inOrder = inOrder(mockWorldpay3dsFlexCredentialsEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockWorldpay3dsFlexCredentialsEntity).setExemptionEngineEnabled(true);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldThrowWrongGatewayAccountExceptionWhenAccountIsNotWorldpay() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
                "value", true)));
        when(mockGatewayAccountEntity.getGatewayName()).thenReturn(EPDQ.getName());
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        var thrown = assertThrows(
                NotSupportedGatewayAccountException.class,
                () -> gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request)
        );

        assertThat(thrown.getMessage(), is(BAD_REQUEST_MESSAGE));
    }

    @Test
    public void shouldThrowMissing3dsFlexCredentialsEntityExceptionWhenWorldpay3dsFlexCredentialsEntityGoesMissing() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
                "value", false)));
        when(mockGatewayAccountEntity.getGatewayName()).thenReturn(WORLDPAY.getName());
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        var thrown = assertThrows(
                MissingWorldpay3dsFlexCredentialsEntityException.class,
                () -> gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request)
        );

        assertThat(thrown.getMessage(), is(BAD_REQUEST_MESSAGE));
    }

    @Test
    public void shouldUpdateAllowAuthorisationApiToTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_authorisation_api",
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowAuthorisationApi(true);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateAllowAuthorisationApiToFalse() {
        JsonPatchRequest request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", "allow_authorisation_api",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowAuthorisationApi(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateRecurringEnabledToTrue() {
        var request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_RECURRING_ENABLED,
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        var inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockGatewayAccountEntity).setRecurringEnabled(true);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateRecurringEnabledToFalse() {
        var request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_RECURRING_ENABLED,
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        var inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockGatewayAccountEntity).setRecurringEnabled(false);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateDisabledToTrue() {
        var request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_DISABLED,
                "value", true)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        var inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockGatewayAccountEntity).setDisabled(true);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
        verify(mockGatewayAccountEntity, never()).setDisabledReason(anyString());
    }

    @Test
    public void shouldUpdateDisabledToFalseAndClearDisabledReason() {
        var request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_DISABLED,
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        var inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockGatewayAccountEntity).setDisabled(false);
        inOrder.verify(mockGatewayAccountEntity).setDisabledReason(null);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateDisabledReason() {
        var request = JsonPatchRequest.from(objectMapper.valueToTree(Map.of(
                "op", "replace",
                "path", FIELD_DISABLED_REASON,
                "value", "Disabled because reasons")));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        var inOrder = inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        inOrder.verify(mockGatewayAccountEntity).setDisabledReason("Disabled because reasons");
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

}
