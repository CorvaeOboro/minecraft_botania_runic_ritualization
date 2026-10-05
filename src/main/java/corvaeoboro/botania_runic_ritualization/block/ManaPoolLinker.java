/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # ManaPoolLinker.java
 * - Shared helper for all three ritual machines to interact with adjacent Botania mana pools.
 *   1. PULL: When the machine's internal pool is not full, drain mana from adjacent pools .
 *   2. DISTRIBUTE: When the machine's internal pool is full, evenly distribute excess to adjacent pools,
 *      acting as a connector that balances mana between pools  
 * 
 * -  performance design goals:
 *   * Long intervals between pool status checks (base ~100 ticks / 5 seconds).
 *   * Position-based offsets so neighboring machines don't all trigger on the same tick.
 *   * Cache of adjacent ManaPool BEs, invalidated only when a nearby block changes.
 *   * if no pools were found on the last scan, the next scan interval is progressively extended 
 *   * Only scans for pools when the cache is invalid; between scans, uses the cached list.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Helper that manages mana pool interactions for a ritual machine block entity.
 * The owning BE must implement {@link ManaReceiver} (or at least expose mana + capacity).
 */
public class ManaPoolLinker {

	//region CONSTANT
	// Maximum interval (in ticks) between pool scans when no pools were found last time. ~30 seconds. 
	private static final int MAX_IDLE_INTERVAL = 600;
	// How much to extend the interval each time no pools are found. 
	private static final int IDLE_EXTENSION = 50;
	// Threshold (fraction of max) above which the machine considers itself "full" for distribution. 
	private static final float FULL_THRESHOLD = 0.98F;
	// Minimum imbalance (fraction of pool capacity) between pools to trigger redistribution. 
	private static final float IMBALANCE_THRESHOLD = 0.10F;
	//endregion

	//region HOST
	// Interface the owning BE implements to expose its mana state. 
	public interface Host {
		int getLinkerMana();
		int getLinkerManaCapacity();
		void setLinkerMana(int mana);
	}
	//endregion

	//region FIELD
	private final Host host;
	private final BlockPos pos;

	// Cached list of adjacent mana pools. Null means "needs rescan". 
	private List<ManaPool> cachedPools = null;
	// Current tick counter for scheduling. 
	private int tickCounter = 0;
	// Current effective interval between checks. Grows when no pools found. 
	private int currentInterval = BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval;
	// Position-based offset so neighboring machines don't all check on the same tick. 
	private final int tickOffset;
	//endregion

	//region CONSTRUCT
	public ManaPoolLinker(Host host, BlockPos pos) {
		this.host = host;
		this.pos = pos;
		// Mix even/odd + position hash to create a per-position offset in [0, BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval).
		long hash = pos.asLong();
		int evenOdd = (int) (hash % 2);
		int posHash = (int) Math.floorMod(hash ^ (hash >>> 17), BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval);
		this.tickOffset = (posHash + evenOdd * 37) % BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval;
	}

	/**
	 * Called when a neighboring block changes. Invalidates the pool cache so the next
	 * tick will rescan for adjacent pools. Also resets the idle interval so the machine
	 * promptly checks if pools appeared or disappeared.
	 */
	public void onNeighborChanged() {
		cachedPools = null;
		currentInterval = BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval;
	}
	//endregion

	//region TICK
	/**
	 * Called every server tick by the owning block entity.
	 * Only does work when the tick counter hits the scheduled check interval.
	 *
	 * @param level The server level
	 */
	public void tick(Level level) {
		if (level.isClientSide) return;
		if (!BotaniaRunicRitualizationConfig.get().manaPoolLinkerEnabled) return;
		tickCounter++;
		if ((tickCounter + tickOffset) % currentInterval != 0) return;

		// Rescan pools if cache is invalid
		if (cachedPools == null) {
			cachedPools = scanAdjacentPools(level);
			if (cachedPools.isEmpty()) {
				// No pools found - extend the idle interval for next time
				currentInterval = Math.min(MAX_IDLE_INTERVAL, currentInterval + IDLE_EXTENSION);
				return;
			} else {
				// Pools found - reset to base interval
				currentInterval = BotaniaRunicRitualizationConfig.get().manaPoolLinkerBaseCheckInterval;
			}
		}

		if (cachedPools.isEmpty()) return;

		int machineMana = host.getLinkerMana();
		int machineCapacity = host.getLinkerManaCapacity();
		boolean machineIsFull = machineMana >= (int) (machineCapacity * FULL_THRESHOLD);

		if (machineIsFull) {
			// DISTRIBUTE: machine is full, balance mana to adjacent pools
			distributeToPools(level, machineMana, machineCapacity);
		} else {
			// PULL: machine needs mana, drain from adjacent pools
			pullFromPools(level, machineMana, machineCapacity);
		}
	}
	//endregion

	//region PULL
	// Pulls mana from adjacent pools into the machine's internal pool.
	// Drains evenly from all pools that have mana, respecting each pool's own capacity.
	private void pullFromPools(Level level, int machineMana, int machineCapacity) {
		int machineSpace = machineCapacity - machineMana;
		if (machineSpace <= 0) return;

		int manaToPull = Math.min(BotaniaRunicRitualizationConfig.get().manaPoolLinkerTransferPerCheck, machineSpace);
		int poolsWithManaCount = 0;
		for (ManaPool pool : cachedPools) {
			// A full pool still has mana to give - isFull() means at capacity, not locked.
			if (pool.getCurrentMana() > 0) {
				poolsWithManaCount++;
			}
		}
		if (poolsWithManaCount == 0) return;

		int perPoolAmount = manaToPull / poolsWithManaCount;
		int remainder = manaToPull % poolsWithManaCount;
		int totalPulled = 0;

		for (ManaPool pool : cachedPools) {
			if (pool instanceof BlockEntity poolBlockEntity && poolBlockEntity.isRemoved()) continue;
			int availableMana = pool.getCurrentMana();
			if (availableMana <= 0) continue;
			int takeAmount = Math.min(perPoolAmount, availableMana);
			if (remainder > 0) {
				takeAmount = Math.min(takeAmount + 1, availableMana);
				remainder--;
			}
			if (takeAmount <= 0) continue;
			pool.receiveMana(-takeAmount);
			totalPulled += takeAmount;
		}

		if (totalPulled > 0) {
			host.setLinkerMana(machineMana + totalPulled);
		}
	}
	//endregion

	//region DISTRIBUTE
	/**
	 * Distributes mana from the machine's full internal pool to adjacent pools.
	 * Acts as a connector: if there's a significant imbalance between adjacent pools,
	 * it transfers mana from the machine to the least-full pools to balance them.
	 */
	private void distributeToPools(Level level, int machineMana, int machineCapacity) {
		// Only distribute if we have excess above the full threshold
		int excessMana = machineMana - (int) (machineCapacity * FULL_THRESHOLD);
		if (excessMana <= 0) return;

		// Check for imbalance between adjacent pools
		// If all pools are nearly equally full (within IMBALANCE_THRESHOLD), don't distribute.
		int minPoolMana = Integer.MAX_VALUE;
		int maxPoolMana = 0;
		int poolsNeedingManaCount = 0;
		for (ManaPool pool : cachedPools) {
			if (pool instanceof BlockEntity poolBlockEntity && poolBlockEntity.isRemoved()) continue;
			int poolMana = pool.getCurrentMana();
			int poolCap = pool.getMaxMana();
			if (poolMana < poolCap) {
				poolsNeedingManaCount++;
			}
			minPoolMana = Math.min(minPoolMana, poolMana);
			maxPoolMana = Math.max(maxPoolMana, poolMana);
		}

		if (poolsNeedingManaCount == 0) return; // all pools full, nothing to do

		// Check if there's a significant imbalance between pools
		// Also distribute if any pool simply has room (machine is full, might as well share)
		int manaToDistribute = Math.min(BotaniaRunicRitualizationConfig.get().manaPoolLinkerTransferPerCheck, excessMana);
		int perPoolAmount = manaToDistribute / poolsNeedingManaCount;
		int remainder = manaToDistribute % poolsNeedingManaCount;
		int totalSent = 0;

		for (ManaPool pool : cachedPools) {
			if (pool instanceof BlockEntity poolBlockEntity && poolBlockEntity.isRemoved()) continue;
			int poolMana = pool.getCurrentMana();
			int poolCap = pool.getMaxMana();
			int poolSpace = poolCap - poolMana;
			if (poolSpace <= 0) continue;
			int giveAmount = Math.min(perPoolAmount, poolSpace);
			if (remainder > 0) {
				giveAmount = Math.min(giveAmount + 1, poolSpace);
				remainder--;
			}
			if (giveAmount <= 0) continue;
			pool.receiveMana(giveAmount);
			totalSent += giveAmount;
		}

		if (totalSent > 0) {
			host.setLinkerMana(machineMana - totalSent);
		}
	}
	//endregion

	//region SCAN
	// Scans the 6 adjacent positions for Botania mana pool block entities.
	private List<ManaPool> scanAdjacentPools(Level level) {
		List<ManaPool> adjacentPools = new ArrayList<>();
		for (Direction direction : Direction.values()) {
			BlockPos adjacentPos = pos.relative(direction);
			BlockEntity blockEntityAtAdjacent = level.getBlockEntity(adjacentPos);
			if (blockEntityAtAdjacent instanceof ManaPool pool) {
				adjacentPools.add(pool);
			}
		}
		return adjacentPools;
	}
	//endregion
}
