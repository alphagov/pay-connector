package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.DigitalWalletNotSupportedGatewayException;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResourceDTO;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountServiceTest {

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

    private GatewayAccountService gatewayAccountService;
    
    private static final Long GATEWAY_ACCOUNT_ID = 100L;

    @Before
    public void setUp() {
        gatewayAccountService = new GatewayAccountService(mockGatewayAccountDao, mockCardTypeDao);
        when(getMockGatewayAccountEntity1.getType()).thenReturn("test");
        when(getMockGatewayAccountEntity1.getServiceName()).thenReturn("service one");
        when(getMockGatewayAccountEntity2.getType()).thenReturn("test");
        when(getMockGatewayAccountEntity2.getServiceName()).thenReturn("service two");
    }

    @Test
    public void shouldGetGatewayAccount() {
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccountEntity> gatewayAccountEntity = gatewayAccountService.getGatewayAccount(GATEWAY_ACCOUNT_ID);

        assertThat(gatewayAccountEntity.get(), is(this.mockGatewayAccountEntity));
    }

    @Test
    public void shouldGetAllGatewayAccounts() {
        when(mockGatewayAccountDao.listAll()).thenReturn(Arrays.asList(getMockGatewayAccountEntity1, getMockGatewayAccountEntity2));
        List<GatewayAccountResourceDTO> gatewayAccounts = gatewayAccountService.getAllGatewayAccounts();

        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getServiceName(), is("service one"));
        assertThat(gatewayAccounts.get(1).getServiceName(), is("service two"));
    }

    @Test
    public void shouldGetGatewayAccountsByIds() {
        List<Long> accountIds = Arrays.asList(1L, 2L);
        when(mockGatewayAccountDao.list(accountIds)).thenReturn(Arrays.asList(getMockGatewayAccountEntity1, getMockGatewayAccountEntity2));

        List<GatewayAccountResourceDTO> gatewayAccounts = gatewayAccountService.getGatewayAccounts(accountIds);

        assertThat(gatewayAccounts, hasSize(2));
        assertThat(gatewayAccounts.get(0).getServiceName(), is("service one"));
        assertThat(gatewayAccounts.get(1).getServiceName(), is("service two"));
    }

    @Test
    public void shouldUpdateNotifySettingsWhenUpdate() {
        Map<String, String> settings = Map.of("api_token", "anapitoken",
                "template_id", "atemplateid");
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
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
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
                "path", "notify_settings")));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setNotifySettings(null);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateEmailCollectionMode() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
                "path", "email_collection_mode",
                "value", "off")));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);

        assertThat(optionalGatewayAccount.isPresent(), is(true));
        InOrder inOrder = Mockito.inOrder(mockGatewayAccountEntity, mockGatewayAccountDao);
        inOrder.verify(mockGatewayAccountEntity).setEmailCollectionMode(EmailCollectionMode.OFF);
        inOrder.verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }
    
    @Test
    public void shouldUpdateCorporateCreditCardSurchargeAmount() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
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
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
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
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 100)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setCorporatePrepaidDebitCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidCreditCardSurchargeAmount() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
                "path", "corporate_prepaid_credit_card_surcharge_amount",
                "value", 100)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setCorporatePrepaidCreditCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test(expected = DigitalWalletNotSupportedGatewayException.class)
    public void shouldNotAllowDigitalWalletForUnsupportedGateways() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "add",
                "path", "allow_apple_pay",
                "value", "true")));

        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("epdq");
        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(false));
    }

    @Test
    public void shouldUpdateAllowZeroAmountTrue() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
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
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of("op", "replace",
                "path", "allow_zero_amount",
                "value", false)));

        when(mockGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(mockGatewayAccountEntity));
        Optional<GatewayAccount> optionalGatewayAccount = gatewayAccountService.doPatch(GATEWAY_ACCOUNT_ID, request);
        assertThat(optionalGatewayAccount.isPresent(), is(true));
        verify(mockGatewayAccountEntity).setAllowZeroAmount(false);
        verify(mockGatewayAccountDao).merge(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateIntegrationVersion3ds() {
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(Map.of(
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
}
