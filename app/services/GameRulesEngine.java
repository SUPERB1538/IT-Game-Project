package services;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

import java.util.Set;

/**
 * Handles user input events and coordinates game logic such as movement,
 * combat, card play, and unit interaction.
 */
public class GameRulesEngine {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final MovementService movementService = new MovementService();
    private final CombatResolver combatResolver = new CombatResolver();
    private final SummonService summonService = new SummonService();
    private final CardPlayService cardPlayService = new CardPlayService();

    public void onTileClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState == null || gameState.getBoard() == null) return;
        if (gameState.isGameOver()) return;

        int x = message.get("tilex").asInt();
        int y = message.get("tiley").asInt();

        Position clickedPos = tilePos(x, y);
        Tile clickedTile = BasicObjectBuilders.loadTile(x, y);

        UnitEntity unitAt = gameState.getBoard().getUnitAt(clickedPos).orElse(null);

        if (gameState.isWaitingSpellTarget()) {
            String key = x + "," + y;

            if (gameState.getHighlightedSpellTargets().contains(key)) {
                cardPlayService.castSelectedSpell(out, gameState, clickedPos);
                return;
            }

            if (!gameState.getHighlightedSpellTargets().contains(key)) {
                ui.clearSpellTargeting(out, gameState);
                gameState.setSelectedCardPos(null);
            }
        }

        if (gameState.getSelectedCardPos() != null && isSummonHighlighted(gameState, x, y)) {
            if (unitAt != null) {
                ui.notifyP1(out, "Tile occupied", 2);
            } else {
                summonService.trySummonFromSelectedCard(out, gameState, clickedTile, clickedPos);
            }
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedCardPos(null);
            return;
        }

        if (unitAt != null && isAttackHighlighted(gameState, x, y)) {
            Integer selectedId = gameState.getSelectedUnitId();
            if (selectedId != null) {
                UnitEntity attacker = gameState.getUnitById(selectedId);
                if (attacker != null) {
                    combatResolver.tryAttack(out, gameState, attacker, unitAt);
                }
            }
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedUnitId(null);
            return;
        }

        if (isMoveHighlighted(gameState, x, y)) {
            movementService.moveSelectedUnitTo(out, gameState, clickedPos, clickedTile);
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedUnitId(null);
            return;
        }

        if (unitAt == null) {
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedUnitId(null);
            return;
        }

        if (unitAt.getOwnerPlayerId() == gameState.getCurrentPlayerId()) {
            ui.clearAllHighlights(out, gameState);

            gameState.setSelectedUnitId(null);
            gameState.setSelectedCardPos(null);

            gameState.setSelectedUnitId(unitAt.getId());

            int currentTurn = gameState.getGlobalTurnNumber();
            boolean canMove = unitAt.canMove(currentTurn);
            boolean canAttack = unitAt.canAttack(currentTurn);

            if (!canMove && !canAttack) {
                ui.highlightCenterTile(out, gameState, unitAt.getPosition());
                ui.notifyP1(out, "This unit cannot move or attack right now", 2);
                return;
            }

            if (canMove) {
                ui.highlightMoveTiles(out, gameState, movementService.computeMovesForUnit(gameState, unitAt));
            }

            ui.highlightCenterTile(out, gameState, unitAt.getPosition());

            if (canAttack) {
                ui.highlightAttackTargets(out, gameState, unitAt);
            }

            return;
        }

        ui.clearAllHighlights(out, gameState);
        gameState.setSelectedUnitId(null);
    }

    public void onCardClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState == null) return;
        if (gameState.isGameOver()) return;

        if (message.has("tilex") && message.has("tiley")) {
            onTileClicked(out, gameState, message);
            return;
        }

        if (!message.has("position")) return;
        int pos = message.get("position").asInt();

        cardPlayService.onCardClicked(out, gameState, pos);
    }

    public void onOtherClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState == null) return;
        if (gameState.isGameOver()) return;

        ui.clearSpellTargeting(out, gameState);
        ui.clearAllHighlights(out, gameState);

        gameState.setSelectedUnitId(null);
        gameState.setSelectedCardPos(null);
    }

    private boolean isMoveHighlighted(GameState s, int x, int y) {
        return s.getHighlightedMoveTiles().contains(x + "," + y);
    }

    private boolean isAttackHighlighted(GameState s, int x, int y) {
        return s.getHighlightedAttackTiles().contains(x + "," + y);
    }

    private boolean isSummonHighlighted(GameState s, int x, int y) {
        return s.getHighlightedSummonTiles().contains(x + "," + y);
    }

    public static void clearAllHighlightsUI(ActorRef out, GameState gameState) {
        new CommandDispatcher().clearAllHighlights(out, gameState);
    }

    private Position tilePos(int tilex, int tiley) {
        Position p = new Position();
        p.setTilex(tilex);
        p.setTiley(tiley);
        return p;
    }
}