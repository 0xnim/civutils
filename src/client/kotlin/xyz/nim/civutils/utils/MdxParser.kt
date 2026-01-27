package xyz.nim.civutils.utils

import org.yaml.snakeyaml.Yaml
import xyz.nim.civutils.data.handbook.*

/**
 * Parser for MDX files with YAML frontmatter.
 *
 * MDX files have the format:
 * ```
 * ---
 * yaml frontmatter here
 * ---
 *
 * markdown body here
 * ```
 */
object MdxParser {

    private val yaml = Yaml()

    /**
     * Parse an MDX file content into an ItemDefinition.
     * Returns null if the content is invalid or cannot be parsed.
     */
    fun parseItemMdx(content: String): ItemDefinition? {
        val (frontmatter, body) = extractFrontmatter(content) ?: return null

        return try {
            val data = yaml.load<Map<String, Any>>(frontmatter) ?: return null
            mapToItemDefinition(data, body.trim())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract frontmatter and body from MDX content.
     * Returns null if frontmatter markers are not found.
     */
    fun extractFrontmatter(content: String): Pair<String, String>? {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) return null

        val endIndex = trimmed.indexOf("---", 3)
        if (endIndex < 0) return null

        val frontmatter = trimmed.substring(3, endIndex).trim()
        val body = trimmed.substring(endIndex + 3).trim()

        return frontmatter to body
    }

    /**
     * Convert a YAML map to an ItemDefinition.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToItemDefinition(data: Map<String, Any>, description: String): ItemDefinition? {
        val id = data["id"] as? String ?: return null
        val name = data["name"] as? String ?: return null

        val categoryStr = data["category"] as? String ?: "MISC"
        val category = try {
            ItemCategory.valueOf(categoryStr)
        } catch (e: IllegalArgumentException) {
            ItemCategory.MISC
        }

        val filters = (data["filters"] as? Map<String, Any>)?.let { mapToFilters(it) }
        val recipes = (data["recipes"] as? List<Map<String, Any>>)?.mapNotNull { mapToRecipe(it) }
        val drops = (data["drops"] as? List<Map<String, Any>>)?.map { mapToRecipeSlot(it) }
        val metadata = (data["metadata"] as? Map<String, Any>)?.mapValues { it.value.toString() }

        return ItemDefinition(
            id = id,
            name = name,
            summary = data["summary"] as? String,
            description = description.ifEmpty { null },
            category = category,
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>(),
            order = (data["order"] as? Number)?.toInt() ?: 0,
            displayItem = data["displayItem"] as? String,
            customTexture = data["customTexture"] as? String,
            filters = filters,
            recipes = recipes,
            drops = drops,
            usedIn = (data["usedIn"] as? List<*>)?.filterIsInstance<String>(),
            related = (data["related"] as? List<*>)?.filterIsInstance<String>(),
            requiredClass = data["requiredClass"] as? String,
            metadata = metadata
        )
    }

    /**
     * Convert a YAML map to ItemFilters.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToFilters(data: Map<String, Any>): ItemFilters {
        // Handle loreContains as either a string or list
        val loreContains = when (val lore = data["loreContains"]) {
            is String -> listOf(lore)
            is List<*> -> lore.filterIsInstance<String>()
            else -> emptyList()
        }
        val loreExact = (data["loreExact"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        return ItemFilters(
            baseItem = data["baseItem"] as? String,
            customName = data["customName"] as? String,
            customNameContains = data["customNameContains"] as? String,
            customNameExcludes = data["customNameExcludes"] as? String,
            loreContains = loreContains,
            loreExact = loreExact,
            customModelData = (data["customModelData"] as? Number)?.toInt()
        )
    }

    /**
     * Convert a YAML map to a Recipe.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToRecipe(data: Map<String, Any>): Recipe? {
        val typeStr = data["type"] as? String ?: return null
        val type = try {
            RecipeType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            RecipeType.CUSTOM
        }

        val key = (data["key"] as? Map<String, Map<String, Any>>)?.mapValues { mapToRecipeSlot(it.value) }
        val ingredients = (data["ingredients"] as? List<Map<String, Any>>)?.map { mapToRecipeSlot(it) }
        val outputs = (data["outputs"] as? List<Map<String, Any>>)?.map { mapToRecipeSlot(it) }
        val customInputs = (data["customInputs"] as? List<Map<String, Any>>)?.map { mapToRecipeSlot(it) }
        val metadata = (data["metadata"] as? Map<String, Any>)?.mapValues { it.value.toString() }

        return Recipe(
            type = type,
            name = data["name"] as? String,
            outputs = outputs,
            pattern = (data["pattern"] as? List<*>)?.filterIsInstance<String>(),
            key = key,
            ingredients = ingredients,
            input = (data["input"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            cookingTime = (data["cookingTime"] as? Number)?.toInt(),
            experience = (data["experience"] as? Number)?.toFloat(),
            template = (data["template"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            base = (data["base"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            addition = (data["addition"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            brewIngredient = (data["brewIngredient"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            brewInput = (data["brewInput"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            brewFuel = (data["brewFuel"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            stonecutterInput = (data["stonecutterInput"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            mapInput = (data["mapInput"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            materialInput = (data["materialInput"] as? Map<String, Any>)?.let { mapToRecipeSlot(it) },
            customInputs = customInputs,
            processingTime = data["processingTime"] as? String,
            metadata = metadata
        )
    }

    /**
     * Convert a YAML map to a RecipeSlot.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToRecipeSlot(data: Map<String, Any>): RecipeSlot {
        return RecipeSlot(
            item = data["item"] as? String,
            count = (data["count"] as? Number)?.toInt() ?: 1,
            alternatives = (data["alternatives"] as? List<*>)?.filterIsInstance<String>(),
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>()
        )
    }
}
