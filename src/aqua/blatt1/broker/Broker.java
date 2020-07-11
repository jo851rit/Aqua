package aqua.blatt1.broker;

import aqua.blatt1.AquaBroker;
import aqua.blatt1.AquaClient;
import aqua.blatt1.SecureEndpointLaura;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker implements AquaBroker {
    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        Broker broker = new Broker();
        Registry registry = LocateRegistry.createRegistry(
                Registry.REGISTRY_PORT);
        AquaBroker stub = (AquaBroker)
                UnicastRemoteObject.exportObject(broker, 0);
        registry.bind(Properties.BROKER_NAME, stub);
    }

    SecureEndpointLaura endpoint;
    ClientCollection clientCollection;
    int counter = 0;
    int NUMTHREADS = 5;
    ExecutorService executor;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    volatile boolean stopRequested = false;
    Timer timer = new Timer();
    long leaseTime = 10000;

    public Broker() {
        endpoint = new SecureEndpointLaura(Properties.PORT);
        clientCollection = new ClientCollection();
        executor = Executors.newFixedThreadPool(NUMTHREADS);
    }

    public void register(AquaClient aquaClient) throws RemoteException, AlreadyBoundException {
        int indexOfClient = clientCollection.indexOf(aquaClient);
        long date = System.currentTimeMillis();
        String id;
        if (indexOfClient == -1) {
            id = "tank" + counter;
            counter++;

            Registry registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
            registry.bind(id, aquaClient);

//      add tank to ClientCollection
            clientCollection.add(id, aquaClient, date);
            int newTankAddressIndex = clientCollection.indexOf(aquaClient);
            AquaClient leftNeighbor = (AquaClient) clientCollection.getLeftNeighorOf(newTankAddressIndex);
            AquaClient rightNeighbor = (AquaClient) clientCollection.getRightNeighorOf(newTankAddressIndex);

            AquaClient leftOfLeftNeighbor = (AquaClient) clientCollection.getLeftNeighorOf(clientCollection.indexOf(leftNeighbor));
            AquaClient rightOfRightNeighbor = (AquaClient) clientCollection.getRightNeighorOf(clientCollection.indexOf(rightNeighbor));

//      send messages
            if (clientCollection.size() == 1) {
                aquaClient.updateNeighbors(aquaClient, aquaClient);
                aquaClient.receiveToken(new Token());
            } else {
                aquaClient.updateNeighbors(leftNeighbor,rightNeighbor);
                leftNeighbor.updateNeighbors(leftOfLeftNeighbor, aquaClient);
                rightNeighbor.updateNeighbors(aquaClient, rightOfRightNeighbor);
            }
        } else {
            id = clientCollection.updateClient(indexOfClient, date);
        }
        aquaClient.onRegistration(id, leaseTime);

    }

    public void deregister(String id) throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
        registry.unbind(id);

        AquaClient leftNeighborAddress = (AquaClient) clientCollection.getLeftNeighorOf(clientCollection.indexOf(id));
        AquaClient rightNeighborAddress = (AquaClient) clientCollection.getRightNeighorOf(clientCollection.indexOf(id));

        int leftNeighborIndex = clientCollection.indexOf(clientCollection.getLeftNeighorOf(clientCollection.indexOf(id)));
        int rightNeighborIndex = clientCollection.indexOf(clientCollection.getRightNeighorOf(clientCollection.indexOf(id)));

        AquaClient leftOfLeftNeighbor = (AquaClient) clientCollection.getLeftNeighorOf(leftNeighborIndex);
        AquaClient rightOfRightNeighbor = (AquaClient) clientCollection.getRightNeighorOf(rightNeighborIndex);

        if (clientCollection.size() == 2) {
            leftNeighborAddress.updateNeighbors(leftNeighborAddress, leftNeighborAddress);
        } else {
            leftNeighborAddress.updateNeighbors(leftOfLeftNeighbor, rightNeighborAddress);
            rightNeighborAddress.updateNeighbors(leftNeighborAddress, rightOfRightNeighbor);
        }

//      remove tank from list
        clientCollection.remove(clientCollection.indexOf(id));
    }

    public void handleNameResolutionRequest(String tankId, String id, AquaClient aquaClient) throws RemoteException {
        int indexOf = clientCollection.indexOf(tankId);
        AquaClient tankAddress = (AquaClient) clientCollection.getClient(indexOf);
        aquaClient.handleNameResolutionResponse(tankAddress, id, aquaClient);
    }
}