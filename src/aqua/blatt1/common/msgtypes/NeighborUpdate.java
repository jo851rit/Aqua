package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NeighborUpdate implements Serializable {
    private final InetSocketAddress addressLeft;
    private final InetSocketAddress addressRight;

    public NeighborUpdate(InetSocketAddress addressLeft, InetSocketAddress addressRight) {
        this.addressLeft = addressLeft;
        this.addressRight = addressRight;
    }

    public InetSocketAddress getAddressLeft() {
        return addressLeft;
    }

    public InetSocketAddress getAddressRight() {
        return addressRight;
    }
}
