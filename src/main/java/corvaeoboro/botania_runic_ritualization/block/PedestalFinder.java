/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalFinder.java
 * - Utility for finding pedestals within a horizontal radius around a machine.
 * - pedestals within PEDESTAL_SEARCH_RADIUS horizontally (same Y level) are connected.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PedestalFinder {

	//region CONSTANT
	// Default horizontal radius for finding pedestals around the machine. Overridden by config at runtime. 
	public static final int PEDESTAL_SEARCH_RADIUS_DEFAULT = 2;
	//endregion

	private PedestalFinder() {}

	//region FIND
	/**
	 * Finds all pedestals within the configured search radius horizontally (same Y level) of the machine.
	 *
	 * @param level  The level (server or client)
	 * @param machinePos  The machine's block position
	 * @return List of PedestalOfLivingRockBlockEntity instances that are in range
	 */
	public static List<PedestalOfLivingRockBlockEntity> findNearbyPedestals(Level level, BlockPos machinePos) {
		List<PedestalOfLivingRockBlockEntity> nearbyPedestals = new ArrayList<>();
		Set<BlockPos> foundPositions = new HashSet<>();
		int searchRadius = BotaniaRunicRitualizationConfig.get().pedestalSearchRadius;
		for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
			for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
				if (offsetX == 0 && offsetZ == 0) continue;
				BlockPos candidatePos = machinePos.offset(offsetX, 0, offsetZ);
				if (foundPositions.contains(candidatePos)) continue;
				BlockEntity blockEntityAtPos = level.getBlockEntity(candidatePos);
				if (blockEntityAtPos instanceof PedestalOfLivingRockBlockEntity pedestal && !pedestal.getStack().isEmpty()) {
					foundPositions.add(candidatePos);
					nearbyPedestals.add(pedestal);
				}
			}
		}
		return nearbyPedestals;
	}
	//endregion
}
