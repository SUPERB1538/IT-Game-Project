package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;
import structures.basic.Player;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

/**
 * Initializes the game state including board setup, players, avatars,
 * decks, starting hands, and initial turn configuration.
 */
public class GameInitializationService {
    private static final int BOARD_W = 9;
    private static final int BOARD_H = 5;

    public void initializeGame(ActorRef out, GameState gameState) {

        // 1) Create and store board
        Board board = new Board(BOARD_W, BOARD_H);
        gameState.setBoard(board);

        // 2) Draw tiles
        for (int x = 0; x < BOARD_W; x++) {
            for (int y = 0; y < BOARD_H; y++) {
                Tile tile = BasicObjectBuilders.loadTile(x, y);
                BasicCommands.drawTile(out, tile, 0);
            }
        }

        // 3) Players
        Player p1 = new Player(20, 0);
        Player p2 = new Player(20, 0);
        gameState.setPlayer1(p1);
        gameState.setPlayer2(p2);

        // 4) Spawn tiles per rules: Human at (2,3), AI mirrored at (8,3)
        Tile p1Spawn = BasicObjectBuilders.loadTile(1, 2);
        Tile p2Spawn = BasicObjectBuilders.loadTile(7, 2);

        // 5) Load avatar unit visuals from config
        AvatarUnit p1Avatar = (AvatarUnit) BasicObjectBuilders.loadUnit(
                "conf/gameconfs/avatars/avatar1.json", 1001, AvatarUnit.class
        );

        // If you have an AI avatar config use it; otherwise re-use humanAvatar safely
        AvatarUnit p2Avatar = (AvatarUnit) BasicObjectBuilders.loadUnit(
                "conf/gameconfs/avatars/avatar2.json", 2001, AvatarUnit.class
        );

        // 6) Sync their tile positions
        p1Avatar.setPositionByTile(p1Spawn);
        p2Avatar.setPositionByTile(p2Spawn);

        // 7) Gameplay stats
        p1Avatar.setOwnerPlayerId(1);
        p1Avatar.setMaxHealth(20);
        p1Avatar.setHealth(20);
        p1Avatar.setAttack(2);

        p2Avatar.setOwnerPlayerId(2);
        p2Avatar.setMaxHealth(20);
        p2Avatar.setHealth(20);
        p2Avatar.setAttack(2);

        // 8) Store into state
        gameState.setP1Avatar(p1Avatar);
        gameState.setP2Avatar(p2Avatar);
        gameState.addUnit(p1Avatar);
        gameState.addUnit(p2Avatar);

        // 9) Register occupancy
        board.putUnit(p1Avatar.getPosition(), p1Avatar);
        board.putUnit(p2Avatar.getPosition(), p2Avatar);

        // 10) Draw units
        try { Thread.sleep(100); } catch (Exception e) {}
        BasicCommands.drawUnit(out, p1Avatar, p1Spawn);
        BasicCommands.drawUnit(out, p2Avatar, p2Spawn);

        try { Thread.sleep(100); } catch (Exception e) {}
        BasicCommands.setUnitHealth(out, p1Avatar, p1Avatar.getHealth());
        BasicCommands.setUnitAttack(out, p1Avatar, p1Avatar.getAttack());
        BasicCommands.setUnitHealth(out, p2Avatar, p2Avatar.getHealth());
        BasicCommands.setUnitAttack(out, p2Avatar, p2Avatar.getAttack());

        // 11) Update player UI (health + mana)
        BasicCommands.setPlayer1Health(out, p1);
        BasicCommands.setPlayer2Health(out, p2);
        BasicCommands.setPlayer1Mana(out, p1);
        BasicCommands.setPlayer2Mana(out, p2);

        // -----------------------------
        // 12) Decks + starting hands
        // -----------------------------
        // Build ordered decks (no shuffle) and assign to player runtime states
        Deck p1Deck = structures.DeckFactory.buildHumanDeck(gameState);
        Deck p2Deck = structures.DeckFactory.buildAIDeck(gameState);
        gameState.getP1State().setDeck(p1Deck);
        gameState.getP2State().setDeck(p2Deck);

        // Create hands
        Hand p1Hand = new Hand();
        Hand p2Hand = new Hand();
        gameState.getP1State().setHand(p1Hand);
        gameState.getP2State().setHand(p2Hand);

        // Draw 3 cards each at game start
        drawStartingHand(out, p1Deck, p1Hand, true);
        drawStartingHand(out, p2Deck, p2Hand, false);

        // -----------------------------
        // 13) Start first turn: give Player 1 mana and update UI
        // -----------------------------
        gameState.setCurrentPlayerId(1);
        gameState.beginTurn();
        BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
        BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());

        System.out.println("[Initalize] Board created: 9x5");
        System.out.println("[Initalize] P1 avatar at (" + p1Avatar.getPosition().getTilex() + "," + p1Avatar.getPosition().getTiley() + ")");
        System.out.println("[Initalize] P2 avatar at (" + p2Avatar.getPosition().getTilex() + "," + p2Avatar.getPosition().getTiley() + ")");
        System.out.println("[Initalize] P1 deck size=" + p1Deck.size() + ", hand size=" + p1Hand.view().size());
        System.out.println("[Initalize] P2 deck size=" + p2Deck.size() + ", hand size=" + p2Hand.view().size());
    }

    private void drawStartingHand(ActorRef out, Deck deck, Hand hand, boolean showUI) {
        if (deck == null || hand == null) return;

        // 0) Safety: clear ALL card slots to remove stale hitboxes
        // This prevents card UI click areas from overlapping the board.
        if (showUI) {
            for (int pos = 1; pos <= 6; pos++) {
                BasicCommands.deleteCard(out, pos);
            }
            try { Thread.sleep(150); } catch (Exception ignored) {}
        }

        // 2) Draw up to 3 cards into slots 1..3
        for (int slot = 1; slot <= 3; slot++) {
            if (deck.isEmpty()) break;

            structures.CardInstance ci = deck.drawTop();
            if (ci == null) break;

            hand.putIntoSlot(slot, ci);

            if (showUI) {
                try { Thread.sleep(80); } catch (Exception ignored) {}
                BasicCommands.drawCard(out, ci.getVisual(), slot, 0);
            }
        }
    }
}
