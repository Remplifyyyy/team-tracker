# Team Tracker for Minecraft 26.2 (Fabric) — v1.1.0

Shows your teammates' positions through the Team Tracker relay. Fully compatible with
older versions: same relay, same team codes, same messages. Older versions can still see
you and you can still see them.

## Build
Requires Java 25 or newer. Run `.\build.bat`. The mod jar is in `build\libs\`
(use `team-tracker-1.1.0+26.2.jar`, not the `-sources.jar`). Delete the old jar from your mods folder.

## Menu
Press **Right Shift** (or `/teamtracker`). Tabs:
- **Text** – show name, name size slider, show distance, distance size slider, stacked / one-line layout, shadow, dark backing
- **Symbol** – None, Arrow, Chevron, Dot, Ring, Diamond, Square, Triangle, Plus, Cross, Player Head; size slider; outline
- **Colours** – Unique per player, Rainbow, Red, Orange, Yellow, Green, Lime, Aqua, Blue, Purple, Pink, White, or a custom hex code; distance text white or matching
- **Visibility** – tracker on/off, only show text when looking near a teammate (+ radius slider), hide within X blocks, range limit
- **Team** – team code, nickname, apply & reconnect

There is a live preview on the right of the menu.

## Keybinds (Options > Controls > Key Binds > Team Tracker)
- Toggle Team Tracker – O
- Open Team Tracker Menu – Right Shift
- Hold: Show All Names – unbound (shows names even with "only when looking" on)
- Next Symbol, Next Colour Mode, Toggle Names, Toggle Distance, Toggle Only-When-Looking – unbound

## Nickname
Set it in the Team tab or with `/teamtracker nick <name>` (`/teamtracker nick` clears it).
It is sent as an optional extra `nick` field, only when a nickname is set. Teammates on
1.1.0+ show it; older versions ignore it and show your username. With no nickname set,
messages are byte-for-byte what older versions send.
