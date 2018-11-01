package uk.gov.pay.connector.applepay;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.UUID;

public class AsymmetricKeyVerifier {
    private static final String SIGNATURE_ALGORITHM_NAME = "SHA256withECDSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    AsymmetricKeyVerifier(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    boolean verify() throws Exception {
        byte[] data = UUID.randomUUID().toString().getBytes();
        byte[] digitalSignature = signData(data);
        return verifySig(data, digitalSignature);
    }

    private byte[] signData(byte[] data) throws Exception {
        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM_NAME);
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }

    private boolean verifySig(byte[] data, byte[] sig) throws Exception {
        Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM_NAME);
        signer.initVerify(publicKey);
        signer.update(data);
        return signer.verify(sig);
    }
}
