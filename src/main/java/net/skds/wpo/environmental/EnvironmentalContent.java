package net.skds.wpo.environmental;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.skds.wpo.environmental.block.CollectorBlock;
import net.skds.wpo.environmental.block.CollectorBlock.CollectorProfile;
import net.skds.wpo.environmental.block.SurfaceIceBlock;
import net.skds.wpo.environmental.blockentity.CollectorBlockEntity;

public final class EnvironmentalContent {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnvironmentalExpansion.MOD_ID);

    public static final DeferredBlock<CollectorBlock> RAIN_BARREL = registerCollector("rain_barrel", CollectorProfile.RAIN_BARREL);
    public static final DeferredBlock<CollectorBlock> CISTERN = registerCollector("cistern", CollectorProfile.CISTERN);
    public static final DeferredBlock<CollectorBlock> ROOF_COLLECTOR = registerCollector("roof_collector", CollectorProfile.ROOF_COLLECTOR);
    public static final DeferredBlock<CollectorBlock> GROUND_BASIN = registerCollector("ground_basin", CollectorProfile.GROUND_BASIN);
    public static final DeferredBlock<CollectorBlock> INTAKE_GRATE_COLLECTOR = registerCollector("intake_grate_collector", CollectorProfile.INTAKE_GRATE_COLLECTOR);
    public static final DeferredBlock<SurfaceIceBlock> SURFACE_ICE = BLOCKS.register("surface_ice", SurfaceIceBlock::new);
    public static final DeferredItem<BlockItem> SURFACE_ICE_ITEM = ITEMS.register("surface_ice", () -> new BlockItem(SURFACE_ICE.get(), new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + EnvironmentalExpansion.MOD_ID))
        .icon(() -> new ItemStack(RAIN_BARREL.get()))
        .displayItems((parameters, output) -> {
            output.accept(RAIN_BARREL.get());
            output.accept(CISTERN.get());
            output.accept(ROOF_COLLECTOR.get());
            output.accept(GROUND_BASIN.get());
            output.accept(INTAKE_GRATE_COLLECTOR.get());
            output.accept(SURFACE_ICE_ITEM.get());
        })
        .build());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CollectorBlockEntity>> COLLECTOR_BLOCK_ENTITY = BLOCK_ENTITIES.register(
        "collector",
        () -> BlockEntityType.Builder.of(
            CollectorBlockEntity::new,
            RAIN_BARREL.get(),
            CISTERN.get(),
            ROOF_COLLECTOR.get(),
            GROUND_BASIN.get(),
            INTAKE_GRATE_COLLECTOR.get()
        ).build(null)
    );

    private EnvironmentalContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COLLECTOR_BLOCK_ENTITY.get(), CollectorBlockEntity::getFluidHandler);
    }

    private static DeferredBlock<CollectorBlock> registerCollector(String name, CollectorProfile profile) {
        DeferredBlock<CollectorBlock> block = BLOCKS.register(name, () -> new CollectorBlock(profile, profile.createProperties()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
