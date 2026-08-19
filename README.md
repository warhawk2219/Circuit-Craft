# Logic Gate Explorer

A Java Swing desktop app for building and simulating digital logic circuits
by hand: drop gates on a grid, wire them together, and watch signals
propagate in real time — with a live truth table, a live signal waveform,
undo/redo, save/load, PNG/CSV export, and a dark/light theme.

## Features

### Gates
- **10 combinational gate types** with real IEEE-style symbols: `INPUT`
  (toggle switch), `OUTPUT` (LED), `NOT`/`BUFFER` (triangle), `AND`/`NAND`
  (dome), and `OR`/`NOR`/`XOR`/`XNOR` (curved shield with the extra curved
  line and/or bubble that distinguish each variant)
- **2 sequential elements**: a free-running `CLOCK` (click to start/stop,
  double-click to set its period) and a `DFF` (D flip-flop — latches D on
  every rising edge of CLK)

### Building circuits
- Click a toolbar button to arm a gate — it gets a **black (light mode) /
  white (dark mode) border** while armed, and stays armed so you can drop
  multiple copies without re-clicking. Click it again, right-click the
  canvas, or press `Esc` to disarm.
- Placement and dragging **snap to an adjustable grid** (`+`/`\u2212` in the
  toolbar, 8–80px), drawn in black dots (light mode) or white dots (dark
  mode).
- Drag from a gate's output pin to another gate's input pin to wire them.
- Double-click an `INPUT`/`OUTPUT` to give it a custom label; double-click
  a `CLOCK` to change its period.
- Right-click a gate or wire (or select it and press `Delete`) to remove it.
- **Undo / Redo** — `Ctrl+Z` / `Ctrl+Y` (or the toolbar buttons) — covers
  placing, moving, deleting, wiring, renaming, and clearing.
- **Copy / Paste** — select a gate, `Ctrl+C`, then `Ctrl+V`.
- **Zoom** — toolbar `+`/`\u2212` or `Ctrl`+scroll on the canvas (50%–200%).

### Live views
- **Live Truth Table** (side panel, or pop out into its own window) —
  automatically cycles every `INPUT` through all 2ⁿ combinations, simulates
  the circuit for each, and shows every `OUTPUT`'s resulting value. The row
  matching your circuit's *current* state is highlighted. Export it to CSV
  any time.
- **Live Signal Waveform** (side panel, or pop out) — a mini logic-analyzer
  strip chart tracing every `INPUT`/`OUTPUT`/`CLOCK`/`DFF` value over time.
  Every toggle, tick, or circuit edit appends a new sample.
- **Float either panel** into its own resizable, closeable window with the
  "Float Truth Table" / "Float Waveform" buttons — maximize that window for
  a full-screen view. Closing it docks the panel back into the sidebar.

### File & export
- **Save / Load** circuits as a simple, human-readable `.lgc` text file.
- **Export PNG** — a clean image of the current circuit diagram.
- **Export CSV** — the current truth table.

### Appearance
- **Dark / Light mode** toggle — re-skins the canvas, gates, wires, grid,
  toolbar, and both side panels.

## How to run

You need a JDK installed (Java 8 or newer — anything recent works).

```bash
# from inside the LogicGateExplorer folder
javac *.java
java Main
```

## Project structure

| File                  | Responsibility                                                        |
|------------------------|------------------------------------------------------------------------|
| `Main.java`            | Application entry point                                               |
| `MainFrame.java`       | Window: toolbars, theme, floating panels, save/load/export, help      |
| `CircuitPanel.java`    | The canvas — placement, wiring, dragging, zoom/grid, undo/redo, clock ticking, simulation, gate symbol rendering, and live-data snapshots |
| `Gate.java`            | A single gate: position, pins, values, evaluate logic, clock/DFF state |
| `GateType.java`        | Enum of gate kinds and how many inputs each has                       |
| `Wire.java`            | A connection from one gate's output to another's input                |
| `Palette.java`         | Light/dark color palettes shared by every panel                       |
| `PillButton.java`      | Custom rounded toolbar button with hover + "armed" border states      |
| `TruthTablePanel.java` | Live truth table panel + CSV export                                   |
| `WaveformPanel.java`   | Live signal waveform panel (auto-scrolling strip chart)               |

## How simulation works

Each gate has zero or more input pins and (for everything but `OUTPUT`) one
output pin. On every structural change, `CircuitPanel.simulate()` runs:

1. Reset any input pin with **no wire connected to it** to `false`
   (floating = low). Connected pins keep their last propagated value as a
   warm start — this matters for the `DFF`, whose rising-edge detection
   depends on actually seeing its `CLK` input change between calls.
2. Repeatedly evaluate every gate and push its output along any wires into
   the next gate's input, enough times to let combinational logic settle
   regardless of gate creation order.
3. Push fresh data to the truth table (which sweeps all 2ⁿ `INPUT`
   combinations in an isolated pass, saving and restoring both your real
   input states *and* any `DFF`'s latched state so the sweep never disturbs
   your actual circuit) and to the waveform (which just records the current
   live values as one new sample).

A background timer ticks every 50ms and advances any running `CLOCK` gate,
triggering a `simulate()` whenever one flips.

**Known limitation:** the truth table only covers `INPUT`/`OUTPUT` gates
(a `CLOCK` isn't a fixed value to sweep over). If a `DFF` sits between an
`INPUT` and an `OUTPUT` in your circuit, its behavior during the sweep is
saved/restored around the sweep so your real simulation is never corrupted,
but the swept rows themselves won't reflect real clocked timing — use the
live waveform to observe actual sequential behavior instead.

## The `.lgc` save format

Plain text, one gate or wire per line:

```
GATE <index> <TYPE> <x> <y> <inputState> <label|-> <clockPeriodMs>
WIRE <fromIndex> <toIndex> <toInputPinIndex>
```

Easy to read, diff, or hand-edit.

## Ideas for extending it further

- Multi-select and group copy/paste
- True clocked timing in the truth table (step-by-step simulation mode)
- Additional flip-flop types (JK, T, SR) and a shift register
- Pan via spacebar-drag in addition to scrollbars
