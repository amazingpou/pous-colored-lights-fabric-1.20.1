package dev.prismlights;

import dev.prismlights.platform.ContentPlatform;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PrismLights {
    private static ContentPlatform platform;
    public static final String ID = "prism_lights";
    public static final Map<String, PrismBlock> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Block> NEW_BLOCKS = new LinkedHashMap<>();
    public static final Map<net.minecraft.particle.DefaultParticleType,Integer> CANDLE_FLAMES = new LinkedHashMap<>();
    public static BlockEntityType<PrismBlockEntity> LIGHT_ENTITY;
    public static Item FIREWORK_LAUNCHER;
    public static EntityType<PrismFireworkEntity> FIREWORK_ENTITY;
    public static Identifier id(String path) { return new Identifier(ID, path); }

    public static void initialize(ContentPlatform implementation) {
        if (platform != null) throw new IllegalStateException("Content already initialized");
        platform = java.util.Objects.requireNonNull(implementation);
        FireworkLifecycleValidation.register(platform);
        CandleServerValidation.register(platform);
        FIREWORK_ENTITY=platform.register(Registries.ENTITY_TYPE,id("prism_firework"),
            platform.fireworkEntityType(EntityType.Builder.<PrismFireworkEntity>create(PrismFireworkEntity::new,SpawnGroup.MISC)
                .setDimensions(0.25f,0.25f).maxTrackingRange(8).trackingTickInterval(1)));
        FIREWORK_LAUNCHER=platform.register(Registries.ITEM,id("firework_launcher"),
            new FireworkLauncherItem(new Item.Settings().maxCount(1)));
        String[] names = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        int[] colors = {0xFFFFFF,0xFF720A,0xFF20CC,0x54BFFF,0xFFFF05,0x80FF16,0xFF75B2,0x8991AA,0xC8D4E5,0x05FFDC,0x8024FF,0x054DFF,0xAD641F,0x12CA39,0xFF1510,0x514975};
        for (int i = 0; i < names.length; i++) {
            PrismBlock block = new PrismBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                .strength(1.5f, 6).sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .luminance(state -> state.get(PrismBlock.ACTIVE) ? 15 : 0), colors[i]);
            Identifier id = id(names[i] + "_prism");
            platform.register(Registries.BLOCK, id, block);
            platform.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
            BLOCKS.put(names[i], block);
            PrismBlock smooth = new PrismBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                .strength(1.5f, 6).sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .luminance(state -> state.get(PrismBlock.ACTIVE) ? 15 : 0), colors[i]);
            registerBlock(names[i] + "_smooth_light", smooth, true);
            RedstonePrismBlock powered = new RedstonePrismBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                .strength(1.5f, 6).sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .luminance(state -> state.get(PrismBlock.ACTIVE) ? 15 : 0), colors[i]);
            registerBlock(names[i] + "_redstone_prism", powered, true);
            RedstonePrismBlock poweredSmooth = new RedstonePrismBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                .strength(1.5f, 6).sounds(BlockSoundGroup.AMETHYST_BLOCK)
                .luminance(state -> state.get(PrismBlock.ACTIVE) ? 15 : 0), colors[i]);
            registerBlock(names[i] + "_redstone_smooth_light", poweredSmooth, true);

            ColoredLanternBlock lantern = new ColoredLanternBlock(AbstractBlock.Settings.create()
                .strength(3.5f).sounds(BlockSoundGroup.LANTERN).nonOpaque().luminance(state -> 15), colors[i]);
            registerBlock(names[i] + "_lantern", lantern, true);

            ColoredTorchBlock torch = new ColoredTorchBlock(AbstractBlock.Settings.create()
                .noCollision().breakInstantly().luminance(state -> 14).sounds(BlockSoundGroup.WOOD), colors[i]);
            ColoredWallTorchBlock wallTorch = new ColoredWallTorchBlock(AbstractBlock.Settings.create()
                .noCollision().breakInstantly().luminance(state -> 14).sounds(BlockSoundGroup.WOOD), colors[i]);
            registerBlock(names[i] + "_torch", torch, false);
            registerBlock(names[i] + "_wall_torch", wallTorch, false);
            platform.register(Registries.ITEM, id(names[i] + "_torch"),
                new VerticallyAttachableBlockItem(torch, wallTorch, new Item.Settings(), Direction.DOWN));

            var flame=platform.register(Registries.PARTICLE_TYPE,id(names[i]+"_candle_flame"),platform.simpleParticleType());
            CANDLE_FLAMES.put(flame,colors[i]);
            Block vanillaCandle=Registries.BLOCK.get(new Identifier("minecraft",names[i]+"_candle"));
            ColoredCandleBlock candle=new ColoredCandleBlock(AbstractBlock.Settings.copy(vanillaCandle),colors[i],flame);
            registerBlock(names[i]+"_candle",candle,true);
            var vanillaCake=Registries.BLOCK.get(new Identifier("minecraft",names[i]+"_candle_cake"));
            registerBlock(names[i]+"_candle_cake",new ColoredCandleCakeBlock(candle,
                AbstractBlock.Settings.copy(vanillaCake),colors[i],flame),false);
        }
        ArrayList<Block> sources = new ArrayList<>(BLOCKS.values());
        sources.addAll(NEW_BLOCKS.values());
        LIGHT_ENTITY = platform.register(Registries.BLOCK_ENTITY_TYPE, id("prism"),
            platform.lightEntityType(sources.toArray(Block[]::new)));
        platform.register(Registries.ITEM_GROUP, id("lights"), platform.itemGroupBuilder()
            .displayName(Text.translatable("itemGroup.prism_lights"))
            .icon(() -> new ItemStack(BLOCKS.get("yellow")))
            .entries((context, entries) -> {
                BLOCKS.values().forEach(entries::add);
                for (String family : new String[]{"smooth_light", "redstone_prism", "redstone_smooth_light", "lantern", "torch", "candle"}) {
                    for (String color : names) entries.add(NEW_BLOCKS.get(color + "_" + family));
                }
                entries.add(FIREWORK_LAUNCHER);
            }).build());
    }

    private static void registerBlock(String name, Block block, boolean item) {
        platform.register(Registries.BLOCK, id(name), block);
        if (item) platform.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
        NEW_BLOCKS.put(name, block);
    }
}
