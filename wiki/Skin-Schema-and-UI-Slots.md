# Skin Schema & UI Slots Reference

Comprehensive technical reference for the `skin.json` declarative schema and component styling slots in **XDM Enhanced**.

---

## 1. Top-Level Descriptor Attributes

| Property | Type | Required | Description |
|:---|:---:|:---:|:---|
| `id` | String | **Yes** | Unique reverse-DNS identifier (e.g. `com.author.themename`). |
| `name` | String | **Yes** | Human-readable display name shown in settings. |
| `version` | String | **Yes** | Semantic version string (e.g. `1.0.0`). |
| `author` | String | No | Author or organization name. |
| `description` | String | No | Short summary of the visual theme. |
| `baseTheme` | String | **Yes** | Foundation FlatLaf theme: `LIGHT` or `DARK`. |
| `colors` | Object | **Yes** | Color token palette overrides (hex format `#RRGGBB`). |
| `backgrounds` | Object | No | Window and dialog background artwork definitions. |
| `styling` | Object | No | Component geometry and padding rules. |

---

## 2. Color Tokens (`colors`)

| Token | Hex Example | Description |
|:---|:---:|:---|
| `accent` | `#06b6d4` | Highlight color for progress bars, focused borders, and primary actions. |
| `background` | `#0b1120` | Main canvas background color behind table/card views. |
| `sidebar` | `#070b14` | Background color for the category navigation sidebar. |
| `cardBackground` | `#111827` | Background color for transfer cards in Classic Cards view. |
| `textPrimary` | `#f8fafc` | Primary typography color for headers and active labels. |
| `textSecondary` | `#94a3b8` | Secondary typography color for transfer rates, sizes, and ETAs. |
| `border` | `#1e293b` | Divider line color for toolbars, tables, and dialog borders. |

---

## 3. Background Slots (`backgrounds`)

Background artwork can be assigned to specific UI containers:

### Available UI Slots:
- `mainWindow`: Canvas behind the main download queue.
- `categorySidebar`: Vertical panel holding category navigation items.
- `transferTable`: Tabular download grid.
- `modalDialog`: Dialog backdrops (e.g. Properties, Progress, Settings).

### Background Slot Properties:

```json
"mainWindow": {
  "image": "backgrounds/main-window.png",
  "mode": "COVER",
  "opacity": 0.15
}
```

- **`image` (String):** Relative path to an image inside the `.xdmskin` archive (`.png`, `.jpg`, `.webp`, `.svg`).
- **`mode` (String):**
  - `COVER`: Scales image proportionally to fill the container (crops overflow).
  - `CONTAIN`: Scales image to fit entirely within the container (letterboxes).
  - `TILE`: Repeats the image as a seamless 2D pattern.
  - `CENTER`: Centers the image at original dimensions.
- **`opacity` (Float):** Decimal value between `0.0` (invisible) and `1.0` (fully opaque). Recommended: `0.10` to `0.25`.

---

## 4. Component Geometry (`styling`)

| Property | Type | Default | Description |
|:---|:---:|:---:|:---|
| `buttonArc` | Integer | `8` | Corner radius in pixels for buttons and input fields. |
| `cardArc` | Integer | `12` | Corner radius in pixels for transfer cards. |
| `toolbarPadding` | Integer | `6` | Internal vertical padding in pixels for the main toolbar. |

---

## 5. Modal Legibility Protection Engine

To prevent user-selected wallpapers from rendering dialog text unreadable:
1. The `SkinStyleResolver` computes background luminance across dialog regions.
2. If background contrast against `textPrimary` drops below WCAG AA thresholds (4.5:1), an automated contrast scrim is composited behind the labels.
3. Modal background artwork is capped at a maximum opacity of `0.30`.

---

## Related Developer Articles

* [[Skin Developer Quick Start|Skin-Developer-Quick-Start]]: Packaging tutorial.
* [[Architecture Overview|Architecture-Overview]]: Internal UI subsystem structure.

