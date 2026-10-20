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
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

/**
 * Flattens the javadoc 9+ page skeleton into the plain document flow that
 * javadoc 8/11 used and that the HTML Help viewer renders correctly.
 *
 * <p>javadoc 9+ wraps the whole page in
 * <pre>
 * &lt;body&gt;
 *   &lt;div class="flex-box"&gt;
 *     &lt;header class="flex-header"&gt; ... nav bars ... &lt;/header&gt;
 *     &lt;div class="flex-content"&gt; ... the real page ... &lt;/div&gt;
 *   &lt;/div&gt;
 * &lt;/body&gt;
 * </pre>
 * and lets flexbox give the content pane its scroll range. The Internet
 * Explorer engine inside the HTML Help viewer has no flexbox, the pane ends up
 * with no height and the page shows either no scrollbar at all or a dead one.
 *
 * <p>Removing the three wrapper elements turns the page back into the
 * javadoc 8/11 shape - header, then content, one after the other in normal
 * document flow - which the viewer scrolls exactly like any other document.
 * No CSS trick is involved, so nothing can break it again.
 */
public class LayoutNeutralizer {

    /**
     * {@code <div class="flex-box">} up to its matching closing tag.
     *
     * <p>Only the opening tag is matched here; the closing tag is located with a
     * depth counter because the box contains hundreds of nested divs.
     */
    private static final String FLEX_BOX_OPEN = "<div class=\"flex-box\">";

    /** {@code <header ... class="flex-header" ...>...</header>} */
    private static final Pattern FLEX_HEADER = Pattern.compile(
            "(?s)<header([^>]*)class=\"flex-header\"([^>]*)>(.*?)</header>");
    /**
     * @return the number of pages rewritten
     */
    public int apply(File root) throws IOException {
        List<File> pages = new ArrayList<File>();
        collect(root, pages);

        int changed = 0;
        for (File page : pages) {
            String html = FileUtils.readFileToString(page, Config.encoding);
            if (html.indexOf("flex-box") < 0) continue;

            String result = flatten(html);
            if (!result.equals(html)) {
                FileUtils.writeStringToFile(page, result, Config.encoding);
                changed++;
            }
        }
        System.out.println("layout flattened: " + changed + " page(s)");
        return changed;
    }

    /**
     * Kept for reference: the tab bar is left in place on purpose.
     *
     * <p>It was removed at one point because its buttons only call
     * {@code show()}/{@code switchTab()}, which cannot work without JavaScript.
     * The bar is still the visual marker of the javadoc layout users expect, so
     * it stays and the stylesheet renders it as the javadoc 11 coloured tabs.
     */
    @SuppressWarnings("unused")
    private String removeTableTabs(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int pos = 0;

        while (true) {
            int start = html.indexOf("<div class=\"table-tabs\"", pos);
            if (start < 0) {
                out.append(html, pos, html.length());
                break;
            }
            int openEnd = html.indexOf('>', start);
            if (openEnd < 0) {
                out.append(html, pos, html.length());
                break;
            }
            int end = findMatchingDivClose(html, openEnd + 1);
            if (end < 0) {
                out.append(html, pos, html.length());
                break;
            }
            out.append(html, pos, start);
            pos = end + "</div>".length();
        }
        return out.toString();
    }

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

    private String flatten(String html) {
        int start = html.indexOf(FLEX_BOX_OPEN);
        if (start < 0) return html;

        int afterOpen = start + FLEX_BOX_OPEN.length();
        int close = findMatchingDivClose(html, afterOpen);
        if (close < 0) return html;

        String inner = html.substring(afterOpen, close);

        // 2) drop the header / content wrappers inside the box
        inner = unwrapHeader(inner);
        inner = unwrapContent(inner);

        // 3) the box itself disappears: its children become body children
        return html.substring(0, start)
                + "\n" + inner + "\n"
                + html.substring(close + "</div>".length());
    }

    /**
     * @return index of the '&lt;' of the {@code </div>} that closes the element
     *         opened just before {@code from}
     */
    private int findMatchingDivClose(String html, int from) {
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
     * Replaces {@code <header class="flex-header">...</header>} with
     * {@code <header class="fixedNav">}.
     *
     * <p>This mirrors the javadoc 8/11 markup, which the HTML Help viewer renders
     * correctly: the header is a band fixed to the top of the viewport
     * ({@code position:fixed}) and the document below it scrolls normally.
     * javadoc 9+ instead made the <i>whole page</i> a fixed flex container, and
     * the old engine cannot distribute the height inside it.
     */
    private String unwrapHeader(String html) {
        Matcher m = FLEX_HEADER.matcher(html);
        if (!m.find()) return html;
        String inner = m.group(3);
        return html.substring(0, m.start())
                + "<header role=\"banner\" class=\"fixedNav\">" + inner + "</header>"
                + html.substring(m.end());
    }

    /**
     * Replaces {@code <div class="flex-content">...</div>} by
     * {@code <div class="chm-content">}. The closing tag is found with a depth
     * counter, a greedy regex would swallow the whole page.
     */
    private String unwrapContent(String html) {
        String openTag = "<div class=\"flex-content\">";
        int start = html.indexOf(openTag);
        if (start < 0) return html;

        int afterOpen = start + openTag.length();
        int close = findMatchingDivClose(html, afterOpen);
        if (close < 0) return html;

        String inner = html.substring(afterOpen, close);
        // No navPadding spacer here: the header is in normal flow in this layout,
        // so reserving its height again only produced a blank gap between the
        // navigation bar and the page body.
        return html.substring(0, start)
                + "<div class=\"chm-content\">" + inner + "</div>"
                + html.substring(close + "</div>".length());
    }
}
