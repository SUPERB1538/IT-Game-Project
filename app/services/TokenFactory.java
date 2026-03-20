package services;

import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

/**
 * Factory responsible for creating and initializing token units.
 * It only builds the unit object and applies its base properties.
 * Board placement, game-state registration, and UI drawing remain in SummonService.
 */
public class TokenFactory {

    public UnitEntity createToken(GameState gameState, String unitConfig, Position targetPos, int ownerId) {
        if (gameState == null || unitConfig == null || targetPos == null) return null;

        UnitEntity summoned;
        try {
            summoned = (UnitEntity) BasicObjectBuilders.loadUnit(
                    unitConfig,
                    gameState.nextUnitId(),
                    UnitEntity.class
            );
        } catch (Exception e) {
            return null;
        }

        if (summoned == null) return null;

        // Base owner
        summoned.setOwnerPlayerId(ownerId);

        // Base token stats
        applyTokenStats(summoned, unitConfig);

        // Keywords
        SummonService.applyKeywordsForUnit(summoned, unitConfig);

        // Position binding
        Tile tile = BasicObjectBuilders.loadTile(targetPos.getTilex(), targetPos.getTiley());
        summoned.setPositionByTile(tile);

        // Summoning sickness / turn tracking
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());

        return summoned;
    }

    /**
     * Apply default stats for known tokens.
     * For now, Wraithlings are standard 1/1.
     * Unknown tokens also fall back to 1/1 safely.
     */
    private void applyTokenStats(UnitEntity unit, String unitConfig) {
        if (unit == null) return;

        String k = (unitConfig == null) ? "" : unitConfig.toLowerCase();

        if (k.contains("wraithling")) {
            unit.setMaxHealth(1);
            unit.setHealth(1);
            unit.setAttack(1);
            return;
        }

        // Default fallback token stats
        unit.setMaxHealth(1);
        unit.setHealth(1);
        unit.setAttack(1);
    }
}