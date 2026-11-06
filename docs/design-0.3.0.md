# Spark 0.3.0 shell

Status: design direction agreed on 2026-11-06. The interactive reference is
[design_proposal.html](design_proposal.html). The older
[prototype_preview.html](prototype_preview.html) is retained for comparison.
Desktop implementation follows this specification; the HTML page is a simulation.
Latest clarification: the desktop header has a bounded, scrollable site dock with
icons and names revealed on hover, following the current application's approach.
There is no separate site-pinning feature. The HTML remains unchanged for reference;
the dock and interaction rules below govern the desktop implementation.

## Appearance

- Use a neutral graphite shell, without the previous green cast. Keep the website's own UI visible and unchanged.
- Mix the selected site's accent into the thin window outline and active tab. Keep the shell background constant: switching sites should be recognizable without recoloring the window.
- Static prototype accent references: Claude orange `#D97757`, ChatGPT soft white `#ECECEC`, Gemini blue `#7CA5F8`, Notes neutral gray. These demonstrate the visual treatment; desktop colors are determined semi-automatically as described below. These colors describe the shell, not brand guidelines.
- Reserve a fixed 30 × 30 logical-pixel slot at the left of the header for a future optional mascot. No character, animation, generation tracking, or click action is required in this iteration.
- Header height: about 52 logical pixels. Tooltips, visible keyboard focus, and accessible button names are required. The dock viewport and window controls stay stable while individual site names expand inside the dock. Selection must also be indicated by shape and text, not color alone.

### Semi-automatic site accents

- When adding a site, attempt to extract a representative color from its icon. Ignore transparent pixels and account for alpha when sampling partially transparent pixels. White and grayscale icons are valid results: a white ChatGPT icon should be allowed to produce a soft white accent rather than an unrelated saturated hue.
- If the icon is missing, unreadable, or yields no usable pixels, choose a random color from a palette suitable for the graphite shell. Generate it once and persist it with the site's stable ID; switching sites, showing the window, and restarting must not randomize it again.
- Expose an accent color picker in site editing. A user's explicit color always takes priority over extraction, fallback generation, and later icon downloads or replacements. Preserve existing saved colors when migrating configurations.
- Keep the resolved color and its source (icon, random fallback, or manual) so that an explicit **Use icon color** action can retry extraction without silently overwriting a manual choice. If extraction still fails, retain the existing fallback rather than generating a different color on every retry.
- Apply the resolved accent through the same restrained outline/selection mixing for all sites. Keep readable foreground text and a visible selection indicator independently of the sampled color. Color changes must not navigate or recreate the live page.

## Sites and large collections

- All sites belong to one horizontally scrollable header dock. Bound its viewport to a comfortable budget of roughly three to four expanded links; this is a layout budget, not a limit on the number of sites or a pinned subset. Calculate link widths from horizontal padding, icon width, icon/name gap, and measured name width with a cap for long names; include inter-item gaps. Clamp the dock budget to the space remaining after the mascot slot, minimum drag area, and window controls. More sites never expand the whole header.
- Prefer the current application's icon-first interaction: show site icons, reveal a site's name on hover or keyboard focus, and retain the active site's name when space allows. Names expand within the bounded dock and may shift adjacent dock items, but must not move the dock's outer bounds or window controls. Keep icon/button height and icon hit areas stable. Truncate long names and expose the full name in a tooltip; freeze expansion while sorting to avoid moving drop targets.
- All sites opens a height-limited, scrollable list with search by name or address, the active selection, and explicit add/edit actions. Adding and editing use the existing validation rules for HTTP(S) addresses.
- Persist one site order keyed by stable IDs, shared by the dock and full list. Drag dock items or a list row's dedicated grip to reorder. Show an insertion indicator and scroll the respective viewport at its edges during sorting. Dragging a site must not move the window, navigate, reload, or select it. Preserve the existing user order when migrating; no pinned-site metadata is needed.
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

## Window behavior

- Always-on-top defaults to **on** for a new configuration. The header pin button toggles it and persists the choice. Focus changes and subsequent show/hide calls must not silently re-enable it. Temporary handling for owned authentication/file dialogs must preserve the user's choice.
- Auto-hide is a separate option and defaults to off. Clicking Spark menus, owned windows, file pickers, or authentication dialogs must not cause accidental hiding. The optional top-edge pill uses click to open; hover opening defaults to off.
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
4. Complete site management, the bounded icon-first scrollable dock, semi-automatic accents with a manual color picker, and shared ID-based ordering, including migration from the current URL-based order where needed. Review the existing DominantColorExtractor before reuse: grayscale support and a persisted random fallback are required. Import Heroicons UI assets with their notices and load site favicons with a local cache.
5. Validate on desktop: one/multiple monitors, mixed DPI, narrow windows, 30+ sites, long/duplicate names, missing icons, drag cancellation, keyboard ordering, resize during loading, repeated show/hide, pin on/off, file upload, authentication, and another application's fullscreen window. For accents, cover transparent/white/multicolor icons, failed extraction, fallback stability across restarts, and manual colors surviving icon updates without losing drafts.

The prototype demonstrates layout, color, ordering, editing, and simulated sessions.
Native z-order, focus return, dialog ownership, mixed DPI, and real draft retention
must be verified in the application. Do not add automatic page unloading or reloading.
Acceptance also covers a bounded dock with 30+ sites, hover/focus name expansion
without moving window controls, selecting an offscreen site from the list, and
every pointer/scroll ownership case described above.
