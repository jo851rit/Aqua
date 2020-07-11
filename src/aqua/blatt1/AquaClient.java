package aqua.blatt1;

import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import messaging.Message;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AquaClient extends Remote {

    void onRegistration(String id, long leaseTime) throws RemoteException;

    void receiveFish(FishModel fish) throws RemoteException;

    void updateNeighbors(AquaClient leftNeighbor, AquaClient rightNeighbor) throws RemoteException;

    void receiveToken(Token token) throws RemoteException;

    void receiveSnapshotMarker(AquaClient sender, SnapshotMarker snapshotMarker) throws RemoteException;

    void handleSnapshotCollector(SnapshotCollector snapshotCollector) throws RemoteException;

    void locateFishLocally(String fishId) throws RemoteException;

    void handleNameResolutionResponse(AquaClient homeAddress, String fishId, AquaClient sender) throws RemoteException;

    void handleLocationUpdate(String fishId, AquaClient currentTank) throws RemoteException;
}
