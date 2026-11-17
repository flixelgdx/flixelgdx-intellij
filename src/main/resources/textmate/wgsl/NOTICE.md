# Third-party notice

`syntaxes/wgsl.tmLanguage.json` is based on the grammar of the vscode-wgsl extension by Bartlomiej Marynczak
(https://github.com/PolyMeilex/vscode-wgsl), which is released under the MIT License. The license text is in the
`LICENSE` file next to this notice.

Changes made for FlixelGDX: float literals accept the `f` and `h` suffixes and integer literals accept `i`; `const`,
`override` and `alias` are highlighted like `let` and `var`; `requires`, `diagnostic` and `const_assert` are keywords;
and texture and sampler types are highlighted as types. The file was also reformatted.
