/**
 * javadoc.chm
 * http://subchen.github.io/javadoc.chm/
 * 
 * Copyright 2010-2014 Guoqiang Chen. All rights reserved.
 * Email: subchen@gmail.com
 * 
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
package jetbrick.tools.chm.reader;

import java.util.*;
import org.apache.commons.lang.StringUtils;

/**
 * Collects every occurrence of an index keyword (e.g. {@code get}) so that a
 * single "occurrences" page can be generated for it.
 *
 * <p>The generated file name only keeps an ASCII safe prefix of the keyword:
 * javadoc 9+ index keys are real signatures such as
 * {@code whenComplete(BiConsumer<? super T, ? super Throwable>)} and Windows
 * rejects {@code <}, {@code >}, {@code ?}, {@code :} and {@code |} in file
 * names. The original key is still used as the visible index keyword.
 */
public class KeyManager {
    private String key;
    private String url;
    private List<String> pairs = new ArrayList<String>();

    /** Page url of each occurrence, parallel to {@link #pairs}. */
    private List<String> urls = new ArrayList<String>();

    /** The single page every occurrence points at, or {@code null}. */
    private String commonPage;

    public KeyManager(String key) {
        this.key = key;
        this.url = "redirs/" + toSafeFileName(key) + Math.abs(key.hashCode()) + ".html";
    }

    public String getKey() {
        return key;
    }

    public void add(String name, String url) {
        String text;
        if (name.indexOf('(') >= 0) {
            text = StringUtils.replace(name, key + "(", "<b>" + escape(key) + "</b>(");
        } else {
            text = StringUtils.replace(name, "." + key, ".<b>" + escape(key) + "</b>");
        }
        text = "<a href='" + url + "'>" + text + "</a>";
        pairs.add(text);
        urls.add(stripAnchor(url));

        String page = stripAnchor(url);
        if (commonPage == null) {
            commonPage = page;
        } else if (!commonPage.equals(page)) {
            commonPage = "";
        }
    }

    /** @return the url without its fragment, so two overloads compare equal */
    private static String stripAnchor(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /**
     * True when the keyword occurs more than once on <b>different pages</b>.
     *
     * <p>Overloads of one member (e.g. the two {@code SerialClob} constructors)
     * all live on the same class page, so an "occurrences" list would only make
     * the user click twice to reach the page they wanted. Those stay a direct
     * link to the class page instead.
     */
    public boolean isRepeat() {
        return !urls.isEmpty() && commonPage != null && commonPage.length() > 0
                && urls.size() > 1;
    }

    /**
     * @return the class page all occurrences share, for the collapsed case;
     *         an empty string when the keyword really spans several pages
     */
    public String getCommonPage() {
        return commonPage == null ? "" : commonPage;
    }

    public List<String> getPairs() {
        return pairs;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Prepends the api folder so a link stored as {@code redirs/x.html} becomes
     * {@code api/redirs/x.html} when the CHM is compiled from the docs root.
     */
    public void prefixUrl(String prefix) {
        if (prefix == null || prefix.length() == 0) return;
        if (url.startsWith(prefix)) return;
        this.url = prefix + url;
    }

    public void sort() {
        Collections.sort(pairs);
    }

    /** Escapes the few characters that would break the generated HTML page. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Builds a file name that is valid on Windows and safe inside the CHM. */
    private static String toSafeFileName(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == '(' || c == ')' || c == ',' || c == ' ') {
                sb.append('_');
            }
            // every other character (generics, ?, :, |, ...) is simply dropped
            if (sb.length() >= 40) break;
        }
        if (sb.length() == 0) sb.append("key");
        return sb.toString() + "_";
    }
}
