package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordState;
import aqua.blatt1.common.ReferenceFish;
import aqua.blatt1.common.msgtypes.LocationRequest;
import aqua.blatt1.common.msgtypes.SnapshotCollector;
import aqua.blatt1.common.msgtypes.SnapshotMarker;
import aqua.blatt1.common.msgtypes.Token;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    public InetSocketAddress rightNeighbor;
    public InetSocketAddress leftNeighbor;
    protected boolean boolToken;
    Timer timer = new Timer();
    private RecordState recordState = RecordState.IDLE;
    public int localState;
    public boolean initiatorReady;
    ExecutorService executor = Executors.newFixedThreadPool(5);
    public volatile boolean hasCollector;
    public int globalValue;
    public volatile boolean showDialog;
    HashMap<String, ReferenceFish> stringReferenceFishHashMap = new HashMap<String, ReferenceFish>();

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
    }

    synchronized void onRegistration(String id) {
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
            stringReferenceFishHashMap.put(fish.getId(), ReferenceFish.HERE);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        if ((fish.getDirection() == Direction.LEFT && recordState == RecordState.RIGHT) || (fish.getDirection() == Direction.RIGHT && recordState == RecordState.LEFT) || recordState == RecordState.BOTH) {
            localState++;
        }

        fish.setToStart();
        fishies.add(fish);
        updateFishReferenceTankSet(fish, true);
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

    private synchronized void updateFishies() {
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

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public void updateNeighbors(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
        this.leftNeighbor = leftNeighbor;
        this.rightNeighbor = rightNeighbor;
    }

    public void receiveToken(Token token) {
        boolToken = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolToken = false;
                forwarder.sendToken(leftNeighbor, token);
            }
        }, 2000);
    }

    public void hasToken(FishModel fish) {
        if (boolToken) {
            updateFishReferenceTankSet(fish, false);
            forwarder.handOff(fish, leftNeighbor, rightNeighbor);
        } else {
            fish.reverse();
        }
    }

    public void initiateSnapshot() {
        if (recordState == RecordState.IDLE) {
            localState = fishies.size();
            recordState = RecordState.BOTH;
            initiatorReady = true;
        }
        forwarder.sendSnapshotMarker(leftNeighbor, new SnapshotMarker());
        forwarder.sendSnapshotMarker(rightNeighbor, new SnapshotMarker());
    }

    public void receiveSnapshotMarker(InetSocketAddress sender, SnapshotMarker snapshotMarker) { //Lamport/Chandy-Algorithmus
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
                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
            } else {
                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
                forwarder.sendSnapshotMarker(rightNeighbor, snapshotMarker);
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
            forwarder.sendSnapshotCollector(leftNeighbor, new SnapshotCollector(localState));
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
                        forwarder.sendSnapshotCollector(leftNeighbor, new SnapshotCollector(counter));
                        hasCollector = false;
                    }
                }
            });
        }
    }

    public void locateFishGlobally(String fishId) {
        ReferenceFish referenceFish = stringReferenceFishHashMap.get(fishId);

        if (referenceFish == ReferenceFish.HERE) { //Fisch befindet sich im Aquarium
            locateFishLocally(fishId);
        } else { //Fisch ist nach links oder rechts rausgeschwommen
            InetSocketAddress receiver = referenceFish == ReferenceFish.LEFT ? leftNeighbor : rightNeighbor;
            forwarder.sendLocationRequest(receiver, new LocationRequest(fishId));
        }
    }

    public void locateFishLocally(String fishId) { //fishies durchsuchen und FishModel.toggle()
        for (FishModel fish : this)
            if (fish.getId().equals(fishId)) {
                fish.toggle();
                break;
            }
    }

    public void updateFishReferenceTankSet(FishModel fishModel, boolean gotFish) {
        Direction direction = fishModel.getDirection();

        if (stringReferenceFishHashMap.containsKey(fishModel.getId())) {
            if (gotFish) {
                stringReferenceFishHashMap.replace(fishModel.getId(), ReferenceFish.HERE);
            } else {
                ReferenceFish referenceFish = direction == Direction.LEFT ? ReferenceFish.LEFT : ReferenceFish.RIGHT;
                stringReferenceFishHashMap.replace(fishModel.getId(), referenceFish);
            }
        } else {
            stringReferenceFishHashMap.put(fishModel.getId(), ReferenceFish.HERE);
        }
    }
}