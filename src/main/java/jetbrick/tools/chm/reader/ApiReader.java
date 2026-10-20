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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrick.tools.chm.Config;
import jetbrick.tools.chm.model.ModuleInfo;
import jetbrick.tools.chm.model.PackageInfo;
import jetbrick.tools.chm.style.Javadoc9Style;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class ApiReader {
    private JavaInfoReader javaInfoReader = new JavaInfoReader();
    private IndexesReader indexesReader = new IndexesReader();
    private Map<String, Object> context = new HashMap<String, Object>();

    public Map<String, Object> getApiContext() throws Exception {
        // add all files: everything under the CHM root, so that api/ links to
        // sibling folders (legal/, resources/, specs/) resolve inside the CHM.
        Set<String> files = getAllFiles(Config.getChmRoot(), new HashSet<String>());

        // replace anthor
        Iterator<String> it = files.iterator();
        while (it.hasNext()) {
            String file = it.next();
            if (file.endsWith(".html")) {
                AnchorNameManager.addAnchor(new File(Config.getChmRoot(), file), Config.encoding);
            }
        }

        // add index url
        context.put("apiPrefix", Config.getApiPrefix());
        context.put("indexUrl", Config.getApiPrefix() + getIndexUrl(new File(Config.apiLocation, "index.html")));

        // add title: an explicit -Djavadoc.chm.title wins over the javadoc one.
        String doctitle;
        if (Config.title != null && Config.title.trim().length() > 0) {
            doctitle = Config.title.trim();
        } else {
            doctitle = getApiTitle(new File(Config.apiLocation, "index.html"));
            doctitle = doctitle.replaceAll("\\s*[\\(\\[]?Java SE \\d+ & JDK \\d+[\\)\\]]?\\s*$", "").trim();
        }
        if (doctitle.length() == 0) doctitle = "Javadoc API";
        context.put("title", doctitle);
        // Title used inside the hhp file: the hhp parser is line oriented and has
        // no entity support, so keep it plain ASCII-safe text.
        context.put("chmTitle", toPlainTitle(doctitle));
        context.put("fullTextSearch", Boolean.valueOf(Config.fullTextSearch));
        context.put("fullTextSearchText", Config.fullTextSearch ? "Yes" : "No");
        context.put("binaryIndexText", Config.binaryIndex ? "Yes" : "No");
        context.put("binaryTocText", Config.binaryToc ? "Yes" : "No");

        // add packages / modules
        List<PackageInfo> packages = javaInfoReader.getJavaInfo();
        List<ModuleInfo> modules = null;
        if (Config.style.hasModuleLevel()) {
            modules = groupByModule(packages);
            context.put("modules", modules);
            context.put("hasModules", Boolean.valueOf(!modules.isEmpty()));
            // Flat list is still exposed for templates that do not use modules.
            context.put("packages", packages);
        } else {
            context.put("packages", packages);
            context.put("hasModules", Boolean.FALSE);
        }

        // add indexes
        File resource = new File(Config.apiLocation, "index-all.html");
        if (resource.exists()) {
            Map<String, String> indexesMap = indexesReader.getIndexes(resource);
            context.put("sortIndexes", sortIndexed(indexesMap));
        } else {
            File resourceDir = new File(Config.apiLocation, "index-files/");
            File resources[] = resourceDir.listFiles();
            if (resources == null) {
                throw new IOException("Neither 'index-all.html' nor 'index-files/' was found in " + Config.apiLocation);
            }
            Map<String, String> indexesMap = new HashMap<String, String>(8192);
            for (File res : resources) {
                if (res.isDirectory()) continue;
                if (!res.getName().toLowerCase().endsWith(".html")) continue;
                mergeIndexes(indexesMap, indexesReader.getIndexes(res));
            }
            context.put("sortIndexes", sortIndexed(indexesMap));
        }
        Collection<KeyManager> keyManagers = indexesReader.getKeyManagers();
        context.put("keyManagers", keyManagers);

        // Everything indexed so far is relative to the api directory. Prefix it with
        // the api folder name so the paths are valid from the CHM root.
        String prefix = Config.getApiPrefix();
        if (prefix.length() > 0) {
            prefixModel(packages, prefix, modules);
            prefixIndexes(context, prefix);
        }

        // add redir files
        for (KeyManager keyManager : keyManagers) {
            if (keyManager.isRepeat()) {
                files.add(keyManager.getUrl());   // getUrl() already carries the api prefix
            }
        }
        context.put("files", files);

        return context;
    }

    /** Rewrites model urls from {@code pkg/Type.html} to {@code api/pkg/Type.html}. */
    private void prefixModel(List<PackageInfo> packages, String prefix, List<ModuleInfo> modules) {
        if (modules != null) {
            for (ModuleInfo module : modules) {
                String url = module.getUrl();
                if (url != null && !url.startsWith(prefix)) {
                    module.setUrl(prefix + url);
                }
            }
        }
        for (PackageInfo packageInfo : packages) {
            packageInfo.setUrl(prefix + packageInfo.getUrl());
            for (jetbrick.tools.chm.model.ClassInfo classInfo : packageInfo.getClasses()) {
                classInfo.setUrl(prefix + classInfo.getUrl());
                for (jetbrick.tools.chm.model.MethodInfo m : classInfo.getMethods()) {
                    m.setUrl(prefix + m.getUrl());
                }
                for (jetbrick.tools.chm.model.FieldInfo f : classInfo.getFields()) {
                    f.setUrl(prefix + f.getUrl());
                }
            }
        }
    }

    /** Rewrites the index map and the occurrence pages with the api prefix. */
    @SuppressWarnings("unchecked")
    private void prefixIndexes(Map<String, Object> context, String prefix) {
        List<Map.Entry<String, String>> sortIndexes =
                (List<Map.Entry<String, String>>) context.get("sortIndexes");
        if (sortIndexes != null) {
            List<Map.Entry<String, String>> rewritten =
                    new ArrayList<Map.Entry<String, String>>(sortIndexes.size());
            for (Map.Entry<String, String> entry : sortIndexes) {
                rewritten.add(new AbstractMap.SimpleEntry<String, String>(
                        entry.getKey(), prefix + entry.getValue()));
            }
            context.put("sortIndexes", rewritten);
        }

        Collection<KeyManager> keyManagers = (Collection<KeyManager>) context.get("keyManagers");
        if (keyManagers != null) {
            for (KeyManager keyManager : keyManagers) {
                keyManager.prefixUrl(prefix);
            }
        }
    }

    /**
     * Groups the flat package list into the "module -> package" hierarchy that
     * javadoc 9+ produces. Packages without a module are kept in a synthetic
     * module-less group so nothing gets lost.
     */
    private List<ModuleInfo> groupByModule(List<PackageInfo> packages) {
        Map<String, ModuleInfo> moduleMaps = new LinkedHashMap<String, ModuleInfo>();
        for (PackageInfo packageInfo : packages) {
            String moduleName = packageInfo.getModuleName();
            if (moduleName == null) moduleName = "";
            ModuleInfo module = moduleMaps.get(moduleName);
            if (module == null) {
                module = new ModuleInfo(moduleName);
                module.setUrl(moduleName.length() == 0 ? "overview-summary.html" : moduleName + "/module-summary.html");
                moduleMaps.put(moduleName, module);
            }
            module.addPackage(packageInfo);
        }

        List<ModuleInfo> modules = new ArrayList<ModuleInfo>(moduleMaps.values());
        for (ModuleInfo module : modules) {
            Collections.sort(module.getPackages(), new Comparator<PackageInfo>() {
                public int compare(PackageInfo o1, PackageInfo o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
            for (PackageInfo packageInfo : module.getPackages()) {
                Collections.sort(packageInfo.getClasses(), new Comparator<jetbrick.tools.chm.model.ClassInfo>() {
                    public int compare(jetbrick.tools.chm.model.ClassInfo o1, jetbrick.tools.chm.model.ClassInfo o2) {
                        return o1.getShortName().compareToIgnoreCase(o2.getShortName());
                    }
                });
            }
        }
        Collections.sort(modules, new Comparator<ModuleInfo>() {
            public int compare(ModuleInfo o1, ModuleInfo o2) {
                // java.* modules first, then jdk.*, then everything else.
                int r = rank(o1.getName()) - rank(o2.getName());
                if (r != 0) return r;
                return o1.getName().compareToIgnoreCase(o2.getName());
            }

            private int rank(String name) {
                if (name.length() == 0) return 0;
                if (name.startsWith("java.")) return 1;
                if (name.startsWith("jdk.")) return 2;
                return 3;
            }
        });
        return modules;
    }

    /**
     * Merges the per-file index maps.
     *
     * <p>{@code putAll} alone lets a later file overwrite a keyword that an
     * earlier file had already resolved to a type page. javadoc splits its
     * index over {@code index-files/index-1.html ...}; the type entry of
     * {@code AbsentInformationException} may sit in one file and its two
     * constructors in the same one, but the occurrence-page target produced for
     * the constructors must never replace the class-page target. Type keywords
     * therefore win, and an occurrence page only survives when the keyword
     * really spans several class pages.
     */
    private void mergeIndexes(Map<String, String> target, Map<String, String> part) {
        for (Map.Entry<String, String> entry : part.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String existing = target.get(key);
            if (existing == null) {
                target.put(key, value);
                continue;
            }
            if (existing.equals(value)) continue;

            boolean existingIsRedir = existing.indexOf("redirs/") >= 0;
            boolean valueIsRedir = value.indexOf("redirs/") >= 0;

            // a real page always beats an occurrence list
            if (existingIsRedir && !valueIsRedir) {
                target.put(key, value);
            }
            // two real pages: keep the first one seen, unless the newcomer is
            // the class page of a type keyword
            else if (!existingIsRedir && !valueIsRedir) {
                if (indexesReader.isTypeKey(key) && value.indexOf('#') < 0) {
                    target.put(key, value);
                }
            }
        }
    }

    private List<Map.Entry<String, String>> sortIndexed(Map<String, String> indexesMap) {
        List<Map.Entry<String, String>> sortIndexes = new ArrayList<Map.Entry<String, String>>(indexesMap.size());
        sortIndexes.addAll(indexesMap.entrySet());
        Collections.sort(sortIndexes, new Comparator<Map.Entry<String, String>>() {
            public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2) {
                return o1.getKey().toLowerCase().compareTo(o2.getKey().toLowerCase());
            }
        });
        return sortIndexes;
    }

    private Set<String> getAllFiles(File rootdir, Set<String> files) {
        File[] children = rootdir.listFiles();
        if (children == null) return files;

        String rootPath = Config.getChmRoot().getAbsolutePath().replace('\\', '/');
        if (!rootPath.endsWith("/")) rootPath = rootPath + "/";

        for (File resource : children) {
            if (resource.isDirectory()) {
                // Do not descend into the generated output.
                if (resource.getName().equals("redirs") && resource.getParentFile().getAbsoluteFile().equals(Config.apiLocation.getAbsoluteFile())) {
                    continue;
                }
                // "class use" pages bloat the project past what the 32 bit hhc.exe
                // can compile (see Config#skipClassUse).
                if (Config.skipClassUse && resource.getName().equals("class-use")) {
                    continue;
                }
                getAllFiles(resource, files);
            } else {
                String filename = resource.getAbsolutePath().replace('\\', '/');
                if (filename.startsWith(rootPath)) {
                    filename = filename.substring(rootPath.length());
                }
                // The module graph PNG is turned into a text marker by
                // TableNeutralizer, so the file itself is not needed inside the
                // CHM. Dropping the 59 graphs also keeps the project below the
                // file count hhc.exe (32 bit) can still compile.
                if (filename.endsWith("/module-graph.png")) continue;
                files.add(filename);
            }
        }
        return files;
    }

    private String getApiTitle(File resource) throws IOException {
        Pattern p = Config.style.getApiTitleRegex();
        Matcher m = p.matcher(FileUtils.readFileToString(resource, Config.encoding));

        if (m.find()) {
            // <title>Overview (Java SE 21 &amp; JDK 21)</title> -> plain text
            return Javadoc9Style.decode(m.group(1)).trim();
        }
        return "Javadoc API";
    }

    /**
     * Turns a javadoc title into something safe for the {@code .hhp} file and
     * for a Windows file name: entities decoded, no control characters and no
     * path separators.
     */
    private String toPlainTitle(String title) {
        String result = Javadoc9Style.decode(title);
        result = result.replaceAll("[\\p{Cntrl}]", " ");
        result = result.replace('/', '-').replace('\\', '-').replace(':', '-');
        result = result.replaceAll("\\s+", " ").trim();
        if (result.length() == 0) result = "Javadoc API";
        return result;
    }

    private String getIndexUrl(File resource) throws IOException {
        Pattern p = Config.style.getIndexUrlRegex();
        Matcher m = p.matcher(FileUtils.readFileToString(resource, Config.encoding));

        if (m.find()) {
            return m.group(1).trim();
        }
        return "overview-summary.html";
    }

}
