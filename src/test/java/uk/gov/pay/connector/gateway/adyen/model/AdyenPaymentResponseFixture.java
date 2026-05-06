package uk.gov.pay.connector.gateway.adyen.model;

import uk.gov.pay.connector.gateway.adyen.model.json.Action;

public class AdyenPaymentResponseFixture {
        String pspReference;
        String resultCode = "Authorised";
        String refusalReason;
        String refusalReasonCode;
        Action action;

        static AdyenPaymentResponseFixture anAdyenPaymentResponse() {
            return new AdyenPaymentResponseFixture();
        }

        public AdyenPaymentResponseFixture withPspReference(String pspReference) {
            this.pspReference = pspReference;
            return this;
        }

        public AdyenPaymentResponseFixture withResultCode(String resultCode) {
            this.resultCode = resultCode;
            return this;
        }

        public AdyenPaymentResponseFixture withRefusalReason(String refusalReason) {
            this.refusalReason = refusalReason;
            return this;
        }

        public AdyenPaymentResponseFixture withRefusalReasonCode(String refusalReasonCode) {
            this.refusalReasonCode = refusalReasonCode;
            return this;
        }

        public AdyenPaymentResponseFixture withAction(Action action) {
            this.action = action;
            return this;
        }

        public AdyenPaymentResponse build() {
            return new AdyenPaymentResponse(pspReference, resultCode, refusalReason, refusalReasonCode, action);
        }
    }
