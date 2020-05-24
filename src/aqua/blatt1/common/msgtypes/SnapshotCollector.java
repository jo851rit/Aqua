package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class SnapshotCollector implements Serializable {
    public int counter;

    public SnapshotCollector(int localCount) {
        this.counter = this.counter + localCount;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }
}
