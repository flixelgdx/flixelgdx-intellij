# WGSL preview spike: does the IDE browser offer WebGPU?

## Why

A live preview of a `.wgsl` shader inside the IDE would be a great help when writing shaders. The cheapest way to draw
one is the IDE's embedded browser (JCEF, which is Chromium), because Chromium already ships a WebGPU implementation
and WGSL is WebGPU's own language. That only works if the browser the IDE ships actually exposes `navigator.gpu`.
This spike finds out. Nothing here is visible to normal users.

## What the spike is

- `WgslPreviewSpikeAction` (package `org.flixelgdx.intellij.spike`) opens a small window with a `JBCefBrowser` that
  loads `src/main/resources/wgsl-spike/webgpu-probe.html`.
- The page prints: the user agent (so you know the Chromium version), `isSecureContext`, `!!navigator.gpu`, the result
  of `requestAdapter()` (vendor, architecture, features), the result of `requestDevice()`, and whether WebGL2 works.
- A button opens `chrome://gpu`, Chromium's own report about which GPU features are enabled and why others are not.
  Run Again reloads the probe.
- The action is declared in `META-INF/wgsl-spike.xml`, which is an optional dependency on the JCEF module
  (`com.intellij.modules.jcef`). It is marked `internal="true"` and its `update()` also requires the system property
  `flixelgdx.wgslSpike=true`, so it is never shown to normal users. In IDEs without JCEF the file is simply not loaded.

## How to run it

1. Start the sandbox IDE with the switch on (one Gradle run at a time):

   ```sh
   ./gradlew runIde -PwgslSpike=true
   ```

   This adds `-Dflixelgdx.wgslSpike=true` to the sandbox IDE. Without the property the action does not exist.
2. In the sandbox IDE open **Tools > FlixelGDX: WebGPU Probe (Spike)**. If the menu item is missing, JCEF is not
   available in that IDE (the item is hidden when `JBCefApp.isSupported()` is false, for example in a headless or
   remote setup) or the property was not passed.
3. Read the report. Note the user agent line, the `navigator.gpu` line and the `requestAdapter()` line.
4. Click **Open chrome://gpu** and look at the "Graphics Feature Status" list (`WebGPU`, `Vulkan`, `Canvas`,
   `Rasterization`) and the "Problems Detected" section. Copy the WebGPU lines into your notes.
5. If `navigator.gpu` is missing or the adapter is `null`, repeat with Chromium flags (next section).

## Trying Chromium flags

JCEF reads extra command-line switches from the system property `ide.browser.jcef.extra.args` (a comma-separated list;
confirmed in the platform's `SettingsHelper`). The `runIde` task forwards them for you with `-PjcefArgs`:

```sh
./gradlew runIde -PwgslSpike=true -PjcefArgs=--enable-unsafe-webgpu
./gradlew runIde -PwgslSpike=true "-PjcefArgs=--enable-unsafe-webgpu,--enable-features=Vulkan"
```

Mind the comma: because the property is split on commas, a switch that itself takes a comma list (for example
`--enable-features=A,B`) cannot be passed this way. Pass one feature per switch, or test the combination in an
external Chrome first.

Things worth trying, in this order (stop at the first that works):

| Try | Why |
|-----|-----|
| nothing | Recent Chromium versions enable WebGPU on Windows and macOS by default; Linux may differ. |
| `--enable-unsafe-webgpu` | Turns WebGPU on where it is still behind a flag, or on a GPU on the block list. |
| `--enable-features=Vulkan` | WebGPU on Linux runs on Vulkan, so Chromium's Vulkan backend must be allowed. |
| `--use-angle=vulkan` | Forces ANGLE onto Vulkan if the default backend is the problem. |
| `--enable-unsafe-webgpu,--ignore-gpu-blocklist` | Last resort for a GPU that Chromium blocks. |

The IDE adds its own switches as well. On Linux with `DISPLAY` set it passes `--ozone-platform=x11`, and it passes
`--disable-gpu` and related switches when the registry key `ide.browser.jcef.gpu.disable` is on (Help > Find Action >
Registry). Turn that key off while testing. Also compare with the registry key `ide.browser.jcef.osr.enabled`:
off-screen rendering (OSR) draws the page into a bitmap, which can prevent GPU-accelerated canvases from working, while
the windowed mode lets Chromium present on its own.

## Can a plugin set these flags?

Technically there are two routes, and neither is a good idea for a shipped plugin:

- **The system property** `ide.browser.jcef.extra.args` is read once, when JCEF starts. A plugin runs after the IDE
  has started, and the Markdown preview or another plugin may already have started JCEF, so setting the property
  from plugin code is usually too late. The user can set it in **Help > Edit Custom VM Options**, but that is a manual
  step for every user.
- **The extension point** `com.intellij.jcef.appRequiredArgumentsProvider` (`JBCefAppRequiredArgumentsProvider`,
  not dynamic, so it needs an IDE restart) lets a plugin add switches at startup. It is meant for arguments a plugin
  strictly needs to work.

The implications are the same for both: JCEF has **one Chromium process shared by the whole IDE**, so the switches are
application-wide. `--enable-unsafe-webgpu` and `--ignore-gpu-blocklist` would change the behavior of the Markdown
preview, the built-in HTML viewers and every other plugin that embeds a browser. A GPU driver
that crashes with WebGPU could take down the browser for all of them (and the GPU process crash limit is also set by
the IDE). Chromium also marks the "unsafe" switch as unsafe for a reason: the feature is not yet considered
hardened for untrusted web content. Our preview would only show local shader text, but the browser is shared with
pages that are not ours. Recommendation: do not ship a plugin that changes these switches. If WebGPU only works with
flags, document the flags for people who want the preview and keep the feature opt-in.

## Reading the result

| Result | Meaning for the preview |
|--------|-------------------------|
| `navigator.gpu` present, adapter and device ok with no flags | A JCEF preview is viable. Next step: a tool window that renders a full-screen quad with the shader, using the same preamble as `FlixelWgsl` so the shader compiles as in the game. |
| Works only with flags | Viable only as an opt-in, with the implications above. |
| `navigator.gpu` missing, or `isSecureContext` false | Check the secure context first (the page is loaded from a `file:` URL by `loadHTML`; if it is reported as insecure, serve the page from a custom scheme handler or `localhost`). If it is secure and the API is still missing, the Chromium in the JetBrains Runtime was built without WebGPU, and the browser route is not possible. |
| Adapter `null` | Chromium found no usable GPU backend (often Vulkan on Linux). Check `chrome://gpu`. |

Fallbacks when the browser route is closed: open the shader in an external browser with a small page, or run the
validation that the framework's shader Gradle plugin already does and show its `file:line:column` messages in the
IDE. The latter needs no GPU at all.

## Findings so far

These come from reading the IDE 2026.3 (build 263) distribution, because the IDE could not be run for this write-up.

- JCEF is a separate bundled plugin there (`com.intellij.modules.jcef`), so the spike depends on it optionally.
- Extra Chromium switches come from `ide.browser.jcef.extra.args` (comma-separated) and from the
  `com.intellij.jcef.appRequiredArgumentsProvider` extension point. Both are application-wide.
- The IDE adds switches of its own (`--ozone-platform=x11` on Linux, `--disable-gpu` family behind a registry key,
  `--disable-site-isolation-trials` for OSR, `--no-proxy-server`, `--autoplay-policy=no-user-gesture-required`).
- Whether `navigator.gpu` exists in the bundled Chromium is **not yet known**. Run the spike and record the result
  here:

  | IDE build | OS and GPU | Chromium (user agent) | `navigator.gpu` | adapter | flags needed |
  |-----------|------------|-----------------------|-----------------|---------|--------------|
  |           |            |                       |                 |         |              |
