package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }

    Endpoint endpoint;
    ClientCollection clientCollection;
    int counter = 0;
    int NUMTHREADS = 5;
    ExecutorService executor;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    volatile boolean stopRequested = false;

    public Broker() {
        endpoint = new Endpoint(4711);
        clientCollection = new ClientCollection();
        executor = Executors.newFixedThreadPool(NUMTHREADS);
    }

    private class BrokerTask {
        public void brokerTask(Message msg) {
            if (msg.getPayload() instanceof RegisterRequest) {
                synchronized (clientCollection) {
                    register(msg);
                }
            }

            if (msg.getPayload() instanceof DeregisterRequest) {
                synchronized (clientCollection) {
                    deregister(msg);
                }
            }

            if (msg.getPayload() instanceof HandoffRequest) {
                lock.writeLock().lock();
                handoffFish(msg);
                lock.writeLock().unlock();
            }

            if (msg.getPayload() instanceof PoisonPill){
                System.exit(0);
            }

            if (msg.getPayload() instanceof NameResolutionRequest) {
                handleNameResolutionRequest(msg);
            }
        }
    }

    public void broker() {
        executor.execute(() -> {

            JOptionPane.showMessageDialog(null, "Press OK button to stop server");
            stopRequested = true;

        });
        while (!stopRequested) {
            Message msg = endpoint.blockingReceive();
            BrokerTask brokerTask = new BrokerTask();
            executor.execute(() -> brokerTask.brokerTask(msg));
        }
        executor.shutdown();
    }

    public void register(Message msg) {
        String id = "tank" + counter;
        counter++;
        InetSocketAddress newTankAddress = msg.getSender();

//      add tank to ClientCollection
        clientCollection.add(id, newTankAddress);
        int newTankAddressIndex = clientCollection.indexOf(newTankAddress);
        InetSocketAddress leftNeighborAddress = (InetSocketAddress) clientCollection.getLeftNeighorOf(newTankAddressIndex);
        InetSocketAddress rightNeighborAddress = (InetSocketAddress) clientCollection.getRightNeighorOf(newTankAddressIndex);

        InetSocketAddress leftOfLeftNeighbor = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(leftNeighborAddress));
        InetSocketAddress rightOfRightNeighbor = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(rightNeighborAddress));

//      send messages
        if (clientCollection.size() == 1) {
            endpoint.send(newTankAddress, new NeighborUpdate(newTankAddress, newTankAddress));
            endpoint.send(newTankAddress, new Token());
        } else {
            endpoint.send(newTankAddress, new NeighborUpdate(leftNeighborAddress, rightNeighborAddress));
            endpoint.send(leftNeighborAddress, new NeighborUpdate(leftOfLeftNeighbor, newTankAddress));
            endpoint.send(rightNeighborAddress, new NeighborUpdate(newTankAddress, rightOfRightNeighbor));
        }

        endpoint.send(newTankAddress, new RegisterResponse(id));
    }

    public void deregister(Message msg) {
        String removeId = ((DeregisterRequest) msg.getPayload()).getId();

        InetSocketAddress leftNeighborAddress = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(removeId));
        InetSocketAddress rightNeighborAddress = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(removeId));

        int leftNeighborIndex = clientCollection.indexOf(clientCollection.getLeftNeighorOf(clientCollection.indexOf(removeId)));
        int rightNeighborIndex = clientCollection.indexOf(clientCollection.getRightNeighorOf(clientCollection.indexOf(removeId)));

        InetSocketAddress leftOfLeftNeighbor = (InetSocketAddress) clientCollection.getLeftNeighorOf(leftNeighborIndex);
        InetSocketAddress rightOfRightNeighbor = (InetSocketAddress) clientCollection.getRightNeighorOf(rightNeighborIndex);

        if (clientCollection.size() == 2) {
            endpoint.send(leftNeighborAddress, new NeighborUpdate(leftNeighborAddress, leftNeighborAddress));
        } else {
            endpoint.send(leftNeighborAddress, new NeighborUpdate(leftOfLeftNeighbor, rightNeighborAddress));
            endpoint.send(rightNeighborAddress, new NeighborUpdate(leftNeighborAddress, rightOfRightNeighbor));
        }

//      remove tank from list
        clientCollection.remove(clientCollection.indexOf(removeId));
    }

    public void handoffFish(Message msg) {
        Direction direction = ((HandoffRequest) msg.getPayload()).getFish().getDirection();
        InetSocketAddress receiverAddress;
        int index = clientCollection.indexOf(msg.getSender());
        if (direction == Direction.LEFT) {
            receiverAddress = (InetSocketAddress) clientCollection.getLeftNeighorOf(index);
        } else {
            receiverAddress = (InetSocketAddress) clientCollection.getRightNeighorOf(index);
        }

        endpoint.send(receiverAddress, msg.getPayload());
    }

    public void handleNameResolutionRequest(Message msg) {
        String tankId = ((NameResolutionRequest) msg.getPayload()).getTankId();
        String requestId = ((NameResolutionRequest) msg.getPayload()).getRequestId();

        int indexOf = clientCollection.indexOf(tankId);
        InetSocketAddress tankAddress = (InetSocketAddress) clientCollection.getClient(indexOf);

        endpoint.send(msg.getSender(), new NameResolutionResponse(tankAddress, requestId, msg.getSender()));
    }
}