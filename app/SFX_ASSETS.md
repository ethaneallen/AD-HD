# SFX drop-in assets

`Sound.init()` resolves each `Sfx` event to a file **in this folder, by name** (via
`resources.getIdentifier(resName, "raw", pkg)`). Drop a 16-bit `.ogg` (preferred) or
`.wav` here named exactly after the event's `resName` and it is auto-loaded at startup —
no code change. Until a file exists, that event falls back to a synthesized tone.

Recommended format: 16-bit PCM, 44.1 kHz, mono for UI blips / stereo for combat, < ~500 ms.
File names must be lowercase, digits, and `_` only (Android `raw` naming rules).

| Event (`Sfx`)        | File to add            | Category | Notes                          |
|----------------------|------------------------|----------|--------------------------------|
| `SFX_MENU_TAP`       | `sfx_menu_tap.ogg`     | frequent | pitch-varied 0.95–1.05         |
| `SFX_TASK_TOGGLE`    | `sfx_task_toggle.ogg`  | frequent | pitch-varied; task complete    |
| `SFX_RUNE_POP`       | `sfx_rune_pop.ogg`     | frequent | pitch-varied                   |
| `SFX_DICE_ROLL`      | `sfx_dice_roll.ogg`    | frequent | pitch-varied                   |
| `SFX_QUICK_WIN`      | `sfx_quick_win.ogg`    | event    | stable pitch                   |
| `SFX_POTION_HARVEST` | `sfx_potion_harvest.ogg` | event  | stable pitch                   |
| `SFX_STREAK_GUARD`   | `sfx_streak_guard.ogg` | event    | stable pitch                   |
| `SFX_LOCK_POP`       | `sfx_lock_pop.ogg`     | event    | stable pitch                   |
| `SFX_DICE_CRIT`      | `sfx_dice_crit.ogg`    | event    | stable pitch                   |
| `SFX_BOSS_HIT`       | `sfx_boss_hit.ogg`     | event    | stable pitch                   |
| `SFX_BOSS_DEFEAT`    | `sfx_boss_defeat.ogg`  | event    | stable pitch                   |
| `SFX_LEVEL_UP`       | `sfx_level_up.ogg`     | event    | stable pitch                   |
