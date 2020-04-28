package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
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
    boolean stopRequested = false;

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
        }
    }

    public void broker() {
        executor.execute(() -> {

            JFrame f = new JFrame();
            JOptionPane.showMessageDialog(f, "Press OK button to stop server");
            stopRequested = true;

        });
        while (!stopRequested) {
            Message msg = endpoint.blockingReceive();
            BrokerTask brokerTask = new BrokerTask();
            executor.execute(() -> brokerTask.brokerTask(msg));
        }
    }

    public void register(Message msg) {
        String id = "tank" + counter;
        counter++;
//      add tank to ClientCollection
        clientCollection.add(id, msg.getSender());
//      send message
        endpoint.send(msg.getSender(), new RegisterResponse(id));
    }

    public void deregister(Message msg) {
//      remove tank from list
        clientCollection.remove(clientCollection.indexOf(((DeregisterRequest) msg.getPayload()).getId()));
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