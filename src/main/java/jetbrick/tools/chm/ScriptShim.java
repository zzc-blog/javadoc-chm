/**
 * javadoc.chm
 * http://subchen.github.io/javadoc.chm/
 *
 * Copyright 2010-2014 Guoqiang Chen. All rights reserved.
 * Email: subchen@gmail.com
 * Copyright 2026 zzc-blog (https://github.com/zzc-blog)
 * Modified from the original work: JDK 21 support and CHM IE7 engine
 * compatibility were added. See CHANGELOG.md for the list of changes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrick.tools.chm;

/**
 * The JavaScript written over {@code script.js}.
 *
 * <p>The HTML Help viewer runs an old Internet Explorer engine. It does have a
 * working JavaScript interpreter, but no ES5: no {@code querySelectorAll}, no
 * {@code Array.prototype.forEach}, no {@code addEventListener}, no
 * {@code classList}. So the original javadoc script fails, and the page shows a
 * script error dialog.
 *
 * <p>This shim re-implements the two functions the pages actually call, using
 * only APIs that engine knows:
 * <ul>
 *   <li>{@code show(tableId, selected, columns)} - the
 *       "All Methods / Static Methods / ..." filter of the summary tables;</li>
 *   <li>{@code switchTab(event)} - keyboard navigation between those buttons;</li>
 *   <li>{@code loadScripts(root, name)} - the inline bootstrap of every page;</li>
 *   <li>no-ops for the remaining handlers, so nothing throws.</li>
 * </ul>
 */
public class ScriptShim {

    /** The content written over {@code script.js}. */
    static final String CONTENT =
            "/* javadoc.chm shim.\n" +
            " * The HTML Help viewer runs an old Internet Explorer engine: it can execute\n" +
            " * JavaScript, but it has no ES5 (no querySelectorAll, no Array.forEach, no\n" +
            " * addEventListener, no classList). The original javadoc script therefore\n" +
            " * throws on every page. This file re-implements the functions the pages\n" +
            " * call, using only APIs that engine supports.\n" +
            " */\n" +
            "(function () {\n" +
            "\n" +
            "  var EVEN = '#ffffff';\n" +
            "  var ODD  = '#eeeeef';\n" +
            "\n" +
            "  /* className based lookup: the engine has no getElementsByClassName */\n" +
            "  function byClass(tag, className) {\n" +
            "    var all = document.getElementsByTagName(tag);\n" +
            "    var out = [];\n" +
            "    for (var i = 0; i < all.length; i++) {\n" +
            "      if ((' ' + all[i].className + ' ').indexOf(' ' + className + ' ') >= 0) {\n" +
            "        out.push(all[i]);\n" +
            "      }\n" +
            "    }\n" +
            "    return out;\n" +
            "  }\n" +
            "\n" +
            "  function hasClass(el, className) {\n" +
            "    return (' ' + el.className + ' ').indexOf(' ' + className + ' ') >= 0;\n" +
            "  }\n" +
            "\n" +
            "  /* The filter: hide every cell of the table that does not carry the\n" +
            "   * selected tab class, show the ones that do, and re-band the rows.\n" +
            "   *\n" +
            "   * Two things made repeated switching break before:\n" +
            "   *   1) the tab membership class was read from className AFTER the\n" +
            "   *      row-colour rewrite had touched it, so a second switch lost the\n" +
            "   *      markers and every row ended up hidden;\n" +
            "   *   2) rows hidden by an earlier tab kept display:none, so switching\n" +
            "   *      back never made them visible again.\n" +
            "   * Membership is therefore snapshotted once per table, into a\n" +
            "   * data attribute IE7 keeps on the element, and every call restores\n" +
            "   * both the cell and its row state from scratch. */\n" +
            "  function snapshotTable(tableId) {\n" +
            "    var cells = byClass('td', tableId);\n" +
            "    for (var i = 0; i < cells.length; i++) {\n" +
            "      var cell = cells[i];\n" +
            "      if (cell.getAttribute('data-chm-tabs') !== null) { continue; }\n" +
            "      /* keep the tab classes that follow the <tableId>-tabN pattern */\n" +
            "      var keep = [];\n" +
            "      var parts = String(cell.className).split(/\\s+/);\n" +
            "      for (var k = 0; k < parts.length; k++) {\n" +
            "        if (parts[k].indexOf('-tab') >= 0) { keep.push(parts[k]); }\n" +
            "      }\n" +
            "      cell.setAttribute('data-chm-tabs', keep.join(' '));\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  function tabMemberOf(cell, selected) {\n" +
            "    var known = cell.getAttribute('data-chm-tabs');\n" +
            "    if (known === null) { known = ''; }\n" +
            "    if (known === '') {\n" +
            "      /* cell appears in every tab */\n" +
            "      return true;\n" +
            "    }\n" +
            "    return (' ' + known + ' ').indexOf(' ' + selected + ' ') >= 0;\n" +
            "  }\n" +
            "\n" +
            "  function show(tableId, selected, columns) {\n" +
            "    snapshotTable(tableId);\n" +
            "\n" +
            "    var cells = byClass('td', tableId);\n" +
            "    var headers = byClass('th', tableId);\n" +
            "\n" +
            "    for (var i = 0; i < headers.length; i++) {\n" +
            "      headers[i].style.display = '';\n" +
            "    }\n" +
            "\n" +
            "    /* group the cells per row through their common parentNode */\n" +
            "    var rowIndex = -1;\n" +
            "    var lastRow = null;\n" +
            "    var rowVisible = false;\n" +
            "    var seenRow = false;\n" +
            "    for (var j = 0; j < cells.length; j++) {\n" +
            "      var cell = cells[j];\n" +
            "      var row = cell.parentNode;\n" +
            "      if (row !== lastRow) {\n" +
            "        if (lastRow) {\n" +
            "          lastRow.style.display = rowVisible ? '' : 'none';\n" +
            "        }\n" +
            "        lastRow = row;\n" +
            "        rowVisible = false;\n" +
            "        rowIndex++;\n" +
            "      }\n" +
            "      var member = (selected === tableId) || tabMemberOf(cell, selected);\n" +
            "      if (member) {\n" +
            "        cell.style.display = '';\n" +
            "        rowVisible = true;\n" +
            "        if (hasClass(cell, 'odd-row-color') || hasClass(cell, 'even-row-color')) {\n" +
            "          cell.className = cell.className\n" +
            "            .replace(/\\s*odd-row-color\\s*/g, ' ')\n" +
            "            .replace(/\\s*even-row-color\\s*/g, ' ');\n" +
            "          cell.className += (rowIndex % 2 === 0) ? ' even-row-color' : ' odd-row-color';\n" +
            "        }\n" +
            "      } else {\n" +
            "        cell.style.display = 'none';\n" +
            "      }\n" +
            "    }\n" +
            "    if (lastRow) {\n" +
            "      lastRow.style.display = rowVisible ? '' : 'none';\n" +
            "    }\n" +
            "\n" +
            "    updateTabs(tableId, selected);\n" +
            "  }\n" +
            "\n" +
            "  function updateTabs(tableId, selected) {\n" +
            "    /* The buttons live in div.table-tabs, not inside the tabpanel, so\n" +
            "     * scan every button in the document and match on the data-chm-tab\n" +
            "     * attribute that TableNeutralizer added. */\n" +
            "    var buttons = document.getElementsByTagName('button');\n" +
            "    for (var i = 0; i < buttons.length; i++) {\n" +
            "      var b = buttons[i];\n" +
            "      var target = b.getAttribute('data-chm-tab');\n" +
            "      if (!target) { continue; }\n" +
            "      b.className = (target === selected) ? 'active-table-tab' : 'table-tab';\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  /* Keyboard navigation of the tab bar. */\n" +
            "  function switchTab(ev) {\n" +
            "    ev = ev || window.event;\n" +
            "    var el = ev.target || ev.srcElement;\n" +
            "    if (!el) { return; }\n" +
            "    if (ev.keyCode === 37 && el.previousSibling) { el.previousSibling.focus(); }\n" +
            "    if (ev.keyCode === 39 && el.nextSibling) { el.nextSibling.focus(); }\n" +
            "  }\n" +
            "\n" +
            "  /* Every page carries this inline bootstrap in its <head>:\n" +
            "   *   var pathtoroot = '../..'; loadScripts(document, 'script');\n" +
            "   * The original loads search.js and the five *-search-index.js data\n" +
            "   * files by inserting <script> tags - that mechanism works in IE, and\n" +
            "   * the search needs the data files, so it is kept. script.js itself is\n" +
            "   * this file; inserting a tag for it is harmless (the browser skips an\n" +
            "   * already loaded src). */\n" +
            "  function loadScripts(doc, tag) {\n" +
            "    var names = ['search.js',\n" +
            "                 'module-search-index.js',\n" +
            "                 'package-search-index.js',\n" +
            "                 'type-search-index.js',\n" +
            "                 'member-search-index.js',\n" +
            "                 'tag-search-index.js'];\n" +
            "    for (var i = 0; i < names.length; i++) {\n" +
            "      var s = doc.createElement(tag);\n" +
            "      s.src = pathtoroot + names[i];\n" +
            "      var first = doc.getElementsByTagName(tag)[0];\n" +
            "      first.parentNode.insertBefore(s, first);\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  function noop() {}\n" +
            "\n" +
            "  /* jQuery-UI's position.js mis-reads the offsetParent chain in IE7\n" +
            "   * once the flex frame is gone, so the autocomplete popup opened\n" +
            "   * hundreds of pixels to the left of the search box, on top of the\n" +
            "   * page text. Re-anchoring it under the input on every open is both\n" +
            "   * simpler and exact: the menu is a body child, so page coordinates\n" +
            "   * are what its absolute offset needs. */\n" +
            "  function placeAutocomplete() {\n" +
            "    var lists = document.getElementsByTagName('ul');\n" +
            "    for (var i = 0; i < lists.length; i++) {\n" +
            "      var list = lists[i];\n" +
            "      if (!hasClass(list, 'ui-autocomplete') || list.style.display === 'none') { continue; }\n" +
            "      var input = document.getElementById('search-input');\n" +
            "      if (!input) { return; }\n" +
            "      var left = 0, top = 0, el = input;\n" +
            "      while (el) { left += el.offsetLeft; top += el.offsetTop; el = el.offsetParent; }\n" +
            "      list.style.left = left + 'px';\n" +
            "      list.style.top = (top + input.offsetHeight) + 'px';\n" +
            "      list.style.width = input.offsetWidth + 'px';\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  function watchAutocomplete() {\n" +
            "    var input = document.getElementById('search-input');\n" +
            "    if (!input) { return; }\n" +
            "    var handler = function () { setTimeout(placeAutocomplete, 0); };\n" +
            "    if (input.attachEvent) {\n" +
            "      input.attachEvent('onkeyup', handler);\n" +
            "      input.attachEvent('onkeydown', handler);\n" +
            "      input.attachEvent('onclick', handler);\n" +
            "    } else if (input.addEventListener) {\n" +
            "      input.addEventListener('keyup', handler, false);\n" +
            "      input.addEventListener('keydown', handler, false);\n" +
            "      input.addEventListener('click', handler, false);\n" +
            "    }\n" +
            "    /* keep it anchored while the list is open */\n" +
            "    setInterval(placeAutocomplete, 250);\n" +
            "  }\n" +
            "\n" +
            "  if (window.jQuery) {\n" +
            "    window.jQuery(watchAutocomplete);\n" +
            "  } else if (window.attachEvent) {\n" +
            "    window.attachEvent('onload', watchAutocomplete);\n" +
            "  } else {\n" +
            "    window.onload = watchAutocomplete;\n" +
            "  }\n" +
            "\n" +
            "  /* Publish. Inline handlers resolve these on window. */\n" +
            "  window.show        = show;\n" +
            "  window.switchTab   = switchTab;\n" +
            "  window.updateTabs  = updateTabs;\n" +
            "  window.loadScripts = loadScripts;\n" +
            "  window.placeAutocomplete = placeAutocomplete;\n" +
            "\n" +
            "  var names = ['hide','toggle','toggleAllTabs','highlightTab','initTabs',\n" +
            "               'initSearch','initCopyButtons','filterTable','checkRevision',\n" +
            "               'javascriptUrl','initSectionMonitor','searchTag','copySnippet',\n" +
            "               'copyToClipboard'];\n" +
            "  for (var k = 0; k < names.length; k++) {\n" +
            "    if (typeof window[names[k]] !== 'function') { window[names[k]] = noop; }\n" +
            "  }\n" +
            "\n" +
            "  window.pathtoroot = window.pathtoroot || '';\n" +
            "\n" +
            "  /* Flag the IE7 environment for the javadoc scripts. search.js picks\n" +
            "   * its anchor-link icon with this flag: link.png renders in the CHM's\n" +
            "   * IE7 engine, link.svg shows up as a broken-image square. */\n" +
            "  window.__ie7Compat = true;\n" +
            "\n" +
            "  /* The search data files (*-search-index.js) each end with a call to\n" +
            "   * updateSearchResults(). search.js later re-defines it once it has\n" +
            "   * indexed them; the original script.js declared it up front as a no-op\n" +
            "   * so the early call does not throw. That declaration is restored here\n" +
            "   * (the shim replaced script.js). indexFilesLoaded() is likewise global. */\n" +
            "  window.updateSearchResults = function () {};\n" +
            "  window.indexFilesLoaded = function () {\n" +
            "    return !!(window.moduleSearchIndex && window.packageSearchIndex\n" +
            "      && window.typeSearchIndex && window.memberSearchIndex\n" +
            "      && window.tagSearchIndex);\n" +
            "  };\n" +
            "})();\n";

    private ScriptShim() {
    }

    /** @return the shim source */
    public static String content() {
        return CONTENT;
    }
}
