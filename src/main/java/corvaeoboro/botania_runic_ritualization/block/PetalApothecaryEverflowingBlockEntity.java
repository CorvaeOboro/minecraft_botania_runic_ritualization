/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PetalApothecaryEverflowingBlockEntity.java
 * - Drives the Petal Apothecary of the Everflowing.
 * - Holds: 1 seed (reagent) slot, persistent mana pool, output slot, cooldown.
 * - Implements Botania ManaReceiver + SparkAttachable so sparks/spreaders power it.
 * - Crafts botania:petal_apothecary recipes when seed matches reagent + pedestal ingredients available + mana sufficient.
 * - Mana cost is recipe-dependent via FlowerManaCosts (basic generating ~2.5k, high-end functional ~15k).
 * - Mana is persistent .
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;
import corvaeoboro.botania_runic_ritualization.screen.PetalApothecaryEverflowingMenu;

import com.google.common.base.Predicates;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.client.fx.SparkleParticleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PetalApothecaryEverflowingBlockEntity extends BlockEntity
		implements ManaReceiver, SparkAttachable, MenuProvider, ExtendedScreenHandlerFactory, SidedStorageBlockEntity {

	//region CONSTANT
	private static final String TAG_SEED = "Seed";
	private static final String TAG_OUTPUT = "Output";
	private static final String TAG_RECIPE_ID = "RecipeId";
	private static final String TAG_MANA = "Mana";
	private static final String TAG_MANA_TO_GET = "ManaToGet";
	private static final String TAG_COOLDOWN = "Cooldown";
	private static final String TAG_CRAFTING_ACTIVE = "CraftingActive";

	// Default maximum mana the apothecary can hold. Overridden by config at runtime.
	public static final int MANA_CAPACITY_DEFAULT = 50000;
	// Default ritual cooldown in ticks. Overridden by config at runtime.
	public static final int RITUAL_COOLDOWN_TICKS_DEFAULT = 200;

	// Config-backed max mana capacity.
	public static int getManaCapacityConfig() { return BotaniaRunicRitualizationConfig.get().petalApothecaryManaCapacity; }
	// Config-backed ritual cooldown ticks.
	public static int getRitualCooldownTicksConfig() { return BotaniaRunicRitualizationConfig.get().petalApothecaryCooldownTicks; }

	private static final ResourceLocation SOUND_ALTAR_CRAFT_ID = new ResourceLocation("botania", "altar_craft");

	// Block-event id for the once-per-craft client particle burst.
	private static final int EVENT_CRAFT = 1;
	// 5% buckets for mana sync throttling.
	private static final int MANA_SYNC_BUCKETS = 20;
	// Mask for stripe gate: try recipe enumeration only every 4 ticks per BE
	private static final long RECIPE_STRIPE_MASK = 0b11L;
	//endregion

	//region FIELD
	private ItemStack seed = ItemStack.EMPTY;
	private ItemStack output = ItemStack.EMPTY;
	@Nullable private ResourceLocation selectedRecipeId = null;
	private int mana = 0;
	private int manaToGet = 0;
	private int cooldown = 0;
	// 0 idle, 1 collecting, 2 ready (mana full + seed + ingredients present).
	private int signal = 0;
	// Last mana value pushed to client; for sync throttling
	private int lastSyncedMana = 0;
	// Ticks since mana was last received (for detecting stalled vs charging state).
	private int ticksSinceManaReceived = 1000;
	// True when the machine is actively crafting (cooldown, charging, or ready). Synced to client for VFX gating.
	private boolean craftingActive = false;
	// Mana pool linker for adjacent pool pull/distribute interactions.
	private ManaPoolLinker manaPoolLinker;
	//endregion

	//region CONSTRUCT
	public PetalApothecaryEverflowingBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaRunicRitualization.APOTHECARY_BE_TYPE, pos, state);
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
	// Core server-side per-tick logic: mana pool link, cooldown, recipe resolve, mana gate, consume, deposit.
	public static void serverTick(Level level, BlockPos pos, BlockState state, PetalApothecaryEverflowingBlockEntity self) {
		self.manaPoolLinker.tick(level);
		if (self.cooldown > 0) self.cooldown--;
		self.ticksSinceManaReceived++;

		// Resolve the selected recipe to compute its mana cost.
		PetalApothecaryRecipe recipe = self.resolveSelectedRecipe(level);
		int requiredMana = 0;
		if (recipe != null) {
			Recipe<?> recipeAsRecipe = (Recipe<?>) recipe;
			ItemStack recipeResult = recipeAsRecipe.getResultItem(level.registryAccess());
			requiredMana = FlowerManaCosts.getCostForItem(recipeResult);
		}
		if (self.manaToGet != requiredMana) {
			self.manaToGet = requiredMana;
			self.syncToClient();
		}

		// Update comparator signal.
		self.updateSignal(level);

		if (self.cooldown > 0) {
			self.setCraftingActive(true);
			return;
		}
		if (self.seed.isEmpty()) {
			self.setCraftingActive(false);
			return;
		}

		//  when sitting fully charged but unmatched,
		// don't enumerate every petal_apothecary recipe 20 times/second. Stripe per-BE by
		// hashing the position so neighbouring apothecaries don't all check on the same tick.
		long stripeOffset = (pos.asLong() & 0xFFL);
		if (((level.getGameTime() + stripeOffset) & RECIPE_STRIPE_MASK) != 0L) return;

		if (recipe == null) {
			self.setCraftingActive(false);
			return;
		}
		// Reagent (seed slot) must match the selected recipe's reagent ingredient.
		if (!recipe.getReagent().test(self.seed)) {
			self.setCraftingActive(false);
			return;
		}

		// NOTE: cast to Recipe<?> before invoking `getResultItem` / `getIngredients`. Botania's
		// PetalApothecaryRecipe inherits these from Recipe<Container>; 
		Recipe<?> recipeAsRecipe = (Recipe<?>) recipe;
		// Output slot must be able to accept the result; otherwise wait (mana stays buffered).
		ItemStack craftedResult = recipeAsRecipe.getResultItem(level.registryAccess()).copy();
		if (!self.canDepositOutput(craftedResult)) {
			self.setCraftingActive(false);
			return;
		}

		Map<PedestalOfLivingRockBlockEntity, Integer> consumePlan = planIngredientConsumption(
				recipeAsRecipe.getIngredients(), self.findAdjacentPedestals(level));
		if (consumePlan == null) {
			self.setCraftingActive(false); // missing ingredients
			return;
		}

		// All ingredients present - now gate on mana. Only show "charging" VFX/progress
		// when the machine  has everything it needs and is  waiting for mana.
		if (requiredMana > 0 && self.mana < requiredMana) {
			// Charging mana (received recently) vs stalled (no mana source)
			self.setCraftingActive(self.ticksSinceManaReceived < 20);
			return;
		}

		// Craft! Consume mana from the persistent pool.
		self.mana = Math.max(0, self.mana - requiredMana);
		self.seed.shrink(1);
		if (self.seed.isEmpty()) self.seed = ItemStack.EMPTY;
		for (Map.Entry<PedestalOfLivingRockBlockEntity, Integer> pedestalConsumeEntry : consumePlan.entrySet()) {
			pedestalConsumeEntry.getKey().consume(pedestalConsumeEntry.getValue());
		}
		self.depositOutput(craftedResult);

		self.playSound(SOUND_ALTAR_CRAFT_ID, 1F, 1F);
		// One block-event packet -> 25 client-local particles (vs 25 server packets).  
		level.blockEvent(pos, state.getBlock(), EVENT_CRAFT, 0);
		level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.2F);
		level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos);

		self.cooldown = getRitualCooldownTicksConfig();
		self.setCraftingActive(true);
		self.setChanged();
		self.syncToClient();
		self.updateSignal(level);
	}

	@Nullable
	private PetalApothecaryRecipe resolveSelectedRecipe(Level level) {
		if (selectedRecipeId == null) return null;
		return level.getRecipeManager().byKey(selectedRecipeId)
				.filter(recipe -> recipe instanceof PetalApothecaryRecipe)
				.map(recipe -> (PetalApothecaryRecipe) recipe)
				.orElse(null);
	}

	private List<PedestalOfLivingRockBlockEntity> findAdjacentPedestals(Level level) {
		return PedestalFinder.findNearbyPedestals(level, getBlockPos());
	}

	// Exposed for the client Menu lookup + any future BER.
	public List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		if (level == null) return java.util.Collections.emptyList();
		return findAdjacentPedestals(level);
	}

	// Called by the block class when a neighbor changes, to invalidate the mana pool cache.
	public void onNeighborChanged() {
		manaPoolLinker.onNeighborChanged();
	}

	/**
	 * Greedy ingredient -> pedestal assignment. One pedestal can supply multiple ingredients
	 * up to its remaining count. Returns per-pedestal consumption plan, or null if any
	 * ingredient is unsatisfiable.
	 */
	@Nullable
	private static Map<PedestalOfLivingRockBlockEntity, Integer> planIngredientConsumption(
			NonNullList<Ingredient> ingredients,
			List<PedestalOfLivingRockBlockEntity> pedestals) {
		Map<PedestalOfLivingRockBlockEntity, Integer> remainingStacksPerPedestal = new HashMap<>();
		for (PedestalOfLivingRockBlockEntity pedestal : pedestals) remainingStacksPerPedestal.put(pedestal, pedestal.getStack().getCount());
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
	//endregion

	//region INVENTORY
	public ItemStack getSeedStack() { return seed; }
	public void setSeedStack(ItemStack stack) {
		this.seed = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	public ItemStack getOutputStack() { return output; }
	public void setOutputStack(ItemStack stack) {
		this.output = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	private boolean canDepositOutput(ItemStack result) {
		if (result.isEmpty()) return true;
		if (output.isEmpty()) return true;
		if (!ItemStack.isSameItemSameTags(output, result)) return false;
		return output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

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
	// Fabric Transfer API: pipes can insert seeds (reagents) into the seed slot and
	// extract crafted flowers from the output slot.
	@Nullable
	private Storage<ItemVariant> itemStorage;

	@Override
	public Storage<ItemVariant> getItemStorage(Direction side) {
		if (itemStorage == null) {
			itemStorage = new CombinedStorage<>(List.of(
					new ApothecarySeedStorage(),
					new ApothecaryOutputStorage()
			));
		}
		return itemStorage;
	}

	// Insert-only storage for the seed (reagent) input slot.
	private class ApothecarySeedStorage extends SingleStackStorage {
		@Override
		protected ItemStack getStack() { return seed; }

		@Override
		protected void setStack(ItemStack stack) {
			seed = stack == null ? ItemStack.EMPTY : stack;
			setChanged();
			syncToClient();
		}

		@Override
		protected boolean canExtract(ItemVariant itemVariant) { return false; }

		@Override
		public boolean supportsExtraction() { return false; }
	}

	// Extract-only storage for the crafted flower output slot.
	private class ApothecaryOutputStorage extends SingleStackStorage {
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
		// Mana pool always accepts mana up to MANA_CAPACITY.
		this.mana = Math.min(this.mana + amount, getManaCapacityConfig());
		this.ticksSinceManaReceived = 0;
		setChanged();
		// Throttle the chunk-data packet to 5% buckets  
		int previousSyncBucket = (int)((long) lastSyncedMana * MANA_SYNC_BUCKETS / getManaCapacityConfig());
		int currentSyncBucket = (int)((long) mana * MANA_SYNC_BUCKETS / getManaCapacityConfig());
		if (currentSyncBucket != previousSyncBucket || mana == getManaCapacityConfig()) {
			lastSyncedMana = mana;
			syncToClient();
		}
		if (level != null) {
			updateSignal(level);
		}
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
		if (tag.contains(TAG_SEED)) {
			this.seed = ItemStack.of(tag.getCompound(TAG_SEED));
		} else {
			this.seed = ItemStack.EMPTY;
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
		this.cooldown = tag.getInt(TAG_COOLDOWN);
		this.craftingActive = tag.getBoolean(TAG_CRAFTING_ACTIVE);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!seed.isEmpty()) {
			CompoundTag seedTag = new CompoundTag();
			seed.save(seedTag);
			tag.put(TAG_SEED, seedTag);
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
		tag.putInt(TAG_COOLDOWN, cooldown);
		tag.putBoolean(TAG_CRAFTING_ACTIVE, craftingActive);
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
	private void updateSignal(Level level) {
		int newSignal;
		if (selectedRecipeId == null || manaToGet <= 0) {
			newSignal = 0;
		} else if (mana >= manaToGet && !seed.isEmpty()) {
			newSignal = 2;
		} else {
			newSignal = 1;
		}
		if (newSignal != signal) {
			signal = newSignal;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
	public int getCurrentManaPublic() { return mana; }
	public int getManaToGetPublic() { return manaToGet; }
	public int getManaCapacity() { return getManaCapacityConfig(); }
	public int getCooldown() { return cooldown; }
	@Nullable public ResourceLocation getSelectedRecipeId() { return selectedRecipeId; }

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
		this.selectedRecipeId = id;
		setChanged();
		syncToClient();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.botania_runic_ritualization.petal_apothecary_everflowing");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
		return new PetalApothecaryEverflowingMenu(containerId, playerInv, this);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
		buf.writeBlockPos(getBlockPos());
	}
	//endregion

	// Small utility to avoid javac warnings for unused import in certain toolchains.
	@SuppressWarnings("unused") private static final Object UNUSED = Objects.class;
}
