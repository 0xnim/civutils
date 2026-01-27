#!/usr/bin/env bun
/**
 * Convert dropsWhenBroken metadata to structured drops field,
 * and remove dropsFrom metadata (computed dynamically now).
 *
 * Run with: bun run tools/convert-drops.ts
 */

import * as fs from "fs";
import * as path from "path";

const itemsDir = path.resolve(__dirname, "../src/client/resources/assets/civutils/handbook/items");

/**
 * Parse dropsWhenBroken string like "Diamond (1)" or "Raw Iron (1-2)"
 */
function parseDropsWhenBroken(value: string): { item: string; count: number } | null {
  // Match patterns like "Diamond (1)" or "Raw Iron (1-2)" or "Coal (1)"
  const match = value.match(/^(.+?)\s*\((\d+)(?:-\d+)?\)$/);
  if (!match) return null;

  const name = match[1].trim();
  const count = parseInt(match[2], 10);

  // Convert display name to item ID
  const itemId = name.toLowerCase().replace(/\s+/g, "_");

  return { item: itemId, count };
}

/**
 * Process a single MDX file.
 */
function processFile(filePath: string): boolean {
  const content = fs.readFileSync(filePath, "utf-8");

  // Check if file has relevant metadata
  const hasDropsWhenBroken = content.includes("dropsWhenBroken:");
  const hasDropsFrom = content.includes("dropsFrom:");

  if (!hasDropsWhenBroken && !hasDropsFrom) {
    return false;
  }

  let newContent = content;

  // Handle dropsWhenBroken -> drops conversion
  if (hasDropsWhenBroken) {
    // Extract the dropsWhenBroken value
    const match = content.match(/dropsWhenBroken:\s*["']?([^"'\n]+)["']?/);
    if (match) {
      const dropInfo = parseDropsWhenBroken(match[1].trim());
      if (dropInfo) {
        // Remove dropsWhenBroken from metadata
        newContent = newContent.replace(/\n\s*dropsWhenBroken:[^\n]+/, "");

        // Check if metadata section becomes empty
        const metadataMatch = newContent.match(/metadata:\n((?:\s+\w+:[^\n]+\n?)*)/);
        if (metadataMatch) {
          const metadataContent = metadataMatch[1].trim();
          if (!metadataContent) {
            // Remove empty metadata section
            newContent = newContent.replace(/metadata:\n\s*(?=\n|---)/g, "");
          }
        }

        // Add drops field after order (or after displayItem if no order)
        // Find the right place to insert drops
        const dropYaml = `drops:\n  - item: ${dropInfo.item}\n    count: ${dropInfo.count}`;

        // Insert after metadata section, or before --- if no metadata
        if (newContent.includes("metadata:")) {
          // Insert before metadata
          newContent = newContent.replace(/(\nmetadata:)/, `\n${dropYaml}$1`);
        } else {
          // Insert before closing ---
          newContent = newContent.replace(/\n---\n/, `\n${dropYaml}\n---\n`);
        }
      }
    }
  }

  // Handle dropsFrom removal
  if (hasDropsFrom) {
    // Remove dropsFrom line from metadata
    newContent = newContent.replace(/\n\s*dropsFrom:[^\n]+/, "");

    // Check if metadata section becomes empty
    const metadataMatch = newContent.match(/metadata:\n((?:\s+\w+:[^\n]+\n?)*)/);
    if (metadataMatch) {
      const metadataContent = metadataMatch[1].trim();
      if (!metadataContent) {
        // Remove empty metadata section
        newContent = newContent.replace(/metadata:\n\s*(?=\n|---)/g, "");
      }
    }
  }

  // Clean up any double newlines that might have been created
  newContent = newContent.replace(/\n{3,}/g, "\n\n");

  if (newContent !== content) {
    fs.writeFileSync(filePath, newContent);
    return true;
  }

  return false;
}

async function main() {
  console.log("Converting drops metadata to structured format...\n");

  let converted = 0;
  let total = 0;

  // Process all MDX files recursively
  function walkDir(dir: string) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
      const filePath = path.join(dir, file);
      const stat = fs.statSync(filePath);
      if (stat.isDirectory()) {
        walkDir(filePath);
      } else if (file.endsWith(".mdx")) {
        total++;
        if (processFile(filePath)) {
          console.log(`  Updated: ${path.relative(itemsDir, filePath)}`);
          converted++;
        }
      }
    }
  }

  walkDir(itemsDir);

  console.log(`\nDone! Converted ${converted} of ${total} files.`);
}

main().catch(console.error);
