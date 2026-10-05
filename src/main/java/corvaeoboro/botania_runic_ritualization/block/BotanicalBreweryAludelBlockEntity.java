/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotanicalBreweryAludelBlockEntity.java
 * - Drives the Botanical Brewery Aludel: the brewery equivalent of the Runic Altar Dais.
 * - Holds: 1 container input slot (vial/flask/incense stick/blood pendant), 4 output slots, selected brew recipe id, persistent mana pool.
 * - Implements Botania ManaReceiver + SparkAttachable so sparks/spreaders power it.
 * - Crafts native botania:brew recipes; mana cost respects container-specific multiplier (vial 1x, flask 2x, pendant/incense 10x).
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;
import corvaeoboro.botania_runic_ritualization.screen.BotanicalBreweryAludelMenu;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
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

import vazkii.botania.api.brew.BrewContainer;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.api.recipe.BotanicalBreweryRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BotanicalBreweryAludelBlockEntity extends BlockEntity
		implements ManaReceiver, SparkAttachable, MenuProvider, ExtendedScreenHandlerFactory, SidedStorageBlockEntity {

	//region CONSTANT
	private static final String TAG_CONTAINER = "Container";
	private static final String TAG_OUTPUTS = "Outputs";
	private static final String TAG_RECIPE_ID = "RecipeId";
	private static final String TAG_MANA = "Mana";
	private static final String TAG_MANA_TO_GET = "ManaToGet";
	private static final String TAG_CRAFTING_ACTIVE = "CraftingActive";

	// Default maximum mana the aludel can hold. Overridden by config at runtime.
	public static final int MANA_CAPACITY_DEFAULT = 50000;
	// Default mana cost multiplier. Overridden by config at runtime.
	public static final float MANA_MULTIPLIER_DEFAULT = 1.0F;
	// Default ritual cooldown in ticks. Overridden by config at runtime.
	public static final int RITUAL_COOLDOWN_TICKS_DEFAULT = 200;

	// Config-backed max mana capacity.
	public static int getManaCapacityConfig() { return BotaniaRunicRitualizationConfig.get().botanicalBreweryManaCapacity; }
	// Config-backed mana cost multiplier.
	public static float getManaMultiplierConfig() { return BotaniaRunicRitualizationConfig.get().botanicalBreweryManaMultiplier; }
	// Config-backed ritual cooldown ticks.
	public static int getRitualCooldownTicksConfig() { return BotaniaRunicRitualizationConfig.get().botanicalBreweryCooldownTicks; }

	private static final ResourceLocation SOUND_START_ID = new ResourceLocation("botania", "rune_altar_start");
	private static final ResourceLocation SOUND_CRAFT_ID = new ResourceLocation("botania", "altar_craft");

	private static final int EVENT_CRAFT = 1;
	private static final int MANA_SYNC_BUCKETS = 20;
	// Brews are single-item outputs that don't stack, so the aludel has a row of output slots.
	public static final int OUTPUT_SLOT_COUNT = 6;
	//endregion

	//region FIELD
	private ItemStack container = ItemStack.EMPTY;
	private final ItemStack[] outputs = new ItemStack[OUTPUT_SLOT_COUNT];
	{ java.util.Arrays.fill(outputs, ItemStack.EMPTY); }
	@Nullable private ResourceLocation selectedRecipeId = null;
	private int mana = 0;
	private int manaToGet = 0;
	private int cooldown = 0;
	private int signal = 0;
	private int lastSyncedMana = 0;
	// Ticks since mana was last received (for detecting stalled vs charging state).
	private int ticksSinceManaReceived = 1000;
	// True when the machine is actively crafting (cooldown, charging, or ready). Synced to client for VFX gating.
	private boolean craftingActive = false;
	// Mana pool linker for adjacent pool pull/distribute interactions.
	private ManaPoolLinker manaPoolLinker;
	//endregion

	//region CONSTRUCT
	public BotanicalBreweryAludelBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaRunicRitualization.ALUDEL_BE_TYPE, pos, state);
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
	public static void serverTick(Level level, BlockPos pos, BlockState state, BotanicalBreweryAludelBlockEntity self) {
		self.manaPoolLinker.tick(level);
		if (self.cooldown > 0) {
			self.cooldown--;
			self.emitCooldownWisps(level, pos);
		}
		self.ticksSinceManaReceived++;

		BotanicalBreweryRecipe recipe = self.resolveRecipe(level);
		if (recipe == null) {
			if (self.manaToGet != 0) {
				self.manaToGet = 0;
				self.syncToClient();
			}
			self.setCraftingActive(false);
			self.updateSignal();
			return;
		}

		// Calculate the actual mana cost based on the container type, then apply multiplier
		int requiredManaForCurrentRecipe = (int) (self.getActualManaCost(recipe) * getManaMultiplierConfig());
		if (self.manaToGet != requiredManaForCurrentRecipe) {
			self.manaToGet = requiredManaForCurrentRecipe;
			self.syncToClient();
		}

		// "Ready" sparkles
		if (self.mana >= requiredManaForCurrentRecipe && !self.container.isEmpty()
				&& isBrewContainer(self.container) && level.random.nextInt(6) == 0) {
			self.emitReadySparkles(level, pos);
		}

		self.updateSignal();

		if (self.cooldown > 0) {
			self.setCraftingActive(true);
			return;
		}
		if (self.container.isEmpty() || !isBrewContainer(self.container)) {
			self.setCraftingActive(false);
			return;
		}

		// Compute output based on the container
		ItemStack craftedResult = recipe.getOutput(self.container).copy();
		if (craftedResult.isEmpty()) {
			self.setCraftingActive(false);
			return;
		}
		if (!self.canDepositOutput(craftedResult)) {
			self.setCraftingActive(false);
			return;
		}

		// Find pedestal mapping for ingredients
		List<PedestalOfLivingRockBlockEntity> pedestals = self.findAdjacentPedestals(level);
		Map<PedestalOfLivingRockBlockEntity, Integer> consumePlan = planIngredientConsumption(
				((Recipe<?>) recipe).getIngredients(), pedestals);
		if (consumePlan == null) {
			self.setCraftingActive(false); // missing ingredients
			return;
		}

		// All ingredients present then gate on mana. only show charging VFX/progress
		// when the machine actually has everything it needs and is just waiting for mana.
		if (self.mana < requiredManaForCurrentRecipe) {
			// Charging mana (received recently) vs stalled (no mana source)
			self.setCraftingActive(self.ticksSinceManaReceived < 20);
			return;
		}

		// All conditions met , begin craft.
		self.mana = Math.max(0, self.mana - requiredManaForCurrentRecipe);
		self.container.shrink(1);
		if (self.container.isEmpty()) self.container = ItemStack.EMPTY;
		for (Map.Entry<PedestalOfLivingRockBlockEntity, Integer> pedestalConsumeEntry : consumePlan.entrySet()) {
			pedestalConsumeEntry.getKey().consume(pedestalConsumeEntry.getValue());
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

	// Computes the actual mana cost for the current container + recipe.
	private int getActualManaCost(BotanicalBreweryRecipe recipe) {
		if (container.isEmpty() || !(container.getItem() instanceof BrewContainer brewContainer)) {
			return recipe.getManaUsage();
		}
		return brewContainer.getManaCost(recipe.getBrew(), container);
	}

	@Nullable
	private BotanicalBreweryRecipe resolveRecipe(Level level) {
		if (selectedRecipeId == null) return null;
		Optional<? extends Recipe<?>> recipeByKey = level.getRecipeManager().byKey(selectedRecipeId);
		if (recipeByKey.isEmpty()) return null;
		Recipe<?> recipe = recipeByKey.get();
		return recipe instanceof BotanicalBreweryRecipe breweryRecipe ? breweryRecipe : null;
	}

	private List<PedestalOfLivingRockBlockEntity> findAdjacentPedestals(Level level) {
		return PedestalFinder.findNearbyPedestals(level, getBlockPos());
	}

	public List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		if (level == null) return java.util.Collections.emptyList();
		return findAdjacentPedestals(level);
	}

	// Called by the block class when a neighbor changes, to invalidate the mana pool cache.
	public void onNeighborChanged() {
		manaPoolLinker.onNeighborChanged();
	}

	@Nullable
	private static Map<PedestalOfLivingRockBlockEntity, Integer> planIngredientConsumption(
			NonNullList<Ingredient> ingredients,
			List<PedestalOfLivingRockBlockEntity> pedestals) {
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

	private static boolean isBrewContainer(ItemStack stack) {
		return stack.getItem() instanceof BrewContainer;
	}
	//endregion

	//region INVENTORY
	public ItemStack getContainerStack() { return container; }
	public void setContainerStack(ItemStack stack) {
		this.container = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	// Returns the output stack at the given slot index (0..OUTPUT_SLOT_COUNT-1).
	public ItemStack getOutputStack(int slot) {
		if (slot < 0 || slot >= OUTPUT_SLOT_COUNT) return ItemStack.EMPTY;
		return outputs[slot];
	}

	// Sets the output stack at the given slot index.
	public void setOutputStack(int slot, ItemStack stack) {
		if (slot < 0 || slot >= OUTPUT_SLOT_COUNT) return;
		this.outputs[slot] = stack == null ? ItemStack.EMPTY : stack;
		setChanged();
		syncToClient();
	}

	// Legacy single-output accessor: returns the first non-empty output slot, or EMPTY if all empty.
	public ItemStack getOutputStack() {
		for (ItemStack outputStack : outputs) if (!outputStack.isEmpty()) return outputStack;
		return ItemStack.EMPTY;
	}

	private boolean canDepositOutput(ItemStack result) {
		if (result.isEmpty()) return true;
		for (ItemStack outputStack : outputs) {
			if (outputStack.isEmpty()) return true;
			if (ItemStack.isSameItemSameTags(outputStack, result)
					&& outputStack.getCount() + result.getCount() <= outputStack.getMaxStackSize()) return true;
		}
		return false;
	}

	private void depositOutput(ItemStack result) {
		if (result.isEmpty()) return;
		for (int slotIndex = 0; slotIndex < OUTPUT_SLOT_COUNT; slotIndex++) {
			ItemStack outputStack = outputs[slotIndex];
			if (outputStack.isEmpty()) {
				outputs[slotIndex] = result.copy();
				setChanged();
				return;
			}
			if (ItemStack.isSameItemSameTags(outputStack, result)
					&& outputStack.getCount() + result.getCount() <= outputStack.getMaxStackSize()) {
				outputStack.grow(result.getCount());
				setChanged();
				return;
			}
		}
	}
	//endregion

	//region TRANSFER
	// Fabric Transfer API: pipes can insert containers (vials/flasks/pendants) into the
	// container slot and extract brewed items from the 6 output slots.
	@Nullable
	private Storage<ItemVariant> itemStorage;

	@Override
	public Storage<ItemVariant> getItemStorage(Direction side) {
		if (itemStorage == null) {
			List<SingleStackStorage> storages = new ArrayList<>();
			storages.add(new AludelContainerStorage());
			for (int slotIndex = 0; slotIndex < OUTPUT_SLOT_COUNT; slotIndex++) {
				storages.add(new AludelOutputStorage(slotIndex));
			}
			itemStorage = new CombinedStorage<>(storages);
		}
		return itemStorage;
	}

	// Insert-only storage for the container input slot (vial/flask/pendant).
	private class AludelContainerStorage extends SingleStackStorage {
		@Override
		protected ItemStack getStack() { return container; }

		@Override
		protected void setStack(ItemStack stack) {
			container = stack == null ? ItemStack.EMPTY : stack;
			setChanged();
			syncToClient();
		}

		@Override
		protected boolean canExtract(ItemVariant itemVariant) { return false; }

		@Override
		public boolean supportsExtraction() { return false; }
	}

	// Extract-only storage for one of the 6 output slots.
	private class AludelOutputStorage extends SingleStackStorage {
		private final int slotIndex;

		AludelOutputStorage(int slotIndex) {
			this.slotIndex = slotIndex;
		}

		@Override
		protected ItemStack getStack() { return outputs[slotIndex]; }

		@Override
		protected void setStack(ItemStack stack) {
			outputs[slotIndex] = stack == null ? ItemStack.EMPTY : stack;
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
		this.mana = Math.min(this.mana + amount, getManaCapacityConfig());
		this.ticksSinceManaReceived = 0;
		setChanged();
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
		if (tag.contains(TAG_CONTAINER)) {
			this.container = ItemStack.of(tag.getCompound(TAG_CONTAINER));
		} else {
			this.container = ItemStack.EMPTY;
		}
		// Load multi-slot outputs
		java.util.Arrays.fill(outputs, ItemStack.EMPTY);
		if (tag.contains(TAG_OUTPUTS)) {
			net.minecraft.nbt.ListTag outputList = tag.getList(TAG_OUTPUTS, 10);
			for (int slotIndex = 0; slotIndex < outputList.size() && slotIndex < OUTPUT_SLOT_COUNT; slotIndex++) {
				outputs[slotIndex] = ItemStack.of(outputList.getCompound(slotIndex));
			}
		}
		this.selectedRecipeId = tag.contains(TAG_RECIPE_ID) && !tag.getString(TAG_RECIPE_ID).isEmpty()
				? ResourceLocation.tryParse(tag.getString(TAG_RECIPE_ID)) : null;
		this.mana = tag.getInt(TAG_MANA);
		this.manaToGet = tag.getInt(TAG_MANA_TO_GET);
		this.craftingActive = tag.getBoolean(TAG_CRAFTING_ACTIVE);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!container.isEmpty()) {
			CompoundTag containerTag = new CompoundTag();
			container.save(containerTag);
			tag.put(TAG_CONTAINER, containerTag);
		}
		// Save multi-slot outputs
		net.minecraft.nbt.ListTag outputList = new net.minecraft.nbt.ListTag();
		for (ItemStack outputStack : outputs) {
			CompoundTag outputTag = new CompoundTag();
			outputStack.save(outputTag);
			outputList.add(outputTag);
		}
		tag.put(TAG_OUTPUTS, outputList);
		if (selectedRecipeId != null) {
			tag.putString(TAG_RECIPE_ID, selectedRecipeId.toString());
		}
		tag.putInt(TAG_MANA, mana);
		tag.putInt(TAG_MANA_TO_GET, manaToGet);
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
	private void updateSignal() {
		int newSignal;
		if (manaToGet <= 0) {
			newSignal = 0;
		} else if (mana >= manaToGet && !container.isEmpty() && isBrewContainer(container)) {
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
		SparkleParticleData data = SparkleParticleData.sparkle(0.6F, 0.2F, 0.9F, 0.3F, 8);
		serverLevel.sendParticles(data,
				pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
				2, 0.15, 0.05, 0.15, 0.0);
	}

	private void emitCooldownWisps(Level level, BlockPos pos) {
		if (!BotaniaRunicRitualizationConfig.get().enableVfx) return;
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (level.random.nextInt(2) != 0) return;
		WispParticleData data = WispParticleData.wisp(0.2F, 0.2F, 0.4F, 0.2F, 1F);
		serverLevel.sendParticles(data,
				pos.getX() + level.random.nextDouble(), pos.getY() + 0.85, pos.getZ() + level.random.nextDouble(),
				1, 0, 0.025, 0, 0.0);
	}

	private void playSound(ResourceLocation soundId, float volume, float pitch) {
		if (level == null || level.isClientSide) return;
		SoundEvent soundEvent = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(soundId);
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
		setChanged();
		syncToClient();
		if (changed && level != null && !level.isClientSide) {
			playSound(SOUND_START_ID, 0.8F, 1F);
			updateSignal();
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.botania_runic_ritualization.botanical_brewery_aludel");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
		return new BotanicalBreweryAludelMenu(containerId, playerInv, this);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
		buf.writeBlockPos(getBlockPos());
	}
	//endregion
}
