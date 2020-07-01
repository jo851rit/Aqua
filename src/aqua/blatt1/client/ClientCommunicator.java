package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import aqua.blatt1.SecureEndpointLaura;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

import javax.crypto.NoSuchPaddingException;

public class ClientCommunicator {
    private final SecureEndpointLaura endpoint;

    public ClientCommunicator() {
        endpoint = new SecureEndpointLaura();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish, InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
            Direction direction = fish.getDirection();
            InetSocketAddress receiverAddress;

            if (direction == Direction.LEFT) {
                receiverAddress = leftNeighbor;
            } else {
                receiverAddress = rightNeighbor;
            }

            endpoint.send(receiverAddress, new HandoffRequest(fish));
        }

        public void sendToken (InetSocketAddress leftNeighbor, Token token) {
            endpoint.send(leftNeighbor,token);
        }

        public void sendSnapshotMarker (InetSocketAddress receiver, SnapshotMarker snapshotMarker) {
            endpoint.send(receiver, snapshotMarker);
        }

        public void sendSnapshotCollector(InetSocketAddress receiver, SnapshotCollector snapshotCollector) {
            endpoint.send(receiver, snapshotCollector);
        }

        public void sendLocationRequest(InetSocketAddress receiver, LocationRequest locationRequest) {
            endpoint.send(receiver, locationRequest);
        }

        public void sendNameResolutionRequest(NameResolutionRequest nameResolutionRequest) {
            endpoint.send(broker, nameResolutionRequest);
        }
        public void sendLocationUpdate(InetSocketAddress receiver, LocationUpdate locationUpdate) {
            endpoint.send(receiver, locationUpdate);
        }

    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();

                if (msg.getPayload() instanceof RegisterResponse)
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId(), ((RegisterResponse) msg.getPayload()).getLeaseTime());

                if (msg.getPayload() instanceof HandoffRequest)
                    tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

                if (msg.getPayload() instanceof NeighborUpdate) {
                    tankModel.updateNeighbors(((NeighborUpdate) msg.getPayload()).getAddressLeft(), ((NeighborUpdate) msg.getPayload()).getAddressRight());
                }

                if (msg.getPayload() instanceof Token) {
                    tankModel.receiveToken((Token) msg.getPayload());
                }

                if (msg.getPayload() instanceof SnapshotMarker) {
                    tankModel.receiveSnapshotMarker(msg.getSender(), (SnapshotMarker) msg.getPayload());
                }

                if (msg.getPayload() instanceof SnapshotCollector) {
                    tankModel.handleSnapshotCollector((SnapshotCollector) msg.getPayload());
                }

                if (msg.getPayload() instanceof LocationRequest) {
                    tankModel.locateFishLocally(((LocationRequest) msg.getPayload()).getFishId());
                }

                if (msg.getPayload() instanceof NameResolutionResponse) {
                    tankModel.handleNameResolutionResponse(((NameResolutionResponse) msg.getPayload()).getTankAddress(), ((NameResolutionResponse) msg.getPayload()).getRequestId(), ((NameResolutionResponse) msg.getPayload()).getSender());
                }

                if (msg.getPayload() instanceof LocationUpdate) {
                    tankModel.handleLocationUpdate(((LocationUpdate) msg.getPayload()).getFishId(), ((LocationUpdate) msg.getPayload()).getInetSocketAddress());
                }

                if (msg.getPayload() instanceof Deregister) {
                    tankModel.handleDeregister();
                }
            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
