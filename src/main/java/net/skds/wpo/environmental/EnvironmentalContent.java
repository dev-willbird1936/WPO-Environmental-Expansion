package net.skds.wpo.environmental;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.skds.wpo.environmental.block.CollectorBlock;
import net.skds.wpo.environmental.block.CollectorBlock.CollectorProfile;
import net.skds.wpo.environmental.blockentity.CollectorBlockEntity;

public final class EnvironmentalContent {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EnvironmentalExpansion.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnvironmentalExpansion.MOD_ID);

    public static final RegistryObject<CollectorBlock> RAIN_BARREL = registerCollector("rain_barrel", CollectorProfile.RAIN_BARREL);
    public static final RegistryObject<CollectorBlock> CISTERN = registerCollector("cistern", CollectorProfile.CISTERN);
    public static final RegistryObject<CollectorBlock> ROOF_COLLECTOR = registerCollector("roof_collector", CollectorProfile.ROOF_COLLECTOR);
    public static final RegistryObject<CollectorBlock> GROUND_BASIN = registerCollector("ground_basin", CollectorProfile.GROUND_BASIN);
    public static final RegistryObject<CollectorBlock> INTAKE_GRATE_COLLECTOR = registerCollector("intake_grate_collector", CollectorProfile.INTAKE_GRATE_COLLECTOR);
    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + EnvironmentalExpansion.MOD_ID))
        .icon(() -> new ItemStack(RAIN_BARREL.get()))
        .displayItems((parameters, output) -> {
            output.accept(RAIN_BARREL.get());
            output.accept(CISTERN.get());
            output.accept(ROOF_COLLECTOR.get());
            output.accept(GROUND_BASIN.get());
            output.accept(INTAKE_GRATE_COLLECTOR.get());
        })
        .build());

    public static final RegistryObject<BlockEntityType<CollectorBlockEntity>> COLLECTOR_BLOCK_ENTITY = BLOCK_ENTITIES.register(
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

    private static RegistryObject<CollectorBlock> registerCollector(String name, CollectorProfile profile) {
        RegistryObject<CollectorBlock> block = BLOCKS.register(name, () -> new CollectorBlock(profile, profile.createProperties()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
