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
        private static AppleDecryptedPaymentData buildDecryptedPaymentData(String cardHolderName, String email, String lastFourDigitsCardNumber, AppleDecryptedPaymentData.PaymentData paymentData) {
            return new AppleDecryptedPaymentData(
                    new ApplePaymentInfo(
                            lastFourDigitsCardNumber,
                            "visa",
                            PayersCardType.DEBIT,
                            cardHolderName,
                            email
                    ),
                    "4818528840010767",
                    LocalDate.of(2023, 12, 31),
                    "643",
                    10L,
                    "040010030273",
                    "3DSecure",
                    paymentData
            );
        }

        public static AppleDecryptedPaymentData buildDecryptedPaymentData(String cardHolderName, String email, String lastFourDigitsCardNumber) {
            return buildDecryptedPaymentData(cardHolderName, email, lastFourDigitsCardNumber, new AppleDecryptedPaymentData.PaymentData(
                    "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                    "7"
            ));
        }
        public static AppleDecryptedPaymentData buildDecryptedMinimalPaymentData(String lastFourDigitsCardNumber) {
            return buildDecryptedPaymentData(null, null, lastFourDigitsCardNumber, new AppleDecryptedPaymentData.PaymentData(
                    "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                    null
            ));
        }
    }

}
