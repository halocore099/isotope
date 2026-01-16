# Mod Integration Examples

This folder contains example `isotope_links.json` files showing how mods can declare their structure-loot table relationships for Isotope.

## How to Integrate Your Mod

1. Create a file at `data/<your_mod_id>/isotope_links.json`
2. Follow the JSON format shown in these examples
3. Your links will automatically be discovered with MOD_DECLARED confidence (95)

## File Format

```json
{
  "modId": "your_mod_id",
  "version": 1,
  "links": [
    {
      "structure": "your_mod_id:structure_name",
      "loot_tables": [
        "your_mod_id:chests/structure_loot",
        "your_mod_id:chests/structure_treasure"
      ]
    }
  ]
}
```

## Fields

| Field | Required | Description |
|-------|----------|-------------|
| `modId` | Optional | Your mod's ID (defaults to file namespace) |
| `version` | Optional | Format version (currently 1) |
| `links` | Required | Array of structure-loot mappings |
| `links[].structure` | Required | Structure ID (namespace:path) |
| `links[].loot_tables` | Required | Array of loot table IDs |
| `links[].loot_table` | Optional | Single loot table (alternative to array) |

## Examples in This Folder

- `betterend_example.json` - Better End mod structures
- `betternether_example.json` - Better Nether mod structures
- `repurposed_structures_example.json` - Repurposed Structures mod
- `custom_dungeons_example.json` - Generic dungeon mod pattern

## Alternative: loot_metadata.json

Datapacks can use `loot_metadata.json` with the same format. Place it at:
`data/<namespace>/loot_metadata.json`

Both filenames are automatically scanned by Isotope.
