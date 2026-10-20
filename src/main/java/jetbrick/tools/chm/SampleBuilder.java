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
import org.apache.commons.io.FileUtils;

/**
 * Creates a small, representative copy of a javadoc tree.
 *
 * <p>Previewing the whole JDK API costs a 15 minute compile and 60 MB of output
 * for every iteration. With {@code -Djavadoc.chm.sample=N} this class copies
 * just N pages that together exercise every layout the converter touches:
 * module index, package summary, class page with fields/constructors/methods,
 * an interface, an enum, an exception, and the shared assets.
 *
 * <p>The source tree is never modified: the copy is what the neutralizers run on.
 */
public class SampleBuilder {

    /** Pages chosen to cover every kind of layout javadoc 21 emits. */
    private static final String[] SAMPLE_PAGES = {
            "java.base/module-summary.html",                 // module page
            "java.base/java/io/package-summary.html",        // package with many types
            "java.base/java/lang/String.html",               // huge class, all sections
            "java.base/java/lang/Class.html",                // generics in the title
            "java.base/java/lang/Object.html",               // plain class
            "java.base/java/lang/Integer.html",              // class with many static members
            "java.base/java/util/List.html",                 // interface
            "java.base/java/util/Map.html",                  // nested types
            "java.base/java/util/Map.Entry.html",            // nested class page
            "java.base/java/util/stream/Stream.html",        // interface with many methods
            "java.base/java/time/DayOfWeek.html",            // enum
            "java.base/java/lang/annotation/Retention.html",
            "java.base/java/nio/file/Files.html",            // many static overloads
            "java.base/java/lang/Throwable.html",            // exception class
            "java.base/java/lang/constant/Constable.html",   // interface with type parameters
            "java.base/java/util/function/Function.html",    // generic interface
            "java.base/java/util/concurrent/CompletableFuture.html",
            "java.base/java/lang/reflect/Method.html",
            "java.base/java/io/IOException.html",            // exception
            "java.base/java/util/Optional.html"              // generic final class
    };

    private static final String[] SHARED_FILES = {
            "index.html",
            "overview-summary.html",
            "allclasses-index.html",
            "element-list",
            "stylesheet.css",
            "script.js",
            "help-doc.html",
            "search.html",
            "search.js",
            "search-page.js",
            "module-search-index.js",
            "package-search-index.js",
            "type-search-index.js",
            "member-search-index.js",
            "tag-search-index.js"
    };

    private static final String[] SHARED_DIRS = {
            "script-dir",
            "resources",
            "legal",
            "index-files"
    };

    /**
     * Copies a representative subset of {@code source} into {@code target}.
     *
     * @return the number of html pages copied
     */
    public int build(File source, File target, int limit) throws IOException {
        if (target.exists()) {
            FileUtils.deleteDirectory(target);
        }
        target.mkdirs();

        int copied = 0;
        List<String> pages = new ArrayList<String>();
        for (String page : SAMPLE_PAGES) {
            if (copied >= limit) break;
            if (copyFile(source, target, page)) {
                pages.add(page);
                copied++;
            }
        }

        // Every package that owns a copied page also needs its summary, the
        // table of contents links to it.
        java.util.LinkedHashSet<String> packageSummaries = new java.util.LinkedHashSet<String>();
        for (String page : pages) {
            int firstSlash = page.indexOf('/');
            int lastSlash = page.lastIndexOf('/');
            if (firstSlash > 0 && lastSlash > firstSlash) {
                packageSummaries.add(page.substring(0, lastSlash) + "/package-summary.html");
            }
        }
        for (String summaryPage : packageSummaries) {
            if (pages.contains(summaryPage)) continue;
            if (copyFile(source, target, summaryPage)) {
                pages.add(summaryPage);
            }
        }

        // Every module whose summary page was copied also needs its module
        // graph picture, the page links to it next to "Module Graph:".
        java.util.LinkedHashSet<String> modules = new java.util.LinkedHashSet<String>();
        for (String page : pages) {
            int firstSlash = page.indexOf('/');
            if (firstSlash > 0) {
                modules.add(page.substring(0, firstSlash));
            }
        }
        for (String module : modules) {
            copyFile(source, target, module + "/module-graph.png");
            copyFile(source, target, module + "/module-graph.svg");
        }

        for (String name : SHARED_FILES) {
            copyFile(source, target, name);
        }
        for (String dir : SHARED_DIRS) {
            copyDir(source, target, dir);
        }

        // element-list and allclasses-index describe the whole JDK; trim them to
        // the pages that were actually copied, otherwise the table of contents
        // and the index point at thousands of missing files.
        trimElementList(new File(target, "element-list"), pages);
        trimAllClasses(new File(target, "allclasses-index.html"), pages);
        trimIndexFiles(new File(target, "index-files"), pages);

        System.out.println("sample built: " + copied + " page(s) into " + target);
        return copied;
    }

    /**
     * Filters the per-letter index pages the same way.
     *
     * <p>Every entry is a {@code <dt><a href="...">} line that already carries
     * its target url, so the line is kept only when that url belongs to one of
     * the copied pages.
     */
    private void trimIndexFiles(File dir, List<String> pages) throws IOException {
        if (!dir.isDirectory()) return;

        java.util.Set<String> keep = new java.util.HashSet<String>();
        for (String page : pages) {
            keep.add("../" + page);
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".html")) continue;

            List<String> out = new ArrayList<String>();
            for (String line : FileUtils.readLines(file, "utf-8")) {
                if (line.startsWith("<dt><a href=\"")) {
                    String href = extractHref(line);
                    if (href != null) {
                        String target = href;
                        int hash = target.indexOf('#');
                        if (hash >= 0) target = target.substring(0, hash);
                        if (target.endsWith(".html") && !keep.contains(target)) {
                            continue;       // entry for a page that was not copied
                        }
                    }
                }
                out.add(line);
            }
            FileUtils.writeLines(file, "utf-8", out);
        }
    }

    /** Keeps only the packages that the copied pages belong to. */
    private void trimElementList(File elementList, List<String> pages) throws IOException {
        if (!elementList.isFile()) return;

        java.util.Set<String> keepPackages = new java.util.HashSet<String>();
        java.util.Set<String> keepModules = new java.util.HashSet<String>();
        for (String page : pages) {
            int firstSlash = page.indexOf('/');
            if (firstSlash < 0) continue;
            keepModules.add(page.substring(0, firstSlash));
            int lastSlash = page.lastIndexOf('/');
            if (lastSlash > firstSlash) {
                keepPackages.add(page.substring(firstSlash + 1, lastSlash).replace('/', '.'));
            }
        }

        List<String> out = new ArrayList<String>();
        String module = null;
        for (String line : FileUtils.readLines(elementList, "utf-8")) {
            String trimmed = line.trim();
            if (trimmed.length() == 0) continue;
            if (trimmed.startsWith("module:")) {
                module = trimmed.substring("module:".length()).trim();
                out.add("module:" + module);
            } else {
                boolean wanted = module != null && keepModules.contains(module)
                        && keepPackages.contains(trimmed);
                if (wanted) {
                    out.add(trimmed);
                }
            }
        }
        FileUtils.writeLines(elementList, "utf-8", out);
    }

    /**
     * Rewrites {@code allclasses-index.html} so it only lists the copied pages.
     * Filtering is line based: every row of that table is on its own line.
     */
    private void trimAllClasses(File index, List<String> pages) throws IOException {
        if (!index.isFile()) return;

        java.util.Set<String> keep = new java.util.HashSet<String>(pages);
        List<String> out = new ArrayList<String>();
        for (String line : FileUtils.readLines(index, "utf-8")) {
            if (line.indexOf("all-classes-table") >= 0 && line.indexOf("<a href=") >= 0) {
                String href = extractHref(line);
                if (href != null && !keep.contains(href)) {
                    continue;               // drop this row
                }
            }
            out.add(line);
        }
        FileUtils.writeLines(index, "utf-8", out);
    }

    private String extractHref(String line) {
        int start = line.indexOf("<a href=\"");
        if (start < 0) return null;
        start += "<a href=\"".length();
        int end = line.indexOf('"', start);
        if (end < 0) return null;
        return line.substring(start, end);
    }

    private boolean copyFile(File source, File target, String relative) throws IOException {
        File from = new File(source, relative);
        if (!from.isFile()) {
            System.out.println("  missing (skipped): " + relative);
            return false;
        }
        File to = new File(target, relative);
        FileUtils.copyFile(from, to);
        return true;
    }

    private void copyDir(File source, File target, String relative) throws IOException {
        File from = new File(source, relative);
        if (!from.isDirectory()) return;
        File to = new File(target, relative);
        FileUtils.copyDirectory(from, to);
    }
}
