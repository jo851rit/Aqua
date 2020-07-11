package aqua.blatt1.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;

public class SnapshotController implements ActionListener {
	private TankModel tankModel;

	public SnapshotController(TankModel tankModel) {
		this.tankModel = tankModel;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			tankModel.initiateSnapshot();
		} catch (RemoteException remoteException) {
			remoteException.printStackTrace();
		}
	}
}
