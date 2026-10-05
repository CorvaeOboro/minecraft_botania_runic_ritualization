/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RunicAltarDaisBlockEntity.java
 * - Drives the Runic Altar Dais "machine".
 * - Holds: 1 inventory slot (livingrock), selected runic-altar recipe id, persistent mana pool, output slot.
 * - Implements Botania ManaReceiver + SparkAttachable so sparks/spreaders power it.
 * - Crafting uses native botania:runic_altar recipes; ingredients sourced from PedestalsOfLivingRock
 *   connected via radius-based adjacency. Mana is a persistent pool.
 * - Rune items (vazkii.botania.common.item.material.RuneItem) are required for recipe matching
 *   but are NOT consumed - matching the original Runic Altar's rune refund behavior.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;
import corvaeoboro.botania_runic_ritualization.screen.RunicDaisMenu;

import com.google.common.base.Predicates;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.item.material.RuneItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RunicAltarDaisBlockEntity extends BlockEntity
		implements ManaReceiver, SparkAttachable, ExtendedScreenHandlerFactory, SidedStorageBlockEntity {

	//region CONSTANT
	private static final String TAG_LIVINGROCK = "Livingrock";
	private static final String TAG_OUTPUT = "Output";
	private static final String TAG_RECIPE_ID = "RecipeId";
	private static final String TAG_MANA = "Mana";
	private static final String TAG_MANA_TO_GET = "ManaToGet";
	private static final String TAG_CRAFTING_ACTIVE = "CraftingActive";
	private static final String TAG_CRAFT_PROGRESS = "CraftProgress";

	public static final ResourceLocation BOTANIA_LIVINGROCK = new ResourceLocation("botania", "livingrock");

	private static final ResourceLocation SOUND_START_ID = new ResourceLocation("botania", "rune_altar_start");
	private static final ResourceLocation SOUND_CRAFT_ID = new ResourceLocation("botania", "rune_altar_craft");

	// Block-event id for the once-per-craft client particle burst. See triggerEvent(int, int).
	private static final int EVENT_CRAFT = 1;
	// How many sub-buckets the mana bar is split into for sync throttling (5% buckets).
	private static final int MANA_SYNC_BUCKETS = 20;
	// Default maximum mana the dais can hold. Overridden by config at runtime.
	public static final int MANA_CAPACITY_DEFAULT = 50000;
	// Default mana cost multiplier. Overridden by config at runtime.
	public static final float MANA_MULTIPLIER_DEFAULT = 1.0F;
	// Default ritual cooldown in ticks after a successful craft. Overridden by config at runtime.
	public static final int RITUAL_COOLDOWN_TICKS_DEFAULT = 100;
	// Default processing time in ticks before a craft completes. Overridden by config at runtime.
	public static final int CRAFT_DURATION_TICKS_DEFAULT = 400;

	// Config-backed max mana capacity.
	public static int getManaCapacityConfig() { return BotaniaRunicRitualizationConfig.get().runicAltarManaCapacity; }
	// Config-backed mana cost multiplier.
	public static float getManaMultiplierConfig() { return BotaniaRunicRitualizationConfig.get().runicAltarManaMultiplier; }
	// Config-backed ritual cooldown ticks.
	public static int getRitualCooldownTicksConfig() { return BotaniaRunicRitualizationConfig.get().runicAltarCooldownTicks; }
	// Config-backed craft duration ticks.
	public static int getCraftDurationTicksConfig() { return BotaniaRunicRitualizationConfig.get().runicAltarCraftDurationTicks; }
	//endregion

	//region FIELD
	private ItemStack livingrock = ItemStack.EMPTY;
	private ItemStack output = ItemStack.EMPTY;
	@Nullable private ResourceLocation selectedRecipeId = null;
	private int mana = 0;
	private int manaToGet = 0;
	private int cooldown = 0;
	// Progress toward completing a craft (0 = not started, craft duration = complete).
	private int craftProgress = 0;
	// Cached redstone comparator signal: 0 idle, 1 collecting, 2 ready.
	private int signal = 0;
	// Last mana value we pushed to the client, for sync throttling.
	private int lastSyncedMana = 0;
	// Ticks since mana was last received (for detecting stalled vs charging state).
	private int ticksSinceManaReceived = 1000;
	// True when the machine is actively crafting (cooldown, charging, or ready). Synced to client for VFX gating.
	private boolean craftingActive = false;
	// Mana pool linker for adjacent pool pull/distribute interactions.
	private ManaPoolLinker manaPoolLinker;
	//endregion

	//region CONSTRUCT
	public RunicAltarDaisBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaRunicRitualization.DAIS_BE_TYPE, pos, state);
		this.manaPoolLinker = new ManaPoolLinker(new ManaPoolLinker.Host() {
			@Override public int getLinkerMana() { return mana; }
			@Override public int getLinkerManaCapacity() { return getManaCapacityConfig(); }
			@Override public void setLinkerMana(int manaValue) {
				mana = manaValue;
				setChanged();
				syncToClient();
			}
		}, pos);
	}
	//endregion

	//region TICK
	// Core server-side per-tick logic: mana pool link, cooldown, recipe resolve, mana gate,
	// processing phase advancement, consume, deposit. Has a multi-tick craft progress phase.
	public static void serverTick(Level level, BlockPos pos, BlockState state, RunicAltarDaisBlockEntity self) {
		self.manaPoolLinker.tick(level);
		if (self.cooldown > 0) {
			self.cooldown--;
			self.emitCooldownWisps(level, pos);
		}
		self.ticksSinceManaReceived++;

		RunicAltarRecipe recipe = self.resolveRecipe(level);
		if (recipe == null) {
			// No recipe selected - mana pool persists, just clear the display cost.
			if (self.manaToGet != 0) {
				self.manaToGet = 0;
				self.syncToClient();
			}
			self.setCraftingActive(false);
			self.updateSignal();
			return;
		}

		int requiredManaForCurrentRecipe = (int) (recipe.getManaUsage() * getManaMultiplierConfig());
		if (self.manaToGet != requiredManaForCurrentRecipe) {
			self.manaToGet = requiredManaForCurrentRecipe;
			self.syncToClient();
		}

		// "Ready" state: mana pool has enough for this craft -> emit occasional teal sparkles.
		if (self.mana >= requiredManaForCurrentRecipe && self.livingrock.isEmpty() == false
				&& isLivingrock(self.livingrock) && level.random.nextInt(6) == 0) {
			self.emitReadySparkles(level, pos);
		}

		self.updateSignal();

		if (self.cooldown > 0) {
			self.setCraftingActive(true);
			return;
		}

		// If a craft is in progress, advance it.
		if (self.craftProgress > 0) {
			self.craftProgress++;
			self.setCraftingActive(true);
			// Emit processing wisps during crafting
			if (level.random.nextInt(3) == 0) {
				self.emitCooldownWisps(level, pos);
			}
			if (self.craftProgress >= getCraftDurationTicksConfig()) {
				// Craft complete - re-verify conditions, then consume and produce output.
				self.craftProgress = 0;
				ItemStack craftedResult = ((Recipe<?>) recipe).getResultItem(level.registryAccess()).copy();
				if (!self.canDepositOutput(craftedResult)) {
					self.setCraftingActive(false);
					return;
				}
				List<PedestalOfLivingRockBlockEntity> pedestals = self.findAdjacentPedestals(level);
				Map<PedestalOfLivingRockBlockEntity, Integer> consumePlan = planIngredientConsumption(((Recipe<?>) recipe).getIngredients(), pedestals);
				if (consumePlan == null) {
					self.setCraftingActive(false);
					return;
				}
				// Consume mana from the persistent pool.
				self.mana = Math.max(0, self.mana - requiredManaForCurrentRecipe);
				self.livingrock.shrink(1);
				if (self.livingrock.isEmpty()) self.livingrock = ItemStack.EMPTY;
				for (Map.Entry<PedestalOfLivingRockBlockEntity, Integer> pedestalConsumeEntry : consumePlan.entrySet()) {
					PedestalOfLivingRockBlockEntity pedestal = pedestalConsumeEntry.getKey();
					// Runes are catalysts - required for the recipe to match but not consumed,
					// matching Botania's RunicAltarBlockEntity which refunds RuneItem stacks.
					if (isRune(pedestal.getStack())) continue;
					pedestal.consume(pedestalConsumeEntry.getValue());
				}
				self.depositOutput(craftedResult);

				self.playSound(SOUND_CRAFT_ID, 1F, 1F);
				level.blockEvent(pos, state.getBlock(), EVENT_CRAFT, 0);
				level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos);

				self.cooldown = getRitualCooldownTicksConfig();
				self.setCraftingActive(true);
				self.setChanged();
				self.syncToClient();
				self.updateSignal();
			}
			return;
		}

		// No craft in progress - check if all conditions are met to start one.
		if (self.livingrock.isEmpty() || !isLivingrock(self.livingrock)) {
			self.setCraftingActive(false);
			return;
		}

		// Output slot must be able to accept the result; otherwise wait (mana stays in pool).
		ItemStack pendingResult = ((Recipe<?>) recipe).getResultItem(level.registryAccess()).copy();
		if (!self.canDepositOutput(pendingResult)) {
			self.setCraftingActive(false);
			return;
		}

		// Find pedestal mapping for ingredients
		List<PedestalOfLivingRockBlockEntity> pedestals = self.findAdjacentPedestals(level);
		Map<PedestalOfLivingRockBlockEntity, Integer> consumePlan = planIngredientConsumption(((Recipe<?>) recipe).getIngredients(), pedestals);
		if (consumePlan == null) {
			self.setCraftingActive(false); // missing ingredients
			return;
		}

		// All ingredients present - now gate on mana. Only show charging VFX/progress
		// when the machine actually has everything it needs and is just waiting for mana.
		if (self.mana < requiredManaForCurrentRecipe) {
			// Charging mana (received recently) vs stalled (no mana source)
			self.setCraftingActive(self.ticksSinceManaReceived < 20);
			return;
		}

		// All conditions met -> start the crafting process (processing phase).
		self.craftProgress = 1;
		self.playSound(SOUND_START_ID, 1F, 1F);
		self.setCraftingActive(true);
		self.setChanged();
		self.syncToClient();
	}

	@Nullable
	private RunicAltarRecipe resolveRecipe(Level level) {
		if (selectedRecipeId == null) return null;
		Optional<? extends Recipe<?>> recipeByKey = level.getRecipeManager().byKey(selectedRecipeId);
		if (recipeByKey.isEmpty()) return null;
		Recipe<?> recipe = recipeByKey.get();
		return recipe instanceof RunicAltarRecipe runicAltarRecipe ? runicAltarRecipe : null;
	}

	private List<PedestalOfLivingRockBlockEntity> findAdjacentPedestals(Level level) {
		return PedestalFinder.findNearbyPedestals(level, getBlockPos());
	}

	// Exposed for the Dais BER to scan adjacent pedestals client-side.
	public List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		if (level == null) return java.util.Collections.emptyList();
		return findAdjacentPedestals(level);
	}

	// Called by the block class when a neighbor changes, to invalidate the mana pool cache.
	public void onNeighborChanged() {
		manaPoolLinker.onNeighborChanged();
	}

	/**
	 * Greedy ingredient -> pedestal assignment. Returns a per-pedestal consumption plan,
	 * or null if any ingredient cannot be satisfied. Each ingredient consumes 1 unit from
	 * a single pedestal; a pedestal can supply multiple ingredients up to its remaining count.
	 */
	@Nullable
	private static Map<PedestalOfLivingRockBlockEntity, Integer> planIngredientConsumption(
			NonNullList<Ingredient> ingredients,
			List<PedestalOfLivingRockBlockEntity> pedestals) {
		// Snapshot remaining counts per pedestal
		Map<PedestalOfLivingRockBlockEntity, Integer> remainingStacksPerPedestal = new HashMap<>();
		for (PedestalOfLivingRockBlockEntity pedestal : pedestals) {
			remainingStacksPerPedestal.put(pedestal, pedestal.getStack().getCount());
		}
		Map<PedestalOfLivingRockBlockEntity, Integer> consumePlan = new HashMap<>();
		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty()) continue;
			boolean ingredientSatisfied = false;
			for (PedestalOfLivingRockBlockEntity pedestal : pedestals) {
				int remainingCount = remainingStacksPerPedestal.getOrDefault(pedestal, 0);
				if (remainingCount <= 0) continue;
				if (ingredient.test(pedestal.getStack())) {
					remainingStacksPerPedestal.put(pedestal, remainingCount - 1);
					consumePlan.merge(pedestal, 1, Integer::sum);
					ingredientSatisfied = true;
					break;
				}
			}
			if (!ingredientSatisfied) return null;
		}
		return consumePlan;
	}

	private static boolean isLivingrock(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BOTANIA_LIVINGROCK);
	}

	// Rune items are catalysts - required for recipe matching but not consumed on craft.
	private static boolean isRune(ItemStack stack) {
		return stack.getItem() instanceof RuneItem;
	}
	//endregion

	//region INVENTORY
	public ItemStack getLivingrockStack() { return livingrock; }
	public void setLivingrockStack(ItemStack stack) {
		this.livingrock = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	public ItemStack getOutputStack() { return output; }
	public void setOutputStack(ItemStack stack) {
		this.output = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	// True if {@code result} can be merged into the current output slot (empty or same item with room).
	private boolean canDepositOutput(ItemStack result) {
		if (result.isEmpty()) return true;
		if (output.isEmpty()) return true;
		if (!ItemStack.isSameItemSameTags(output, result)) return false;
		return output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	// Merge result into the output slot. Caller must have verified {@link #canDepositOutput}.
	private void depositOutput(ItemStack result) {
		if (result.isEmpty()) return;
		if (output.isEmpty()) {
			output = result.copy();
		} else {
			output.grow(result.getCount());
		}
		setChanged();
	}
	//endregion

	//region TRANSFER
	// Fabric Transfer API: pipes can insert livingrock into the input slot and extract
	// crafted runes from the output slot. Insert and extract are on separate slots.
	@Nullable
	private Storage<ItemVariant> itemStorage;

	@Override
	public Storage<ItemVariant> getItemStorage(Direction side) {
		if (itemStorage == null) {
			itemStorage = new CombinedStorage<>(List.of(
					new DaisInputStorage(),
					new DaisOutputStorage()
			));
		}
		return itemStorage;
	}

	// Insert-only storage for the livingrock input slot.
	private class DaisInputStorage extends SingleStackStorage {
		@Override
		protected ItemStack getStack() { return livingrock; }

		@Override
		protected void setStack(ItemStack stack) {
			livingrock = stack == null ? ItemStack.EMPTY : stack;
			setChanged();
			syncToClient();
		}

		@Override
		protected boolean canExtract(ItemVariant itemVariant) { return false; }

		@Override
		public boolean supportsExtraction() { return false; }
	}

	// Extract-only storage for the crafted output slot.
	private class DaisOutputStorage extends SingleStackStorage {
		@Override
		protected ItemStack getStack() { return output; }

		@Override
		protected void setStack(ItemStack stack) {
			output = stack == null ? ItemStack.EMPTY : stack;
			setChanged();
			syncToClient();
		}

		@Override
		protected boolean canInsert(ItemVariant itemVariant) { return false; }

		@Override
		public boolean supportsInsertion() { return false; }
	}
	//endregion

	//region MANA
	// ManaReceiver: Botania mana hardware (sparks, spreaders) powers the machine through these methods.
	@Override public Level getManaReceiverLevel() { return getLevel(); }
	@Override public BlockPos getManaReceiverPos() { return getBlockPos(); }
	@Override public int getCurrentMana() { return mana; }
	@Override public boolean isFull() { return mana >= getManaCapacityConfig(); }
	@Override public void receiveMana(int amount) {
		// Mana pool always accepts mana up to MANA_CAPACITY, regardless of recipe selection.
		this.mana = Math.min(this.mana + amount, getManaCapacityConfig());
		this.ticksSinceManaReceived = 0;
		setChanged();
		// Do not send a sync packet on every burst hit.
		// Only push a block update when the mana bar visibly moves (5% buckets) or hits the
		// boundaries (0/full). The serverTick path will push the final sync on craft regardless.
		int previousSyncBucket = (int)((long) lastSyncedMana * MANA_SYNC_BUCKETS / getManaCapacityConfig());
		int currentSyncBucket = (int)((long) mana * MANA_SYNC_BUCKETS / getManaCapacityConfig());
		if (currentSyncBucket != previousSyncBucket || mana == getManaCapacityConfig()) {
			lastSyncedMana = mana;
			syncToClient();
		}
		updateSignal();
	}
	@Override public boolean canReceiveManaFromBursts() { return !isFull(); }

	// SparkAttachable: allows sparks to be placed on the machine for mana networking.
	@Override public boolean canAttachSpark(ItemStack stack) { return true; }
	@Override public int getAvailableSpaceForMana() { return Math.max(0, getManaCapacityConfig() - mana); }
	@Override public ManaSpark getAttachedSpark() {
		if (level == null) return null;
		List<Entity> sparks = level.getEntitiesOfClass(Entity.class,
				new AABB(worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(),
						worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1),
				Predicates.instanceOf(ManaSpark.class));
		if (sparks.size() == 1) return (ManaSpark) sparks.get(0);
		return null;
	}
	@Override public boolean areIncomingTranfersDone() { return isFull(); }
	//endregion

	//region NBT
	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains(TAG_LIVINGROCK)) {
			this.livingrock = ItemStack.of(tag.getCompound(TAG_LIVINGROCK));
		} else {
			this.livingrock = ItemStack.EMPTY;
		}
		if (tag.contains(TAG_OUTPUT)) {
			this.output = ItemStack.of(tag.getCompound(TAG_OUTPUT));
		} else {
			this.output = ItemStack.EMPTY;
		}
		this.selectedRecipeId = tag.contains(TAG_RECIPE_ID) && !tag.getString(TAG_RECIPE_ID).isEmpty()
				? ResourceLocation.tryParse(tag.getString(TAG_RECIPE_ID)) : null;
		this.mana = tag.getInt(TAG_MANA);
		this.manaToGet = tag.getInt(TAG_MANA_TO_GET);
		this.craftingActive = tag.getBoolean(TAG_CRAFTING_ACTIVE);
		this.craftProgress = tag.getInt(TAG_CRAFT_PROGRESS);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!livingrock.isEmpty()) {
			CompoundTag livingrockTag = new CompoundTag();
			livingrock.save(livingrockTag);
			tag.put(TAG_LIVINGROCK, livingrockTag);
		}
		if (!output.isEmpty()) {
			CompoundTag outputTag = new CompoundTag();
			output.save(outputTag);
			tag.put(TAG_OUTPUT, outputTag);
		}
		if (selectedRecipeId != null) {
			tag.putString(TAG_RECIPE_ID, selectedRecipeId.toString());
		}
		tag.putInt(TAG_MANA, mana);
		tag.putInt(TAG_MANA_TO_GET, manaToGet);
		tag.putBoolean(TAG_CRAFTING_ACTIVE, craftingActive);
		tag.putInt(TAG_CRAFT_PROGRESS, craftProgress);
	}

	@Override public CompoundTag getUpdateTag() { CompoundTag tag = new CompoundTag(); saveAdditional(tag); return tag; }

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClient() {
		if (level != null && !level.isClientSide) {
			lastSyncedMana = mana;
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}
	//endregion

	//region SIGNAL
	/**
	 * Signal: 0 idle (no selection), 1 collecting mana, 2 ready (mana full + livingrock present).
	 * When the value changes, notify neighbors so comparators update.
	 */
	private void updateSignal() {
		int newSignal;
		if (manaToGet <= 0) {
			newSignal = 0;
		} else if (mana >= manaToGet && !livingrock.isEmpty() && isLivingrock(livingrock)) {
			newSignal = 2;
		} else {
			newSignal = 1;
		}
		if (newSignal != signal) {
			signal = newSignal;
			if (level != null) {
				level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
			}
		}
	}

	public int getSignal() { return signal; }
	//endregion

	//region VFX
	@Override
	public boolean triggerEvent(int id, int param) {
		if (id == EVENT_CRAFT) {
			if (level != null && level.isClientSide && BotaniaRunicRitualizationConfig.get().enableVfx) {
				for (int i = 0; i < 25; i++) {
					float r = level.random.nextFloat();
					float g = level.random.nextFloat();
					float b = level.random.nextFloat();
					SparkleParticleData data = SparkleParticleData.sparkle(level.random.nextFloat() + 0.3F, r, g, b, 10);
					level.addParticle(data,
							worldPosition.getX() + 0.5 + level.random.nextDouble() * 0.4 - 0.2,
							worldPosition.getY() + 1.05,
							worldPosition.getZ() + 0.5 + level.random.nextDouble() * 0.4 - 0.2,
							0, 0, 0);
				}
			}
			return true;
		}
		return super.triggerEvent(id, param);
	}

	private void emitReadySparkles(Level level, BlockPos pos) {
		if (!BotaniaRunicRitualizationConfig.get().enableVfx) return;
		if (!(level instanceof ServerLevel serverLevel)) return;
		// Teal sparkles matching Botania's runic altar ready color (0x00E4D7).
		SparkleParticleData data = SparkleParticleData.sparkle(0.6F, 0F, 0.9F, 0.84F, 8);
		serverLevel.sendParticles(data,
				pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
				2, 0.15, 0.05, 0.15, 0.0);
	}

	private void emitCooldownWisps(Level level, BlockPos pos) {
		if (!BotaniaRunicRitualizationConfig.get().enableVfx) return;
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (level.random.nextInt(2) != 0) return;
		WispParticleData data = WispParticleData.wisp(0.2F, 0.2F, 0.2F, 0.2F, 1F);
		serverLevel.sendParticles(data,
				pos.getX() + level.random.nextDouble(), pos.getY() + 0.85, pos.getZ() + level.random.nextDouble(),
				1, 0, 0.025, 0, 0.0);
	}

	private void playSound(ResourceLocation soundId, float volume, float pitch) {
		if (level == null || level.isClientSide) return;
		SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundId);
		if (soundEvent != null) {
			level.playSound(null, worldPosition, soundEvent, SoundSource.BLOCKS, volume, pitch);
		}
	}
	//endregion

	//region SCREEN
	// Getters and menu providers for the GUI screen.
	@Nullable public ResourceLocation getSelectedRecipeId() { return selectedRecipeId; }
	public int getCurrentManaPublic() { return mana; }
	public int getManaToGetPublic() { return manaToGet; }
	public int getManaCapacity() { return getManaCapacityConfig(); }
	public int getCooldown() { return cooldown; }
	public int getCraftProgress() { return craftProgress; }

	// True when the machine is actively crafting (cooldown, charging mana, or ready to craft). Client uses this to gate VFX.
	public boolean isCraftingActive() { return craftingActive; }

	// Updates craftingActive and syncs to client only when the value changes.
	private void setCraftingActive(boolean active) {
		if (active != craftingActive) {
			craftingActive = active;
			syncToClient();
		}
	}

	public void setSelectedRecipeId(@Nullable ResourceLocation id) {
		boolean changed = !java.util.Objects.equals(this.selectedRecipeId, id);
		this.selectedRecipeId = id;
		// Mana is a persistent pool - do NOT reset on recipe change.
		// manaToGet will be updated in serverTick to reflect the new recipe's cost.
		setChanged();
		syncToClient();
		if (changed && level != null && !level.isClientSide) {
			playSound(SOUND_START_ID, 0.8F, 1F);
			updateSignal();
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.botania_runic_ritualization.runic_altar_dais");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
		return new RunicDaisMenu(containerId, playerInv, this);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
		buf.writeBlockPos(getBlockPos());
	}
	//endregion
}
