package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.Hand;
import structures.CardInstance;
import structures.basic.Tile;
import structures.basic.Position;
import utils.BasicObjectBuilders;

import java.util.List;

/**
 * CommandDispatcher = low-level UI command executor.
 *
 * It should NOT own highlight rules / selection cancel logic.
 * Those are moved into VisualFeedbackManager.
 *
 * For backwards compatibility, legacy methods remain here and delegate to VisualFeedbackManager.
 */
public class CommandDispatcher {

    // Tile modes (match your previous semantics)
    public static final int TILE_NORMAL = 0;
    public static final int TILE_MOVE_HIGHLIGHT = 1;
    public static final int TILE_ATTACK_HIGHLIGHT = 2;
    public static final int TILE_SUMMON_HIGHLIGHT = 1;
    public static final int TILE_SPELL_TARGET_HIGHLIGHT = 2;

    // Card draw modes
    public static final int CARD_NORMAL = 0;
    public static final int CARD_SELECTED = 1;

    // ----------------------------
    // PURE "send commands" helpers
    // ----------------------------

    public void notifyP1(ActorRef out, String text, int seconds) {
        BasicCommands.addPlayer1Notification(out, text, seconds);
    }

    public void drawTileNormal(ActorRef out, int x, int y) {
        Tile t = BasicObjectBuilders.loadTile(x, y);
        BasicCommands.drawTile(out, t, TILE_NORMAL);
    }

    public void drawTileMode(ActorRef out, int x, int y, int mode) {
        Tile t = BasicObjectBuilders.loadTile(x, y);
        BasicCommands.drawTile(out, t, mode);
    }

    public void deleteCardSlot(ActorRef out, int pos) {
        BasicCommands.deleteCard(out, pos);
    }

    public void drawHandCard(ActorRef out, CardInstance ci, int pos, int mode) {
        if (ci == null) return;
        BasicCommands.drawCard(out, ci.getVisual(), pos, mode);
    }

    // ----------------------------
    // Backwards-compat methods (delegate to VisualFeedbackManager)
    // These are the "parts that belong to VFM" but kept to avoid breaking existing code.
    // ----------------------------

    private VisualFeedbackManager vfm() {
        return new VisualFeedbackManager(this);
    }

    public void clearAllHighlights(ActorRef out, GameState gameState) {
        vfm().clearHighlights(out, gameState);
    }

    public void clearSpellTargeting(ActorRef out, GameState gameState) {
        vfm().cancelSpellTargeting(out, gameState);
    }

    public void redrawHandNormal(ActorRef out, GameState gameState) {
        vfm().redrawHandNormal(out, gameState);
    }

    public void highlightHandCard(ActorRef out, Hand hand, int selectedPos) {
        vfm().highlightHandCard(out, hand, selectedPos);
    }

    public void highlightMoveTiles(ActorRef out, GameState gameState, List<Position> tiles) {
        vfm().highlightMoveTiles(out, gameState, tiles);
    }

    public void highlightCenterTile(ActorRef out, GameState gameState, Position center) {
        vfm().highlightCenterTile(out, gameState, center);
    }

    public void highlightAttackTargets(ActorRef out, GameState gameState, structures.UnitEntity attacker) {
        vfm().highlightAttackTargets(out, gameState, attacker);
    }

    public void highlightSummonTiles(ActorRef out, GameState gameState) {
        vfm().highlightSummonTiles(out, gameState);
    }

    public void clearSummonTilesOnly(ActorRef out, GameState gameState) {
        vfm().clearSummonHighlights(out, gameState);
    }

    public void highlightSpellTargetsEnemyUnits(ActorRef out, GameState gameState) {
        vfm().highlightSpellTargetsEnemyUnits(out, gameState);
    }

    public void clearSpellTargetsOnly(ActorRef out, GameState gameState) {
        vfm().clearSpellTargetsOnly(out, gameState);
    }
}