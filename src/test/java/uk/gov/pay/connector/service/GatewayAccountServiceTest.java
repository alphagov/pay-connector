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
        long gatewayAccountId = 42;

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(mockGatewayAccountEntity));

        Optional<GatewayAccountEntity> gatewayAccountEntity = gatewayAccountService.getGatewayAccount(gatewayAccountId);

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
        Long gatewayAccountId = 100L;
        Map<String, String> settings = ImmutableMap.of("api_token", "anapitoken",
                "template_id", "atemplateid");
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", settings)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setNotifySettings(settings);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateNotifySettingsWhenRemove() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "notify_settings")));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setNotifySettings(null);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateEmailCollectionMode() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "email_collection_mode",
                "value", "off")));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        InOrder inOrder = Mockito.inOrder(entity, mockGatewayAccountDao);
        inOrder.verify(entity).setEmailCollectionMode(EmailCollectionMode.OFF);
        inOrder.verify(mockGatewayAccountDao).merge(entity);
    }
    
    @Test
    public void shouldUpdateCorporateCreditCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporateCreditCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporateDebitCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_debit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporateDebitCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidDebitCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporatePrepaidDebitCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidCreditCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_credit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporatePrepaidCreditCardSurchargeAmount(100L);
        verify(mockGatewayAccountDao).merge(entity);
    }

    @Test(expected = DigitalWalletNotSupportedGatewayException.class)
    public void shouldNotAllowDigitalWalletForUnsupportedGateways() {
        Long gatewayAccountId = 100L;
        JsonPatchRequest request = JsonPatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "add",
                "path", "allow_apple_pay",
                "value", "true")));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);
        when(entity.getGatewayName()).thenReturn("epdq");
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = gatewayAccountService.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(false));
    }
}
