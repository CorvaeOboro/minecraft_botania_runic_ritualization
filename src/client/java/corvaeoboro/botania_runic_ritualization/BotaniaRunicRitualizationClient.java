/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotaniaRunicRitualizationClient.java
 * - Client entrypoint.
 * - Registers MenuScreens for all machine and pedestal GUIs.
 */
package corvaeoboro.botania_runic_ritualization;

import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.client.BotanicalBreweryAludelBlockEntityRenderer;
import corvaeoboro.botania_runic_ritualization.client.PedestalOfLivingRockBlockEntityRenderer;
import corvaeoboro.botania_runic_ritualization.client.PetalApothecaryEverflowingBlockEntityRenderer;
import corvaeoboro.botania_runic_ritualization.client.RunicAltarDaisBlockEntityRenderer;
import corvaeoboro.botania_runic_ritualization.screen.BotanicalBreweryAludelScreen;
import corvaeoboro.botania_runic_ritualization.screen.PedestalOfLivingRockScreen;
import corvaeoboro.botania_runic_ritualization.screen.PetalApothecaryEverflowingScreen;
import corvaeoboro.botania_runic_ritualization.screen.RunicDaisScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

public class BotaniaRunicRitualizationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		//region BER
		// Block entity renderers for all four blocks.
		BlockEntityRendererRegistry.register(BotaniaRunicRitualization.PEDESTAL_BE_TYPE,
				PedestalOfLivingRockBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(BotaniaRunicRitualization.DAIS_BE_TYPE,
				RunicAltarDaisBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(BotaniaRunicRitualization.ALUDEL_BE_TYPE,
				BotanicalBreweryAludelBlockEntityRenderer::new);
		BlockEntityRendererRegistry.register(BotaniaRunicRitualization.APOTHECARY_BE_TYPE,
				PetalApothecaryEverflowingBlockEntityRenderer::new);
		//endregion

		//region RENDERLAYER
		// Translucent render layers for blocks with fluid/glass visuals.
		BlockRenderLayerMap.INSTANCE.putBlock(BotaniaRunicRitualization.APOTHECARY_BLOCK, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(BotaniaRunicRitualization.ALUDEL_BLOCK, RenderType.translucent());
		//endregion

		//region SCREEN
		MenuScreens.register(BotaniaRunicRitualization.DAIS_MENU_TYPE, RunicDaisScreen::new);
		MenuScreens.register(BotaniaRunicRitualization.APOTHECARY_MENU_TYPE, PetalApothecaryEverflowingScreen::new);
		MenuScreens.register(BotaniaRunicRitualization.PEDESTAL_MENU_TYPE, PedestalOfLivingRockScreen::new);
		MenuScreens.register(BotaniaRunicRitualization.ALUDEL_MENU_TYPE, BotanicalBreweryAludelScreen::new);
		//endregion

		//region TOOLTIP
		// tooltip lines for the four modded blocks,
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (stack.isEmpty()) return;
			var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (itemId == null) return;
			String id = itemId.toString();
			String tooltipKey = null;
			if (id.equals("botania_runic_ritualization:runic_altar_dais")) {
				tooltipKey = "tooltip.botania_runic_ritualization.runic_altar_dais";
			} else if (id.equals("botania_runic_ritualization:petal_apothecary_everflowing")) {
				tooltipKey = "tooltip.botania_runic_ritualization.petal_apothecary_everflowing";
			} else if (id.equals("botania_runic_ritualization:botanical_brewery_aludel")) {
				tooltipKey = "tooltip.botania_runic_ritualization.botanical_brewery_aludel";
			} else if (id.equals("botania_runic_ritualization:pedestal_of_living_rock")) {
				tooltipKey = "tooltip.botania_runic_ritualization.pedestal_of_living_rock";
			}
			if (tooltipKey != null) {
				lines.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
			}
		});
		//endregion
	}
}
