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
package jetbrick.tools.chm.style;

import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;

public class JavadocStyle {

    public Pattern getAnchorNameRegex() {
        return Pattern.compile("<A NAME=\"([^\"]*)\"[^>]*>");
    }

    public Pattern getIndexRegex() {
        return Pattern.compile("<DT><A HREF=\"([^\"]*)\"[^>]*><B>([^<]*)</B></A>");
    }

    /**
     * Matches a <i>type</i> row of the index (class, interface, enum, ...).
     *
     * <p>Only javadoc 9+ marks those rows explicitly with
     * {@code class="type-name-link"}; older generations mix types and members in
     * {@link #getIndexRegex()}, so this pattern matches nothing there and the
     * members-only path is used.
     */
    public Pattern getJavaIndexTypeRegex() {
        return Pattern.compile("(?!)");
    }

    public Pattern getJavaClassRegex() {
        return Pattern.compile("<A HREF=\"(([^\"]*)\\.html)\"[^>]*>(?:<I>)?([^<]*)(?:</I>)?</A>");
    }

    public Pattern getJavaFieldRegex() {
        return Pattern.compile("<TD><CODE><B><A HREF=\"([^\"]*)\"[^>]*>([^<]*)</A>");
    }

    public Pattern getIndexUrlRegex() {
        return Pattern.compile("<A HREF=\"([^\"]*)\"[^>]*>");
    }

    public Pattern getApiTitleRegex() {
        return Pattern.compile("<TITLE>([^<]*)</TITLE>");
    }

    public boolean isMethod(String url) {
        return url.endsWith("(");
    }

    public String getMethodFullName(String url) {
        return StringUtils.substringAfter(url, "#");
    }

    /**
     * javadoc 9+ documents may be organised as {@code module/package/Class.html}
     * and therefore expose a module level in the table of contents.
     *
     * @return {@code true} when a module level should be rendered.
     */
    public boolean hasModuleLevel() {
        return false;
    }
}
