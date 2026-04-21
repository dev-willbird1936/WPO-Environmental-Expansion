package net.skds.wpo.environmental.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SurfaceIceBlock extends Block {

    public static final MapCodec<SurfaceIceBlock> CODEC = simpleCodec(SurfaceIceBlock::new);
    private static final VoxelShape[] SHAPES = new VoxelShape[9];

    static {
        SHAPES[0] = Shapes.empty();
        for (int layers = 1; layers <= 8; ++layers) {
            SHAPES[layers] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, Math.min(16.0D, layers * 2.0D), 16.0D);
        }
    }

    public SurfaceIceBlock() {
        this(BlockBehaviour.Properties.ofFullCopy(Blocks.ICE)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .friction(0.98F)
            .noOcclusion());
    }

    private SurfaceIceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LAYERS, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[Mth.clamp(state.getValue(BlockStateProperties.LAYERS), 1, 8)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(this) && state.hasProperty(BlockStateProperties.LAYERS)) {
            int layers = state.getValue(BlockStateProperties.LAYERS);
            return state.setValue(BlockStateProperties.LAYERS, Math.min(8, layers + 1));
        }
        return defaultBlockState();
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(BlockStateProperties.LAYERS);
        if (context.getItemInHand().is(asItem()) && layers < 8) {
            return context.replacingClickedOnBlock() ? context.getClickedFace() == Direction.UP : true;
        }
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LAYERS);
    }
}
