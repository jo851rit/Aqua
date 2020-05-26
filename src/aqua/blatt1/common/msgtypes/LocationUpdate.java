package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class LocationUpdate implements Serializable {
    private final String fishId;
    private final InetSocketAddress inetSocketAddress;

    public LocationUpdate(String fishId, InetSocketAddress inetSocketAddress) {
        this.fishId = fishId;
        this.inetSocketAddress = inetSocketAddress;
    }

    public String getFishId() {
        return fishId;
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }
}
