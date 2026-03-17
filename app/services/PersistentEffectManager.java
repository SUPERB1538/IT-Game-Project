package services;

import structures.UnitEntity;

public class PersistentEffectManager {

    public void applyPermanentAttackBuff(UnitEntity unit, int amount) {
        if (unit == null || amount == 0) return;
        unit.setAttack(unit.getAttack() + amount);
    }

    public void applyPermanentAttackAndHealthBuff(UnitEntity unit, int attackAmount, int healthAmount) {
        if (unit == null) return;

        if (attackAmount != 0) {
            unit.setAttack(unit.getAttack() + attackAmount);
        }

        if (healthAmount != 0) {
            unit.setMaxHealth(unit.getMaxHealth() + healthAmount);
            unit.setHealth(unit.getHealth() + healthAmount);
        }
    }

    // Silverguard Knight (25-26): Zeal = permanent +2 attack
    public void applyZealBuff(UnitEntity unit) {
        if (unit == null) return;
        unit.setAttack(unit.getAttack() + 2);
    }
}