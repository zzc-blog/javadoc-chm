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

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrick.tools.chm.Config;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Anchors in javadoc 8 and earlier are written as
 * {@code <a name="foo-java.lang.String-">} and contain characters ({@code -},
 * {@code :}, {@code [}) that the HTML Help viewer does not accept, so every
 * anchor is rewritten to a hash based {@code HH_xxxx} name and an extra
 * {@code <A NAME>} is inserted in front of the original one.
 *
 * <p>javadoc 9+ writes {@code id="charAt(int)"} instead. Those ids are valid
 * HTML anchors and need no rewriting, so the style pattern never matches and
 * {@link #getNewAnchorName(String)} only hashes the fragment - nothing on disk
 * is modified.
 */
public class AnchorNameManager {

    public static void addAnchor(File file, String encoding) throws IOException {
        String html = FileUtils.readFileToString(file, encoding);

        Pattern p = Config.style.getAnchorNameRegex();
        Matcher m = p.matcher(html);
        if (!m.find()) {
            return; // javadoc 9+ or a page without anchors: nothing to do.
        }
        m.reset();

        html = html.replace('$', '\uFFE5');

        int findCount = 0;
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            findCount++;
            String anchor = m.group(1);
            String oldAnchor = "<A HH=\"1\" NAME=\"" + anchor + "\">";
            String newAnchor = "<A HH=\"1\" NAME=\"" + hashAnchorName(anchor) + "\"></A>";
            m.appendReplacement(sb, newAnchor + oldAnchor);
        }
        m.appendTail(sb);

        System.out.println("addAnchor(" + findCount + ") : " + file);

        if (findCount > 0) {
            html = sb.toString().replace('\uFFE5', '$');
            FileUtils.writeStringToFile(file, html, encoding);
        }
    }

    public static String replaceAnchor(String url) {
        String anchor = StringUtils.substringAfter(url, "#");
        if ("".equals(anchor)) return url;
        return StringUtils.substringBefore(url, "#") + "#" + getNewAnchorName(anchor);
    }

    /**
     * Maps a javadoc anchor (old or new style) to the name used inside the CHM.
     * An empty fragment stays empty so that plain page links keep working.
     */
    public static String getNewAnchorName(String anchor) {
        if (anchor == null || anchor.length() == 0) return anchor == null ? "" : anchor;
        return hashAnchorName(anchor);
    }

    private static String hashAnchorName(String anchor) {
        return "HH_" + Math.abs(anchor.hashCode());
    }
}
