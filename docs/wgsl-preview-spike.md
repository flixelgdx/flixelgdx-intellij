# WGSL preview: why there is none

A live preview of a `.wgsl` shader inside the IDE would need WebGPU, because FlixelGDX shaders are WGSL and only a
WebGPU device can run them. The only way for a plugin to show live GPU output without shipping native code is the
IDE's embedded browser (JCEF, which is Chromium), so the question was whether that browser offers `navigator.gpu`.

## What was tried

A developer-only action opened a small JCEF window with a test page that reported `navigator.gpu`,
`requestAdapter()`, `requestDevice()` and WebGL2 support. It was run in a sandbox IntelliJ IDEA 2026.3 on Linux.

## Result

WebGPU was not available in the IDE's embedded browser, so the preview cannot be built on JCEF. The test action has
been removed from the plugin.

## Why the flags route was not taken further

Chromium can sometimes be pushed into WebGPU with command-line flags such as `--enable-unsafe-webgpu`. In the IDE
those flags are shared by every plugin that embeds a browser, because all of them use one Chromium process, and the
IDE also adds its own GPU switches. Changing them for a shader preview would risk breaking other plugins, so the
plugin does not do it.

## Other options for later

- Render the preview with the framework itself in a separate process (for example a tiny desktop window started from
  the IDE) and stream frames or keep it as its own window.
- Revisit JCEF when a future IDE version ships a Chromium build with WebGPU enabled.
