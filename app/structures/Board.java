package structures;

import structures.basic.Position;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


public class Board {

    private final int width;
    private final int height;

    private final Map<String, UnitEntity> occupancy = new HashMap<>();

    public Board(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Board dimensions must be positive.");
        }
        this.width = width;
        this.height = height;
    }

    public boolean isWithinBounds(Position p) {
        Objects.requireNonNull(p, "Position cannot be null.");
        int x = p.getTilex();
        int y = p.getTiley();
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isValidPosition(Position p) {
        return isWithinBounds(p);
    }

    public boolean isOccupied(Position p) {
        requireValid(p);
        return occupancy.containsKey(key(p));
    }

    public Optional<UnitEntity> getUnitAt(Position p) {
        requireValid(p);
        return Optional.ofNullable(occupancy.get(key(p)));
    }

    public void putUnit(Position p, UnitEntity unit) {
        requireValid(p);
        Objects.requireNonNull(unit, "UnitEntity cannot be null.");

        if (isOccupied(p)) {
            throw new IllegalStateException("Tile already occupied: " + key(p));
        }

        occupancy.put(key(p), unit);
    }

    public void moveUnit(Position from, Position to) {
        requireValid(from);
        requireValid(to);

        String fromKey = key(from);
        String toKey = key(to);

        UnitEntity unit = occupancy.get(fromKey);
        if (unit == null) {
            throw new IllegalStateException("No unit at source tile: " + fromKey);
        }

        if (occupancy.containsKey(toKey)) {
            throw new IllegalStateException("Target tile occupied: " + toKey);
        }

        occupancy.remove(fromKey);
        occupancy.put(toKey, unit);
    }

    public void removeUnit(Position p) {
        requireValid(p);
        occupancy.remove(key(p));
    }

    private void requireValid(Position p) {
        if (!isValidPosition(p)) {
            throw new IllegalArgumentException(
                    "Invalid tile position (0-based): (" + p.getTilex() + "," + p.getTiley() + "). " +
                            "Expected x in [0.." + (width-1) + "], y in [1.." + height + "]."
            );
        }
    }

    private String key(Position p) {
        return p.getTilex() + "," + p.getTiley();
    }
}