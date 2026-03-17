package services;

import java.util.ArrayList;
import java.util.List;
import structures.*;
import structures.basic.Position;

/**
 * Service: Targeting Strategy (Person B)
 * Responsibilities: Encapsulates the rules for calculating valid target coordinates 
 * for spells without modifying the underlying game state.
 */
public class TargetingService {

    /**
     * Computes a collection of valid board positions based on the spell's specific criteria.
     */
    public List<Position> getValidTargets(CardInstance card, GameState gameState) {
        List<Position> targets = new ArrayList<>();
        String key = card.getCardKey().toUpperCase();

        // Target: ANY Enemy Unit
        if (key.contains("TRUE_STRIKE")) {
            for (UnitEntity u : gameState.getUnitsById().values()) {
                if (u.getOwnerPlayerId() != gameState.getCurrentPlayerId()) {
                    targets.add(u.getPosition());
                }
            }
        }
        // Target: Enemy NON-AVATAR Units
        else if (key.contains("BEAM_SHOCK") || key.contains("DARK_TERMINUS")) {
            for (UnitEntity u : gameState.getUnitsById().values()) {
                if (u.getOwnerPlayerId() != gameState.getCurrentPlayerId() && !(u instanceof structures.AvatarUnit)) {
                    targets.add(u.getPosition());
                }
            }
        }
        // Target: ANY Allied Unit
        else if (key.contains("SUNDROP_ELIXIR")) {
            for (UnitEntity u : gameState.getUnitsById().values()) {
                if (u.getOwnerPlayerId() == gameState.getCurrentPlayerId()) {
                    targets.add(u.getPosition());
                }
            }
        }
        // Person B: Target Allied Avatar (for Artifacts like Horn of the Forsaken)
        // This allows artifacts to be equipped to the player's own hero.
        else if (key.contains("HORN_OF_THE_FORSAKEN")) {
            AvatarUnit myAvatar = gameState.getP1Avatar(); // 获取己方主将
            if (myAvatar != null && myAvatar.getPosition() != null) {
                targets.add(myAvatar.getPosition());
            }
        }
        // Target: Empty Board Tiles (Standard 9x5 grid)
        else if (key.contains("WRAITHLING_SWARM")) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 5; y++) {
                    Position p = new Position();
                    p.setTilex(x);
                    p.setTiley(y);

                    if (gameState.getBoard() != null && gameState.getBoard().isValidPosition(p)) {
                        if (!gameState.getBoard().isOccupied(p)) {
                            targets.add(p);
                        }
                    }
                }
            }
        }
        
        return targets;
    }
}