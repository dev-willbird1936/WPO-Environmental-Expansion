package net.skds.wpo.environmental.block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.skds.wpo.environmental.EnvironmentalConfig;
import net.skds.wpo.environmental.EnvironmentalContent;
import net.skds.wpo.environmental.blockentity.CollectorBlockEntity;

public class CollectorBlock extends BaseEntityBlock {

    private static final VoxelShape FULL_CUBE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape LOW_BASIN = Shapes.or(
        Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
        Block.box(0.0D, 4.0D, 0.0D, 2.0D, 10.0D, 16.0D),
        Block.box(14.0D, 4.0D, 0.0D, 16.0D, 10.0D, 16.0D),
        Block.box(2.0D, 4.0D, 0.0D, 14.0D, 10.0D, 2.0D),
        Block.box(2.0D, 4.0D, 14.0D, 14.0D, 10.0D, 16.0D)
    );
    private static final VoxelShape ROOF_COLLECTOR_SHAPE = Shapes.or(
        Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D),
        Block.box(2.0D, 6.0D, 2.0D, 14.0D, 12.0D, 14.0D)
    );
    private static final VoxelShape GRATE_COLLECTOR_SHAPE = Shapes.or(
        Block.box(0.0D, 12.0D, 0.0D, 16.0D, 14.0D, 16.0D),
        Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D)
    );

    private final CollectorProfile profile;
    private final MapCodec<CollectorBlock> codec;

    public CollectorBlock(CollectorProfile profile, Properties properties) {
        super(properties);
        this.profile = profile;
        this.codec = simpleCodec(blockProperties -> new CollectorBlock(profile, blockProperties));
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    public CollectorProfile getProfile() {
        return profile;
    }

    public boolean allowsPassiveRainCollection(BlockState state) {
        return profile != CollectorProfile.RAIN_BARREL || state.getValue(BlockStateProperties.OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (profile) {
            case ROOF_COLLECTOR -> ROOF_COLLECTOR_SHAPE;
            case GROUND_BASIN -> LOW_BASIN;
            case INTAKE_GRATE_COLLECTOR -> GRATE_COLLECTOR_SHAPE;
            default -> FULL_CUBE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (profile) {
            case ROOF_COLLECTOR -> ROOF_COLLECTOR_SHAPE;
            case GROUND_BASIN -> LOW_BASIN;
            case INTAKE_GRATE_COLLECTOR -> GRATE_COLLECTOR_SHAPE;
            default -> FULL_CUBE;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (FluidUtil.getFluidHandler(stack).isPresent()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (profile != CollectorProfile.RAIN_BARREL) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), 3);
            level.playSound(null, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, open ? 1.0F : 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.OPEN);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CollectorBlockEntity collector) {
            return collector.getComparatorOutput();
        }
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CollectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, EnvironmentalContent.COLLECTOR_BLOCK_ENTITY.get(), CollectorBlockEntity::serverTick);
    }

    public enum CollectorProfile {
        RAIN_BARREL(8_000, 125, 0, 0, true),
        CISTERN(32_000, 175, 0, 0, true),
        ROOF_COLLECTOR(4_000, 250, 0, 250, true),
        GROUND_BASIN(6_000, 75, 2, 125, false),
        INTAKE_GRATE_COLLECTOR(6_000, 0, 3, 125, false);

        private final int defaultCapacityMb;
        private final int defaultRainMb;
        private final int defaultSurfaceDrainLevels;
        private final int defaultTransferMb;
        private final boolean directRainCollection;

        CollectorProfile(int defaultCapacityMb, int defaultRainMb, int defaultSurfaceDrainLevels, int defaultTransferMb, boolean directRainCollection) {
            this.defaultCapacityMb = defaultCapacityMb;
            this.defaultRainMb = defaultRainMb;
            this.defaultSurfaceDrainLevels = defaultSurfaceDrainLevels;
            this.defaultTransferMb = defaultTransferMb;
            this.directRainCollection = directRainCollection;
        }

        public int getCapacityMb() {
            return switch (this) {
                case RAIN_BARREL -> EnvironmentalConfig.COMMON.rainBarrelBuckets.get() * 1000;
                case CISTERN -> EnvironmentalConfig.COMMON.cisternBuckets.get() * 1000;
                case ROOF_COLLECTOR -> EnvironmentalConfig.COMMON.roofCollectorBuckets.get() * 1000;
                case GROUND_BASIN -> EnvironmentalConfig.COMMON.groundBasinBuckets.get() * 1000;
                case INTAKE_GRATE_COLLECTOR -> EnvironmentalConfig.COMMON.intakeCollectorBuckets.get() * 1000;
            };
        }

        public int getRainMbPerCycle() {
            return directRainCollection ? (int) Mth.clamp(defaultRainMb * EnvironmentalConfig.COMMON.collectorEfficiency.get(), 0.0D, 32000.0D) : 0;
        }

        public int getSurfaceDrainLevels() {
            return switch (this) {
                case GROUND_BASIN -> EnvironmentalConfig.COMMON.basinSurfaceDrainLevels.get();
                case INTAKE_GRATE_COLLECTOR -> EnvironmentalConfig.COMMON.grateSurfaceDrainLevels.get();
                default -> defaultSurfaceDrainLevels;
            };
        }

        public int getTransferMbPerCycle() {
            return switch (this) {
                case ROOF_COLLECTOR -> EnvironmentalConfig.COMMON.roofCollectorTransferMb.get();
                case GROUND_BASIN, INTAKE_GRATE_COLLECTOR -> defaultTransferMb;
                default -> 0;
            };
        }

        public boolean collectsRainDirectly() {
            return directRainCollection;
        }

        public BlockBehaviour.Properties createProperties() {
            return switch (this) {
                case RAIN_BARREL -> BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).strength(2.0F, 3.0F);
                case CISTERN -> BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).sound(SoundType.STONE).strength(3.5F, 6.0F);
                case ROOF_COLLECTOR -> BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).sound(SoundType.COPPER).mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 5.0F);
                case GROUND_BASIN -> BlockBehaviour.Properties.ofFullCopy(Blocks.MUD_BRICKS).strength(2.5F, 4.0F);
                case INTAKE_GRATE_COLLECTOR -> BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.METAL).strength(4.0F, 6.0F);
            };
        }
    }
}
