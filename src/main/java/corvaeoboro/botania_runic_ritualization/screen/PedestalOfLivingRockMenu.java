/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalOfLivingRockMenu.java
 * - Container/menu for the Pedestal of Living Rock.
 * - Single slot view backed by the block entity's stack. Shift-click fills from player inventory.
 * - Rejects mixing item types (mayPlace returns false when existing content differs).
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;

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

public class PedestalOfLivingRockMenu extends AbstractContainerMenu {

	//region FIELD
	@Nullable public final PedestalOfLivingRockBlockEntity blockEntity;
	public final BlockPos blockPos;
	private final Container input;
	//endregion

	//region CONSTRUCT
	// Server-side ctor (live BE).
	public PedestalOfLivingRockMenu(int syncId, Inventory playerInventory, PedestalOfLivingRockBlockEntity blockEntity) {
		super(BotaniaRunicRitualization.PEDESTAL_MENU_TYPE, syncId);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.input = new BackedContainer(blockEntity);
		addSlots(playerInventory);
	}

	// Client-side ctor (resolve BE from pos).
	public PedestalOfLivingRockMenu(int syncId, Inventory playerInventory, BlockPos pos) {
		super(BotaniaRunicRitualization.PEDESTAL_MENU_TYPE, syncId);
		this.blockPos = pos;
		BlockEntity blockEntityAtPos = playerInventory.player.level().getBlockEntity(pos);
		this.blockEntity = blockEntityAtPos instanceof PedestalOfLivingRockBlockEntity pedestal ? pedestal : null;
		this.input = blockEntity != null ? new BackedContainer(blockEntity) : new SimpleContainer(1);
		addSlots(playerInventory);
	}

	private void addSlots(Inventory playerInventory) {
		// Single slot centered above the player inventory at standard chest-style x=80, y=35
		this.addSlot(new Slot(input, 0, 80, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				ItemStack existing = this.getItem();
				if (existing.isEmpty()) return true;
				return ItemStack.isSameItemSameTags(existing, stack);
			}
		});

		// Player inventory
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		// Hotbar
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
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
			if (index == 0) {
				// Pedestal -> player inventory
				if (!this.moveItemStackTo(original, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Player inventory -> pedestal slot (only if compatible)
				ItemStack existing = this.slots.get(0).getItem();
				if (existing.isEmpty() || ItemStack.isSameItemSameTags(existing, original)) {
					if (!this.moveItemStackTo(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else {
					return ItemStack.EMPTY;
				}
			}
			if (original.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}
	//endregion

	//region INPUTCONTAINER
	// Container view backed directly by the BE's stack field (no copy).
	private static class BackedContainer implements Container {
		private final PedestalOfLivingRockBlockEntity blockEntity;
		BackedContainer(PedestalOfLivingRockBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return 1; }
		@Override public boolean isEmpty() { return blockEntity.getStack().isEmpty(); }
		@Override public ItemStack getItem(int slot) { return slot == 0 ? blockEntity.getStack() : ItemStack.EMPTY; }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getStack();
			if (slot != 0 || currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setStack(currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			if (slot != 0) return ItemStack.EMPTY;
			ItemStack currentStack = blockEntity.getStack();
			blockEntity.setStack(ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) {
			if (slot == 0) blockEntity.setStack(stack);
		}
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() { blockEntity.setStack(ItemStack.EMPTY); }
	}
	//endregion
}
