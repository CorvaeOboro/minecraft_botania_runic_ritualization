/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # SelectPetalPayload.java
 * - Server-bound packet: sets or clears the selected petal-apothecary recipe id on a PetalApothecaryEverflowingBlockEntity.
 * - Sent when a petal recipe button is clicked. Layout: BlockPos pos, String recipeId ("" = clear).
 */
package corvaeoboro.botania_runic_ritualization.network;

import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlockEntity;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class SelectPetalPayload {

	private SelectPetalPayload() {}

	//region ENCODE
	public static FriendlyByteBuf encode(BlockPos blockPos, String recipeId) {
		FriendlyByteBuf buffer = PacketByteBufs.create();
		buffer.writeBlockPos(blockPos);
		buffer.writeUtf(recipeId == null ? "" : recipeId);
		return buffer;
	}
	//endregion

	//region HANDLE
	public static void handle(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
			FriendlyByteBuf buffer, PacketSender responseSender) {
		BlockPos blockPos = buffer.readBlockPos();
		String recipeString = buffer.readUtf();
		ResourceLocation recipeId = recipeString.isEmpty() ? null : ResourceLocation.tryParse(recipeString);
		server.execute(() -> {
			if (player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) > 64.0) return;
			BlockEntity blockEntity = player.serverLevel().getBlockEntity(blockPos);
			if (blockEntity instanceof PetalApothecaryEverflowingBlockEntity apothecary) {
				apothecary.setSelectedRecipeId(recipeId);
			}
		});
	}
	//endregion
}
