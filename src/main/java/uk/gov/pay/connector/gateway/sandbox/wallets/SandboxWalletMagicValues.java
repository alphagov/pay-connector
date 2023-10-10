package uk.gov.pay.connector.gateway.sandbox.wallets;

public enum SandboxWalletMagicValues {
    REFUSED(""),
    DECLINED(""),
    ERROR("This transaction could be not be processed.");

    public final String errorMessage;

    SandboxWalletMagicValues(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static SandboxWalletMagicValues magicValueFromString(String str) {
        for (SandboxWalletMagicValues value : SandboxWalletMagicValues.values()) {
            if (value.name().equalsIgnoreCase(str)) {
                return value;
            }
        }
        return null;
    }
}
