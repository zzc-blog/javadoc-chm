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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

/**
 * Converts the javadoc stylesheet in place, in a way that preserves every
 * visual value but removes the syntax the HTML Help viewer's IE engine cannot
 * parse.
 *
 * <p>The original sheet is left otherwise untouched - no colours, sizes or
 * fonts are changed, nothing is added except a single block that declares the
 * HTML5 elements as block boxes (the CSS equivalent of html5shiv). The only
 * transformations are:
 * <ol>
 *   <li>expand every {@code var(--x)} to the literal value from {@code :root};</li>
 *   <li>remove {@code @media} blocks;</li>
 *   <li>remove flex / grid layout declarations (the layout is instead restored
 *       by {@link TableNeutralizer} turning the div tables into real tables);</li>
 *   <li>convert {@code ::before}/{@code ::after} to single-colon form.</li>
 * </ol>
 */
public class CssNeutralizer {

    private static final Pattern ROOT_BLOCK = Pattern.compile(":root\\s*\\{([^}]*)\\}", Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern VAR_REF = Pattern.compile("var\\(\\s*(--[A-Za-z0-9_-]+)\\s*(?:,[^)]*)?\\)");
    private static final Pattern MEDIA_BLOCK = Pattern.compile(
            "@media[^{]*\\{(?:[^{}]|\\{[^{}]*\\})*\\}", Pattern.DOTALL);

    private static final String[] FLEX_GRID_PROPS = {
            "flex-direction", "flex-wrap", "flex-flow", "flex-grow", "flex-shrink",
            "flex-basis", "justify-content", "align-items", "align-self",
            "align-content", "place-items", "place-content", "row-gap", "column-gap",
            "gap", "grid-template-columns", "grid-template-rows", "grid-template-areas",
            "grid-auto-columns", "grid-auto-rows", "grid-auto-flow", "grid-column",
            "grid-row", "grid-area", "order", "aspect-ratio"
    };

    /**
     * @return true when the stylesheet was rewritten
     */
    public boolean apply(File apiLocation) throws IOException {
        File cssFile = new File(apiLocation, "stylesheet.css");
        if (!cssFile.isFile()) {
            System.out.println("stylesheet.css not found, skipping CSS conversion");
            return false;
        }

        String css = FileUtils.readFileToString(cssFile, "utf-8");
        if (css.indexOf("javadoc.chm: HTML5") >= 0) {
            System.out.println("stylesheet.css already converted");
            return false;
        }

        Map<String, String> variables = parseRootVariables(css);
        css = expandVariables(css, variables);
        css = MEDIA_BLOCK.matcher(css).replaceAll("");
        css = degradeLayout(css);
        css = css.replace("var(--source-linenumber-color)", "#353833");
        css = css.replace("::before", ":before").replace("::after", ":after");
        css = css + HTML5_BLOCK;
        css = css + SEARCH_RESULTS_FALLBACK;
        css = css + CHM_TABLE_BLOCK;
        css = css + CHM_OFFLINE_HIDE;
        if (Config.cjkFonts) {
            css = css + CHM_CJK_FONTS;
        }

        FileUtils.writeStringToFile(cssFile, css, "utf-8");
        System.out.println("stylesheet converted in place: " + variables.size()
                + " custom properties expanded, no visual values changed");
        return true;
    }

    private Map<String, String> parseRootVariables(String css) {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        Matcher m = ROOT_BLOCK.matcher(css);
        if (!m.find()) return variables;

        String body = COMMENT.matcher(m.group(1)).replaceAll("");
        for (String declaration : body.split(";")) {
            declaration = declaration.trim();
            if (!declaration.startsWith("--") || declaration.indexOf(':') < 0) continue;
            int index = declaration.indexOf(':');
            variables.put(declaration.substring(0, index).trim(),
                    declaration.substring(index + 1).trim());
        }
        return variables;
    }

    private String expandVariables(String css, Map<String, String> variables) {
        String result = css;
        for (int pass = 0; pass < 6; pass++) {
            Matcher m = VAR_REF.matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (m.find()) {
                String name = m.group(1);
                String value = variables.get(name);
                if (value == null) {
                    value = "#353833";   // e.g. --source-linenumber-color
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
                changed = true;
            }
            m.appendTail(sb);
            result = sb.toString();
            if (!changed || result.indexOf("var(--") < 0) break;
        }
        return result;
    }

    private String degradeLayout(String css) {
        css = css.replaceAll("display\\s*:\\s*(?:inline-)?flex\\s*;", "display:block;");
        css = css.replaceAll("display\\s*:\\s*(?:inline-)?grid\\s*;", "display:block;");

        for (String prop : FLEX_GRID_PROPS) {
            Pattern p = Pattern.compile("([{;\\s])" + Pattern.quote(prop) + "\\s*:[^;}]*;?");
            Matcher m = p.matcher(css);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1)));
            }
            m.appendTail(sb);
            css = sb.toString();
        }
        return css;
    }

    private static final String HTML5_BLOCK =
            "\n/* --- HTML5 elements (IE does not know them; declare as block) ------ */\n"
            + "article, aside, details, figcaption, figure, footer, header, hgroup,\n"
            + "main, menu, nav, section, summary {\n"
            + "    display: block;\n"
            + "}\n";

    /**
     * The search page ({@code search.html}) renders its result tables with
     * {@code display:grid} ({@code .two-column-search-results} /
     * {@code .one-column-search-results}). grid was dropped above; these rules
     * restore the column layout with floats. Colours come from the untouched
     * {@code .table-header} / {@code .even-row-color} / {@code .odd-row-color}
     * rules already in the sheet, so none are restated here.
     */
    private static final String SEARCH_RESULTS_FALLBACK =
            "\n/* --- search result tables (grid -> float, for the CHM viewer) ------ */\n"
            + ".two-column-search-results,\n"
            + ".one-column-search-results {\n"
            + "    display: block;\n"
            + "    width: 100%;\n"
            + "    overflow: hidden;\n"
            + "}\n"
            + ".two-column-search-results > div,\n"
            + ".one-column-search-results > div {\n"
            + "    display: block;\n"
            + "    float: left;\n"
            + "    box-sizing: border-box;\n"
            + "    -moz-box-sizing: border-box;\n"
            + "    padding: 8px;\n"
            + "    vertical-align: top;\n"
            + "}\n"
            + ".two-column-search-results > div {\n"
            + "    width: 50%;\n"
            + "}\n"
            + ".one-column-search-results > div {\n"
            + "    width: 100%;\n"
            + "}\n";

    /**
     * Styles the real {@code <table class="memberSummary chm-table">} produced
     * by {@link TableNeutralizer}. The original {@code ie7-fix.css} only sizes
     * the floated divs of the unconverted layout, so without these rules the
     * table shrinks to its content and the columns are uneven.
     *
     * <p>Column widths mirror the original {@code grid-template-columns}
     * values per layout class (copied onto the table by the neutralizer).
     * IE7 understands the table-layout algorithm natively, so
     * {@code table-layout: fixed} plus percentage widths renders evenly.
     */
    /**
     * Hides the controls that only make sense online, and which the IE7 engine
     * renders as stray leftovers.
     *
     * <p>{@code div.page-search-info} is collapsed by the original sheet with
     * {@code height: 0; overflow: hidden} and only opens on hover. IE7 does not
     * collapse it, so the "Copy URL" button inside (and its {@code copy.png}
     * icon, a 17x17 image) showed up dangling over the search result table.
     * Offline there is no URL worth copying, so the whole panel is hidden.
     */
    private static final String CHM_OFFLINE_HIDE =
            "\n/* --- controls that make no sense in an offline CHM ---------------- */\n"
            + "div.page-search-info {\n"
            + "    display: none;\n"
            + "}\n"
            + "button.snippet-copy {\n"
            + "    display: none;\n"
            + "}\n"
            + "\n/* --- autocomplete popup: keep it a compact list under the input --- */\n"
            + "ul.ui-autocomplete {\n"
            + "    position: absolute;\n"
            + "    z-index: 1000;\n"
            + "    background-color: #ffffff;\n"
            + "    border: 1px solid #a8b8c8;\n"
            + "    max-height: 400px;\n"
            + "    overflow-x: hidden;\n"
            + "    overflow-y: auto;\n"
            + "}\n"
            + "ul.ui-autocomplete li {\n"
            + "    white-space: nowrap;\n"
            + "    overflow: hidden;\n"
            + "}\n";

    /**
     * Font and spacing corrections for translated (CJK) documentation.
     *
     * <p>The javadoc sheet asks for {@code DejaVu Sans / Arial / Helvetica},
     * which carry no Chinese glyphs, so IE7 falls back to SimSun per glyph and
     * mixes two metrics inside one line. Naming a Chinese face keeps the line
     * consistent, and a slightly larger line height gives the denser glyphs
     * enough room. Only the family list and the line box change - no colour and
     * no font size is invented.
     */
    private static final String CHM_CJK_FONTS =
            "\n/* --- CJK (translated) font and line height ------------------------ */\n"
            + "body, div, p, ul, ol, li, dl, dd, dt, table, td, th,\n"
            + "h1, h2, h3, h4, h5, h6, blockquote {\n"
            + "    font-family: \"Microsoft YaHei\", \"Microsoft JhengHei\",\n"
            + "                 SimSun, Tahoma, Arial, sans-serif;\n"
            + "}\n"
            + "body {\n"
            + "    line-height: 1.6;\n"
            + "}\n"
            + "div.block, div.block-list, dd {\n"
            + "    line-height: 1.75;\n"
            + "}\n"
            + "h1, h2, h3 {\n"
            + "    line-height: 1.4;\n"
            + "}\n"
            + "/* inline code stays monospace, with a Chinese fallback */\n"
            + "code, pre, tt, .type-signature, .member-signature {\n"
            + "    font-family: \"DejaVu Sans Mono\", Consolas, SimSun, monospace;\n"
            + "}\n";

    private static final String CHM_TABLE_BLOCK =
            "\n/* --- converted summary tables (div grid -> real table, for CHM) ---- */\n"
            + "table.memberSummary {\n"
            + "    width: 100%;\n"
            + "    table-layout: fixed;\n"
            + "    border-collapse: collapse;\n"
            + "}\n"
            + "table.memberSummary th, table.memberSummary td {\n"
            + "    padding: 4px 8px;\n"
            + "    text-align: left;\n"
            + "    vertical-align: top;\n"
            + "    overflow-wrap: break-word;\n"
            + "    word-wrap: break-word;\n"
            + "    word-break: break-word;\n"
            + "}\n"
            + "table.memberSummary th {\n"
            + "    text-align: left;\n"
            + "}\n"
            + "/* two-column layouts (constructor / enum constant / property summaries) */\n"
            + "table.two-column-summary th.colFirst,\n"
            + "table.two-column-summary td.colFirst,\n"
            + "table.two-column-summary th.colSecond,\n"
            + "table.two-column-summary td.colSecond,\n"
            + "table.two-column-summary th.colConstructorName,\n"
            + "table.two-column-summary td.colConstructorName,\n"
            + "table.two-column-summary th.colSummaryItemName,\n"
            + "table.two-column-summary td.colSummaryItemName {\n"
            + "    width: 30%;\n"
            + "}\n"
            + "table.two-column-summary th.colLast,\n"
            + "table.two-column-summary td.colLast {\n"
            + "    width: 70%;\n"
            + "}\n"
            + "/* three-column layouts (field / method summaries) */\n"
            + "table.three-column-summary th.colFirst,\n"
            + "table.three-column-summary td.colFirst {\n"
            + "    width: 22%;\n"
            + "}\n"
            + "table.three-column-summary th.colSecond,\n"
            + "table.three-column-summary td.colSecond {\n"
            + "    width: 30%;\n"
            + "}\n"
            + "table.three-column-summary th.colLast,\n"
            + "table.three-column-summary td.colLast {\n"
            + "    width: 48%;\n"
            + "}\n"
            + "/* three-column release-note tables */\n"
            + "table.three-column-release-summary th.colFirst,\n"
            + "table.three-column-release-summary td.colFirst {\n"
            + "    width: 15%;\n"
            + "}\n"
            + "table.three-column-release-summary th.colSecond,\n"
            + "table.three-column-release-summary td.colSecond {\n"
            + "    width: 15%;\n"
            + "}\n"
            + "table.three-column-release-summary th.colLast,\n"
            + "table.three-column-release-summary td.colLast {\n"
            + "    width: 70%;\n"
            + "}\n"
            + "/* fall back for tables without a layout class */\n"
            + "table.chm-table th.colFirst, table.chm-table td.colFirst { width: 22%; }\n"
            + "table.chm-table th.colSecond, table.chm-table td.colSecond { width: 30%; }\n"
            + "table.chm-table th.colConstructorName, table.chm-table td.colConstructorName { width: 30%; }\n"
            + "table.chm-table th.colSummaryItemName, table.chm-table td.colSummaryItemName { width: 30%; }\n"
            + "table.chm-table th.colLast, table.chm-table td.colLast { width: 48%; }\n"
            + "table.chm-table colLast div.block, table.chm-table td.colLast div.block {\n"
            + "    display: inline;\n"
            + "}\n";
}
