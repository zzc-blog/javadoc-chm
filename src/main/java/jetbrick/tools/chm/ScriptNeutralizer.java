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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Adapts the JavaScript of the javadoc output to the HTML Help viewer's old IE
 * engine, changing as little as possible.
 *
 * <p>What the viewer can and cannot run:
 * <ul>
 *   <li>JavaScript itself works, but only ES3: no {@code addEventListener}, no
 *       {@code querySelectorAll}, no {@code classList}, no {@code const};</li>
 *   <li>jQuery 1.12.4 and jQuery-UI 1.12.1 are the last releases that support
 *       IE 7/8, so those are substituted for the bundled 3.x / 1.14 versions;</li>
 *   <li>with those libraries the original {@code search.js} works as-is, except
 *       for five {@code const} declarations that are rewritten to {@code var}.</li>
 * </ul>
 *
 * <p>{@code script.js} itself (the page bootstrap) is replaced by a small shim
 * because the original uses {@code addEventListener} / {@code querySelectorAll}
 * on load, which throws before anything else can run. The shim keeps
 * {@code loadScripts()} working so the search data files are still injected.
 */
public class ScriptNeutralizer {

    /** The IE7 jQuery build shipped with the tool. */
    private static final String JQUERY_IE = "/script-dir/jquery-1.12.4.min.js";
    /** The IE7 jQuery-UI build (autocomplete + menu + position). */
    private static final String JQUERY_UI_IE = "/script-dir/jquery-ui-1.12.1.min.js";

    private static final String SHIM = ScriptShim.content();

    /**
     * @return the number of files that were rewritten
     */
    public int apply(File apiLocation) throws IOException {
        int count = 0;

        // 1) page bootstrap shim (keeps loadScripts working)
        File scriptJs = new File(apiLocation, "script.js");
        if (scriptJs.isFile()) {
            FileUtils.writeStringToFile(scriptJs, SHIM, "utf-8");
            count++;
        }

        // 2) swap the jQuery / jQuery-UI libraries for their IE7 builds.
        //    The stylesheet (jquery-ui.min.css) is NOT replaced: the javadoc
        //    1.7KB file only lays out the drop-down (position, padding) and
        //    leaves all colours to stylesheet.css, so it works with the 1.12
        //    widgets unchanged.
        count += copyResource(apiLocation, "script-dir/jquery-3.7.1.min.js", JQUERY_IE);
        count += copyResource(apiLocation, "script-dir/jquery-ui.min.js", JQUERY_UI_IE);

        // 3) search.js: const → var AND aria-* attr() → raw setAttribute.
        //    jQuery 1.12.4's .attr("aria-label") hits its IE<8 generic hook, which
        //    assigns el["aria-label"] = value - IE7 throws "Member not found"
        //    (0x80020006) for those bracket assignments. Raw setAttribute works
        //    for every aria-* name, so the calls are rewritten to it.
        File searchJs = new File(apiLocation, "search.js");
        if (searchJs.isFile()) {
            String js = FileUtils.readFileToString(searchJs, Config.encoding);
            String converted = es3ify(js);
            converted = fixAriaAttr(converted);
            converted = tuneSearchPerf(converted);
            converted = removeAnchorLinks(converted);
            converted = fixForInLoops(converted);
            if (!converted.equals(js)) {
                FileUtils.writeStringToFile(searchJs, converted, Config.encoding);
                count++;
            }
        }

        // 4) search-page.js: same treatment - the for..in loops live here
        File searchPage = new File(apiLocation, "search-page.js");
        if (searchPage.isFile()) {
            String js = FileUtils.readFileToString(searchPage, Config.encoding);
            String converted = es3ify(js);
            converted = fixAriaAttr(converted);
            converted = fixForInLoops(converted);
            converted = tunePageSearchPerf(converted);
            if (!converted.equals(js)) {
                FileUtils.writeStringToFile(searchPage, converted, Config.encoding);
                count++;
            }
        }

        // 5) jquery-ui.min.js: same problem inside the widget code (39 aria
        //    writes). Patch its .attr("aria-…") calls the same way.
        File jqueryUi = new File(apiLocation, "script-dir/jquery-ui.min.js");
        if (jqueryUi.isFile()) {
            String js = FileUtils.readFileToString(jqueryUi, "utf-8");
            String converted = fixAriaAttr(js);
            if (!converted.equals(js)) {
                FileUtils.writeStringToFile(jqueryUi, converted, "utf-8");
                count++;
            }
        }

        System.out.println("script adapted for IE7: " + count + " file(s)");
        return count;
    }

    /** Rewrites the few ES6 constructs that appear in the javadoc search scripts
     * into ES3. Only {@code const} is present (5 occurrences); nothing else in
     * those files is post-ES3.
     */
    private String es3ify(String js) {
        return js.replaceAll("\\bconst\\b", "var");
    }

    /**
     * Caps the search result count and raises the keystroke delay for the IE7
     * engine: {@code doSearch} regex-scans 51k members on every keystroke and
     * jQuery-UI then builds one DOM node per hit. IE7 executes that roughly
     * 50x slower than a modern browser, so 300 results per keystroke makes the
     * input field feel frozen. 50 hits cover every realistic lookup and render
     * in a fraction of the time.
     */
    private String tuneSearchPerf(String js) {
        String out = js.replace("var MAX_RESULTS = 300;", "var MAX_RESULTS = 50;");
        out = out.replace("delay: 200,", "delay: 350,");
        return out;
    }

    /**
     * Same capping for the standalone search page: it requests up to 1200 hits
     * per category, which produced several thousand result links that IE7
     * needed many seconds to build. 200 per category keeps the page usable.
     */
    private String tunePageSearchPerf(String js) {
        return js.replace(
                "doSearch({ term: term, maxResults: 1200 }, renderResults);",
                "doSearch({ term: term, maxResults: 200 }, renderResults);");
    }

    /**
     * Replaces the two {@code for (var x in array)} loops of search-page.js
     * with index loops. The compat.js shims add enumerable methods to
     * {@code Array.prototype} (IE7 lacks them natively), and JScript's
     * {@code for..in} enumerates those too, so {@code result} yields the names
     * "forEach"/"map"/... as items. {@code item.category} is then undefined and
     * {@code r[undefined].push} throws "无法获取未定义或 null 引用的属性 push".
     * IE7 has no {@code Object.defineProperty}, so the shims cannot be made
     * non-enumerable - the loops themselves have to go.
     */
    private String fixForInLoops(String js) {
        String out = js.replace(
                "for (var i in result) {",
                "for (var i = 0; i < result.length; i++) {");
        out = out.replace(
                "for (var key in r) {",
                "for (var ki = 0; ki < RESULT_KEYS.length; ki++) { var key = RESULT_KEYS[ki];");
        // supply the fixed key list right before the renderResults function
        if (out.indexOf("RESULT_KEYS") >= 0 && out.indexOf("var RESULT_KEYS") < 0) {
            out = out.replace(
                    "function renderResults(result) {",
                    "var RESULT_KEYS = [\"types\", \"members\", \"packages\", \"modules\", \"searchTags\"];\n"
                  + "    function renderResults(result) {");
        }
        return out;
    }

    /**
     * Removes the hover "anchor link" icon that search.js appends to every
     * section heading. The icon is hidden on hover-capable browsers with
     * {@code a.anchor-link { opacity: 0 }}, a property IE7 does not support -
     * the icon would be permanently visible, and in the CHM the link.png fails
     * to decode from {@code mk:@MSITStore}, leaving a broken-image square next
     * to every heading. The links add no value offline, so the block goes.
     */
    private String removeAnchorLinks(String js) {
        // The block starts at $("section[id] > :header, ...) - locate the '$('
        // whose selector begins with "section[id]".
        String needle = "section[id] > :header";
        int sel = js.indexOf(needle);
        if (sel < 0) return js;
        int start = js.lastIndexOf("$(", sel);
        if (start < 0) return js;

        int openParen = start + 1;
        int close = findCallClose(js, openParen + 1);
        if (close < 0) return js;
        // the full statement is $("...").each(function(){...}); - the .each
        // chain follows the closing paren of the selector call.
        int eachAt = js.indexOf(".each(", close);
        if (eachAt >= 0 && eachAt - close < 8) {
            int eachOpen = eachAt + 5;
            int eachClose = findCallClose(js, eachOpen + 1);
            if (eachClose >= 0) {
                // include the trailing semicolon if present
                int end = eachClose + 1;
                if (end < js.length() && js.charAt(end) == ';') end++;
                return js.substring(0, start)
                        + "/* anchor-link icons removed for the CHM viewer */"
                        + js.substring(end);
            }
        }
        return js.substring(0, start)
                + "/* anchor-link icons removed for the CHM viewer */"
                + js.substring(close + 1);
    }

    /**
     * {@code .attr("aria-…", …)} → {@code .each(function(){this.setAttribute(...)}}.
     *
     * <p>jQuery 1.12.4 on IE7 throws "Member not found" for aria attribute names
     * whose suffix is not one of its rboolean keywords ({@code hidden},
     * {@code controls}, ...): the generic hook ends in {@code elem[name] = value},
     * and IE7 rejects unknown-property bracket assignment. Raw
     * {@code setAttribute} accepts every aria name, so each jQuery call is
     * replaced with an equivalent that runs it on the raw element.
     *
     * <p>Handles the two argument shapes present in the javadoc scripts:
     * {@code .attr("aria-x", expr)} and {@code .attr("aria-x")} (read form).
     */
    private String fixAriaAttr(String js) {
        if (js.indexOf(".attr(\"aria-") < 0 && js.indexOf(".attr('aria-") < 0
                && js.indexOf("attr(\"aria-") < 0 && js.indexOf("attr('aria-") < 0) {
            return js;
        }

        // write form: .attr("aria-…", <argument>) - the argument may contain
        // nested parens (string concatenation), so it is captured by balance.
        StringBuilder out = new StringBuilder(js.length() + 256);
        int pos = 0;
        while (true) {
            int at = indexOfAttrAria(js, pos);
            if (at < 0) {
                out.append(js, pos, js.length());
                break;
            }
            int nameStart = js.indexOf('"', at);
            if (nameStart < 0) nameStart = js.indexOf('\'', at);
            char quote = js.charAt(nameStart);
            int nameEnd = js.indexOf(quote, nameStart + 1);
            String name = js.substring(nameStart + 1, nameEnd);

            int comma = skipSpace(js, nameEnd + 1);
            int argsOpen = -1;
            int argsClose = -1;
            if (comma < js.length() && js.charAt(comma) == ',') {
                argsOpen = comma + 1;
                argsClose = findCallClose(js, at + 5);
            } else {
                argsClose = js.indexOf(')', at);
            }

            out.append(js, pos, at);
            if (argsOpen >= 0) {
                String arg = js.substring(argsOpen, argsClose).trim();
                out.append("each(function(){this.setAttribute(\"")
                   .append(name).append("\",").append(arg).append(")})");
            } else {
                out.append("each(function(){return this.getAttribute(\"")
                   .append(name).append("\")})");
            }
            pos = argsClose + 1;
        }
        return out.toString();
    }    /** Finds the next {@code .attr("aria-…")} call at or after {@code from}. */
    private int indexOfAttrAria(String js, int from) {
        int i = from;
        while (true) {
            int at = js.indexOf("attr(", i);
            if (at < 0) return -1;
            int q = at + 5;
            while (q < js.length() && js.charAt(q) == ' ') q++;
            if (q < js.length() && (js.charAt(q) == '"' || js.charAt(q) == '\'')) {
                int e = js.indexOf(js.charAt(q), q + 1);
                if (e > 0 && js.substring(q + 1, e).startsWith("aria-")) return at;
            }
            i = at + 5;
        }
    }

    /** @return index just past the space run starting at {@code i} */
    private int skipSpace(String s, int i) {
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    /**
     * @return index of the ')' that closes the argument list of the call whose
     *         argument list starts at {@code afterOpen} (i.e. just past the
     *         '(' of {@code .attr(}), counting nesting and skipping string
     *         literals. The initial '(' itself is expected to be already
     *         consumed, so the scan starts with depth 1.
     */
    private int findCallClose(String js, int afterOpen) {
        int depth = 1;
        boolean inStr = false;
        char quote = 0;
        for (int i = afterOpen; i < js.length(); i++) {
            char c = js.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == quote) inStr = false;
                continue;
            }
            if (c == '\'' || c == '"') { inStr = true; quote = c; continue; }
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int copyResource(File apiLocation, String relative, String resource) throws IOException {
        File target = new File(apiLocation, relative);
        InputStream in = ScriptNeutralizer.class.getResourceAsStream(resource);
        if (in == null) {
            return 0;   // resource not bundled; leave the original in place
        }
        try {
            FileUtils.copyInputStreamToFile(in, target);
        } finally {
            IOUtils.closeQuietly(in);
        }
        return 1;
    }
}
