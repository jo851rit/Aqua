package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NameResolutionResponse implements Serializable {
    private final InetSocketAddress tankAddress;
    private final String requestId;
    private final InetSocketAddress sender;

    public NameResolutionResponse(InetSocketAddress tankAddress, String requestId, InetSocketAddress sender) {
        this.tankAddress = tankAddress;
        this.requestId = requestId;
        this.sender = sender;
    }

    public String getRequestId() {
        return requestId;
    }

    public InetSocketAddress getTankAddress() {
        return tankAddress;
    }

    public InetSocketAddress getSender() {
        return sender;
    }
}
