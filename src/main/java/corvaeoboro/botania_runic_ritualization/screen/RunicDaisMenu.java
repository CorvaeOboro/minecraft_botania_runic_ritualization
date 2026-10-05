/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RunicDaisMenu.java
 * - Container/menu for the Runic Altar Dais.
 * - Exposes input slot (Living Rock, centered) and output slot (selected rune result, above input).
 * - Recipe selection handled via SelectRunePayload packet stored on the block entity.
 * - Standard player inventory + hotbar at the bottom (240-tall GUI).
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.RunicAltarDaisBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

public class RunicDaisMenu extends AbstractContainerMenu {

	//region FIELD
	@Nullable public final RunicAltarDaisBlockEntity blockEntity;
	public final BlockPos blockPos;
	private final Container input;
	private final Container output;
	//endregion

	//region CONSTRUCT
	// Server-side constructor (has the live block entity).
	public RunicDaisMenu(int syncId, Inventory playerInventory, RunicAltarDaisBlockEntity blockEntity) {
		super(BotaniaRunicRitualization.DAIS_MENU_TYPE, syncId);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.input = new BackedContainer(blockEntity);
		this.output = new OutputContainer(blockEntity);
		addSlots(playerInventory);
	}

	// Client-side constructor (resolve BE from the position).
	public RunicDaisMenu(int syncId, Inventory playerInventory, BlockPos pos) {
		super(BotaniaRunicRitualization.DAIS_MENU_TYPE, syncId);
		this.blockPos = pos;
		BlockEntity blockEntityAtPos = playerInventory.player.level().getBlockEntity(pos);
		this.blockEntity = blockEntityAtPos instanceof RunicAltarDaisBlockEntity dais ? dais : null;
		this.input = blockEntity != null ? new BackedContainer(blockEntity) : new SimpleContainer(1);
		this.output = blockEntity != null ? new OutputContainer(blockEntity) : new SimpleContainer(1);
		addSlots(playerInventory);
	}

	private void addSlots(Inventory playerInventory) {
		// Slot 0: input (Livingrock) - centered, center of the ritual ellipse
		this.addSlot(new Slot(input, 0, 80, 37) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BuiltInRegistries.ITEM.getKey(stack.getItem())
						.equals(RunicAltarDaisBlockEntity.BOTANIA_LIVINGROCK);
			}
		});
		// Slot 1: output (read-only - players can extract but not insert) - centered, above input
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
		// Hotbar
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
				if (!this.moveItemStackTo(original, 2, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Player inventory -> input slot only if livingrock; output slot never accepts.
				if (BuiltInRegistries.ITEM.getKey(original.getItem())
						.equals(RunicAltarDaisBlockEntity.BOTANIA_LIVINGROCK)) {
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
	// Container view backed by the block entity's livingrock field.
	private static class BackedContainer implements Container {
		private final RunicAltarDaisBlockEntity blockEntity;
		BackedContainer(RunicAltarDaisBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return 1; }
		@Override public boolean isEmpty() { return blockEntity.getLivingrockStack().isEmpty(); }
		@Override public ItemStack getItem(int slot) { return slot == 0 ? blockEntity.getLivingrockStack() : ItemStack.EMPTY; }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getLivingrockStack();
			if (slot != 0 || currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setLivingrockStack(currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			if (slot != 0) return ItemStack.EMPTY;
			ItemStack currentStack = blockEntity.getLivingrockStack();
			blockEntity.setLivingrockStack(ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) {
			if (slot == 0) blockEntity.setLivingrockStack(stack);
		}
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() { blockEntity.setLivingrockStack(ItemStack.EMPTY); }
	}
	//endregion

	//region OUTPUTCONTAINER
	// Container view backed by the BE's output stack.
	private static class OutputContainer implements Container {
		private final RunicAltarDaisBlockEntity blockEntity;
		OutputContainer(RunicAltarDaisBlockEntity blockEntity) { this.blockEntity = blockEntity; }
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
