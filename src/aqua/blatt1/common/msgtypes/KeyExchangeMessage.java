package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.security.PublicKey;

@SuppressWarnings("serial")
public final class KeyExchangeMessage implements Serializable {
    private final PublicKey publicKey;
    private final boolean tellMeYours;

    public KeyExchangeMessage(PublicKey publicKey, boolean tellMeYours) {
        this.publicKey = publicKey;
        this.tellMeYours = tellMeYours;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public boolean isTellMeYours() {
        return tellMeYours;
    }

}
