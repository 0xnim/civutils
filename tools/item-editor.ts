import { select, input, confirm, checkbox } from "@inquirer/prompts";
import * as fs from "fs";
import * as path from "path";

// Types matching the Kotlin data classes
type RecipeType =
  | "CRAFTING_SHAPED"
  | "CRAFTING_SHAPELESS"
  | "CRAFTING_2X2"
  | "SMELTING"
  | "BLASTING"
  | "SMOKING"
  | "CAMPFIRE"
  | "SMITHING"
  | "STONECUTTING"
  | "BREWING"
  | "CUSTOM";

type ItemCategory =
  | "MATERIALS"
  | "TOOLS"
  | "ARMOR"
  | "WEAPONS"
  | "FOOD"
  | "BREWING"
  | "MISC";

interface RecipeSlot {
  item?: string;
  count?: number;
  alternatives?: string[];
  tag?: string;
}

interface Recipe {
  type: RecipeType;
  name?: string;
  outputs?: RecipeSlot[];
  pattern?: string[];
  key?: Record<string, RecipeSlot>;
  ingredients?: RecipeSlot[];
  input?: RecipeSlot;
  cookingTime?: number;
  experience?: number;
  template?: RecipeSlot;
  base?: RecipeSlot;
  addition?: RecipeSlot;
  metadata?: Record<string, string>;
}

interface ItemFilters {
  baseItem?: string;
  customName?: string;
  customNameContains?: string;
  customNameExcludes?: string;
  loreContains?: string[];
  loreExact?: string[];
  customModelData?: number;
}

interface ItemDefinition {
  id: string;
  name: string;
  summary?: string;
  description?: string;
  category: ItemCategory;
  tags?: string[];
  order?: number;
  displayItem?: string;
  filters?: ItemFilters;
  recipes?: Recipe[];
  usedIn?: string[];
  related?: string[];
  requiredClass?: string;
  metadata?: Record<string, string>;
}

interface ItemsIndex {
  version: number;
  items: ItemDefinition[];
  categoryOrder?: ItemCategory[];
}

const ITEMS_PATH = path.join(
  import.meta.dir,
  "../src/client/resources/assets/civutils/handbook/items.json"
);

const CATEGORY_NAMES: Record<ItemCategory, string> = {
  MATERIALS: "Materials",
  TOOLS: "Tools",
  ARMOR: "Armor",
  WEAPONS: "Weapons",
  FOOD: "Food & Healing",
  BREWING: "Brewing",
  MISC: "Miscellaneous",
};

const RECIPE_TYPE_NAMES: Record<RecipeType, string> = {
  CRAFTING_SHAPED: "Crafting (Shaped)",
  CRAFTING_SHAPELESS: "Crafting (Shapeless)",
  CRAFTING_2X2: "Crafting (2x2)",
  SMELTING: "Smelting",
  BLASTING: "Blast Furnace",
  SMOKING: "Smoker",
  CAMPFIRE: "Campfire Cooking",
  SMITHING: "Smithing Table",
  STONECUTTING: "Stonecutter",
  BREWING: "Brewing Stand",
  CUSTOM: "Custom Recipe",
};

let data: ItemsIndex;
let hasUnsavedChanges = false;

function loadData(): ItemsIndex {
  const content = fs.readFileSync(ITEMS_PATH, "utf-8");
  return JSON.parse(content);
}

function saveData(): void {
  const content = JSON.stringify(data, null, 2) + "\n";
  fs.writeFileSync(ITEMS_PATH, content);
  hasUnsavedChanges = false;
  console.log("\nSaved to items.json");
}

function generateId(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_|_$/g, "");
}

async function listItems(): Promise<void> {
  const categories = data.categoryOrder || Object.keys(CATEGORY_NAMES) as ItemCategory[];

  for (const category of categories) {
    const items = data.items
      .filter((i) => i.category === category)
      .sort((a, b) => (a.order || 0) - (b.order || 0));

    if (items.length === 0) continue;

    console.log(`\n=== ${CATEGORY_NAMES[category]} (${items.length}) ===`);
    for (const item of items) {
      const recipes = item.recipes?.length || 0;
      const classReq = item.requiredClass ? ` [${item.requiredClass}]` : "";
      console.log(`  ${item.id.padEnd(25)} ${item.name}${classReq} (${recipes} recipes)`);
    }
  }
}

async function searchItems(): Promise<void> {
  const query = await input({ message: "Search query:" });
  const lowerQuery = query.toLowerCase();

  const matches = data.items.filter(
    (item) =>
      item.id.toLowerCase().includes(lowerQuery) ||
      item.name.toLowerCase().includes(lowerQuery) ||
      item.summary?.toLowerCase().includes(lowerQuery) ||
      item.tags?.some((t) => t.toLowerCase().includes(lowerQuery))
  );

  if (matches.length === 0) {
    console.log("\nNo items found.");
    return;
  }

  console.log(`\nFound ${matches.length} items:`);
  for (const item of matches) {
    console.log(`  ${item.id.padEnd(25)} ${item.name} [${item.category}]`);
    if (item.summary) {
      console.log(`    ${item.summary}`);
    }
  }
}

async function addItem(): Promise<void> {
  const name = await input({ message: "Item name:" });
  if (!name.trim()) {
    console.log("Cancelled.");
    return;
  }

  const suggestedId = generateId(name);
  const id = await input({
    message: "Item ID:",
    default: suggestedId,
  });

  if (data.items.find((i) => i.id === id)) {
    console.log(`Error: Item with ID "${id}" already exists.`);
    return;
  }

  const summary = await input({ message: "Summary (brief):" });
  const description = await input({ message: "Description (detailed):" });

  const category = await select<ItemCategory>({
    message: "Category:",
    choices: Object.entries(CATEGORY_NAMES).map(([value, name]) => ({
      value: value as ItemCategory,
      name,
    })),
  });

  const tagsInput = await input({ message: "Tags (comma-separated):" });
  const tags = tagsInput
    .split(",")
    .map((t) => t.trim())
    .filter((t) => t);

  const maxOrder = Math.max(
    0,
    ...data.items.filter((i) => i.category === category).map((i) => i.order || 0)
  );
  const order = await input({
    message: "Order (for sorting):",
    default: String(maxOrder + 10),
  });

  const displayItem = await input({
    message: "Display item (e.g., minecraft:iron_ingot):",
  });

  const hasFilters = await confirm({
    message: "Add NBT filters for custom item matching?",
    default: false,
  });

  let filters: ItemFilters | undefined;
  if (hasFilters) {
    const baseItem = await input({
      message: "Base item ID (vanilla item this represents):",
    });
    const customNameContains = await input({
      message: "Custom name contains (partial match):",
    });

    filters = {};
    if (baseItem) filters.baseItem = baseItem;
    if (customNameContains) filters.customNameContains = customNameContains;
  }

  const requiredClass = await input({
    message: "Required class (e.g., blacksmith:2, or leave empty):",
  });

  const newItem: ItemDefinition = {
    id,
    name,
    summary: summary || undefined,
    description: description || undefined,
    category,
    tags: tags.length > 0 ? tags : undefined,
    order: parseInt(order) || 0,
    displayItem: displayItem || undefined,
    filters,
    requiredClass: requiredClass || undefined,
  };

  // Add recipes?
  const addRecipe = await confirm({
    message: "Add a recipe now?",
    default: false,
  });

  if (addRecipe) {
    const recipe = await createRecipe(newItem);
    if (recipe) {
      newItem.recipes = [recipe];
    }
  }

  data.items.push(newItem);
  hasUnsavedChanges = true;
  console.log(`\nAdded item: ${name} (${id})`);
}

async function selectItem(message: string = "Select item:"): Promise<ItemDefinition | null> {
  const choices = data.items
    .sort((a, b) => a.name.localeCompare(b.name))
    .map((item) => ({
      value: item.id,
      name: `${item.name} (${item.id}) [${item.category}]`,
    }));

  choices.unshift({ value: "__cancel__", name: "< Cancel >" });

  const selectedId = await select({
    message,
    choices,
    pageSize: 20,
  });

  if (selectedId === "__cancel__") return null;
  return data.items.find((i) => i.id === selectedId) || null;
}

async function editItem(): Promise<void> {
  const item = await selectItem("Select item to edit:");
  if (!item) return;

  const field = await select({
    message: "Field to edit:",
    choices: [
      { value: "name", name: `Name: ${item.name}` },
      { value: "summary", name: `Summary: ${item.summary || "(empty)"}` },
      { value: "description", name: `Description: ${item.description?.substring(0, 50) || "(empty)"}...` },
      { value: "category", name: `Category: ${item.category}` },
      { value: "tags", name: `Tags: ${item.tags?.join(", ") || "(none)"}` },
      { value: "order", name: `Order: ${item.order}` },
      { value: "displayItem", name: `Display Item: ${item.displayItem || "(none)"}` },
      { value: "requiredClass", name: `Required Class: ${item.requiredClass || "(none)"}` },
      { value: "filters", name: `NBT Filters: ${item.filters ? "configured" : "(none)"}` },
      { value: "cancel", name: "< Cancel >" },
    ],
  });

  if (field === "cancel") return;

  switch (field) {
    case "name":
      item.name = await input({ message: "New name:", default: item.name });
      break;
    case "summary":
      item.summary = await input({ message: "New summary:", default: item.summary || "" }) || undefined;
      break;
    case "description":
      item.description = await input({ message: "New description:", default: item.description || "" }) || undefined;
      break;
    case "category":
      item.category = await select<ItemCategory>({
        message: "New category:",
        choices: Object.entries(CATEGORY_NAMES).map(([value, name]) => ({
          value: value as ItemCategory,
          name,
        })),
        default: item.category,
      });
      break;
    case "tags":
      const tagsInput = await input({
        message: "Tags (comma-separated):",
        default: item.tags?.join(", ") || "",
      });
      item.tags = tagsInput.split(",").map((t) => t.trim()).filter((t) => t);
      if (item.tags.length === 0) item.tags = undefined;
      break;
    case "order":
      item.order = parseInt(await input({ message: "New order:", default: String(item.order || 0) })) || 0;
      break;
    case "displayItem":
      item.displayItem = await input({ message: "Display item:", default: item.displayItem || "" }) || undefined;
      break;
    case "requiredClass":
      item.requiredClass = await input({
        message: "Required class (e.g., blacksmith:2):",
        default: item.requiredClass || "",
      }) || undefined;
      break;
    case "filters":
      await editFilters(item);
      break;
  }

  hasUnsavedChanges = true;
  console.log(`\nUpdated ${item.name}`);
}

async function editFilters(item: ItemDefinition): Promise<void> {
  if (!item.filters) {
    const add = await confirm({ message: "No filters configured. Add filters?", default: true });
    if (!add) return;
    item.filters = {};
  }

  item.filters.baseItem = await input({
    message: "Base item ID:",
    default: item.filters.baseItem || "",
  }) || undefined;

  item.filters.customNameContains = await input({
    message: "Custom name contains:",
    default: item.filters.customNameContains || "",
  }) || undefined;

  item.filters.customNameExcludes = await input({
    message: "Custom name excludes:",
    default: item.filters.customNameExcludes || "",
  }) || undefined;

  // Clean up empty filters object
  if (!item.filters.baseItem && !item.filters.customNameContains && !item.filters.customNameExcludes) {
    item.filters = undefined;
  }
}

async function removeItem(): Promise<void> {
  const item = await selectItem("Select item to remove:");
  if (!item) return;

  const confirmed = await confirm({
    message: `Remove "${item.name}" (${item.id})? This cannot be undone.`,
    default: false,
  });

  if (!confirmed) {
    console.log("Cancelled.");
    return;
  }

  const index = data.items.findIndex((i) => i.id === item.id);
  data.items.splice(index, 1);
  hasUnsavedChanges = true;
  console.log(`\nRemoved: ${item.name}`);
}

async function manageRecipes(): Promise<void> {
  const item = await selectItem("Select item to manage recipes:");
  if (!item) return;

  while (true) {
    const recipeCount = item.recipes?.length || 0;
    console.log(`\n=== Recipes for ${item.name} (${recipeCount}) ===`);

    if (item.recipes && item.recipes.length > 0) {
      item.recipes.forEach((r, i) => {
        const outputs = r.outputs?.map((o) => `${o.count || 1}x ${o.item}`).join(", ") || "?";
        console.log(`  ${i + 1}. ${RECIPE_TYPE_NAMES[r.type]}${r.name ? ` "${r.name}"` : ""} -> ${outputs}`);
      });
    }

    const action = await select({
      message: "Action:",
      choices: [
        { value: "add", name: "Add recipe" },
        { value: "edit", name: "Edit recipe", disabled: recipeCount === 0 },
        { value: "remove", name: "Remove recipe", disabled: recipeCount === 0 },
        { value: "back", name: "< Back >" },
      ],
    });

    if (action === "back") break;

    if (action === "add") {
      const recipe = await createRecipe(item);
      if (recipe) {
        if (!item.recipes) item.recipes = [];
        item.recipes.push(recipe);
        hasUnsavedChanges = true;
        console.log("\nRecipe added.");
      }
    } else if (action === "edit" && item.recipes) {
      const recipeIndex = await select({
        message: "Select recipe to edit:",
        choices: item.recipes.map((r, i) => ({
          value: i,
          name: `${i + 1}. ${RECIPE_TYPE_NAMES[r.type]}${r.name ? ` "${r.name}"` : ""}`,
        })),
      });
      await editRecipe(item.recipes[recipeIndex]);
      hasUnsavedChanges = true;
    } else if (action === "remove" && item.recipes) {
      const recipeIndex = await select({
        message: "Select recipe to remove:",
        choices: item.recipes.map((r, i) => ({
          value: i,
          name: `${i + 1}. ${RECIPE_TYPE_NAMES[r.type]}${r.name ? ` "${r.name}"` : ""}`,
        })),
      });
      item.recipes.splice(recipeIndex, 1);
      if (item.recipes.length === 0) item.recipes = undefined;
      hasUnsavedChanges = true;
      console.log("\nRecipe removed.");
    }
  }
}

async function createRecipe(item: ItemDefinition): Promise<Recipe | null> {
  const type = await select<RecipeType>({
    message: "Recipe type:",
    choices: Object.entries(RECIPE_TYPE_NAMES).map(([value, name]) => ({
      value: value as RecipeType,
      name,
    })),
  });

  const recipeName = await input({ message: "Recipe name/variant (optional):" });

  const recipe: Recipe = {
    type,
    name: recipeName || undefined,
  };

  // Type-specific fields
  if (type === "CRAFTING_SHAPED") {
    console.log("\nEnter pattern (3 rows, use space for empty, letters for items):");
    console.log("Example: 'SSS' / 'S S' / 'SSS' for a chest pattern");

    const row1 = await input({ message: "Row 1:" });
    const row2 = await input({ message: "Row 2:" });
    const row3 = await input({ message: "Row 3:" });

    recipe.pattern = [row1, row2, row3].filter((r) => r.trim());

    // Find unique characters
    const chars = new Set(recipe.pattern.join("").replace(/ /g, "").split(""));
    recipe.key = {};

    for (const char of chars) {
      const itemId = await input({ message: `Key '${char}' = item ID:` });
      recipe.key[char] = { item: itemId };
    }
  } else if (type === "CRAFTING_SHAPELESS") {
    console.log("\nEnter ingredients (comma-separated item IDs):");
    const ingredientsInput = await input({ message: "Ingredients:" });
    recipe.ingredients = ingredientsInput
      .split(",")
      .map((i) => i.trim())
      .filter((i) => i)
      .map((item) => ({ item }));
  } else if (type === "SMELTING" || type === "BLASTING" || type === "SMOKING") {
    const inputItem = await input({ message: "Input item ID:" });
    recipe.input = { item: inputItem };

    const cookingTime = await input({ message: "Cooking time (ticks, default 200):", default: "200" });
    recipe.cookingTime = parseInt(cookingTime) || 200;
  } else if (type === "SMITHING") {
    const template = await input({ message: "Template item ID:" });
    const base = await input({ message: "Base item ID:" });
    const addition = await input({ message: "Addition item ID:" });

    recipe.template = { item: template };
    recipe.base = { item: base };
    recipe.addition = { item: addition };
  }

  // Outputs
  const outputCount = await input({ message: "Output count:", default: "1" });
  recipe.outputs = [{ item: item.id, count: parseInt(outputCount) || 1 }];

  return recipe;
}

async function editRecipe(recipe: Recipe): Promise<void> {
  // Build choices based on recipe type
  const choices: Array<{ value: string; name: string }> = [
    { value: "name", name: `Name: ${recipe.name || "(none)"}` },
    { value: "outputs", name: `Output count: ${recipe.outputs?.[0]?.count || 1}` },
  ];

  // Add type-specific fields
  if (recipe.type === "CRAFTING_SHAPED") {
    choices.push({ value: "pattern", name: `Pattern: ${recipe.pattern?.join(" / ") || "(none)"}` });
    choices.push({ value: "key", name: `Key mappings: ${Object.keys(recipe.key || {}).length} defined` });
  } else if (recipe.type === "CRAFTING_SHAPELESS") {
    const ingList = recipe.ingredients?.map((i) => i.item).join(", ") || "(none)";
    choices.push({ value: "ingredients", name: `Ingredients: ${ingList}` });
  } else if (recipe.type === "SMELTING" || recipe.type === "BLASTING" || recipe.type === "SMOKING") {
    choices.push({ value: "input", name: `Input: ${recipe.input?.item || "(none)"}` });
    choices.push({ value: "cookingTime", name: `Cooking time: ${recipe.cookingTime || 200} ticks` });
  } else if (recipe.type === "SMITHING") {
    choices.push({ value: "template", name: `Template: ${recipe.template?.item || "(none)"}` });
    choices.push({ value: "base", name: `Base: ${recipe.base?.item || "(none)"}` });
    choices.push({ value: "addition", name: `Addition: ${recipe.addition?.item || "(none)"}` });
  }

  choices.push({ value: "cancel", name: "< Back >" });

  const field = await select({ message: "Field to edit:", choices });

  if (field === "cancel") return;

  switch (field) {
    case "name":
      recipe.name = await input({ message: "Recipe name:", default: recipe.name || "" }) || undefined;
      break;

    case "outputs":
      const count = await input({
        message: "Output count:",
        default: String(recipe.outputs?.[0]?.count || 1),
      });
      if (recipe.outputs && recipe.outputs[0]) {
        recipe.outputs[0].count = parseInt(count) || 1;
      }
      break;

    case "pattern":
      console.log("\nCurrent pattern: " + (recipe.pattern?.join(" / ") || "(none)"));
      console.log("Enter new pattern (3 rows, use space for empty, letters for items):");
      const row1 = await input({ message: "Row 1:", default: recipe.pattern?.[0] || "" });
      const row2 = await input({ message: "Row 2:", default: recipe.pattern?.[1] || "" });
      const row3 = await input({ message: "Row 3:", default: recipe.pattern?.[2] || "" });
      recipe.pattern = [row1, row2, row3].filter((r) => r.trim());
      break;

    case "key":
      await editRecipeKey(recipe);
      break;

    case "ingredients":
      console.log("\nCurrent ingredients: " + (recipe.ingredients?.map((i) => i.item).join(", ") || "(none)"));
      const ingredientsInput = await input({
        message: "Ingredients (comma-separated item IDs):",
        default: recipe.ingredients?.map((i) => i.item).join(", ") || "",
      });
      recipe.ingredients = ingredientsInput
        .split(",")
        .map((i) => i.trim())
        .filter((i) => i)
        .map((item) => ({ item }));
      break;

    case "input":
      recipe.input = { item: await input({ message: "Input item ID:", default: recipe.input?.item || "" }) };
      break;

    case "cookingTime":
      recipe.cookingTime = parseInt(
        await input({ message: "Cooking time (ticks):", default: String(recipe.cookingTime || 200) })
      ) || 200;
      break;

    case "template":
      recipe.template = { item: await input({ message: "Template item ID:", default: recipe.template?.item || "" }) };
      break;

    case "base":
      recipe.base = { item: await input({ message: "Base item ID:", default: recipe.base?.item || "" }) };
      break;

    case "addition":
      recipe.addition = { item: await input({ message: "Addition item ID:", default: recipe.addition?.item || "" }) };
      break;
  }
}

async function editRecipeKey(recipe: Recipe): Promise<void> {
  if (!recipe.key) recipe.key = {};

  while (true) {
    console.log("\nCurrent pattern: " + (recipe.pattern?.join(" / ") || "(none)"));
    console.log("Current key mappings:");
    for (const [char, slot] of Object.entries(recipe.key)) {
      console.log(`  '${char}' = ${slot.item}${slot.alternatives ? ` (+ ${slot.alternatives.length} alternatives)` : ""}`);
    }

    const action = await select({
      message: "Action:",
      choices: [
        { value: "edit", name: "Edit mapping" },
        { value: "add", name: "Add mapping" },
        { value: "remove", name: "Remove mapping" },
        { value: "back", name: "< Back >" },
      ],
    });

    if (action === "back") break;

    if (action === "add") {
      const char = await input({ message: "Character (single letter):" });
      if (char.length !== 1) {
        console.log("Must be a single character.");
        continue;
      }
      const itemId = await input({ message: `Item ID for '${char}':` });
      recipe.key[char] = { item: itemId };
      console.log(`Added '${char}' = ${itemId}`);
    } else if (action === "edit" || action === "remove") {
      const chars = Object.keys(recipe.key);
      if (chars.length === 0) {
        console.log("No mappings to " + action + ".");
        continue;
      }

      const char = await select({
        message: `Select mapping to ${action}:`,
        choices: chars.map((c) => ({
          value: c,
          name: `'${c}' = ${recipe.key![c].item}`,
        })),
      });

      if (action === "remove") {
        delete recipe.key[char];
        console.log(`Removed '${char}'`);
      } else {
        const newItem = await input({
          message: `New item ID for '${char}':`,
          default: recipe.key[char].item || "",
        });
        recipe.key[char] = { item: newItem };
        console.log(`Updated '${char}' = ${newItem}`);
      }
    }
  }
}

async function validateContent(): Promise<void> {
  console.log("\n=== Validation Results ===\n");
  let issues = 0;

  // Check for duplicate IDs
  const ids = new Set<string>();
  for (const item of data.items) {
    if (ids.has(item.id)) {
      console.log(`ERROR: Duplicate ID "${item.id}"`);
      issues++;
    }
    ids.add(item.id);
  }

  // Check for missing required fields
  for (const item of data.items) {
    if (!item.name) {
      console.log(`ERROR: Item "${item.id}" has no name`);
      issues++;
    }
    if (!item.category) {
      console.log(`ERROR: Item "${item.id}" has no category`);
      issues++;
    }
  }

  // Check recipe references
  for (const item of data.items) {
    if (!item.recipes) continue;

    for (const recipe of item.recipes) {
      const inputs = getAllRecipeInputs(recipe);
      for (const input of inputs) {
        if (!input.item) continue;
        // Skip vanilla items and tags
        if (input.item.includes(":") || input.tag) continue;
        // Check if custom item exists
        if (!ids.has(input.item)) {
          console.log(`WARNING: Item "${item.id}" recipe references unknown item "${input.item}"`);
          issues++;
        }
      }
    }
  }

  // Check usedIn references
  for (const item of data.items) {
    if (!item.usedIn) continue;
    for (const ref of item.usedIn) {
      if (!ids.has(ref)) {
        console.log(`WARNING: Item "${item.id}" usedIn references unknown item "${ref}"`);
        issues++;
      }
    }
  }

  if (issues === 0) {
    console.log("No issues found.");
  } else {
    console.log(`\nFound ${issues} issue(s).`);
  }
}

function getAllRecipeInputs(recipe: Recipe): RecipeSlot[] {
  const inputs: RecipeSlot[] = [];
  if (recipe.key) inputs.push(...Object.values(recipe.key));
  if (recipe.ingredients) inputs.push(...recipe.ingredients);
  if (recipe.input) inputs.push(recipe.input);
  if (recipe.template) inputs.push(recipe.template);
  if (recipe.base) inputs.push(recipe.base);
  if (recipe.addition) inputs.push(recipe.addition);
  return inputs;
}

async function mainMenu(): Promise<void> {
  console.log("\n=== CivUtils Item Editor ===");
  console.log(`Loaded ${data.items.length} items`);
  if (hasUnsavedChanges) {
    console.log("(unsaved changes)");
  }

  const action = await select({
    message: "Action:",
    choices: [
      { value: "list", name: "List items" },
      { value: "search", name: "Search items" },
      { value: "add", name: "Add new item" },
      { value: "edit", name: "Edit item" },
      { value: "remove", name: "Remove item" },
      { value: "recipes", name: "Manage recipes" },
      { value: "validate", name: "Validate content" },
      { value: "save", name: "Save changes" },
      { value: "exit", name: "Exit" },
    ],
  });

  switch (action) {
    case "list":
      await listItems();
      break;
    case "search":
      await searchItems();
      break;
    case "add":
      await addItem();
      break;
    case "edit":
      await editItem();
      break;
    case "remove":
      await removeItem();
      break;
    case "recipes":
      await manageRecipes();
      break;
    case "validate":
      await validateContent();
      break;
    case "save":
      saveData();
      break;
    case "exit":
      if (hasUnsavedChanges) {
        const save = await confirm({
          message: "You have unsaved changes. Save before exiting?",
          default: true,
        });
        if (save) saveData();
      }
      process.exit(0);
  }
}

// Main
console.log("Loading items.json...");
data = loadData();

while (true) {
  await mainMenu();
}
