/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PetalApothecaryEverflowingMenu.java
 * - Menu for the Petal Apothecary of the Everflowing.
 * - Exposes one seed/reagent slot plus the standard player inventory.
 * - Mana buffer and cooldown read directly from the synced block entity.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

public class PetalApothecaryEverflowingMenu extends AbstractContainerMenu {

	//region FIELD
	@Nullable public final PetalApothecaryEverflowingBlockEntity blockEntity;
	public final BlockPos blockPos;
	private final Container input;
	private final Container output;
	//endregion

	//region CONSTRUCT
	// Server-side ctor - BE is live.
	public PetalApothecaryEverflowingMenu(int syncId, Inventory playerInventory, PetalApothecaryEverflowingBlockEntity blockEntity) {
		super(BotaniaRunicRitualization.APOTHECARY_MENU_TYPE, syncId);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.input = new BackedContainer(blockEntity);
		this.output = new OutputContainer(blockEntity);
		addSlots(playerInventory);
	}

	// Client-side ctor - resolve BE by pos sent via ExtendedScreenHandlerFactory.
	public PetalApothecaryEverflowingMenu(int syncId, Inventory playerInventory, BlockPos pos) {
		super(BotaniaRunicRitualization.APOTHECARY_MENU_TYPE, syncId);
		this.blockPos = pos;
		BlockEntity blockEntityAtPos = playerInventory.player.level().getBlockEntity(pos);
		this.blockEntity = blockEntityAtPos instanceof PetalApothecaryEverflowingBlockEntity apothecary ? apothecary : null;
		this.input = blockEntity != null ? new BackedContainer(blockEntity) : new SimpleContainer(1);
		this.output = blockEntity != null ? new OutputContainer(blockEntity) : new SimpleContainer(1);
		addSlots(playerInventory);
	}

	private void addSlots(Inventory playerInventory) {
		// Slot 0: reagent/seed slot - centered, center of the ritual ellipse
		this.addSlot(new Slot(input, 0, 80, 37));
		// Slot 1: output slot (read-only, centered, above the seed slot)
		this.addSlot(new Slot(output, 0, 80, 15) {
			@Override
			public boolean mayPlace(ItemStack stack) { return false; }
		});

		// Player inventory (3 rows of 9) - bottom section
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 178));
		}
	}
	//endregion

	//region LOGIC
	@Override
	public boolean stillValid(Player player) {
		return blockEntity != null
				&& player.level().getBlockEntity(blockPos) == blockEntity
				&& player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack original = slot.getItem();
			result = original.copy();
			if (index == 0 || index == 1) {
				// Input or output -> player inventory
				if (!this.moveItemStackTo(original, 2, this.slots.size(), true)) return ItemStack.EMPTY;
			} else {
				// Player inventory -> input slot (output never accepts)
				if (!this.moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
			}
			if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
		}
		return result;
	}
	//endregion

	//region INPUTCONTAINER
	// Single-slot container backed by the block entity's seed field.
	private static class BackedContainer implements Container {
		private final PetalApothecaryEverflowingBlockEntity blockEntity;
		BackedContainer(PetalApothecaryEverflowingBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return 1; }
		@Override public boolean isEmpty() { return blockEntity.getSeedStack().isEmpty(); }
		@Override public ItemStack getItem(int slot) { return slot == 0 ? blockEntity.getSeedStack() : ItemStack.EMPTY; }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getSeedStack();
			if (slot != 0 || currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setSeedStack(currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			if (slot != 0) return ItemStack.EMPTY;
			ItemStack currentStack = blockEntity.getSeedStack();
			blockEntity.setSeedStack(ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) { if (slot == 0) blockEntity.setSeedStack(stack); }
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() { blockEntity.setSeedStack(ItemStack.EMPTY); }
	}
	//endregion

	//region OUTPUTCONTAINER
	// Container view backed by the BE's output stack.
	private static class OutputContainer implements Container {
		private final PetalApothecaryEverflowingBlockEntity blockEntity;
		OutputContainer(PetalApothecaryEverflowingBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return 1; }
		@Override public boolean isEmpty() { return blockEntity.getOutputStack().isEmpty(); }
		@Override public ItemStack getItem(int slot) { return slot == 0 ? blockEntity.getOutputStack() : ItemStack.EMPTY; }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getOutputStack();
			if (slot != 0 || currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setOutputStack(currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			if (slot != 0) return ItemStack.EMPTY;
			ItemStack currentStack = blockEntity.getOutputStack();
			blockEntity.setOutputStack(ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) { if (slot == 0) blockEntity.setOutputStack(stack); }
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() { blockEntity.setOutputStack(ItemStack.EMPTY); }
	}
	//endregion
}
