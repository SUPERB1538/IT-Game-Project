                       package structures;

import structures.basic.Player;


public class GameState {

	// -----------------------------
	// Players (UI objects)
	// -----------------------------
	private Player player1 = new Player();
	private Player player2 = new Player();

	public Player getPlayer1() { return player1; }
	public Player getPlayer2() { return player2; }

	public void setPlayer1(Player p) {
		this.player1 = p;
		this.p1State.setPlayer(p);
	}
	public void setPlayer2(Player p) {
		this.player2 = p;
		this.p2State.setPlayer(p);
	}

	// -----------------------------
	// Player runtime state (logic)
	// -----------------------------
	private PlayerState p1State = new PlayerState(1, player1);
	private PlayerState p2State = new PlayerState(2, player2);

	public PlayerState getP1State() { return p1State; }
	public PlayerState getP2State() { return p2State; }

	// -----------------------------
	// Board
	// -----------------------------
	private Board board;
	public Board getBoard() { return board; }
	public void setBoard(Board board) { this.board = board; }

	// -----------------------------
	// Units index
	// -----------------------------
	private final java.util.Map<Integer, UnitEntity> unitsById = new java.util.HashMap<>();
	public java.util.Map<Integer, UnitEntity> getUnitsById() { return unitsById; }
	public void addUnit(UnitEntity u) { unitsById.put(u.getId(), u); }
	public UnitEntity getUnitById(int id) { return unitsById.get(id); }
	public void removeUnitById(int id) { unitsById.remove(id); }

	// -----------------------------
	// Avatars
	// -----------------------------
	private AvatarUnit p1Avatar;
	private AvatarUnit p2Avatar;

	public AvatarUnit getP1Avatar() { return p1Avatar; }
	public void setP1Avatar(AvatarUnit a) { this.p1Avatar = a; }
	public AvatarUnit getP2Avatar() { return p2Avatar; }
	public void setP2Avatar(AvatarUnit a) { this.p2Avatar = a; }

	// -----------------------------
	// UI interaction state (selection / highlights / turn)
	// -----------------------------
	private Integer selectedUnitId = null;

	// Card play selection
	private Integer selectedCardPos = null;
	public Integer getSelectedCardPos() { return selectedCardPos; }
	public void setSelectedCardPos(Integer pos) { this.selectedCardPos = pos; }

	// Split highlights so attack tiles are NOT treated as move tiles.
	private final java.util.Set<String> highlightedMoveTiles = new java.util.HashSet<>();
	private final java.util.Set<String> highlightedAttackTiles = new java.util.HashSet<>();
	private final java.util.Set<String> highlightedSummonTiles = new java.util.HashSet<>();

	// 1 = human, 2 = AI
	private int currentPlayerId = 1;

	public Integer getSelectedUnitId() { return selectedUnitId; }
	public void setSelectedUnitId(Integer id) { this.selectedUnitId = id; }

	public java.util.Set<String> getHighlightedMoveTiles() { return highlightedMoveTiles; }
	public java.util.Set<String> getHighlightedAttackTiles() { return highlightedAttackTiles; }
	public java.util.Set<String> getHighlightedSummonTiles() { return highlightedSummonTiles; }

	public void clearAllHighlights() {
		highlightedMoveTiles.clear();
		highlightedAttackTiles.clear();
		highlightedSummonTiles.clear();
		highlightedSpellTargets.clear();
	}

	public int getCurrentPlayerId() { return currentPlayerId; }
	public void setCurrentPlayerId(int id) { this.currentPlayerId = id; }

	public PlayerState getCurrentPlayerState() {
		return (currentPlayerId == 1) ? p1State : p2State;
	}

	// -----------------------------
	// Turn + Mana
	// -----------------------------
	private int globalTurnNumber = 0;
	private int p1TurnNumber = 0;
	private int p2TurnNumber = 0;
	public int getGlobalTurnNumber() {
		return globalTurnNumber;
	}

	public void beginTurn() {
		globalTurnNumber++;

		int current = getCurrentPlayerId();

		if (current == 1) {
			p1TurnNumber++;
			int mana = Math.min(9, p1TurnNumber + 1);
			getPlayer1().setMana(mana);
		} else {
			p2TurnNumber++;
			int mana = Math.min(9, p2TurnNumber + 1);
			getPlayer2().setMana(mana);
		}

		for (UnitEntity u : unitsById.values()) {
			if (u == null) continue;
			if (u.getOwnerPlayerId() != current) continue;
			u.resetTurnFlags(globalTurnNumber);
		}
	}

	public void clearCurrentMana() {
		getCurrentPlayerState().setMana(0);
	}


	// -----------------------------
	// Unit id generator (for summoned units)
	// -----------------------------
	private int nextUnitId = 3000;
	public int nextUnitId() { return nextUnitId++; }

	// -----------------------------
	// Hand UI state (backend-only)
	// -----------------------------
	private boolean handHidden = false;

	public boolean isHandHidden() { return handHidden; }
	public void setHandHidden(boolean hidden) { this.handHidden = hidden; }

	// --- Spell targeting ---
	private boolean waitingSpellTarget = false;
	private Integer selectedSpellCardPos = null; // 1..6
	private final java.util.Set<String> highlightedSpellTargets = new java.util.HashSet<>();

	public boolean isWaitingSpellTarget() { return waitingSpellTarget; }
	public void setWaitingSpellTarget(boolean b) { this.waitingSpellTarget = b; }

	public Integer getSelectedSpellCardPos() { return selectedSpellCardPos; }
	public void setSelectedSpellCardPos(Integer pos) { this.selectedSpellCardPos = pos; }

	public java.util.Set<String> getHighlightedSpellTargets() { return highlightedSpellTargets; }

	private boolean gameOver = false;

	public boolean isGameOver() { return gameOver; }
	public void setGameOver(boolean b) { this.gameOver = b; }
}