package aqua.blatt1.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SnapshotController implements ActionListener {
	private TankModel tankModel;

	public SnapshotController(TankModel tankModel) {
		this.tankModel = tankModel;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		tankModel.initiateSnapshot();
	}
}
