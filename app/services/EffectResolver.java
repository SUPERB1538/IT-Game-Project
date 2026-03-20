package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;
import structures.basic.Position;
import structures.basic.Tile;          // Person B: Required for UI rendering conversions
import structures.basic.EffectAnimation; 
import utils.BasicObjectBuilders;      

/**
 * Centralized Spell Resolution Framework.
 * Handles the logic for all spell cards, ensuring a separation 
 * between UI triggers and gameplay effects.
 */
public class EffectResolver {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final DamageService damageService = new DamageService();
    private final UnitRemovalService unitRemovalService = new UnitRemovalService();
    private final SummonService summonService = new SummonService(); 

    public void applySpell(ActorRef out, GameState gameState, CardInstance spellCard, UnitEntity targetUnit, Position targetPos) {
        if (out == null || gameState == null || spellCard == null || targetPos == null) return;

        String key = spellCard.getCardKey().toUpperCase();
        int currentPlayerId = gameState.getCurrentPlayerId(); 
        ui.notifyP1(out, "Casting: " + spellCard.getCardKey(), 2);

        // Person B: Convert logical Position to physical Tile for frontend UI commands.
        // BasicCommands.playEffectAnimation strictly requires a Tile object.
        Tile targetTile = BasicObjectBuilders.loadTile(targetPos.getTilex(), targetPos.getTiley());

        // ---------------------------------------------------------
        // 1. TRUE_STRIKE: Deals 2 damage.
        // ---------------------------------------------------------
        if (key.contains("TRUE_STRIKE")) {
            if (targetUnit != null) {
                EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_truestrike.json");
                // Person B: Pass targetTile instead of targetPos to resolve type conflict
                BasicCommands.playEffectAnimation(out, anim, targetTile); 
                
                damageService.dealDamage(out, gameState, targetUnit, 2);
                
                if (targetUnit.getHealth() <= 0 && !(targetUnit instanceof AvatarUnit)) {
                    unitRemovalService.removeUnit(out, gameState, targetUnit, "Destroyed by Spell");
                }
            }
        } 
        // ---------------------------------------------------------
        // 2. HORN_OF_THE_FORSAKEN: Artifact equipment.
        // ---------------------------------------------------------
        else if (key.contains("HORN_OF_THE_FORSAKEN")) {
            if (targetUnit instanceof AvatarUnit) {
                EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json");
                BasicCommands.playEffectAnimation(out, anim, targetTile);
                targetUnit.setHornOfForsaken(true);
            }
        }
        // ---------------------------------------------------------
        // 3. SUNDROP_ELIXIR: Healing effect.
        // ---------------------------------------------------------
        else if (key.contains("SUNDROP_ELIXIR")) {
            if (targetUnit != null) {
                EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_martyrdom.json");
                BasicCommands.playEffectAnimation(out, anim, targetTile);
                
                targetUnit.setHealth(targetUnit.getHealth() + 4);
                BasicCommands.setUnitHealth(out, targetUnit, targetUnit.getHealth());
                damageService.syncIfAvatar(out, gameState, targetUnit);
            }
        }
        // ---------------------------------------------------------
        // 4. WRAITHLING_SWARM: Token summoning logic.
        // ---------------------------------------------------------
        else if (key.contains("WRAITHLING_SWARM")) {
            String wraithConf = "conf/gameconfs/units/wraithling.json";
            EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_summon.json");
            int summonsCount = 0;
            
            for (int dx = -1; dx <= 1 && summonsCount < 3; dx++) {
                for (int dy = -1; dy <= 1 && summonsCount < 3; dy++) {
                    Position p = new Position();
                    p.setTilex(targetPos.getTilex() + dx);
                    p.setTiley(targetPos.getTiley() + dy);
                    if (gameState.getBoard().isValidPosition(p) && !gameState.getBoard().isOccupied(p)) {
                        
                        // Person B: Dynamically load a Tile for each valid spawn position in the AoE loop
                        Tile pTile = BasicObjectBuilders.loadTile(p.getTilex(), p.getTiley());
                        BasicCommands.playEffectAnimation(out, anim, pTile);
                        
                        // Summon service uses Position, so we pass 'p' here
                        summonService.summonToken(out, gameState, wraithConf, p, currentPlayerId);
                        summonsCount++;
                    }
                }
            }
        }
        // ---------------------------------------------------------
        // 5. DARK_TERMINUS: Exile logic.
        // ---------------------------------------------------------
        else if (key.contains("DARK_TERMINUS")) {
            if (targetUnit != null && !(targetUnit instanceof AvatarUnit)) {
                EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_soulshatter.json");
                BasicCommands.playEffectAnimation(out, anim, targetTile);
                
                unitRemovalService.removeUnit(out, gameState, targetUnit, "Exiled");
                // summonToken expects Position, so we pass targetPos
                summonService.summonToken(out, gameState, "conf/gameconfs/units/wraithling.json", targetPos, currentPlayerId);
            }
        }
        // ---------------------------------------------------------
        // 6. BEAM_SHOCK: State-based debuff.
        // ---------------------------------------------------------
        else if (key.contains("BEAM_SHOCK")) {
            if (targetUnit != null) {
                EffectAnimation anim = BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_stun.json");
                BasicCommands.playEffectAnimation(out, anim, targetTile);
                
                targetUnit.setStunned(true);
                ui.notifyP1(out, "Unit Stunned: Cannot act next turn", 2);
            }
        }
    }
}