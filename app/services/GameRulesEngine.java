package services;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import structures.*;
import structures.basic.Position;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

public class GameRulesEngine {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final MovementService movementService = new MovementService();
    private final CombatResolver combatResolver = new CombatResolver();
    private final SummonService summonService = new SummonService();
    private final CardPlayService cardPlayService = new CardPlayService();

    public void onTileClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState.isGameOver()) return;
        if (gameState == null || gameState.getBoard() == null) return;

        int x = message.get("tilex").asInt();
        int y = message.get("tiley").asInt();

        Position clickedPos = tilePos(x, y);
        Tile clickedTile = BasicObjectBuilders.loadTile(x, y);

        UnitEntity unitAt = gameState.getBoard().getUnitAt(clickedPos).orElse(null);

        // ---- Spell targeting execution/cancel ----
        if (gameState.isWaitingSpellTarget()) {
            String key = x + "," + y;

            // If clicked a valid spell target: cast spell
            if (unitAt != null && gameState.getHighlightedSpellTargets().contains(key)) {
                cardPlayService.castSelectedSpellOnTarget(out, gameState, unitAt);
                ui.showHumanHandUI(out, gameState);
                return;
            }

            // Otherwise: cancel spell targeting but continue normal click
            if (!gameState.getHighlightedSpellTargets().contains(key)) {
                ui.clearSpellTargeting(out, gameState);
                ui.showHumanHandUI(out, gameState);
                gameState.setSelectedCardPos(null);
                // continue normal click handling
            }
        }

        // 0) Summon: selected card + summon tile
        if (gameState.getSelectedCardPos() != null && isSummonHighlighted(gameState, x, y)) {
            if (unitAt != null) {
                ui.notifyP1(out, "Tile occupied", 2);
            } else {
                summonService.trySummonFromSelectedCard(out, gameState, clickedTile, clickedPos);
            }
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedCardPos(null);
            ui.showHumanHandUI(out, gameState);
            return;
        }

        // 1) Attack: click enemy on attack-highlight tile
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
            ui.showHumanHandUI(out, gameState);
            return;
        }

        // 2) Move: click move-highlight tile
        if (isMoveHighlighted(gameState, x, y)) {
            movementService.moveSelectedUnitTo(out, gameState, clickedPos, clickedTile);
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedUnitId(null);
            ui.showHumanHandUI(out, gameState);
            return;
        }

        // 3) Click empty tile -> clear selection/highlights
        if (unitAt == null) {
            ui.clearAllHighlights(out, gameState);
            gameState.setSelectedUnitId(null);
            ui.showHumanHandUI(out, gameState);
            return;
        }

        // 4) Click friendly unit -> re-select + re-highlight
        if (unitAt.getOwnerPlayerId() == gameState.getCurrentPlayerId()) {

            ui.clearAllHighlights(out, gameState);

            gameState.setSelectedUnitId(null);
            gameState.setSelectedCardPos(null);

            gameState.setSelectedUnitId(unitAt.getId());

            int t = gameState.getGlobalTurnNumber();
            boolean canMove = unitAt.canMove(t);
            boolean canAttack = unitAt.canAttack(t);

            if (!canMove && !canAttack) {
                ui.highlightCenterTile(out, gameState, unitAt.getPosition());
                ui.showHumanHandUI(out, gameState);
                ui.notifyP1(out, "Summoned units can't move/attack this turn", 2);
                return;
            }

            ui.hideHumanHandUI(out, gameState);

            if (canMove) {
                ui.highlightMoveTiles(out, gameState, movementService.computeDefaultMoves(gameState, unitAt.getPosition()));
            }

            ui.highlightCenterTile(out, gameState, unitAt.getPosition());

            if (canAttack) {
                ui.highlightAttackTargets(out, gameState, unitAt);
            }

            return;
        }

        // 5) Click enemy unit (not highlighted) -> clear
        ui.clearAllHighlights(out, gameState);
        gameState.setSelectedUnitId(null);
        ui.showHumanHandUI(out, gameState);
    }

    public void onCardClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState.isGameOver()) return;
        if (gameState == null || message == null) return;

        if (message.has("tilex") && message.has("tiley")) {
            onTileClicked(out, gameState, message);
            return;
        }

        if (!message.has("position")) return;
        int pos = message.get("position").asInt();

        cardPlayService.onCardClicked(out, gameState, pos);
    }

    public void onOtherClicked(ActorRef out, GameState gameState, JsonNode message) {
        if (gameState.isGameOver()) return;
        if (gameState == null) return;

        ui.clearSpellTargeting(out, gameState);
        ui.clearAllHighlights(out, gameState);

        gameState.setSelectedUnitId(null);
        gameState.setSelectedCardPos(null);

        ui.showHumanHandUI(out, gameState);
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