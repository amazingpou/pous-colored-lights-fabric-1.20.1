# Run only in an empty area: replaces a 21 x 9 x 21 block region around the executor.
fill ~-10 ~-1 ~-3 ~10 ~7 ~17 minecraft:white_concrete hollow
fill ~-8 ~ ~-3 ~8 ~5 ~-3 minecraft:air
# Grass test strip: verifies both the opaque grass-block surface and the
# cutout-mipped short-grass render path receive prism lighting.
fill ~-8 ~-1 ~0 ~8 ~-1 ~4 minecraft:grass_block
setblock ~-6 ~ ~2 minecraft:grass
setblock ~-1 ~ ~2 minecraft:grass
setblock ~4 ~ ~2 minecraft:grass
setblock ~-3 ~ ~5 prism_lights:yellow_prism
setblock ~3 ~ ~5 prism_lights:blue_prism
setblock ~-7 ~ ~12 prism_lights:red_prism
setblock ~7 ~ ~12 prism_lights:cyan_prism
tp @s ~ ~ ~ 0 15
give @s prism_lights:yellow_prism 16
give @s prism_lights:blue_prism 16
tellraw @s {"text":"Prism Lab ready. G: mixing mode | V: colored lighting | Empty-hand click: switch a prism.","color":"aqua"}
