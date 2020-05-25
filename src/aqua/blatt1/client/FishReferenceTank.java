package aqua.blatt1.client;

import aqua.blatt1.common.ReferenceFish;

import java.util.Objects;

public class FishReferenceTank {
    String id;
    ReferenceFish referenceFish;

    public FishReferenceTank(String id, ReferenceFish referenceFish) {
        this.id = id;
        this.referenceFish = referenceFish;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReferenceFish getReferenceFish() {
        return referenceFish;
    }

    public void setReferenceFish(ReferenceFish referenceFish) {
        this.referenceFish = referenceFish;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FishReferenceTank that = (FishReferenceTank) o;
        return Objects.equals(id, that.id);
    }
}
