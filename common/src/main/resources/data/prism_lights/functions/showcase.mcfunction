# Screenshot gallery. Built at x/z 70..130, safely away from the original validation room.
fill 70 -59 70 130 -43 99 minecraft:air
fill 70 -59 100 130 -43 130 minecraft:air
fill 70 -60 70 130 -60 130 minecraft:polished_blackstone

# Luminous runway and framing strips.
fill 97 -59 70 103 -59 115 minecraft:smooth_quartz
fill 99 -59 70 101 -59 115 minecraft:sea_lantern
fill 100 -59 70 100 -59 115 minecraft:smooth_quartz
fill 72 -59 72 72 -59 128 minecraft:white_concrete
fill 128 -59 72 128 -59 128 minecraft:white_concrete

# Main white pavilion: columns, beams, and stepped roofline.
fill 82 -59 90 84 -48 92 minecraft:smooth_quartz
fill 116 -59 90 118 -48 92 minecraft:smooth_quartz
fill 82 -59 116 84 -48 118 minecraft:smooth_quartz
fill 116 -59 116 118 -48 118 minecraft:smooth_quartz
fill 82 -48 90 118 -46 92 minecraft:smooth_quartz
fill 82 -48 116 118 -46 118 minecraft:smooth_quartz
fill 82 -48 93 84 -46 115 minecraft:smooth_quartz
fill 116 -48 93 118 -46 115 minecraft:smooth_quartz
fill 86 -47 94 114 -47 114 minecraft:white_stained_glass
fill 90 -46 98 110 -46 110 minecraft:quartz_slab[type=top]

# Three monumental arches on the rear backdrop.
fill 76 -59 121 124 -47 123 minecraft:white_concrete
fill 79 -58 120 89 -50 123 minecraft:air
fill 95 -58 120 105 -50 123 minecraft:air
fill 111 -58 120 121 -50 123 minecraft:air
fill 80 -49 120 88 -48 123 minecraft:air
fill 96 -49 120 104 -48 123 minecraft:air
fill 112 -49 120 120 -48 123 minecraft:air
fill 76 -59 119 124 -59 124 minecraft:smooth_quartz

# Central color-mixing dais and floating prism crown.
fill 92 -59 96 108 -59 112 minecraft:quartz_block
fill 94 -58 98 106 -58 110 minecraft:calcite
fill 97 -57 101 103 -57 107 minecraft:smooth_quartz
setblock 100 -56 104 prism_lights:white_prism
setblock 97 -55 104 prism_lights:red_prism
setblock 103 -55 104 prism_lights:cyan_prism
setblock 100 -55 101 prism_lights:yellow_prism
setblock 100 -55 107 prism_lights:blue_prism
fill 97 -54 104 103 -54 104 minecraft:iron_bars
fill 100 -54 101 100 -54 107 minecraft:iron_bars

# Colored alcoves on the left: warm gradient.
fill 74 -59 78 80 -52 113 minecraft:white_concrete
fill 75 -58 80 79 -53 88 minecraft:air
fill 75 -58 92 79 -53 100 minecraft:air
fill 75 -58 104 79 -53 112 minecraft:air
setblock 77 -56 84 prism_lights:red_prism
setblock 77 -56 96 prism_lights:orange_prism
setblock 77 -56 108 prism_lights:yellow_prism
fill 75 -59 80 79 -59 88 minecraft:smooth_quartz
fill 75 -59 92 79 -59 100 minecraft:smooth_quartz
fill 75 -59 104 79 -59 112 minecraft:smooth_quartz

# Colored alcoves on the right: cool gradient.
fill 120 -59 78 126 -52 113 minecraft:white_concrete
fill 121 -58 80 125 -53 88 minecraft:air
fill 121 -58 92 125 -53 100 minecraft:air
fill 121 -58 104 125 -53 112 minecraft:air
setblock 123 -56 84 prism_lights:lime_prism
setblock 123 -56 96 prism_lights:cyan_prism
setblock 123 -56 108 prism_lights:blue_prism
fill 121 -59 80 125 -59 88 minecraft:smooth_quartz
fill 121 -59 92 125 -59 100 minecraft:smooth_quartz
fill 121 -59 104 125 -59 112 minecraft:smooth_quartz

# Portal sculptures at the entrance, with yellow/blue overlap in the middle.
fill 87 -59 76 89 -50 78 minecraft:quartz_pillar
fill 111 -59 76 113 -50 78 minecraft:quartz_pillar
fill 87 -50 76 113 -48 78 minecraft:quartz_pillar
setblock 90 -54 77 prism_lights:yellow_prism
setblock 110 -54 77 prism_lights:blue_prism
setblock 100 -50 77 prism_lights:green_prism

# Small reflective pools under magenta and cyan light.
fill 85 -59 101 89 -59 109 minecraft:water
fill 111 -59 101 115 -59 109 minecraft:water
setblock 87 -57 105 prism_lights:magenta_prism
setblock 113 -57 105 prism_lights:cyan_prism
fill 84 -59 100 90 -59 100 minecraft:smooth_quartz
fill 84 -59 110 90 -59 110 minecraft:smooth_quartz
fill 110 -59 100 116 -59 100 minecraft:smooth_quartz
fill 110 -59 110 116 -59 110 minecraft:smooth_quartz

# Clean screenshot state and a useful hotbar.
kill @e[type=item,x=70,y=-60,z=70,dx=60,dy=20,dz=60]
give @a prism_lights:red_prism 16
give @a prism_lights:yellow_prism 16
give @a prism_lights:blue_prism 16
give @a prism_lights:cyan_prism 16
tellraw @a {"text":"Pou's Colored Lights showcase ready. F1 hides the HUD; F5 switches camera.","color":"aqua"}
