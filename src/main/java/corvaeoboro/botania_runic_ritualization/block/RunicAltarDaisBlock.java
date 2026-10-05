/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RunicAltarDaisBlock.java
 * - Block opens the runic dais menu on right-click.
 * - Provides a server-side ticker on its block entity.
 * - Drops livingrock and output contents on break.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class RunicAltarDaisBlock extends Block implements EntityBlock {
	//region SHAPE
	// 3-cuboid approximation of runic_altar_dais.json:
	// base slab+posts (y0-4), beam (y4-6), table top (y6-12).
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(1, 0, 1, 15, 4, 15),     // base slab + corner posts
			Block.box(4, 4, 4, 12, 6, 12),     // middle beam
			Block.box(1, 6, 1, 15, 12, 15)     // table top
	);

	public RunicAltarDaisBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
	//endregion

	//region BLOCKENTITY
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RunicAltarDaisBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return type == BotaniaRunicRitualization.DAIS_BE_TYPE
				? (lvl, pos, st, blockEntity) -> RunicAltarDaisBlockEntity.serverTick(lvl, pos, st, (RunicAltarDaisBlockEntity) blockEntity)
				: null;
	}
	//endregion

	//region INTERACT
	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof RunicAltarDaisBlockEntity dais) {
				serverPlayer.openMenu(dais);
			}
		}
		return InteractionResult.CONSUME;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		if (blockEntityAtPos instanceof RunicAltarDaisBlockEntity dais) {
			dais.onNeighborChanged();
		}
	}
	//endregion

	//region SIGNAL
	@SuppressWarnings("deprecation")
	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		return blockEntityAtPos instanceof RunicAltarDaisBlockEntity dais ? dais.getSignal() : 0;
	}
	//endregion

	//region DROP
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof RunicAltarDaisBlockEntity dais) {
				ItemStack livingrockStack = dais.getLivingrockStack();
				if (!livingrockStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, livingrockStack);
				}
				ItemStack outputStack = dais.getOutputStack();
				if (!outputStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, outputStack);
				}
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}
	//endregion
}
