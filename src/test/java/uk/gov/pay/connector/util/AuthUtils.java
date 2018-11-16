package uk.gov.pay.connector.util;

import uk.gov.pay.connector.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.applepay.api.ApplePaymentInfo;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;

import java.time.LocalDate;

public class AuthUtils {
    public static Auth3dsDetails buildAuth3dsDetails() {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("sample-pa-response");
        return auth3dsDetails;
    }


    public static class ApplePay {
        private static AppleDecryptedPaymentData buildDecryptedPaymentData(String cardHolderName, String email, String tokenNumber, AppleDecryptedPaymentData.PaymentData paymentData) {
            return new AppleDecryptedPaymentData(
                    new ApplePaymentInfo(
                            "4242",
                            "visa",
                            PayersCardType.DEBIT,
                            cardHolderName,
                            email
                    ),
                    tokenNumber,
                    LocalDate.of(2023, 12, 31),
                    "643",
                    10L,
                    "040010030273",
                    "3DSecure",
                    paymentData
            );
        }

        public static AppleDecryptedPaymentData buildDecryptedPaymentData(String cardHolderName, String email, String tokenNumber) {
            return buildDecryptedPaymentData(cardHolderName, email, tokenNumber, new AppleDecryptedPaymentData.PaymentData(
                    "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                    "7"
            ));
        }
        public static AppleDecryptedPaymentData buildDecryptedMinimalPaymentData(String tokenNumber) {
            return buildDecryptedPaymentData(null, null, tokenNumber, new AppleDecryptedPaymentData.PaymentData(
                    "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                    null
            ));
        }
    }

}
