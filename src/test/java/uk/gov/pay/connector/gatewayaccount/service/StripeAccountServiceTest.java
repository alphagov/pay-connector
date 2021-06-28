package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class StripeAccountServiceTest {

    private StripeAccountService stripeAccountService;

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Before
    public void setUp() {
        stripeAccountService = new StripeAccountService();
    }

    @Test
    public void buildStripeAccountResponseShouldReturnStripeAccountResponse() {
        String stripeAccountId = "acct_123example123";
        given(mockGatewayAccountEntity.getCredentials())
                .willReturn(Collections.singletonMap("stripe_account_id", stripeAccountId));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(mockGatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.get().getStripeAccountId(), is(stripeAccountId));
    }

    @Test
    public void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsIsNull() {
        given(mockGatewayAccountEntity.getCredentials()).willReturn(null);

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(mockGatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    @Test
    public void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsAreEmpty() {
        given(mockGatewayAccountEntity.getCredentials()).willReturn(Collections.emptyMap());

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(mockGatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    @Test
    public void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsPropertyIsNull() {
        given(mockGatewayAccountEntity.getCredentials())
                .willReturn(Collections.singletonMap("stripe_account_id", null));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(mockGatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

}
