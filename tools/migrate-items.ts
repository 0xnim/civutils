#!/usr/bin/env bun
/**
 * Migration script to convert items.json to individual MDX files with YAML frontmatter.
 *
 * Run with: bun run tools/migrate-items.ts
 */

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
  brewIngredient?: RecipeSlot;
  brewInput?: RecipeSlot;
  brewFuel?: RecipeSlot;
  stonecutterInput?: RecipeSlot;
  mapInput?: RecipeSlot;
  materialInput?: RecipeSlot;
  customInputs?: RecipeSlot[];
  processingTime?: string;
  metadata?: Record<string, string>;
}

interface ItemFilters {
  baseItem?: string;
  customNameContains?: string;
  customNameExcludes?: string;
  customModelData?: number;
  loreContains?: string;
}

interface ItemDefinition {
  id: string;
  name: string;
  summary?: string;
  description?: string;
  category: ItemCategory;
  tags?: string[];
  order: number;
  displayItem?: string;
  customTexture?: string;
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

// Category to folder name mapping (lowercase)
const categoryFolders: Record<ItemCategory, string> = {
  MATERIALS: "materials",
  TOOLS: "tools",
  ARMOR: "armor",
  WEAPONS: "weapons",
  FOOD: "food",
  BREWING: "brewing",
  MISC: "misc",
};

/**
 * Check if a string needs quoting in YAML.
 */
function needsQuoting(value: string): boolean {
  if (value === "") return true;
  if (value === "true" || value === "false" || value === "null" || value === "yes" || value === "no") return true;
  if (/^[\d.+-]+$/.test(value)) return true;
  if (value.startsWith(" ") || value.endsWith(" ")) return true;
  if (/[:#\[\]{}|>&*!?,'"\\@`]/.test(value)) return true;
  if (value.includes("\n")) return true;
  return false;
}

/**
 * Quote a YAML string if needed.
 */
function quote(value: string): string {
  if (needsQuoting(value)) {
    return JSON.stringify(value);
  }
  return value;
}

/**
 * Serialize a value to YAML at given indent level.
 */
function toYaml(value: unknown, indent: number): string {
  const pad = "  ".repeat(indent);

  if (value === null || value === undefined) {
    return "null";
  }

  if (typeof value === "boolean" || typeof value === "number") {
    return String(value);
  }

  if (typeof value === "string") {
    return quote(value);
  }

  if (Array.isArray(value)) {
    if (value.length === 0) return "[]";

    // Check if all items are scalars
    const allScalar = value.every(v =>
      v === null || typeof v === "string" || typeof v === "number" || typeof v === "boolean"
    );

    if (allScalar) {
      return "[" + value.map(v => toYaml(v, 0)).join(", ") + "]";
    }

    // Multi-line array
    const lines: string[] = [];
    for (const item of value) {
      if (typeof item === "object" && item !== null && !Array.isArray(item)) {
        // Object in array
        const entries = Object.entries(item).filter(([_, v]) => v !== undefined);
        if (entries.length === 0) {
          lines.push(`${pad}- {}`);
        } else {
          let first = true;
          for (const [k, v] of entries) {
            const prefix = first ? `${pad}- ` : `${pad}  `;
            first = false;

            const vStr = toYaml(v, indent + 2);
            if (isMultiline(v)) {
              lines.push(`${prefix}${k}:`);
              lines.push(vStr);
            } else {
              lines.push(`${prefix}${k}: ${vStr}`);
            }
          }
        }
      } else {
        lines.push(`${pad}- ${toYaml(item, indent + 1)}`);
      }
    }
    return lines.join("\n");
  }

  if (typeof value === "object") {
    const entries = Object.entries(value).filter(([_, v]) => v !== undefined);
    if (entries.length === 0) return "{}";

    const lines: string[] = [];
    for (const [k, v] of entries) {
      const vStr = toYaml(v, indent + 1);
      if (isMultiline(v)) {
        lines.push(`${pad}${k}:`);
        lines.push(vStr);
      } else {
        lines.push(`${pad}${k}: ${vStr}`);
      }
    }
    return lines.join("\n");
  }

  return String(value);
}

/**
 * Check if a value will be serialized as multi-line YAML.
 */
function isMultiline(value: unknown): boolean {
  if (value === null || value === undefined) return false;
  if (typeof value !== "object") return false;

  if (Array.isArray(value)) {
    // Inline if all scalar
    return !value.every(v =>
      v === null || typeof v === "string" || typeof v === "number" || typeof v === "boolean"
    );
  }

  // Objects are always multi-line unless empty
  const entries = Object.entries(value).filter(([_, v]) => v !== undefined);
  return entries.length > 0;
}

/**
 * Serialize root object to YAML (no leading indent).
 */
function rootToYaml(obj: Record<string, unknown>): string {
  const entries = Object.entries(obj).filter(([_, v]) => v !== undefined);
  const lines: string[] = [];

  for (const [k, v] of entries) {
    const vStr = toYaml(v, 1);
    if (isMultiline(v)) {
      lines.push(`${k}:`);
      lines.push(vStr);
    } else {
      lines.push(`${k}: ${vStr}`);
    }
  }

  return lines.join("\n");
}

/**
 * Generate YAML frontmatter for an item.
 */
function generateFrontmatter(item: ItemDefinition): string {
  const frontmatter: Record<string, unknown> = {
    id: item.id,
    name: item.name,
  };

  if (item.summary) {
    frontmatter.summary = item.summary;
  }

  frontmatter.category = item.category;

  if (item.tags && item.tags.length > 0) {
    frontmatter.tags = item.tags;
  }

  frontmatter.order = item.order;

  if (item.displayItem) {
    frontmatter.displayItem = item.displayItem;
  }

  if (item.customTexture) {
    frontmatter.customTexture = item.customTexture;
  }

  if (item.filters) {
    frontmatter.filters = item.filters;
  }

  if (item.requiredClass) {
    frontmatter.requiredClass = item.requiredClass;
  }

  if (item.metadata && Object.keys(item.metadata).length > 0) {
    frontmatter.metadata = item.metadata;
  }

  if (item.recipes && item.recipes.length > 0) {
    frontmatter.recipes = item.recipes;
  }

  if (item.usedIn && item.usedIn.length > 0) {
    frontmatter.usedIn = item.usedIn;
  }

  if (item.related && item.related.length > 0) {
    frontmatter.related = item.related;
  }

  return rootToYaml(frontmatter);
}

/**
 * Generate MDX file content for an item.
 */
function generateMdx(item: ItemDefinition): string {
  const frontmatter = generateFrontmatter(item);
  const description = item.description?.trim() || "";

  return `---
${frontmatter}
---

${description}
`.trim() + "\n";
}

async function main() {
  const projectRoot = path.resolve(__dirname, "..");
  const itemsJsonPath = path.join(
    projectRoot,
    "src/client/resources/assets/civutils/handbook/items.json"
  );
  const outputDir = path.join(
    projectRoot,
    "src/client/resources/assets/civutils/handbook/items"
  );

  console.log("Reading items.json...");
  const itemsJson = fs.readFileSync(itemsJsonPath, "utf-8");
  const itemsIndex: ItemsIndex = JSON.parse(itemsJson);

  console.log(`Found ${itemsIndex.items.length} items to migrate`);

  // Remove existing items directory to start fresh
  if (fs.existsSync(outputDir)) {
    fs.rmSync(outputDir, { recursive: true });
  }

  // Create output directories for each category
  for (const folder of Object.values(categoryFolders)) {
    const categoryDir = path.join(outputDir, folder);
    fs.mkdirSync(categoryDir, { recursive: true });
  }

  // Process each item
  let migratedCount = 0;
  const manifest: Record<string, string[]> = {};

  for (const item of itemsIndex.items) {
    const folder = categoryFolders[item.category] || "misc";
    const fileName = `${item.id}.mdx`;
    const filePath = path.join(outputDir, folder, fileName);

    const mdxContent = generateMdx(item);
    fs.writeFileSync(filePath, mdxContent);

    // Track in manifest
    if (!manifest[folder]) {
      manifest[folder] = [];
    }
    manifest[folder].push(fileName);

    migratedCount++;
    if (migratedCount % 10 === 0) {
      console.log(`  Migrated ${migratedCount}/${itemsIndex.items.length} items...`);
    }
  }

  // Write manifest
  const manifestPath = path.join(outputDir, "..", "items-manifest.json");
  const manifestData = {
    version: itemsIndex.version,
    categories: Object.entries(manifest).map(([category, files]) => ({
      folder: category,
      files: files.sort(),
    })),
  };
  fs.writeFileSync(manifestPath, JSON.stringify(manifestData, null, 2) + "\n");

  console.log(`\nMigration complete!`);
  console.log(`  - Migrated ${migratedCount} items`);
  console.log(`  - Created manifest at ${path.relative(projectRoot, manifestPath)}`);
  console.log(`  - Output directory: ${path.relative(projectRoot, outputDir)}`);
  console.log(`\nCategory breakdown:`);
  for (const [category, files] of Object.entries(manifest)) {
    console.log(`  - ${category}: ${files.length} items`);
  }
}

main().catch(console.error);
