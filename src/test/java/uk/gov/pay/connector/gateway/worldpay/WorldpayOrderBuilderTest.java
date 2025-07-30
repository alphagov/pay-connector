package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.SendWorldpayExemptionRequest.DO_NOT_SEND_EXEMPTION_REQUEST;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsServiceTest.WORLDPAY_ONE_OFF_CREDENTIALS;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class WorldpayOrderBuilderTest {
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
            3ds_enabled, send_payer_email_to_gateway, send_payer_ip_address_to_gateway, email_address, is_moto_payment, three_ds_flex_ddc_result, include_email, include_ip
            true, true, true, citizen@example.org, false, null, true, true
            true, true, true, null, false, null, false, true
            true, false, true, test@email.invalid, false, null, false, true
            true, true, false, citizen@example.org, false, null, true, false
            true, false, false, citizen@example.org, false, null, false, false
            false, true, true, citizen@example.org, false, null, false, false
            false, true, true, null, false, null, false, false
            false, true, true, citizen@example.org, false, three-ds-string, true, true
            false, true, true, citizen@example.org, true, three-ds-string, false, false
            true, true, true, citizen@example.org, true, three-ds-string, false, false
            """)
    void testVariationsOfSendPayerEmailAndSendPayerIPAddress(boolean is3dsEnabled, boolean sendEmail, boolean sendIPAddress, String emailAddress, 
                                                             boolean isMotoPayment, String threeDsFlexDdcResult, boolean includeEmail, boolean includeIP) {
        GatewayAccountEntity worldpayGatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .withRequires3ds(is3dsEnabled)
                .withSendPayerIpAddressToGateway(sendIPAddress)
                .withSendPayerEmailAddressToGateway(sendEmail)
                .build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(worldpayGatewayAccountEntity)
                .withCredentials(WORLDPAY_ONE_OFF_CREDENTIALS)
                .withState(ACTIVE)
                .build();
        worldpayGatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));
        ChargeEntity worldpayCharge = aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGatewayAccountEntity)
                .withPaymentProvider("worldpay")
                .withStatus(ChargeStatus.AUTHORISATION_READY)
                .withEmail(emailAddress)
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withMoto(isMotoPayment)
                .build();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardType(PayersCardType.CREDIT_OR_DEBIT)
                .withCorporateCard(Boolean.FALSE)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .withIpAddress(sendIPAddress ? "1.1.1.1" : null)
                .withWorldpay3dsFlexDdcResult(threeDsFlexDdcResult)
                .build();
        
        CardAuthorisationGatewayRequest request = CardAuthorisationGatewayRequest.valueOf(worldpayCharge, authCardDetails);

        var gatewayOrder = WorldpayOrderBuilder.buildAuthoriseOrder(request, DO_NOT_SEND_EXEMPTION_REQUEST, new AcceptLanguageHeaderParser()).build();
        
        assertThat(gatewayOrder.getOrderRequestType(), is(OrderRequestType.AUTHORISE));
        assertThat(gatewayOrder.getPayload().contains("shopperEmailAddress"), is(includeEmail));
        assertThat(gatewayOrder.getPayload().contains("shopperIPAddress"), is(includeIP));
    }
}
