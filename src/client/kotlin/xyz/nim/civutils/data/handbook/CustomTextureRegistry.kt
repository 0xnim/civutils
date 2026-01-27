package xyz.nim.civutils.data.handbook

/**
 * Maps custom texture paths to custom model data values for rendering.
 * Uses minecraft:barrier with custom_model_data overrides to render custom textures.
 */
object CustomTextureRegistry {

    private val textureToModelData = mapOf(
        // Materials
        "item/padded_leather" to 1,
        "item/tool_handle" to 2,
        "item/armor_plate_chainmail" to 3,
        "item/plant_fiber" to 4,
        "item/whetstone" to 5,
        "item/blueprint" to 6,

        // Armor plates by tier
        "item/armor_plate_copper" to 10,
        "item/armor_plate_iron" to 11,
        "item/armor_plate_gold" to 12,
        "item/armor_plate_diamond" to 13,
        "item/armor_plate_netherite" to 14,

        // Copper tool heads
        "item/copper_pickaxe_head" to 20,
        "item/copper_axe_head" to 21,
        "item/copper_shovel_head" to 22,
        "item/copper_hoe_head" to 23,
        "item/copper_sword_head" to 24,
        "item/copper_shear" to 25,

        // Iron tool heads
        "item/iron_pickaxe_head" to 30,
        "item/iron_axe_head" to 31,
        "item/iron_shovel_head" to 32,
        "item/iron_hoe_head" to 33,
        "item/iron_sword_head" to 34,

        // Gold tool heads
        "item/gold_pickaxe_head" to 40,
        "item/gold_axe_head" to 41,
        "item/gold_shovel_head" to 42,
        "item/gold_hoe_head" to 43,
        "item/gold_sword_head" to 44,
        "item/gold_shear" to 45,

        // Diamond tool heads
        "item/diamond_pickaxe_head" to 50,
        "item/diamond_axe_head" to 51,
        "item/diamond_shovel_head" to 52,
        "item/diamond_hoe_head" to 53,
        "item/diamond_sword_head" to 54,
        "item/diamond_shear" to 55,

        // Netherite tool heads
        "item/netherite_pickaxe_head" to 60,
        "item/netherite_axe_head" to 61,
        "item/netherite_shovel_head" to 62,
        "item/netherite_hoe_head" to 63,
        "item/netherite_sword_head" to 64,
        "item/netherite_shear" to 65,

        // Wood shear
        "item/wood_shear" to 70,

        // Armor plate sets
        "item/copper_armor_plateset" to 80,
        "item/iron_armor_plateset" to 81,
        "item/gold_armor_plateset" to 82,
        "item/diamond_armor_plateset" to 83,
        "item/netherite_armor_plateset" to 84,

        // Armor pieces - Copper
        "item/copper_breastplate" to 90,
        "item/copper_greaves" to 91,
        "item/copper_helm" to 92,
        "item/copper_sabaton" to 93,

        // Armor pieces - Iron
        "item/iron_breastplate" to 94,
        "item/iron_greaves" to 95,
        "item/iron_helm" to 96,
        "item/iron_sabaton" to 97,

        // Armor pieces - Gold
        "item/gold_breastplate" to 98,
        "item/gold_greaves" to 99,
        "item/gold_helm" to 100,
        "item/gold_sabaton" to 101,

        // Armor pieces - Diamond
        "item/diamond_breastplate" to 102,
        "item/diamond_greaves" to 103,
        "item/diamond_helm" to 104,
        "item/diamond_sabaton" to 105,

        // Armor pieces - Netherite
        "item/netherite_breastplate" to 106,
        "item/netherite_greaves" to 107,
        "item/netherite_helm" to 108,
        "item/netherite_sabaton" to 109,

        // Javelins
        "item/wooden_javelin" to 110,
        "item/stone_javelin" to 111,
        "item/copper_javelin" to 112,
        "item/iron_javelin" to 113,
        "item/golden_javelin" to 114,
        "item/diamond_javelin" to 115,
        "item/netherite_javelin" to 116,

        // Iron shear (uses netherite texture as placeholder)
        "item/netherite_shear" to 117,

        // Copper finished tools/armor
        "item/copper_axe" to 120,
        "item/copper_pickaxe" to 121,
        "item/copper_shovel" to 122,
        "item/copper_hoe" to 123,
        "item/copper_sword" to 124,
        "item/copper_helmet" to 125,
        "item/copper_chestplate" to 126,
        "item/copper_leggings" to 127,
        "item/copper_boots" to 128,
        "item/copper_nugget" to 129
    )

    /**
     * Get the custom model data value for a texture path.
     * @param texturePath The texture path (e.g., "item/copper_pickaxe_head")
     * @return The custom model data value, or null if not registered
     */
    fun getModelData(texturePath: String): Int? {
        return textureToModelData[texturePath]
    }

    /**
     * Check if a texture path is registered.
     */
    fun hasTexture(texturePath: String): Boolean {
        return texturePath in textureToModelData
    }
}
