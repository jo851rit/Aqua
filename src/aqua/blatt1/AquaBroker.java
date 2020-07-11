package aqua.blatt1;

import java.rmi.AlreadyBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AquaBroker extends Remote {
    void register(AquaClient aquaClient) throws RemoteException, AlreadyBoundException;

    void deregister(String id) throws RemoteException;

    void handleNameResolutionRequest(String tankId, String id, AquaClient aquaClient) throws RemoteException;
}
