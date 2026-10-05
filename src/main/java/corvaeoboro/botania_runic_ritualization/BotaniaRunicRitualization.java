/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotaniaRunicRitualization.java
 * - Common/server mod entrypoint.
 * - Registers blocks, block items, block entity types, menu type, and server-bound packets.
 * - Wires Botania API lookups (ManaReceiver, SparkAttachable) for all machine block entities
 * - Botania is a HARD dependency. Recipes are reused from native botania:runic_altar, botania:petal_apothecary, and botania:brew.
 */
package corvaeoboro.botania_runic_ritualization;

import corvaeoboro.botania_runic_ritualization.block.BotanicalBreweryAludelBlock;
import corvaeoboro.botania_runic_ritualization.block.BotanicalBreweryAludelBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlock;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlock;
import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.RunicAltarDaisBlock;
import corvaeoboro.botania_runic_ritualization.block.RunicAltarDaisBlockEntity;
import corvaeoboro.botania_runic_ritualization.network.SelectBrewPayload;
import corvaeoboro.botania_runic_ritualization.network.SelectPetalPayload;
import corvaeoboro.botania_runic_ritualization.network.SelectRunePayload;
import corvaeoboro.botania_runic_ritualization.screen.BotanicalBreweryAludelMenu;
import corvaeoboro.botania_runic_ritualization.screen.PedestalOfLivingRockMenu;
import corvaeoboro.botania_runic_ritualization.screen.PetalApothecaryEverflowingMenu;
import corvaeoboro.botania_runic_ritualization.screen.RunicDaisMenu;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import vazkii.botania.api.BotaniaFabricCapabilities;

public class BotaniaRunicRitualization implements ModInitializer {
	public static final String MODID = "botania_runic_ritualization";

	//region ID
	public static final ResourceLocation DAIS_ID = new ResourceLocation(MODID, "runic_altar_dais");
	public static final ResourceLocation PEDESTAL_ID = new ResourceLocation(MODID, "pedestal_of_living_rock");
	public static final ResourceLocation APOTHECARY_ID = new ResourceLocation(MODID, "petal_apothecary_everflowing");
	public static final ResourceLocation ALUDEL_ID = new ResourceLocation(MODID, "botanical_brewery_aludel");
	public static final ResourceLocation SELECT_RUNE_PACKET_ID = new ResourceLocation(MODID, "select_rune");
	public static final ResourceLocation SELECT_PETAL_PACKET_ID = new ResourceLocation(MODID, "select_petal");
	public static final ResourceLocation SELECT_BREW_PACKET_ID = new ResourceLocation(MODID, "select_brew");
	//endregion

	//region FIELD
	public static Block DAIS_BLOCK;
	public static Block PEDESTAL_BLOCK;
	public static Block APOTHECARY_BLOCK;
	public static Block ALUDEL_BLOCK;
	public static Item DAIS_ITEM;
	public static Item PEDESTAL_ITEM;
	public static Item APOTHECARY_ITEM;
	public static Item ALUDEL_ITEM;
	public static BlockEntityType<RunicAltarDaisBlockEntity> DAIS_BE_TYPE;
	public static BlockEntityType<PedestalOfLivingRockBlockEntity> PEDESTAL_BE_TYPE;
	public static BlockEntityType<PetalApothecaryEverflowingBlockEntity> APOTHECARY_BE_TYPE;
	public static BlockEntityType<BotanicalBreweryAludelBlockEntity> ALUDEL_BE_TYPE;
	public static ExtendedScreenHandlerType<RunicDaisMenu> DAIS_MENU_TYPE;
	public static ExtendedScreenHandlerType<PetalApothecaryEverflowingMenu> APOTHECARY_MENU_TYPE;
	public static ExtendedScreenHandlerType<PedestalOfLivingRockMenu> PEDESTAL_MENU_TYPE;
	public static ExtendedScreenHandlerType<BotanicalBreweryAludelMenu> ALUDEL_MENU_TYPE;
	public static CreativeModeTab CREATIVE_TAB;
	//endregion

	//region INIT
	@Override
	public void onInitialize() {
		// Load config first so all registrations and BEs can read it.
		BotaniaRunicRitualizationConfig.get();

		//region BLOCK
		// Properties.of() avoids requiresCorrectToolForDrops (inherited from copy(STONE)),
		// so blocks drop themselves and their inventory with any tool or empty hand.
		DAIS_BLOCK = Registry.register(BuiltInRegistries.BLOCK, DAIS_ID,
				new RunicAltarDaisBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()));
		PEDESTAL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, PEDESTAL_ID,
				new PedestalOfLivingRockBlock(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).noOcclusion()));
		APOTHECARY_BLOCK = Registry.register(BuiltInRegistries.BLOCK, APOTHECARY_ID,
				new PetalApothecaryEverflowingBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()));
		ALUDEL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, ALUDEL_ID,
				new BotanicalBreweryAludelBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).noOcclusion()));
		//endregion

		//region ITEM
		DAIS_ITEM = Registry.register(BuiltInRegistries.ITEM, DAIS_ID,
				new BlockItem(DAIS_BLOCK, new Item.Properties()));
		PEDESTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, PEDESTAL_ID,
				new BlockItem(PEDESTAL_BLOCK, new Item.Properties()));
		APOTHECARY_ITEM = Registry.register(BuiltInRegistries.ITEM, APOTHECARY_ID,
				new BlockItem(APOTHECARY_BLOCK, new Item.Properties()));
		ALUDEL_ITEM = Registry.register(BuiltInRegistries.ITEM, ALUDEL_ID,
				new BlockItem(ALUDEL_BLOCK, new Item.Properties()));
		//endregion

		//region BLOCKENTITY
		DAIS_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, DAIS_ID,
				FabricBlockEntityTypeBuilder.create(RunicAltarDaisBlockEntity::new, DAIS_BLOCK).build());
		PEDESTAL_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PEDESTAL_ID,
				FabricBlockEntityTypeBuilder.create(PedestalOfLivingRockBlockEntity::new, PEDESTAL_BLOCK).build());
		APOTHECARY_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, APOTHECARY_ID,
				FabricBlockEntityTypeBuilder.create(PetalApothecaryEverflowingBlockEntity::new, APOTHECARY_BLOCK).build());
		ALUDEL_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ALUDEL_ID,
				FabricBlockEntityTypeBuilder.create(BotanicalBreweryAludelBlockEntity::new, ALUDEL_BLOCK).build());
		//endregion

		//region MENU
		// Extended screen handler types send BlockPos to the client via the buffer.
		DAIS_MENU_TYPE = Registry.register(BuiltInRegistries.MENU, DAIS_ID,
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
					BlockPos pos = buf.readBlockPos();
					return new RunicDaisMenu(syncId, inv, pos);
				}));
		APOTHECARY_MENU_TYPE = Registry.register(BuiltInRegistries.MENU, APOTHECARY_ID,
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
					BlockPos pos = buf.readBlockPos();
					return new PetalApothecaryEverflowingMenu(syncId, inv, pos);
				}));
		ALUDEL_MENU_TYPE = Registry.register(BuiltInRegistries.MENU, ALUDEL_ID,
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
					BlockPos pos = buf.readBlockPos();
					return new BotanicalBreweryAludelMenu(syncId, inv, pos);
				}));
		PEDESTAL_MENU_TYPE = Registry.register(BuiltInRegistries.MENU, PEDESTAL_ID,
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
					BlockPos pos = buf.readBlockPos();
					return new PedestalOfLivingRockMenu(syncId, inv, pos);
				}));
		//endregion

		//region BOTANIA
		// All machine BEs implement ManaReceiver + SparkAttachable directly 
		BotaniaFabricCapabilities.MANA_RECEIVER.registerSelf(DAIS_BE_TYPE, APOTHECARY_BE_TYPE, ALUDEL_BE_TYPE);
		BotaniaFabricCapabilities.SPARK_ATTACHABLE.registerSelf(DAIS_BE_TYPE, APOTHECARY_BE_TYPE, ALUDEL_BE_TYPE);
		//endregion

		//region TAB
		// Creative tab containing all four modded blocks 
		CREATIVE_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
				new ResourceLocation(MODID, "main"),
				FabricItemGroup.builder()
						.title(Component.translatable("itemGroup." + MODID))
						.icon(() -> new ItemStack(DAIS_ITEM))
						.displayItems((params, output) -> {
							output.accept(DAIS_ITEM);
							output.accept(PEDESTAL_ITEM);
							output.accept(APOTHECARY_ITEM);
							output.accept(ALUDEL_ITEM);
						})
						.build());
		//endregion

		//region PACKET
		// Server-bound packets: client -> server selects a recipe by id.
		ServerPlayNetworking.registerGlobalReceiver(SELECT_RUNE_PACKET_ID, SelectRunePayload::handle);
		ServerPlayNetworking.registerGlobalReceiver(SELECT_PETAL_PACKET_ID, SelectPetalPayload::handle);
		ServerPlayNetworking.registerGlobalReceiver(SELECT_BREW_PACKET_ID, SelectBrewPayload::handle);
		//endregion
	}
	//endregion
}
