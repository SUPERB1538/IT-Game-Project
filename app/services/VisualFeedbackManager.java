package services;

import akka.actor.ActorRef;
import structures.CardInstance;
import structures.GameState;
import structures.Hand;
import structures.PlayerState;
import structures.UnitEntity;
import structures.basic.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VisualFeedbackManager = the coordinator ("director").
 *
 * Owns:
 * - highlight computation/drawing and state sets
 * - clearing highlights
 * - canceling selections (UI/interaction state)
 * - hand show/hide/redraw visuals
 *
 * Does NOT:
 * - move units, attack, summon, spend mana, remove units (gameplay logic)
 */
public class VisualFeedbackManager {

    private final CommandDispatcher ui;

    public VisualFeedbackManager(CommandDispatcher dispatcher) {
        this.ui = (dispatcher != null) ? dispatcher : new CommandDispatcher();
    }

    // ----------------------------
    // Hand visuals
    // ----------------------------

    public void hideHumanHandUI(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        if (gameState.getCurrentPlayerId() != 1) return;
        if (gameState.isHandHidden()) return;

        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            ui.deleteCardSlot(out, pos);
        }
        gameState.setHandHidden(true);
    }

    public void showHumanHandUI(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        if (!gameState.isHandHidden()) return;

        PlayerState p1 = gameState.getP1State();
        if (p1 == null || p1.getHand() == null) {
            gameState.setHandHidden(false);
            return;
        }

        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            ui.deleteCardSlot(out, pos);
        }

        Hand hand = p1.getHand();
        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            CardInstance ci = hand.getBySlot(pos);
            if (ci != null) ui.drawHandCard(out, ci, pos, CommandDispatcher.CARD_NORMAL);
        }

        gameState.setHandHidden(false);
    }

    public void redrawHandNormal(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        if (gameState.isHandHidden()) return;

        PlayerState p1 = gameState.getP1State();
        if (p1 == null || p1.getHand() == null) return;

        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            ui.deleteCardSlot(out, pos);
        }

        Hand hand = p1.getHand();
        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            CardInstance ci = hand.getBySlot(pos);
            if (ci != null) ui.drawHandCard(out, ci, pos, CommandDispatcher.CARD_NORMAL);
        }
    }

    public void highlightHandCard(ActorRef out, Hand hand, int selectedPos) {
        if (out == null || hand == null) return;

        for (int pos = Hand.MIN_SLOT; pos <= Hand.MAX_SLOT; pos++) {
            CardInstance ci = hand.getBySlot(pos);
            if (ci == null) continue;

            int mode = (pos == selectedPos) ? CommandDispatcher.CARD_SELECTED : CommandDispatcher.CARD_NORMAL;
            ui.drawHandCard(out, ci, pos, mode);
        }
    }

    // ----------------------------
    // Highlight clearing
    // ----------------------------

    public void clearHighlights(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        Set<String> all = new HashSet<>();
        all.addAll(gameState.getHighlightedMoveTiles());
        all.addAll(gameState.getHighlightedAttackTiles());
        all.addAll(gameState.getHighlightedSummonTiles());
        all.addAll(gameState.getHighlightedSpellTargets());

        for (String key : all) {
            String[] p = key.split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            ui.drawTileNormal(out, x, y);
        }

        gameState.clearAllHighlights();
        gameState.getHighlightedSpellTargets().clear();
    }

    public void clearSummonHighlights(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        for (String key : new HashSet<>(gameState.getHighlightedSummonTiles())) {
            String[] p = key.split(",");
            ui.drawTileNormal(out, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        gameState.getHighlightedSummonTiles().clear();
    }

    public void clearSpellTargetsOnly(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        for (String key : new HashSet<>(gameState.getHighlightedSpellTargets())) {
            String[] p = key.split(",");
            ui.drawTileNormal(out, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        gameState.getHighlightedSpellTargets().clear();
    }

    public void cancelSpellTargeting(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        clearSpellTargetsOnly(out, gameState);
        gameState.setWaitingSpellTarget(false);
        gameState.setSelectedSpellCardPos(null);
    }

    // ----------------------------
    // Highlight drawing + state sets
    // ----------------------------

    public void highlightMoveTiles(ActorRef out, GameState gameState, List<Position> tiles) {
        if (out == null || gameState == null || tiles == null) return;

        for (Position pos : tiles) {
            int x = pos.getTilex();
            int y = pos.getTiley();
            ui.drawTileMode(out, x, y, CommandDispatcher.TILE_MOVE_HIGHLIGHT);
            gameState.getHighlightedMoveTiles().add(x + "," + y);
        }
    }

    public void highlightCenterTile(ActorRef out, GameState gameState, Position center) {
        if (out == null || gameState == null || center == null) return;
        int x = center.getTilex();
        int y = center.getTiley();
        ui.drawTileMode(out, x, y, CommandDispatcher.TILE_MOVE_HIGHLIGHT);
        gameState.getHighlightedMoveTiles().add(x + "," + y);
    }

    public void highlightAttackTargets(ActorRef out, GameState gameState, UnitEntity attacker) {
        if (out == null || gameState == null || attacker == null || attacker.getPosition() == null) return;

        gameState.getHighlightedAttackTiles().clear();

        CombatResolver combatResolver = new CombatResolver();
        Set<Position> legalTargets = combatResolver.computeLegalAttackTargets(gameState, attacker);

        for (Position p : legalTargets) {
            int x = p.getTilex();
            int y = p.getTiley();

            ui.drawTileMode(out, x, y, CommandDispatcher.TILE_ATTACK_HIGHLIGHT);
            gameState.getHighlightedAttackTiles().add(x + "," + y);
        }
    }

    public void highlightSummonTiles(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        gameState.getHighlightedSummonTiles().clear();

        Set<String> seen = new HashSet<>();
        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.getPosition() == null) continue;
            if (u.getOwnerPlayerId() != 1) continue;

            int ux = u.getPosition().getTilex();
            int uy = u.getPosition().getTiley();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    Position p = new Position();
                    p.setTilex(ux + dx);
                    p.setTiley(uy + dy);

                    if (!gameState.getBoard().isValidPosition(p)) continue;
                    if (gameState.getBoard().isOccupied(p)) continue;

                    String key = p.getTilex() + "," + p.getTiley();
                    if (!seen.add(key)) continue;

                    ui.drawTileMode(out, p.getTilex(), p.getTiley(), CommandDispatcher.TILE_SUMMON_HIGHLIGHT);
                    gameState.getHighlightedSummonTiles().add(key);
                }
            }
        }

        if (gameState.getHighlightedSummonTiles().isEmpty()) {
            ui.notifyP1(out, "No valid summon tiles", 2);
        }
    }

    public void highlightSpellTargetsEnemyUnits(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;

        clearSpellTargetsOnly(out, gameState);

        int me = gameState.getCurrentPlayerId();

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.getPosition() == null) continue;
            if (u.getOwnerPlayerId() == me) continue;

            int x = u.getPosition().getTilex();
            int y = u.getPosition().getTiley();
            String key = x + "," + y;

            ui.drawTileMode(out, x, y, CommandDispatcher.TILE_SPELL_TARGET_HIGHLIGHT);
            gameState.getHighlightedSpellTargets().add(key);
        }

        if (gameState.getHighlightedSpellTargets().isEmpty()) {
            ui.notifyP1(out, "No valid spell targets", 2);
            gameState.setWaitingSpellTarget(false);
            gameState.setSelectedSpellCardPos(null);
        }
    }

    public void clearAllHighlights(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        GameRulesEngine.clearAllHighlightsUI(out, gameState);
    }
}