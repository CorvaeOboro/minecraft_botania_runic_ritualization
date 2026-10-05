/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotanicalBreweryAludelMenu.java
 * - Container/menu for the Botanical Brewery Aludel.
 * - Exposes input slot (brew container: vial/flask/incense stick/blood pendant) + 4 output slots + standard player inventory.
 * - Recipe selection handled via SelectBrewPayload packet stored on the block entity.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.BotanicalBreweryAludelBlockEntity;

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

import vazkii.botania.api.brew.BrewContainer;

public class BotanicalBreweryAludelMenu extends AbstractContainerMenu {

	//region FIELD
	@Nullable public final BotanicalBreweryAludelBlockEntity blockEntity;
	public final BlockPos blockPos;
	private final Container input;
	private final Container output;
	// Number of output slots exposed by this menu (matches BE OUTPUT_SLOT_COUNT). 
	public static final int OUTPUT_SLOT_COUNT = BotanicalBreweryAludelBlockEntity.OUTPUT_SLOT_COUNT;
	// First slot index in this menu that is an output slot. 
	public static final int OUTPUT_SLOT_START = 1;
	//endregion

	//region CONSTRUCT
	public BotanicalBreweryAludelMenu(int syncId, Inventory playerInventory, BotanicalBreweryAludelBlockEntity blockEntity) {
		super(BotaniaRunicRitualization.ALUDEL_MENU_TYPE, syncId);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.input = new BackedContainer(blockEntity);
		this.output = new OutputContainer(blockEntity);
		addSlots(playerInventory);
	}

	public BotanicalBreweryAludelMenu(int syncId, Inventory playerInventory, BlockPos pos) {
		super(BotaniaRunicRitualization.ALUDEL_MENU_TYPE, syncId);
		this.blockPos = pos;
		BlockEntity blockEntityAtPos = playerInventory.player.level().getBlockEntity(pos);
		this.blockEntity = blockEntityAtPos instanceof BotanicalBreweryAludelBlockEntity aludel ? aludel : null;
		this.input = blockEntity != null ? new BackedContainer(blockEntity) : new SimpleContainer(1);
		this.output = blockEntity != null ? new OutputContainer(blockEntity) : new SimpleContainer(OUTPUT_SLOT_COUNT);
		addSlots(playerInventory);
	}

	private void addSlots(Inventory playerInventory) {
		// Slot 0: input (brew container) - centered in the ritual ellipse
		this.addSlot(new Slot(input, 0, 80, 37) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof BrewContainer;
			}
		});
		// Slots 1..N: output row (read-only), centered horizontally
		int outputRowStartX = (176 - OUTPUT_SLOT_COUNT * 18) / 2; // centered
		for (int outputSlotIndex = 0; outputSlotIndex < OUTPUT_SLOT_COUNT; outputSlotIndex++) {
			this.addSlot(new Slot(output, outputSlotIndex, outputRowStartX + outputSlotIndex * 18, 15) {
				@Override
				public boolean mayPlace(ItemStack stack) { return false; }
			});
		}

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
			int firstPlayerSlot = 1 + OUTPUT_SLOT_COUNT;
			if (index == 0) {
				// Input -> player inventory
				if (!this.moveItemStackTo(original, firstPlayerSlot, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= OUTPUT_SLOT_START && index < firstPlayerSlot) {
				// Output -> player inventory
				if (!this.moveItemStackTo(original, firstPlayerSlot, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Player inventory -> input slot only if it's a BrewContainer
				if (original.getItem() instanceof BrewContainer) {
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
	private static class BackedContainer implements Container {
		private final BotanicalBreweryAludelBlockEntity blockEntity;
		BackedContainer(BotanicalBreweryAludelBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return 1; }
		@Override public boolean isEmpty() { return blockEntity.getContainerStack().isEmpty(); }
		@Override public ItemStack getItem(int slot) { return slot == 0 ? blockEntity.getContainerStack() : ItemStack.EMPTY; }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getContainerStack();
			if (slot != 0 || currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setContainerStack(currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			if (slot != 0) return ItemStack.EMPTY;
			ItemStack currentStack = blockEntity.getContainerStack();
			blockEntity.setContainerStack(ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) {
			if (slot == 0) blockEntity.setContainerStack(stack);
		}
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() { blockEntity.setContainerStack(ItemStack.EMPTY); }
	}
	//endregion

	//region OUTPUTCONTAINER
	private static class OutputContainer implements Container {
		private final BotanicalBreweryAludelBlockEntity blockEntity;
		OutputContainer(BotanicalBreweryAludelBlockEntity blockEntity) { this.blockEntity = blockEntity; }
		@Override public int getContainerSize() { return BotanicalBreweryAludelBlockEntity.OUTPUT_SLOT_COUNT; }
		@Override public boolean isEmpty() {
			for (int slotIndex = 0; slotIndex < getContainerSize(); slotIndex++) if (!blockEntity.getOutputStack(slotIndex).isEmpty()) return false;
			return true;
		}
		@Override public ItemStack getItem(int slot) { return blockEntity.getOutputStack(slot); }
		@Override public ItemStack removeItem(int slot, int amount) {
			ItemStack currentStack = blockEntity.getOutputStack(slot);
			if (currentStack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
			ItemStack splitStack = currentStack.split(amount);
			blockEntity.setOutputStack(slot, currentStack);
			return splitStack;
		}
		@Override public ItemStack removeItemNoUpdate(int slot) {
			ItemStack currentStack = blockEntity.getOutputStack(slot);
			blockEntity.setOutputStack(slot, ItemStack.EMPTY);
			return currentStack;
		}
		@Override public void setItem(int slot, ItemStack stack) { blockEntity.setOutputStack(slot, stack); }
		@Override public void setChanged() { blockEntity.setChanged(); }
		@Override public boolean stillValid(Player player) { return true; }
		@Override public void clearContent() {
			for (int slotIndex = 0; slotIndex < getContainerSize(); slotIndex++) blockEntity.setOutputStack(slotIndex, ItemStack.EMPTY);
		}
	}
	//endregion
}
