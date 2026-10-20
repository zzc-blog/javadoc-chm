Version 3.0.0 (unreleased)
-----------------------------

* javadoc 9+ support (`Javadoc9Style`): verified against Oracle/Temurin JDK 21 docs
* module support: table of contents is now `module -> package -> class`
  (read from `element-list`, ordered `java.*`, `jdk.*`, then the rest)
* class list is read from `allclasses-index.html` (replaces `allclasses-frame.html`)
* `id="..."` anchors of javadoc 9+ are used as-is; legacy `<a name>` rewriting is kept
  for older docs but no longer touches new-style pages
* index pages are read from `index-files/index-N.html` with the new
  `class="member-name-link"` markup; section-heading entries are skipped
* fix: generated `redirs/*.html` file names are sanitized, so signatures containing
  `<`, `>`, `?` or `:` no longer crash on Windows
* fix: `redirs/*.html` pages now show `pkg.Type#member` instead of `pkg#member`
* fix: CHM title / compiled file name are entity-decoded and stripped of the
  `Java SE nn & JDK nn` suffix
* fix: class pages missing on disk and packages missing from `element-list` are
  reported instead of throwing a `NullPointerException`

### IE7 engine compatibility

The CHM viewer runs the IE7 engine, which the stock javadoc 9+ output cannot
render. The following passes were added and verified against a real IE7 engine
(`documentMode === 7`) loading pages through `mk:@MSITStore:`:

* `LayoutNeutralizer` — unwrap the flexbox page skeleton so the document scrolls
  in normal flow again (`flex-box` / `flex-header` / `flex-content`)
* `TableNeutralizer` — turn the div pseudo-tables back into real `<table>`,
  keeping the original layout classes so columns can be sized
* `CssNeutralizer` — expand `var(--x)`, drop `@media`, degrade flex/grid,
  `::before`/`::after` to the single-colon form; append rules for the converted
  tables, the autocomplete popup and the offline-only hiding
* `ScriptNeutralizer` — `const` → `var`; rewrite `.attr("aria-*", …)` to raw
  `setAttribute` (jQuery's IE<8 hook throws "Member not found"); cap the search
  result count; remove the hover anchor-link icons; replace the array `for..in`
  loops that enumerated the ES5 shims added to `Array.prototype`
* `ScriptShim` — replace `script.js` with an ES3 re-implementation of
  `show` / `switchTab` / `loadScripts`, snapshot tab membership so repeated
  switching keeps working, collapse whole hidden rows, and re-anchor the
  autocomplete popup under the search box
* fix: `stylesheet.css` no longer hides `div.page-search-info` behind
  `height:0` — IE7 does not collapse it, so the "Copy URL" icon leaked
* fix: `module-graph.png` is replaced by a text marker and dropped from the
  project, keeping the file count under what the 32-bit `hhc.exe` can compile
* fix: `redirs/*.html` paths were prefixed twice in the full (non-sample) mode
* fix: index keywords that are also type names (`SerialClob`) point at the class
  page instead of an "occurrences" list; occurrence pages dropped by 73%
* new: `-Djavadoc.chm.cjk=true` injects a CJK font stack and looser line height
  for translated documentation


Version 2.1.0 (2014-03-25)
-----------------------------

* javadoc 8 support


Version 2.0.0 (2013-11-16)
-----------------------------

* rename package
* add license


Version 1.0.0 (2013-04-17)
-----------------------------

* support java 6 & 7 style
* first public release
