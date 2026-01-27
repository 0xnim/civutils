package xyz.nim.civutils.data.handbook

/**
 * Recipe types supporting all Minecraft workstations.
 */
enum class RecipeType(val displayName: String) {
    // Crafting table variants
    CRAFTING_SHAPED("Crafting (Shaped)"),
    CRAFTING_SHAPELESS("Crafting (Shapeless)"),
    CRAFTING_2X2("Crafting (2x2)"),

    // Furnace variants
    SMELTING("Smelting"),
    BLASTING("Blast Furnace"),
    SMOKING("Smoker"),
    CAMPFIRE("Campfire Cooking"),

    // Special workstations
    SMITHING("Smithing Table"),
    CARTOGRAPHY("Cartography Table"),
    STONECUTTING("Stonecutter"),
    BREWING("Brewing Stand"),

    // Server-specific
    CUSTOM("Custom Recipe")
}

/**
 * Unified recipe representation supporting all recipe types.
 * Fields are nullable and used based on recipe type.
 */
data class Recipe(
    /** Recipe type determines rendering layout */
    val type: RecipeType,

    /** Optional recipe name/variant label */
    val name: String? = null,

    /** Primary output item(s) (nullable due to Gson) */
    val outputs: List<RecipeSlot>? = emptyList(),

    // === CRAFTING FIELDS ===

    /** Shape pattern for shaped crafting (3 strings of up to 3 chars each) */
    val pattern: List<String>? = null,

    /** Key mapping pattern chars to items */
    val key: Map<String, RecipeSlot>? = null,

    /** Ingredient list for shapeless crafting */
    val ingredients: List<RecipeSlot>? = null,

    // === FURNACE FIELDS ===

    /** Input item for furnace-style recipes */
    val input: RecipeSlot? = null,

    /** Cooking/smelting time in ticks (20 ticks = 1 second) */
    val cookingTime: Int? = null,

    /** Experience gained from smelting */
    val experience: Float? = null,

    // === SMITHING TABLE FIELDS ===

    /** Smithing template (left slot) */
    val template: RecipeSlot? = null,

    /** Base item to upgrade (middle slot) */
    val base: RecipeSlot? = null,

    /** Addition material (right slot) */
    val addition: RecipeSlot? = null,

    // === BREWING FIELDS ===

    /** Ingredient in top slot */
    val brewIngredient: RecipeSlot? = null,

    /** Input bottles (bottom 3 slots) */
    val brewInput: RecipeSlot? = null,

    /** Fuel item (blaze powder slot) - optional, defaults to blaze powder */
    val brewFuel: RecipeSlot? = null,

    // === STONECUTTING FIELDS ===

    /** Input for stonecutter */
    val stonecutterInput: RecipeSlot? = null,

    // === CARTOGRAPHY FIELDS ===

    /** Map input for cartography table */
    val mapInput: RecipeSlot? = null,

    /** Material input for cartography table (paper, glass pane, etc.) */
    val materialInput: RecipeSlot? = null,

    // === CUSTOM/SERVER FIELDS ===

    /** Generic inputs for custom recipes (when standard fields don't fit) */
    val customInputs: List<RecipeSlot>? = null,

    /** Processing time display string */
    val processingTime: String? = null,

    /** Additional metadata (fuel type, machine name, etc.) (nullable due to Gson) */
    val metadata: Map<String, String>? = emptyMap()
) {
    /**
     * Get the cooking time in seconds (for display).
     */
    val cookingTimeSeconds: Float?
        get() = cookingTime?.let { it / 20f }

    /**
     * Get all input items for this recipe (for dependency tracking).
     */
    fun getAllInputs(): List<RecipeSlot> {
        val inputs = mutableListOf<RecipeSlot>()

        // Crafting inputs
        key?.values?.let { inputs.addAll(it) }
        ingredients?.let { inputs.addAll(it) }

        // Furnace input
        input?.let { inputs.add(it) }

        // Smithing inputs
        template?.let { inputs.add(it) }
        base?.let { inputs.add(it) }
        addition?.let { inputs.add(it) }

        // Brewing inputs
        brewIngredient?.let { inputs.add(it) }
        brewInput?.let { inputs.add(it) }

        // Stonecutting
        stonecutterInput?.let { inputs.add(it) }

        // Cartography
        mapInput?.let { inputs.add(it) }
        materialInput?.let { inputs.add(it) }

        // Custom
        customInputs?.let { inputs.addAll(it) }

        return inputs.filter { !it.item.isNullOrBlank() }
    }
}

/**
 * Represents an item in a recipe slot with optional count and alternatives.
 */
data class RecipeSlot(
    /** Item ID - can be vanilla (minecraft:iron_ingot) or custom item ID (iron_plate) */
    val item: String? = "",

    /** Stack count */
    val count: Int = 1,

    /** Alternative items that can fill this slot (for flexible recipes) */
    val alternatives: List<String>? = null,

    /** Item tag for tag-based recipes (e.g., "minecraft:logs") */
    val tag: String? = null
) {
    companion object {
        /** Empty slot marker */
        val EMPTY = RecipeSlot("", 0)

        /**
         * Parse from shorthand format: "minecraft:iron_ingot|64" or just "iron_plate"
         */
        fun parse(spec: String): RecipeSlot {
            if (spec.isBlank() || spec == "_" || spec == " ") return EMPTY
            val parts = spec.split("|", limit = 2)
            val item = parts[0].trim()
            val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            return RecipeSlot(item, count)
        }
    }

    /** Check if this slot is empty */
    val isEmpty: Boolean get() = item.isNullOrBlank() || count <= 0

    /** Check if this is a custom item (no minecraft: prefix) */
    val isCustomItem: Boolean get() = !item.isNullOrBlank() && !item.contains(":")
}
