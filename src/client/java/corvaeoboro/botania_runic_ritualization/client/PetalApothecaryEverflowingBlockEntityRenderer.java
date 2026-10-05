/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PetalApothecaryEverflowingBlockEntityRenderer.java
 * - Renders the ritual presentation on top of the Petal Apothecary of the Everflowing.
 * - Floating seed/reagent at center (the item in the seed slot).
 * - Mana-progress star overlay (blue tint) scaled by mana fill percentage.
 * - Wand-link visualization: sparkling blue lines from active pedestals when holding a Botania wand.
 */
package corvaeoboro.botania_runic_ritualization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.client.core.helper.RenderHelper;

import java.util.Optional;

public class PetalApothecaryEverflowingBlockEntityRenderer implements BlockEntityRenderer<PetalApothecaryEverflowingBlockEntity> {

	public PetalApothecaryEverflowingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

	//region RENDER
	@Override
	public void render(PetalApothecaryEverflowingBlockEntity blockEntity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int light, int overlay) {
		Level level = blockEntity.getLevel();
		if (level == null) return;

		float time = (level.getGameTime() % 720000L) + partialTick;
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

		// 0) Floating output item above the block (always visible when a recipe is selected)
		PetalApothecaryRecipe recipe = resolveRecipe(blockEntity);
		if (recipe != null) {
			ItemStack outputPreview = ((Recipe<?>) recipe).getResultItem(level.registryAccess());
			if (!outputPreview.isEmpty()) {
				poseStack.pushPose();
				poseStack.translate(0.5F, 1.15F, 0.5F);
				poseStack.scale(0.85F, 0.85F, 0.85F);
				poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.0F));
				poseStack.translate(0, Math.sin(time / 10.0) * 0.03, 0);
				itemRenderer.renderStatic(outputPreview, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
				poseStack.popPose();
			}
		}

		// VFX only shows during an active crafting process (charging, ready, or cooldown).
		// Stalled machines (missing ingredients, missing mana, no seed) show no VFX.
		if (!blockEntity.isCraftingActive()) return;

		// 1) Floating seed/reagent at center
		ItemStack seedStack = blockEntity.getSeedStack();
		if (!seedStack.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 1.2F, 0.5F);
			poseStack.scale(0.6F, 0.6F, 0.6F);
			poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.5F));
			poseStack.translate(0, Math.sin(time / 8.0) * 0.04, 0);
			itemRenderer.renderStatic(seedStack, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
			poseStack.popPose();
		}

		// 2) Mana progress star (blue tint for apothecary)
		int manaToGet = blockEntity.getManaToGetPublic();
		int currentMana = blockEntity.getCurrentManaPublic();
		if (manaToGet > 0 && currentMana > 0) {
			float starScale = Math.min(1F, (float) currentMana / (float) manaToGet) / 75F;
			if (starScale > 0F) {
				int positionSeed = blockEntity.getBlockPos().getX() ^ blockEntity.getBlockPos().getY() ^ blockEntity.getBlockPos().getZ();
				poseStack.pushPose();
				poseStack.translate(0.5F, 1.2F, 0.5F);
				RenderHelper.renderStar(poseStack, buffers, 0x33B5FF, starScale, starScale, starScale, positionSeed);
				poseStack.popPose();
			}
		}

		// 3) Wand-link visualization: sparkling blue lines from active pedestals to this machine.
		if (recipe != null && manaToGet > 0) {
			WandLinkVisualizer.drawPedestalLinks(level, blockEntity.getBlockPos(),
					blockEntity.getAdjacentPedestals(), ((Recipe<?>) recipe).getIngredients());
		}
	}

	@Override
	public boolean shouldRenderOffScreen(PetalApothecaryEverflowingBlockEntity blockEntity) {
		return true;
	}
	//endregion

	//region HELPER
	private static PetalApothecaryRecipe resolveRecipe(PetalApothecaryEverflowingBlockEntity blockEntity) {
		ResourceLocation recipeId = blockEntity.getSelectedRecipeId();
		if (recipeId == null) return null;
		Level level = blockEntity.getLevel();
		if (level == null) return null;
		Optional<? extends Recipe<?>> recipeByKey = level.getRecipeManager().byKey(recipeId);
		return recipeByKey.isPresent() && recipeByKey.get() instanceof PetalApothecaryRecipe apothecaryRecipe ? apothecaryRecipe : null;
	}
	//endregion
}
