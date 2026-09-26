package za.co.neroland.neropower.environmental;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

import za.co.neroland.nerotech.pollution.PollutionManager;

/**
 * The Spent Isotope Pellet the RTG ejects. Handled in a chest it is harmless; destroyed as a
 * dropped item — burnt, thrown in lava, dropped on a cactus, caught in an explosion — it vents a
 * {@code rtgSpentPelletPollution} burst per pellet into NeroTech's regional pollution map at that
 * spot ({@link PollutionManager#record}), the way NeroTech's fusion containment breach vents one.
 * Disposal means storage, not the lava pit.
 *
 * <p>The burst fires from vanilla's item-entity destruction hook, so it follows vanilla's rules for
 * what destroys an item entity: water does not (the pellet survives), despawning after five minutes
 * does not, and falling out of the world simply removes the entity (vanilla does not route that
 * through the destruction hook, so no burst). Aggregate, regional data only — no owner is passed,
 * so nothing is attributed to a player (POPIA/GDPR).
 */
public class SpentIsotopePelletItem extends Item {

    public SpentIsotopePelletItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        super.onDestroyed(itemEntity);
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int burst = RtgMath.spentPelletBurst(EnvironmentalConfig.rtgSpentPelletPollution(),
                itemEntity.getItem().getCount());
        if (burst > 0) {
            PollutionManager.record(serverLevel, itemEntity.blockPosition(), burst, null);
        }
    }
}
