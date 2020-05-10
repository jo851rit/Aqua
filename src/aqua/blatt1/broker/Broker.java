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
//      add tank to ClientCollection
        clientCollection.add(id, msg.getSender());

        InetSocketAddress leftNeighbor = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(id));
        InetSocketAddress rightNeighbor = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(id));

//      send message
        endpoint.send(msg.getSender(), new RegisterResponse(id));
        endpoint.send(msg.getSender(), new NeighborUpdate(leftNeighbor, rightNeighbor));
    }

    public void deregister(Message msg) {
        String removeId = ((DeregisterRequest) msg.getPayload()).getId();

        InetSocketAddress leftNeighborAddress = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(removeId));
        InetSocketAddress rightNeighborAddress = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(removeId));

        int leftNeighborIndex = clientCollection.indexOf(clientCollection.getLeftNeighorOf(clientCollection.indexOf(removeId)));
        int rightNeighborIndex = clientCollection.indexOf(clientCollection.getRightNeighorOf(clientCollection.indexOf(removeId)));

        InetSocketAddress leftOfLeftNeighbor = (InetSocketAddress) clientCollection.getLeftNeighorOf(leftNeighborIndex);
        InetSocketAddress rightOfRightNeighbor = (InetSocketAddress) clientCollection.getRightNeighorOf(rightNeighborIndex);

//        System.out.println("leftNeighborAddress :" + leftNeighborAddress );
//        System.out.println("rightNeighborAddress :" + rightNeighborAddress );
//        System.out.println("leftOfLeftNeighbor :" + leftOfLeftNeighbor );
//        System.out.println("rightOfRightNeighbor :" + rightOfRightNeighbor );

//      remove tank from list
        clientCollection.remove(clientCollection.indexOf(removeId));

        endpoint.send(leftNeighborAddress, new NeighborUpdate(leftOfLeftNeighbor, rightNeighborAddress));
        endpoint.send(rightNeighborAddress, new NeighborUpdate(leftNeighborAddress, rightOfRightNeighbor));
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
}