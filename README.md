# SlimeLens

SlimeLens is a JEI-style recipe and usage browser for the SXAU Slimefun server. It keeps the original Slimefun Guide book intact and builds its index from the recipes that are actually registered after the server and every Slimefun addon have loaded.

## What it shows

- Enabled Slimefun categories, core items, and addon items.
- Search by display name, Slimefun id, category, or Minecraft material id.
- Crafting recipe, station or machine, machine input/output, and research requirement.
- Reverse lookup for an ingredient: where it is consumed and what it can make.
- Slimefun item recipes, addon `RecipeDisplayItem` recipes, and the runtime vanilla recipe snapshot.

The startup index is processed in small batches. The default `index-work-units-per-tick: 48` avoids blocking the SXAU 26.2 startup thread while indexing the full addon set.

## Use

`/lens` is the short alias for `/slimelens`; `/slens` remains available for compatibility.

| Command | Result |
| --- | --- |
| `/lens` while holding an item | Opens that item's recipe and usage detail page. |
| `/lens` with an empty main hand | Opens the category and search overview. |
| `/lens search <keyword>` | Searches the indexed item list. |
| `/lens recipes` | Opens recipes for the main-hand item. |
| `/lens uses` | Opens usages for the main-hand item. |
| `/lens reload` | Rebuilds the index in batches. Requires `slimelens.admin`. |

All normal players receive `slimelens.use` by default. The original Slimefun Guide is never intercepted unless `intercept-guide-book` is explicitly enabled in `config.yml`.

## Install

1. Download `SlimeLens-<version>.jar` from [Releases](../../releases).
2. Put it in the Leaves/Paper server `plugins` directory alongside Slimefun.
3. Restart the server. SlimeLens will wait for the Slimefun registry, then build its index automatically.

## Local build

The default Maven profile compiles against the exact local SXAU runtime: the `C:/Users/Lotus/Leaves` 26.2 fork and the Slimefun jar installed in `C:/Users/Lotus/SXAU-26.2`.

```powershell
& C:\Users\Lotus\SXAU-26.2\tools\apache-maven-3.9.16\bin\mvn.cmd -DskipTests package
Copy-Item .\target\SlimeLens.jar C:\Users\Lotus\SXAU-26.2\plugins\SlimeLens.jar -Force
```

For another local runtime, override `leaves.root`, `sxau.server.dir`, `leaves.api.jar`, and `slimefun.jar` as Maven properties.

## Releases

Pushing a tag named `v*` runs the release workflow. It checks out the SXAU Leaves source line and the pinned Slimefun commit, installs their built API jars into the Runner's local Maven repository, builds SlimeLens, uploads the jar as a workflow artifact, and creates the GitHub Release with generated notes.
