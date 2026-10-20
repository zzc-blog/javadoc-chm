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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.apache.commons.io.FileUtils;

/**
 * Converts the javadoc 9+ "fake tables" into real HTML tables.
 *
 * <p>javadoc 8 and earlier rendered every summary as a genuine
 * {@code <table class="memberSummary">} with {@code <th class="colFirst">} and
 * {@code <td class="colLast">} cells, which every browser - including the old
 * Internet Explorer engine inside the HTML Help viewer - lays out correctly.
 *
 * <p>javadoc 9 replaced that with a flat list of divs:
 * <pre>
 * &lt;div class="summary-table three-column-summary"&gt;
 *   &lt;div class="table-header col-first"&gt;Modifier and Type&lt;/div&gt;
 *   &lt;div class="table-header col-second"&gt;Field&lt;/div&gt;
 *   &lt;div class="table-header col-last"&gt;Description&lt;/div&gt;
 *   &lt;div class="col-first even-row-color"&gt;protected Writer&lt;/div&gt;
 *   ...
 * &lt;/div&gt;
 * </pre>
 * and positions the cells with flexbox. Without flex (IE) the cells lose their
 * row grouping: headers wrap, the description lands in the wrong column and
 * every cell grows its own scrollbar.
 *
 * <p>This class rewrites those blocks into {@code <table>} rows so the layout
 * is done by the table algorithm instead of by flexbox. It runs on every HTML
 * page of the tree and is idempotent (already converted pages are skipped).
 */
public class TableNeutralizer {

    private static final String MARKER = "chm-table";

    /** Div class names that identify the cells of one row. */
    private static final String[] CELL_CLASSES = {
            "col-first", "col-second", "col-last",
            "col-constructor-name", "col-summary-item-name"
    };

    /**
     * Replaces the module graph image of {@code module-summary.html}.
     *
     * <p>javadoc 9+ renders the module graph as an inline {@code <img src="*.svg">}.
     * The Internet Explorer engine inside the HTML Help viewer cannot decode SVG,
     * so the graph shows up as a broken-image box. The picture carries no
     * information that is not already in the surrounding text, so the {@code <img>}
     * is replaced by a plain link to the file.
     */
    private String replaceSvgImages(String html) {
        // 1) The "Copy snippet" button is pure JavaScript (copySnippet()), which
        //    cannot run in the CHM. Its icon is an inline SVG that IE cannot
        //    decode either, so the whole button is removed instead of being left
        //    behind as a broken image next to the word "Copy".
        if (html.indexOf("snippet-copy") >= 0) {
            html = SNIPPET_BUTTON.matcher(html).replaceAll("");
        }

        // 2) The module graph is an <img src="module-graph.png|svg">. The picture
        //    duplicates information that is already in the surrounding text, so
        //    it becomes a plain marker.
        //
        //    The PNG variant matters for the full build: hhc.exe is a 32 bit
        //    program and dies with "runtime error R6002" once the [FILES] list
        //    grows past roughly 12.3k entries. Each module otherwise contributes
        //    a module-graph.png, and dropping those 59 pictures is what keeps
        //    the JDK 21 full build compilable.
        if (html.indexOf("module-graph.") >= 0) {
            StringBuffer gsb = new StringBuffer(html.length());
            Matcher gm = MODULE_GRAPH_IMG.matcher(html);
            while (gm.find()) {
                gm.appendReplacement(gsb, Matcher.quoteReplacement(
                        "<span class=\"chm-svg-fallback\">[module graph]</span>"));
            }
            gm.appendTail(gsb);
            html = gsb.toString();
        }

        if (html.indexOf(".svg") < 0) return html;

        // 3) Remaining inline SVG pictures become plain markers as well.
        StringBuffer sb = new StringBuffer(html.length());
        Matcher m = SVG_IMG.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            String name = src.substring(src.lastIndexOf('/') + 1);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<span class=\"chm-svg-fallback\">[" + name + "]</span>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code <img ... src="module-graph.png">} of a module page */
    private static final java.util.regex.Pattern MODULE_GRAPH_IMG =
            java.util.regex.Pattern.compile("<img[^>]*src=\"[^\"]*module-graph\\.(?:png|svg)\"[^>]*>");

    private static final java.util.regex.Pattern SVG_IMG = java.util.regex.Pattern.compile(
            "<img[^>]*src=\"([^\"]*\\.svg)\"[^>]*>");

    /** {@code <button class="copy snippet-copy" ...>...</button>} */
    private static final java.util.regex.Pattern SNIPPET_BUTTON = java.util.regex.Pattern.compile(
            "(?s)<button[^>]*class=\"[^\"]*snippet-copy[^\"]*\"[^>]*>.*?</button>\\s*");

    /**
     * @return the number of pages that were converted
     */
    public int apply(File root) throws IOException {
        List<File> pages = new ArrayList<File>();
        collect(root, pages);

        int converted = 0;
        for (File page : pages) {
            String html = FileUtils.readFileToString(page, Config.encoding);
            String result = html;

            if (result.indexOf("summary-table") >= 0) {
                result = convert(result);
            }
            result = prepareTabButtons(result);
            result = replaceSvgImages(result);

            if (!result.equals(html)) {
                FileUtils.writeStringToFile(page, result, Config.encoding);
                converted++;
            }
        }
        System.out.println("tables converted to <table>: " + converted + " page(s)");
        return converted;
    }

    /**
     * Annotates the summary tab buttons so the shim can toggle them.
     *
     * <p>Each button carries {@code onclick="show('method-summary-table',
     * 'method-summary-table-tab1', 3)"}. The selection callback needs to know
     * which class a button stands for, so the second argument is copied into a
     * {@code data-chm-tab} attribute; the shim cannot rely on
     * {@code classList} or {@code querySelector} in that engine.
     */
    private String prepareTabButtons(String html) {
        if (html.indexOf("table-tabs") < 0) return html;

        Matcher m = TAB_ONCLICK.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String whole = m.group(0);
            String selected = m.group(2);
            if (whole.indexOf("data-chm-tab") >= 0) {
                m.appendReplacement(sb, Matcher.quoteReplacement(whole));
                continue;
            }
            String patched = whole.replace("<button",
                    "<button data-chm-tab=\"" + selected + "\"");
            m.appendReplacement(sb, Matcher.quoteReplacement(patched));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code onclick="show('table', 'selected', columns)"} inside a button tag */
    private static final java.util.regex.Pattern TAB_ONCLICK = java.util.regex.Pattern.compile(
            "<button[^>]*onclick=\"show\\('([^']*)',\\s*'([^']*)',\\s*(\\d+)\\)\"[^>]*>");

    private void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (child.getName().equals("class-use") && Config.skipClassUse) continue;
                if (child.getName().equals("redirs")) continue;
                collect(child, out);
            } else if (child.getName().toLowerCase().endsWith(".html")) {
                out.add(child);
            }
        }
    }

    /**
     * Rewrites every {@code <div class="summary-table ...">...</div>} block.
     *
     * <p>The div block is not nested in a way that allows a naive regex, so the
     * block is walked with a small depth counter and its children are grouped
     * into rows by looking at the col-* class of each direct child.
     */
    private String convert(String html) {
        StringBuilder out = new StringBuilder(html.length() + html.length() / 10);
        int pos = 0;

        while (true) {
            int start = html.indexOf("<div class=\"summary-table", pos);
            if (start < 0) {
                out.append(html, pos, html.length());
                break;
            }

            int openTagEnd = html.indexOf('>', start);
            if (openTagEnd < 0) {
                out.append(html, pos, html.length());
                break;
            }

            int end = findBlockEnd(html, openTagEnd + 1);
            if (end < 0) {
                out.append(html, pos, html.length());
                break;
            }

            String openTag = html.substring(start, openTagEnd + 1);
            String body = html.substring(openTagEnd + 1, end);

            out.append(html, pos, start);
            out.append(buildTable(openTag, body));
            pos = end + "</div>".length();
        }
        return out.toString();
    }

    /** @return index of the '&lt;' that closes the block opened before {@code from} */
    private int findBlockEnd(String html, int from) {
        int depth = 1;
        int i = from;
        while (i < html.length()) {
            int nextOpen = html.indexOf("<div", i);
            int nextClose = html.indexOf("</div>", i);
            if (nextClose < 0) return -1;
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                i = nextOpen + 4;
            } else {
                depth--;
                if (depth == 0) return nextClose;
                i = nextClose + 6;
            }
        }
        return -1;
    }

    /**
     * Builds {@code <table>} rows from the div children.
     *
     * <p>The class name is chosen so the javadoc 8/11 stylesheet styles it
     * without extra rules: {@code memberSummary} for classes, and the same
     * {@code colFirst} / {@code colSecond} / {@code colLast} camel case names
     * those generations use. camelCase is kept alongside the original kebab
     * case so a jdk 21 sheet still matches the cell rules.
     *
     * <p>Cells are grouped into rows by their col-* class: a new row starts at
     * {@code col-first} (and at {@code col-second} when the first column is
     * absent). Header cells become {@code <th>}.
     */
    /**
     * Builds {@code <table>} rows from the div children.
     *
     * <p>Rows are reconstructed from the declared column count (the number of
     * {@code table-header} cells): javadoc emits the cells of a row one after
     * the other, so every {@code columns} consecutive data cells form a row.
     * This is the only reliable rule - a constructor summary has no
     * {@code col-first} cell at all (the first column is the constructor name,
     * marked {@code col-constructor-name}), so keying on "starts with
     * col-first" glued every constructor into the header row.
     */
    private String buildTable(String openTag, String body) {
        List<String> children = splitChildren(body);

        // Keep the layout classes of the original div ("two-column-summary",
        // "three-column-summary", "three-column-release-summary", ...) on the
        // table, so the IE7 style sheet can size the columns per layout.
        String extraClasses = layoutClassesOf(openTag);

        StringBuilder sb = new StringBuilder(body.length() + 256);
        sb.append("<table class=\"memberSummary ").append(MARKER);
        if (extraClasses.length() > 0) {
            sb.append(' ').append(extraClasses);
        }
        sb.append("\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n");

        int columns = 0;
        for (String child : children) {
            String cls = attr(child, "class");
            if (cls != null && cls.indexOf("table-header") >= 0) columns++;
        }
        if (columns <= 0) columns = 3;

        int cellsInRow = 0;
        boolean rowOpen = false;

        for (String child : children) {
            String cls = attr(child, "class");
            boolean isHeader = cls != null && cls.indexOf("table-header") >= 0;
            String col = columnOf(cls);

            // Header cells form the first row; afterwards a new row starts
            // whenever the current one already holds a full set of cells.
            boolean startsRow = !rowOpen || cellsInRow >= columns;

            if (startsRow) {
                if (rowOpen) {
                    sb.append("</tr>\n");
                }
                sb.append("<tr valign=\"top\">");
                rowOpen = true;
                cellsInRow = 0;
            }

            String tag = isHeader ? "th" : "td";
            sb.append('<').append(tag);
            String cellClasses = cellClassOf(cls, col);
            if (cellClasses.length() > 0) {
                sb.append(" class=\"").append(escapeAttr(cellClasses)).append('"');
            }
            if (isHeader) {
                sb.append(" scope=\"col\"");
            }
            sb.append('>');
            sb.append(innerOf(child));
            sb.append("</").append(tag).append('>');
            cellsInRow++;
        }

        if (rowOpen) sb.append("</tr>\n");
        sb.append("</table>\n");
        return sb.toString();
    }

    /**
     * Maps the javadoc 21 kebab-case cell class to the camelCase names used by
     * javadoc 8/11, keeping the original one as well.
     */
    private String cellClassOf(String cls, String col) {
        if (cls == null) return "";
        String trimmed = cls.trim();
        String mapped;
        if ("first".equals(col)) mapped = "colFirst";
        else if ("second".equals(col)) mapped = "colSecond";
        else if ("last".equals(col)) mapped = "colLast";
        else return trimmed;

        if (trimmed.indexOf(mapped) >= 0) return trimmed;
        return mapped + " " + trimmed;
    }

    /** Splits the block body into its direct div children (as raw strings). */
    private List<String> splitChildren(String body) {
        List<String> children = new ArrayList<String>();
        int i = 0;
        while (i < body.length()) {
            int start = body.indexOf("<div", i);
            if (start < 0) break;
            int tagEnd = body.indexOf('>', start);
            if (tagEnd < 0) break;

            // self closing <div ... /> or immediately closed <div ...></div>
            if (body.charAt(tagEnd - 1) == '/') {
                children.add(body.substring(start, tagEnd + 1));
                i = tagEnd + 1;
                continue;
            }

            int end = findBlockEnd(body, tagEnd + 1);
            if (end < 0) break;
            children.add(body.substring(start, end + "</div>".length()));
            i = end + "</div>".length();
        }
        return children;
    }

    /** @return the raw inner HTML of one child block */
    private String innerOf(String block) {
        int tagEnd = block.indexOf('>');
        int close = block.lastIndexOf("</div>");
        if (tagEnd < 0 || close < 0 || close <= tagEnd) return "";
        return block.substring(tagEnd + 1, close);
    }

    /** Maps {@code col-first} to "first" and so on. */
    private String columnOf(String cls) {
        if (cls == null) return null;
        for (String name : CELL_CLASSES) {
            if (cls.indexOf(name) >= 0) {
                if (name.equals("col-first")) return "first";
                if (name.equals("col-second")) return "second";
                if (name.equals("col-last")) return "last";
                if (name.equals("col-constructor-name")) return "second";
                if (name.equals("col-summary-item-name")) return "second";
            }
        }
        return null;
    }

    private String attr(String block, String name) {
        int tagEnd = block.indexOf('>');
        if (tagEnd < 0) return null;
        String head = block.substring(0, tagEnd);
        String needle = name + "=\"";
        int i = head.indexOf(needle);
        if (i < 0) return null;
        int start = i + needle.length();
        int end = head.indexOf('"', start);
        if (end < 0) return null;
        return head.substring(start, end);
    }

    /**
     * Copies the "*-column-*" layout classes of the original
     * {@code <div class="summary-table ...">} open tag onto the table.
     */
    private String layoutClassesOf(String openTag) {
        String cls = attr(openTag, "class");
        if (cls == null) return "";
        StringBuilder out = new StringBuilder();
        for (String token : cls.split("\\s+")) {
            if (token.indexOf("column") >= 0 || token.endsWith("-summary")) {
                if (out.length() > 0) out.append(' ');
                out.append(token);
            }
        }
        return out.toString();
    }

    private String escapeAttr(String value) {
        return value.replace("\"", "&quot;");
    }
}

