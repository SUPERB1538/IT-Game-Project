package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import structures.basic.Tile;
import structures.basic.UnitAnimationType;

import java.util.ArrayList;
import java.util.List;

/**
 * Movement logic extracted from GameRulesEngine.
 */
public class MovementService {

    private final CommandDispatcher ui = new CommandDispatcher();

    public List<Position> computeDefaultMoves(GameState gameState, Position from) {
        List<Position> positions = new ArrayList<>();
        int x = from.getTilex();
        int y = from.getTiley();

        // Cardinal 1 tile
        addIfValidEmpty(positions, gameState, tilePos(x + 1, y));
        addIfValidEmpty(positions, gameState, tilePos(x - 1, y));
        addIfValidEmpty(positions, gameState, tilePos(x, y + 1));
        addIfValidEmpty(positions, gameState, tilePos(x, y - 1));

        // Cardinal 2 tiles
        addIfValidEmpty(positions, gameState, tilePos(x + 2, y));
        addIfValidEmpty(positions, gameState, tilePos(x - 2, y));
        addIfValidEmpty(positions, gameState, tilePos(x, y + 2));
        addIfValidEmpty(positions, gameState, tilePos(x, y - 2));

        // Diagonal 1 tile
        addIfValidEmpty(positions, gameState, tilePos(x + 1, y + 1));
        addIfValidEmpty(positions, gameState, tilePos(x + 1, y - 1));
        addIfValidEmpty(positions, gameState, tilePos(x - 1, y + 1));
        addIfValidEmpty(positions, gameState, tilePos(x - 1, y - 1));

        return positions;
    }

    public List<Position> computeMovesForUnit(GameState gameState, UnitEntity unit) {
        List<Position> positions = new ArrayList<>();
        if (gameState == null || unit == null || unit.getPosition() == null) return positions;

        Position from = unit.getPosition();

        // PROVOKE: adjacent enemy provoke means no movement allowed
        if (hasAdjacentEnemyProvoke(gameState, unit)) {
            return positions;
        }

        // FLYING: any empty tile
        if (unit.hasKeyword("FLYING")) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 5; y++) {
                    Position p = tilePos(x, y);

                    if (from.getTilex() == x && from.getTiley() == y) continue;
                    if (!gameState.getBoard().isValidPosition(p)) continue;
                    if (gameState.getBoard().isOccupied(p)) continue;

                    positions.add(p);
                }
            }
            return positions;
        }

        return computeDefaultMoves(gameState, from);
    }

    public boolean moveSelectedUnitTo(ActorRef out, GameState gameState, Position targetPos, Tile targetTile) {

        Integer selectedId = gameState.getSelectedUnitId();
        if (selectedId == null) return false;

        UnitEntity unit = gameState.getUnitById(selectedId);
        if (unit == null) return false;

        int t = gameState.getGlobalTurnNumber();
        if (!unit.canMove(t)) {
            ui.notifyP1(out, "This unit can't move again this turn.", 2);
            return false;
        }

        if (hasAdjacentEnemyProvoke(gameState, unit)) {
            ui.notifyP1(out, "This unit cannot move while next to an enemy Provoke unit", 2);
            return false;
        }

        if (!gameState.getBoard().isValidPosition(targetPos)) return false;
        if (gameState.getBoard().isOccupied(targetPos)) return false;

        Position from = unit.getPosition();

        gameState.getBoard().moveUnit(from, targetPos);

        unit.moveTo(targetTile);
        unit.markMoved(t);

        BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.move);
        BasicCommands.moveUnitToTile(out, unit, targetTile);
        BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.idle);

        return true;
    }

    private boolean hasAdjacentEnemyProvoke(GameState gameState, UnitEntity unit) {
        if (gameState == null || unit == null || unit.getPosition() == null) return false;

        int ux = unit.getPosition().getTilex();
        int uy = unit.getPosition().getTiley();

        for (UnitEntity other : gameState.getUnitsById().values()) {
            if (other == null) continue;
            if (other.isDead()) continue;
            if (other.getOwnerPlayerId() == unit.getOwnerPlayerId()) continue;
            if (!other.hasKeyword("PROVOKE")) continue;
            if (other.getPosition() == null) continue;

            int ox = other.getPosition().getTilex();
            int oy = other.getPosition().getTiley();

            int dx = Math.abs(ux - ox);
            int dy = Math.abs(uy - oy);

            boolean adjacent = (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0));
            if (adjacent) return true;
        }

        return false;
    }

    private void addIfValidEmpty(List<Position> out, GameState gameState, Position p) {
        if (!gameState.getBoard().isValidPosition(p)) return;
        if (gameState.getBoard().isOccupied(p)) return;
        out.add(p);
    }

    private Position tilePos(int tilex, int tiley) {
        Position p = new Position();
        p.setTilex(tilex);
        p.setTiley(tiley);
        return p;
    }
}