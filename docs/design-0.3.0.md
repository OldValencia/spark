# Spark 0.3.0 shell

Status: design direction agreed on 2026-11-06. The interactive reference is
[design_proposal.html](design_proposal.html). The older
[prototype_preview.html](prototype_preview.html) is retained for comparison.
Desktop implementation follows this specification; the HTML page is a simulation.
Latest clarification after desktop feedback: the header has a bounded, scrollable
site dock with icons and permanently visible names. Hover never changes tab size.
There is no separate site-pinning feature. The HTML remains unchanged for reference;
the dock and interaction rules below govern the desktop implementation.

## Appearance

- Use a neutral graphite shell, without the previous green cast. Keep the website's own UI visible and unchanged.
- Keep the outer window outline neutral; remove blue focus rings. Mix the selected site's accent into the active tab's border and background. Keep the shell background constant.
- Static prototype accent references: Claude orange `#D97757`, ChatGPT soft white `#ECECEC`, Gemini blue `#7CA5F8`, Notes neutral gray. These demonstrate the visual treatment; desktop colors are determined semi-automatically as described below. These colors describe the shell, not brand guidelines.
- Reserve a fixed 30 × 30 logical-pixel slot at the left of the header for a future optional mascot. No character, animation, generation tracking, or click action is required in this iteration.
- Header height: about 52 logical pixels. Tooltips, neutral keyboard focus, and accessible button names are required. Tab labels are always visible, and the dock viewport, individual tab widths, and window controls stay stable on hover and selection. Selection must also be indicated by shape and text, not color alone.
- The size trigger is an icon without numeric dimensions. Its popup offers S/M/L and simple actions. Always-on-top uses a pushpin glyph, not a bookmark. The size and behavior menus share the same owned popup surface as All sites.

### Semi-automatic site accents

- When adding a site, attempt to extract a representative color from its icon. Ignore transparent pixels and account for alpha when sampling partially transparent pixels. White and grayscale icons are valid results: a white ChatGPT icon should be allowed to produce a soft white accent rather than an unrelated saturated hue.
- If the icon is missing, unreadable, or yields no usable pixels, choose a random color from a palette suitable for the graphite shell. Generate it once and persist it with the site's stable ID; switching sites, showing the window, and restarting must not randomize it again.
- Expose an accent color picker in site editing. A user's explicit color always takes priority over extraction, fallback generation, and later icon downloads or replacements. Preserve existing saved colors when migrating configurations.
- Keep the resolved color and its source (icon, random fallback, or manual) so that an explicit **Use icon color** action can retry extraction without silently overwriting a manual choice. If extraction still fails, retain the existing fallback rather than generating a different color on every retry.
- Apply the resolved accent through restrained mixing in the selected tab for all sites. Keep readable foreground text and a visible selection indicator independently of the sampled color. Color changes must not navigate or recreate the live page.

## Sites and large collections

- All sites belong to one horizontally scrollable header dock. Bound its viewport to a comfortable budget of roughly three to four expanded links; this is a layout budget, not a limit on the number of sites or a pinned subset. For short collections, shrink the dock to its actual content width so All sites follows the final tab immediately; reserve arrow space only when scrolling is needed. Use 12 px site names and measure using the same font. Calculate widths from padding, icon width, icon/name gap, measured name width, and inter-item gaps. Clamp the budget to the space remaining after the mascot slot, minimum drag area, and window controls.
- Always show the icon and site name in a fixed-width tab. Measure the name using the actual rendered font, allowing for borders and pixel rounding so short names fit fully. Truncate only genuinely long names and expose the full name in a tooltip. Scroll arrows occupy dedicated space outside the viewport; the final tab must be fully reachable and never hidden behind an arrow. There is no hover expansion.
- All sites opens a height-limited, scrollable list with search by name or address, the active selection, and explicit add/edit actions. Adding and editing use the existing validation rules for HTTP(S) addresses.
- Persist one site order keyed by stable IDs, shared by the dock and full list. Drag dock items or a list row's dedicated grip to reorder. Show an insertion indicator and scroll the respective viewport at its edges during sorting. Dragging a site must not move the window, navigate, reload, or select it. Preserve the existing user order when migrating; no pinned-site metadata is needed.
- Dock and list sorting both animate neighboring items over about 150 ms. Reduce motion removes the interpolation. The list shows a dragged-row image, opens an insertion gap, accepts drops in that gap, and preserves its scroll position after dropping.
- Provide a keyboard alternative: focus a list grip and use Alt+Up/Down. Disable reordering while search is active and explain how to restore the full list; this avoids ambiguous positions among hidden matches.
- Selecting a site from **All sites** marks its row active and scrolls its dock item into view without reordering sites. Preserve list search, scroll, and focus when practical. A prototype-only large-collection control exercises overflow; it is not an application feature.
- Name-only edits and reordering preserve live pages. An explicit address change may replace that site's page, with a warning about losing its draft; unrelated pages stay alive.

### Pointer and scroll ownership

- Start window movement only from the header's designated empty drag area. Do not attach an unrestricted move handler to an ancestor of tabs, lists, popups, forms, scrollbars, or controls.
- Wheel and trackpad gestures over the site list scroll that list; over the dock they scroll the dock horizontally. At either viewport's scroll limits, they must not move, detach, resize, or hide the window. Dragging the scrollbar thumb is scrolling, not site sorting or window movement.
- Start list sorting only from its explicit grip after the drag threshold. Pressing or dragging the rest of a row must not initiate window movement. Tab sorting and window movement have disjoint start targets.
- The interaction that owns a pointer keeps it until release/cancellation; crossing into another area must not transfer a list gesture to window movement. Clear capture and sorting state on cancellation, focus loss, menu closure, and window hiding.
- Treat the reported HTML scroll/drag conflict as a desktop regression scenario. Verify mouse wheel, precision touchpad, scrollbar dragging, sorting near list boundaries, and opening/closing the menu during a gesture with real JavaFX/native windows. The HTML is intentionally not being changed in this iteration.

## Icon assets

- Selected UI set: Heroicons outline icons, under its [MIT license](https://github.com/tailwindlabs/heroicons/blob/master/LICENSE). Import only the assets used by Spark, with consistent stroke/size. Preserve upstream copyright and license text in the distributed third-party notices; record the source version when importing. No new icon assets are imported during design planning.
- Load site icons as favicons and cache them locally. Use them for destination identification and semi-automatic accent extraction. If loading fails, show an initial or generic website icon. Per the user's scope decision, no separate bundled brand-logo collection or brand-asset review workstream is planned for this iteration.
- The pushpin is an original Spark glyph under the project MIT license; other shell control glyphs use the bundled Heroicons assets.

## Settings and site editing

- Settings contains general preferences, the shortcut, and browser preferences. Site management belongs to All sites; do not duplicate an AI Providers section in Settings.
- Use a contained, owned graphite settings surface with a fixed heading and close button, scrollable content, subdued captions, and compact switches. Enabled switches are green. Order: highlighted Support Spark card (Coffee/Ko-fi and app version), Global hotkey, General, Browser. GitHub remains in the footer.
- Add/Edit site uses the same graphite palette, typography, field borders, and button shapes. Center it within the owner window and keep it in the monitor's working area. Validation appears inline. Site removal remains available in Edit with explicit confirmation.

## Window behavior

- Always-on-top defaults to **on** for a new configuration. Its control lives first in Window size, followed by Hide on outside click; both precede S/M/L. There are no duplicates in the header or three-dot menu. The rows have icons and clear On/Off badges; On is green. Focus changes and subsequent show/hide calls must not silently re-enable the user's choice. Temporary handling for owned authentication/file dialogs must preserve it.
- Auto-hide is a separate option and defaults to off. Clicking Spark menus, owned windows, file pickers, or authentication dialogs must not cause accidental hiding. Top-edge pill On keeps the pill visible while Spark is hidden. Off auto-hides it: approaching its top-center area slides/fades the pill in over 150 ms; leaving hides it after a short grace period and matching animation. Clicking the pill opens Spark. Reduced motion makes the appearance/disappearance immediate. Hover preview does not enable the persistent pill setting or change main-window placement.
- For a new configuration, placement defaults to floating and size M (860 × 700, clamped to the work area). Existing saved geometry and placement are restored. **Dock at the top** is a toggle with an On/Off badge, present only in Window size. Turning it off detaches into floating placement. The matching S/M/L preset is highlighted.
- **Open on top-edge hover**, also in Window size, opens a hidden window after a 300 ms dwell when the pill is kept visible. With an auto-hidden pill, hover previews only the pill and clicking opens Spark. The control explains this distinction in its tooltip. Native mouse notifications are sampled only while hidden, at most once per 40 ms; no permanent cursor-polling timer is used.
- All action and state rows in the small menus have icons. Zoom in the three-dot menu offers minus, a percentage/reset button, and plus. Tooltips explain the action and page shortcuts Ctrl+-, Ctrl+0, and Ctrl++. Buttons use the same increments and limits as keyboard zoom, and the percentage updates on zoom changes, site switches, and header recreation. Pin changes retain a visible state row after native z-order changes.
- Placement is docked or floating; visibility is hidden/opening/visible/closing. Pinning, auto-hide, selected monitor, geometry, and reduced motion are independent state.
- Dock to the selected monitor's top working-area edge, accounting for DPI and taskbars. Detach after a small drag threshold (about 5 logical pixels); use the same stage and browser session.
- Show a snap target near the top edge while dragging. Release there or choose **Dock at the top** to dock. A floating window stays floating across show/hide cycles.
- Offer S 700 × 580, M 860 × 700, L 1120 × 820, and fill available space, clamped to the monitor's working area. Keep the last manual size separately and offer **Restore manual size** after using a preset.
- Resize with edges/corners and a bottom height grip. Docked horizontal resizing remains centered; floating resizing anchors the opposite edge. The top edge is fixed while docked.
- Persist geometry and placement; recover into a visible working area after a monitor is disconnected. A short, restrained transition is optional; reduced motion removes it. No FPS promise.
- The existing global shortcut remains the reliable show/hide action. Escape closes Spark menus first and must not intercept website dialogs or cancellation actions. No forced website editor focus.

## Implementation order and acceptance

1. Extract window state and geometry control with meaningful tests for clamping, docking, resizing, persistence, and monitor removal.
2. Transfer the shell with working resize controls and site switching in the first desktop increment. Preserve JavaFX/JNA and visited live pages.
3. Add detaching, snap feedback, placement restoration, independent pinning/auto-hide, and optional pill behavior.
4. Complete site management, the bounded dock with fixed named tabs, semi-automatic accents with a manual color picker, and shared ID-based ordering, including migration from the current URL-based order where needed. Review the existing DominantColorExtractor before reuse: grayscale support and a persisted random fallback are required. Import Heroicons UI assets with their notices and load site favicons with a local cache.
5. Validate on desktop: one/multiple monitors, mixed DPI, narrow windows, 30+ sites, long/duplicate names, missing icons, drag cancellation, keyboard ordering, resize during loading, repeated show/hide, pin on/off, file upload, authentication, and another application's fullscreen window. For accents, cover transparent/white/multicolor icons, failed extraction, fallback stability across restarts, and manual colors surviving icon updates without losing drafts.

The prototype demonstrates layout, color, ordering, editing, and simulated sessions.
Native z-order, focus return, dialog ownership, mixed DPI, and real draft retention
must be verified in the application. Do not add automatic page unloading or reloading.
Acceptance also covers a bounded dock with 30+ sites, stable named tabs and a fully
reachable final tab, selecting an offscreen site from the list, and
every pointer/scroll ownership case described above.

## First desktop implementation

The JavaFX/JNA shell is implemented in the working tree. It includes a separate
WindowController, docked/floating placement, drag detachment and snap feedback,
edge/corner resizing and a bottom grip, size presets and manual-size restoration,
persisted geometry and always-on-top, a bounded icon dock, and an owned searchable
site popup with editing and ordering. The header reserves space for the mascot.
Site editing includes an accent picker and explicit icon-color extraction; random
fallback colors and manual choices persist. Heroicons and their MIT notice are
bundled in the application resources. The HTML reference was not changed during
the desktop implementation.

The optional top pill, reduced motion, and independent outside-click hiding are
also connected. On Windows, outside-click handling checks the foreground root
owner's process to avoid treating native browser children and owned dialogs as
external applications. Explicit hiding attempts to return focus to the previously
active window; outside-click hiding keeps focus on the application clicked.

Validation on Windows used an isolated profile and a real local WebView2 page:
30 sites, search/list scrolling, detachment, explicit docking, edge resizing,
presets/manual size, pin persistence, settings reload, manual/random accent
persistence, and a live draft surviving geometry changes and hide/show. The
existing unit suite and native application shutdown check passed. The temporary
visual probe and screenshots are in the ignored build directory.

Physical multi-monitor/mixed-DPI handling, real authentication and file pickers,
outside-click behavior with other applications, and tactile drag/scroll behavior
still need user acceptance. No full native regression suite was repeated.

## Desktop visual revision

The first functional shell was not visually accepted. The revision above removes
hover expansion and numeric dimensions, replaces the bookmark with a pushpin,
separates scroll controls from tabs, neutralizes the frame, and applies a shared
surface to menus, Edit, and Settings. A local visual check exercised 30 sites and
confirmed the final tab is fully reachable. The distribution build and existing
native application shutdown check passed; no broad test suite was repeated.

Current desktop captures use a local page inside the real native WebView:
[shell](previews/0.3.0-shell.png), [size menu](previews/0.3.0-size-menu.png),
[options](previews/0.3.0-options-menu.png), [Edit](previews/0.3.0-edit-site.png),
[Settings](previews/0.3.0-settings.png), and [scrolled dock](previews/0.3.0-overflow.png).
They are stored outside build so cleaning build does not remove the review images.
