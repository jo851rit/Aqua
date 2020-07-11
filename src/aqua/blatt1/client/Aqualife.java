package aqua.blatt1.client;

import aqua.blatt1.AquaBroker;
import aqua.blatt1.common.Properties;

import javax.swing.SwingUtilities;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Aqualife {

	public static void main(String[] args) throws RemoteException, NotBoundException, AlreadyBoundException {
		Registry registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
		AquaBroker aquaBroker = (AquaBroker) registry.lookup(Properties.BROKER_NAME);

		TankModel tankModel = new TankModel(aquaBroker);

		SwingUtilities.invokeLater(new AquaGui(tankModel));
		tankModel.run();
	}
}
