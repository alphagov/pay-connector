package uk.gov.pay.connector.charge.util;


import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;

class StripeFeeCalculatorTest {

    private CaptureGatewayRequest request;
    private ChargeEntity charge;
    private Long stripeFee = 10L;
    private Double feePercentage = 0.09;

    @BeforeEach
    void setUp() {
        charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(100000L)
                .build();
        request = CaptureGatewayRequest.valueOf(charge);
    }

    @Test
    void shouldReturnCorrectConnectFee() {
        Long result = StripeFeeCalculator.getTotalAmountForConnectFee(stripeFee, request, feePercentage);
        assertThat(result, is(100L));
    }

    @Test
    void shouldReturnCorrectFeeForV2No3dsCharge() {
        Long result = StripeFeeCalculator.getTotalAmountForV2(stripeFee, request, feePercentage, 5, 10);
        assertThat(result, is(105L));
    }

    @Test
    void shouldReturnCorrectFeeForV2With3dsCharge() {
        charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(100000L)
                .withEvents(List.of(aChargeEventEntity()
                        .withStatus(AUTHORISATION_3DS_REQUIRED).build()))
                .build();
        request = CaptureGatewayRequest.valueOf(charge);
        Long result = StripeFeeCalculator.getTotalAmountForV2(stripeFee, request, feePercentage, 5, 10);
        assertThat(result, is(115L));
    }

    @Test
    void shouldReturnFeeList() {
        charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(100000L)
                .withEvents(List.of(aChargeEventEntity()
                        .withStatus(AUTHORISATION_3DS_REQUIRED).build()))
                .build();
        request = CaptureGatewayRequest.valueOf(charge);
        List<Fee> feeList = StripeFeeCalculator.getFeeList(stripeFee, request, feePercentage, 5, 10);
        assertThat(feeList.size(), is(3));
        assertThat(feeList.get(0).getFeeType(), Is.is(FeeType.TRANSACTION));
        assertThat(feeList.get(0).getAmount(), Is.is(100L));
        assertThat(feeList.get(1).getFeeType(), Is.is(FeeType.RADAR));
        assertThat(feeList.get(1).getAmount(), Is.is(5L));
        assertThat(feeList.get(2).getFeeType(), Is.is(FeeType.THREE_D_S));
        assertThat(feeList.get(2).getAmount(), Is.is(10L));
    }
}