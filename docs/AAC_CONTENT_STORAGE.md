# AAC Content Storage

## Current State
- The current AAC emoji placeholders are temporary.
- The current AAC layout and tile geometry remain fixed for now.
- This document only describes future content storage direction and packaged fallback content.

## Packaged Fallback Assets
- Packaged fallback AAC page definitions can live in `app/src/main/assets/aac/pages/`.
- The initial packaged fallback page is `main_4x4.json`.
- Packaged assets are useful as default content when no custom content exists yet.

## Future Custom Content Storage
- Future AAC icons and page definitions should load from app-private storage.
- Custom icons and custom pages must survive APK upgrades.
- Recommended future app-private paths:
  - `files/aac/custom/icons/`
  - `files/aac/custom/pages/`

## Recommended Future Loading Order
- First load custom AAC content from app-private storage when available.
- Fall back to packaged assets in `app/src/main/assets/aac/pages/` when custom content is missing.
- This keeps the app usable out of the box while allowing user-specific AAC content later.

## Future Settings Direction
- Later settings should allow text ON/OFF.
- Later settings should allow grid sizes `3x3`, `4x4`, and `5x5`.
- Those settings should not require hardcoded page limits or icon limits.

## Final Design Direction
- The final design should have no hardcoded limit on AAC pages.
- The final design should have no hardcoded limit on AAC icons.
- Packaged emoji placeholders are only a temporary fallback, not the final icon system.