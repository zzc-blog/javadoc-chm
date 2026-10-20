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
package jetbrick.tools.chm;

import java.io.File;
import java.nio.charset.Charset;
import jetbrick.tools.chm.style.JavadocStyle;

public class Config {

    /** The directory that holds {@code index.html}, {@code element-list}, ... */
    public static File apiLocation = new File(".");

    /**
     * The root the CHM is compiled from; every path in {@code htmlhelp.hhp} is
     * relative to it.
     *
     * <p>Usually this is the parent of {@link #apiLocation}, because the api pages
     * link to sibling directories ({@code ../../legal/copyright.html},
     * {@code ../../resources/...}). Defaults to {@link #apiLocation} when the
     * caller keeps everything self contained.
     */
    public static File chmRoot = null;

    /**
     * hhc.exe is a 32 bit program limited to 2 GB of address space. It dies with
     * no message once the project grows past roughly 18k pages / 350 MB, so the
     * "class use" pages (who references a type, ~22% of the tree) are optional.
     * Set {@code -Djavadoc.chm.skipClassUse=false} to keep them.
     */
    public static boolean skipClassUse = Boolean.parseBoolean(
            System.getProperty("javadoc.chm.skipClassUse", "true"));

    /**
     * Full text search needs a lot of memory at compile time. Set
     * {@code -Djavadoc.chm.fullTextSearch=false} to trade it for a much faster,
     * much lighter build.
     */
    public static boolean fullTextSearch = Boolean.parseBoolean(
            System.getProperty("javadoc.chm.fullTextSearch", "true"));

    /**
     * {@code Binary Index=Yes} asks hhc to pre-build a binary keyword tree. With
     * ~67k keywords on a 32 bit process this is what pushes it over the 2 GB
     * limit and makes it hang after writing a truncated .chm. Turning it off
     * keeps every keyword, only the lookup is done on the fly.
     */
    public static boolean binaryIndex = Boolean.parseBoolean(
            System.getProperty("javadoc.chm.binaryIndex", "false"));

    /** Same trade-off for the table of contents. */
    public static boolean binaryToc = Boolean.parseBoolean(
            System.getProperty("javadoc.chm.binaryToc", "false"));

    public static String encoding = Charset.defaultCharset().name();

    /**
     * Window title and compiled file name of the CHM.
     * Override with {@code -Djavadoc.chm.title="jdk21 API-en"}. When empty the
     * javadoc &lt;title&gt; of index.html is used.
     */
    public static String title = System.getProperty("javadoc.chm.title", "");

    /**
     * Adds a CJK font stack and looser line height to the stylesheet.
     *
     * <p>The javadoc sheet only names {@code DejaVu Sans / Arial / Helvetica},
     * none of which carry Chinese glyphs. A translated document therefore falls
     * back to whatever the system picks (SimSun) and looks cramped, because the
     * Latin metrics are tighter than Chinese text needs. Turn this on for
     * translated docs with {@code -Djavadoc.chm.cjk=true}.
     */
    public static boolean cjkFonts = Boolean.parseBoolean(
            System.getProperty("javadoc.chm.cjk", "false"));

    /**
     * When greater than zero, only that many representative pages are processed
     * and the result is written to {@link #sampleDir}.
     *
     * <p>The pages are <b>copied</b> first: the neutralizers rewrite HTML, so
     * running them on a real documentation tree would leave it modified.
     * {@code -Djavadoc.chm.sample=20} gives a quick preview build.
     */
    public static int sampleLimit = Integer.parseInt(
            System.getProperty("javadoc.chm.sample", "0"));

    /** Where the sample copy is written. */
    public static File sampleDir = new File(
            System.getProperty("javadoc.chm.sampleDir", "build/sample"));

    public static JavadocStyle style = new JavadocStyle();

    /** @return the directory the CHM project files are written to and built from */
    public static File getChmRoot() {
        return chmRoot == null ? apiLocation : chmRoot;
    }

    /** @return the location of the api pages relative to the CHM root, using '/' */
    public static String getApiPrefix() {
        File root = getChmRoot();
        if (root.equals(apiLocation)) return "";

        String rootPath = root.getAbsolutePath().replace('\\', '/');
        String apiPath = apiLocation.getAbsolutePath().replace('\\', '/');
        if (!apiPath.startsWith(rootPath)) return "";

        String prefix = apiPath.substring(rootPath.length());
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (prefix.length() > 0 && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix;
    }
}
