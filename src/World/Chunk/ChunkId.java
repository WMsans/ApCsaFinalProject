package World.Chunk;

import java.util.Objects;

/**
 * Represents the unique identifier for a Chunk using integer coordinates.
 * Includes equals and hashCode methods for use in collections like HashMap.
 */
public class ChunkId {
    public final int x, y, z;

    public ChunkId(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkId chunkId = (ChunkId) o;
        return x == chunkId.x && y == chunkId.y && z == chunkId.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "ChunkId{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}

