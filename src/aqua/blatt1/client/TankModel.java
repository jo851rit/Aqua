package aqua.blatt1.client;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.AquaBroker;
import aqua.blatt1.AquaClient;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordState;
import aqua.blatt1.common.msgtypes.*;

public class TankModel extends Observable implements Iterable<FishModel>, AquaClient {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    //    protected final ClientCommunicator.ClientForwarder forwarder;
    public AquaClient rightNeighbor;
    public AquaClient leftNeighbor;
    protected boolean boolToken;
    Timer timer = new Timer();
    private RecordState recordState = RecordState.IDLE;
    public int localState;
    public boolean initiatorReady;
    ExecutorService executor = Executors.newFixedThreadPool(5);
    public volatile boolean hasCollector;
    public int globalValue;
    public volatile boolean showDialog;
    HashMap<String, AquaClient> homeAgent = new HashMap<String, AquaClient>();
    final AquaBroker aquaBroker;
    AquaClient aquaClientStub;

    public TankModel(AquaBroker aquaBroker) throws RemoteException {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.aquaBroker = aquaBroker;
        aquaClientStub = (AquaClient)
                UnicastRemoteObject.exportObject(this, 0);
    }

    public synchronized void onRegistration(String id, long leaseTime) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    aquaBroker.register(aquaClientStub);
                } catch (RemoteException | AlreadyBoundException e) {
                    e.printStackTrace();
                }
            }
        }, leaseTime - 3000);

        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
            homeAgent.put(fish.getId(), null);
        }
    }

    public void receiveFish(FishModel fish) throws RemoteException {
        if ((fish.getDirection() == Direction.LEFT && recordState == RecordState.RIGHT) || (fish.getDirection() == Direction.RIGHT && recordState == RecordState.LEFT) || recordState == RecordState.BOTH) {
            localState++;
        }

        fish.setToStart();
        fishies.add(fish);
        updateHomeAgent(fish);
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() throws RemoteException {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();
            if (fish.hitsEdge())
                hasToken(fish);

            if (fish.disappears()) {
                it.remove();
            }
        }
    }

    private synchronized void update() throws RemoteException {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() throws RemoteException, AlreadyBoundException {
        aquaBroker.register(aquaClientStub);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException | RemoteException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() throws RemoteException {
        aquaBroker.deregister(id);
    }

    public void updateNeighbors(AquaClient leftNeighbor, AquaClient rightNeighbor) {
        this.leftNeighbor = leftNeighbor;
        this.rightNeighbor = rightNeighbor;
    }

    public void receiveToken(Token token) {
        boolToken = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolToken = false;
                try {
                    leftNeighbor.receiveToken(token);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }, 2000);
    }

    public void hasToken(FishModel fish) throws RemoteException {
        if (boolToken) {
            Direction direction = fish.getDirection();
            AquaClient receiverAddress;
            if (direction == Direction.LEFT) {
                receiverAddress = leftNeighbor;
            } else {
                receiverAddress = rightNeighbor;
            }
            receiverAddress.receiveFish(fish);
        } else {
            fish.reverse();
        }
    }

    public void initiateSnapshot() throws RemoteException {
        if (recordState == RecordState.IDLE) {
            localState = fishies.size();
            recordState = RecordState.BOTH;
            initiatorReady = true;
        }
        leftNeighbor.receiveSnapshotMarker(aquaClientStub, new SnapshotMarker());
        rightNeighbor.receiveSnapshotMarker(aquaClientStub, new SnapshotMarker());
    }

    public void receiveSnapshotMarker(AquaClient sender, SnapshotMarker snapshotMarker) throws RemoteException { //Lamport/Chandy-Algorithmus
//        falls sich nicht im aufzeichnungsmodus befindet
        if (recordState == RecordState.IDLE) {
//            speichere den Zustand von P
            localState = fishies.size();

//            starte den Aufzeichnungsmodus für alle anderen Eingangskanäle
            if (!leftNeighbor.equals(rightNeighbor)) {
                if (sender.equals(leftNeighbor)) {
                    recordState = RecordState.RIGHT;
                } else if (sender.equals(rightNeighbor)) {
                    recordState = RecordState.LEFT;
                }
            } else {
                recordState = RecordState.BOTH;
            }
//        sende markierung an alle ausgangskanäle
            if (leftNeighbor.equals(rightNeighbor)) {
                leftNeighbor.receiveSnapshotMarker(aquaClientStub, snapshotMarker);
            } else {
                leftNeighbor.receiveSnapshotMarker(aquaClientStub, snapshotMarker);
                rightNeighbor.receiveSnapshotMarker(aquaClientStub, snapshotMarker);
            }
        } else {
            if (!leftNeighbor.equals(rightNeighbor)) {
                if (sender.equals(leftNeighbor)) {
                    if (recordState == RecordState.BOTH) {
                        recordState = RecordState.RIGHT;
                    } else if (recordState == RecordState.LEFT) {
                        recordState = RecordState.IDLE;
                    }
                } else if (sender.equals(rightNeighbor)) {
                    if (recordState == RecordState.BOTH) {
                        recordState = RecordState.LEFT;
                    } else if (recordState == RecordState.RIGHT) {
                        recordState = RecordState.IDLE;
                    }
                }
            } else {
                recordState = RecordState.IDLE;
            }
        }
        if (initiatorReady && recordState == RecordState.IDLE) {
            leftNeighbor.handleSnapshotCollector(new SnapshotCollector(localState));
        }

    }

    public void handleSnapshotCollector(SnapshotCollector snapshotCollector) {
        if (initiatorReady) {
            initiatorReady = false;
            globalValue = snapshotCollector.getCounter();
            showDialog = true;
        } else {
            hasCollector = true;
            executor.execute(() -> {
                while (hasCollector) {
                    if (recordState == RecordState.IDLE) {
                        int counter = snapshotCollector.getCounter() + localState;
                        try {
                            leftNeighbor.handleSnapshotCollector(new SnapshotCollector(counter));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        hasCollector = false;
                    }
                }
            });
        }
    }

    public void updateHomeAgent(FishModel fish) throws RemoteException {
        if (homeAgent.containsKey(fish.getId())) {
            homeAgent.replace(fish.getId(), null);
        } else {
            aquaBroker.handleNameResolutionRequest(fish.getTankId(), fish.getId(), aquaClientStub);
        }
    }

    public void locateFishGlobally(String fishId) throws RemoteException {
        AquaClient inetSocketAddress = homeAgent.get(fishId);

        if (inetSocketAddress == null) { //Fisch befindet sich im Aquarium
            locateFishLocally(fishId);
        } else { //Fisch ist nach links oder rechts rausgeschwommen
            inetSocketAddress.locateFishLocally(fishId);
        }
    }

    public void locateFishLocally(String fishId) { //fishies durchsuchen und FishModel.toggle()
        for (FishModel fish : this)
            if (fish.getId().equals(fishId)) {
                fish.toggle();
                break;
            }
    }

    public void handleNameResolutionResponse(AquaClient homeAddress, String fishId, AquaClient sender) throws RemoteException {
        homeAddress.handleLocationUpdate(id, sender);
    }

    public void handleLocationUpdate(String fishId, AquaClient currentTank) {
        homeAgent.replace(fishId, currentTank);
    }

    public void handleDeregister() throws RemoteException {
        aquaBroker.deregister(this.id);
        System.exit(0);
    }
}