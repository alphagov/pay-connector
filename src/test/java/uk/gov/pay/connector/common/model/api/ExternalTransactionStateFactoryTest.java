package uk.gov.pay.connector.common.model.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExternalTransactionStateFactoryTest {

    @Mock
    private ChargeEntity mockChargeEntity;

    private final ExternalTransactionStateFactory factory = new ExternalTransactionStateFactory();

    @BeforeEach
    void setUp() {
        given(mockChargeEntity.getCanRetry()).willReturn(null);
    }

    @Test
    void createsExternalTransactionStateFromUnfinishedStatusWithoutCodeOrMessage() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.CREATED.toString());

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_CREATED.getStatus()));
        assertThat(result.isFinished(), is(false));
        assertThat(result.getCode(), is(nullValue()));
        assertThat(result.getMessage(), is(nullValue()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionStateFromFinishedStatusWithoutCodeOrMessage() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.CAPTURED.toString());

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_SUCCESS.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(nullValue()));
        assertThat(result.getMessage(), is(nullValue()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionStateFromStatusWithCodeAndMessageButNullCanRetry() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionThatHasNullCanRetryWhenAuthorisationModeAgreementAndNullCanRetry() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.AGREEMENT);
        given(mockChargeEntity.getCanRetry()).willReturn(null);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionThatHasCanRetryNullWhenAuthorisationModeWebAndCanRetryNull() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.WEB);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionThatHasCanRetryTrueWhenAuthorisationModeAgreementAndCanRetryTrue() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.AGREEMENT);
        given(mockChargeEntity.getCanRetry()).willReturn(true);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(true));
    }

    @Test
    void createsExternalTransactionThatHasCanRetryNullWhenAuthorisationModeWebAndCanRetryTrue() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.WEB);
        given(mockChargeEntity.getCanRetry()).willReturn(true);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

    @Test
    void createsExternalTransactionThatHasCanRetryFalseWhenAuthorisationModeAgreementAndCanRetryFalse() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.AGREEMENT);
        given(mockChargeEntity.getCanRetry()).willReturn(false);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(false));
    }

    @Test
    void createsExternalTransactionThatHasCanRetryNullWhenAuthorisationModeWebAndCanRetryFalse() {
        given(mockChargeEntity.getStatus()).willReturn(ChargeStatus.AUTHORISATION_REJECTED.toString());
        given(mockChargeEntity.getAuthorisationMode()).willReturn(AuthorisationMode.WEB);
        given(mockChargeEntity.getCanRetry()).willReturn(false);

        ExternalTransactionState result = factory.newExternalTransactionState(mockChargeEntity);

        assertThat(result.getStatus(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus()));
        assertThat(result.isFinished(), is(true));
        assertThat(result.getCode(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getCode()));
        assertThat(result.getMessage(), is(ExternalChargeState.EXTERNAL_FAILED_REJECTED.getMessage()));
        assertThat(result.getCanRetry(), is(nullValue()));
    }

}