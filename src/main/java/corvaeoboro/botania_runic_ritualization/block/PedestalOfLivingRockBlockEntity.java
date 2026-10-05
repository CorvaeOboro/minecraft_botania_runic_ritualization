/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalOfLivingRockBlockEntity.java
 * - Single-slot inventory holding up to a full stack.
 * - Synced to clients for rendering the floating item via PedestalOfLivingRockBlockEntityRenderer.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.screen.PedestalOfLivingRockMenu;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class PedestalOfLivingRockBlockEntity extends BlockEntity
		implements ExtendedScreenHandlerFactory, SidedStorageBlockEntity {
	//region FIELD
	private ItemStack heldStack = ItemStack.EMPTY;
	//endregion

	//region CONSTRUCT
	public PedestalOfLivingRockBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaRunicRitualization.PEDESTAL_BE_TYPE, pos, state);
	}
	//endregion

	//region INVENTORY
	public ItemStack getStack() {
		return heldStack;
	}

	public void setStack(ItemStack newStack) {
		this.heldStack = newStack == null ? ItemStack.EMPTY : newStack;
		setChanged();
		syncToClient();
	}

	// Consumes up to {@code amount} from the held stack. Returns the amount actually consumed.
	public int consume(int amount) {
		if (heldStack.isEmpty() || amount <= 0) return 0;
		int amountToConsume = Math.min(amount, heldStack.getCount());
		heldStack.shrink(amountToConsume);
		if (heldStack.isEmpty()) heldStack = ItemStack.EMPTY;
		setChanged();
		syncToClient();
		return amountToConsume;
	}
	//endregion

	//region TRANSFER
	// Fabric Transfer API: exposes the single slot to pipe mods for both insert and extract.
	// The storage delegates to getStack/setStack, which handle persistence and client sync.
	@Nullable
	private PedestalItemStorage itemStorage;

	@Override
	public Storage<ItemVariant> getItemStorage(Direction side) {
		if (itemStorage == null) {
			itemStorage = new PedestalItemStorage();
		}
		return itemStorage;
	}

	// Single-slot storage backing the pedestal's transfer API exposure. 
	private class PedestalItemStorage extends SingleStackStorage {
		@Override
		protected ItemStack getStack() {
			return heldStack;
		}

		@Override
		protected void setStack(ItemStack stack) {
			heldStack = stack == null ? ItemStack.EMPTY : stack;
			setChanged();
			syncToClient();
		}
	}
	//endregion

	//region NBT
	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (tag.contains("Item")) {
			this.heldStack = ItemStack.of(tag.getCompound("Item"));
		} else {
			this.heldStack = ItemStack.EMPTY;
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!heldStack.isEmpty()) {
			CompoundTag itemTag = new CompoundTag();
			heldStack.save(itemTag);
			tag.put("Item", itemTag);
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClient() {
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
		}
	}
	//endregion

	//region SCREEN
	// MenuProvider / ExtendedScreenHandlerFactory for the pedestal GUI.
	@Override
	public Component getDisplayName() {
		return Component.translatable("block." + BotaniaRunicRitualization.MODID + ".pedestal_of_living_rock");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
		return new PedestalOfLivingRockMenu(syncId, playerInv, this);
	}

	@Override
	public void writeScreenOpeningData(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
		buf.writeBlockPos(getBlockPos());
	}
	//endregion
}
