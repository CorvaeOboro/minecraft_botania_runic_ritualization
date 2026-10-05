/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalOfLivingRockBlockEntityRenderer.java
 * - Renders the held item floating above the pedestal, slowly rotating.
 * - Item scale 0.85F (close to normal in-game display size).
 * - If the held stack count is > 1, draws the count beneath the item.
 */
package corvaeoboro.botania_runic_ritualization.client;

import com.mojang.blaze3d.vertex.PoseStack;

import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.mojang.math.Axis;

public class PedestalOfLivingRockBlockEntityRenderer implements BlockEntityRenderer<PedestalOfLivingRockBlockEntity> {

	public PedestalOfLivingRockBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

	//region RENDER
	@Override
	public void render(PedestalOfLivingRockBlockEntity blockEntity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int packedLight, int packedOverlay) {
		ItemStack heldStack = blockEntity.getStack();
		if (heldStack.isEmpty()) return;

		Level level = blockEntity.getLevel();
		if (level == null) return;

		float rotationAngle = (level.getGameTime() + partialTick) * 2.0F % 360F;

		poseStack.pushPose();
		poseStack.translate(0.5, 1.05, 0.5);
		poseStack.scale(0.85F, 0.85F, 0.85F);
		poseStack.mulPose(Axis.YP.rotationDegrees(rotationAngle));

		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		itemRenderer.renderStatic(heldStack, ItemDisplayContext.GROUND, packedLight, packedOverlay,
				poseStack, buffers, level, 0);
		poseStack.popPose();
	}
	//endregion
}
