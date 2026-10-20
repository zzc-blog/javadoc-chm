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
package jetbrick.tools.chm.style;

import java.util.regex.Pattern;

/**
 * Style for the javadoc output produced by javadoc 9 and later
 * (9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, ...).
 *
 * <p>Compared with {@link Javadoc8Style} this generation of javadoc:
 * <ul>
 *   <li>uses {@code id="..."} instead of {@code <a name="...">}, and the id of
 *       a member is its real signature, e.g. {@code id="charAt(int)"};</li>
 *   <li>links type names with {@code class="type-name-link"} /
 *       {@code class="member-name-link"};</li>
 *   <li>replaces {@code package-list} / {@code allclasses-frame.html} with
 *       {@code element-list} / {@code allclasses-index.html};</li>
 *   <li>and, when modules are documented, prefixes every page with the module
 *       directory, e.g. {@code java.base/java/lang/String.html}.</li>
 * </ul>
 *
 * @author Guoqiang Chen
 */
public class Javadoc9Style extends JavadocStyle {

    /**
     * Members are no longer wrapped in {@code <a name="...">} by javadoc 9+,
     * so there is nothing to rewrite. The entity is deliberately impossible to
     * match: {@link jetbrick.tools.chm.reader.AnchorNameManager} then becomes a
     * no-op instead of corrupting the page.
     */
    @Override
    public Pattern getAnchorNameRegex() {
        return Pattern.compile("(?!)");
    }

    /**
     * Matches one type row of {@code index-files/index-N.html}:
     * <pre>
     * &lt;dt&gt;&lt;a href="../java.base/java/lang/Class.html" class="type-name-link"
     *        title="class in java.lang"&gt;Class&lt;/a&gt; - Class in &lt;a ...&gt;java.lang&lt;/a&gt;&lt;/dt&gt;
     * </pre>
     * Group 1 = url, group 2 = simple type name, group 3 = "Class in "
     */
    public Pattern getJavaIndexTypeRegex() {
        return Pattern.compile(
                "<dt><a href=\"([^\"]*)\" class=\"type-name-link\"[^>]*>([^<]*)</a>\\s*-\\s*([^<\\r\\n]*)");
    }

    /**
     * Matches one row of {@code index-files/index-N.html}:
     * <pre>
     * &lt;dt&gt;&lt;a href="../java.base/java/lang/String.html#charAt(int)"
     *        class="member-name-link"&gt;charAt&lt;/a&gt; - Method in class java.lang.&lt;a ...&gt;String&lt;/a&gt;&lt;/dt&gt;
     * </pre>
     * Group 1 = url, group 2 = searchable text, group 3 = "Method in class java.lang."
     */
    @Override
    public Pattern getIndexRegex() {
        return Pattern.compile(
                "<dt><a href=\"([^\"]*)\" class=\"(?:member-name-link|search-tag-link)\">([^<]*)</a>\\s*-\\s*([^<\\r\\n]*)");
    }

    /**
     * Matches one row of {@code allclasses-index.html}:
     * <pre>
     * &lt;div class="col-first even-row-color all-classes-table all-classes-table-tab2"&gt;
     *   &lt;a href="java.base/java/lang/String.html" title="class in java.lang"&gt;String&lt;/a&gt;&lt;/div&gt;
     * </pre>
     * Group 1 = url, group 2 = "class in java.lang", group 3 = simple name
     * (possibly containing {@code <wbr>} or {@code &lt;R&gt;}).
     */
    @Override
    public Pattern getJavaClassRegex() {
        return Pattern.compile("(?s)col-first[^>]*><a href=\"([^\"]*)\" title=\"([^\"]*)\">(.*?)</a></div>");
    }

    /**
     * Matches the member summary rows of a class page:
     * <pre>
     * &lt;div class="col-second ..."&gt;&lt;code&gt;&lt;a href="#charAt(int)"
     *   class="member-name-link"&gt;charAt&lt;/a&gt;&lt;wbr&gt;(int&amp;nbsp;index)&lt;/code&gt;&lt;/div&gt;
     * </pre>
     * Group 1 = url (an in-page anchor such as {@code #charAt(int)}),
     * group 2 = member name.
     */
    @Override
    public Pattern getJavaFieldRegex() {
        return Pattern.compile("<a href=\"([^\"]*)\" class=\"member-name-link\">([^<]*)</a>");
    }

    /** Matches {@code <a href="../index.html">Overview</a>} in the navbar. */
    @Override
    public Pattern getIndexUrlRegex() {
        return Pattern.compile("<a href=\"([^\"]*)\"[^>]*>Overview</a>");
    }

    @Override
    public Pattern getApiTitleRegex() {
        return Pattern.compile("<title>([^<]*)</title>");
    }

    /**
     * javadoc 9+ anchors always carry a real signature, so a fragment that
     * contains {@code '('} belongs to a method or constructor; everything else
     * (including constants such as {@code #MAX_VALUE}) is a field.
     */
    @Override
    public boolean isMethod(String url) {
        return url.indexOf('(') >= 0;
    }

    /**
     * Turns {@code #charAt(int)} into {@code charAt(int)},
     * {@code #foo(java.lang.String...)} into {@code foo(java.lang.String...)},
     * and leaves the fragment untouched when it is not a signature.
     */
    @Override
    public String getMethodFullName(String url) {
        int index = url.indexOf('#');
        String name = index < 0 ? url : url.substring(index + 1);
        return decode(name);
    }

    /** javadoc 9+ documents the module level whenever {@code element-list} exists. */
    @Override
    public boolean hasModuleLevel() {
        return true;
    }

    /** Entity-decodes the few characters javadoc escapes inside an id. */
    public static String decode(String text) {
        if (text == null) return null;
        if (text.indexOf('&') < 0) return text;
        String result = text;
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&amp;", "&");
        result = result.replace("&quot;", "\"");
        result = result.replace("&#39;", "'");
        result = result.replace("&nbsp;", " ");
        return result;
    }

    /** Removes inline markup such as {@code <wbr>} or {@code <span>} from a snippet. */
    public static String stripTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "");
    }

    /**
     * Strips the formal type parameters from a displayed type name so that the
     * CHM index keyword is the plain name users actually type:
     * {@code Class<T>} -> {@code Class}, {@code Map.Entry<K,V>} -> {@code Map.Entry}.
     *
     * <p>Before this the JDK 21 index offered the keyword {@code Class<T>} only,
     * which meant searching for "Class" found nothing at all.
     */
    public static String stripTypeParameters(String name) {
        if (name == null) return null;
        int index = name.indexOf('<');
        if (index < 0) return name.trim();
        return name.substring(0, index).trim();
    }
}
