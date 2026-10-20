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
import jetbrick.tools.chm.model.*;
import jetbrick.tools.chm.style.Javadoc9Style;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Reads the package / class / member structure of a javadoc tree.
 *
 * <p>Two layouts are supported:
 * <ul>
 *   <li>javadoc 8 and earlier: {@code package-list} + {@code allclasses-frame.html},
 *       pages located at {@code pkg/Class.html};</li>
 *   <li>javadoc 9 and later: {@code element-list} + {@code allclasses-index.html},
 *       pages located at {@code module/pkg/Class.html}.</li>
 * </ul>
 */
public class JavaInfoReader {

    /**
     * @return one {@link PackageInfo} per documented package; each one knows the
     *         module it belongs to (javadoc 9+) or {@code null} otherwise.
     */
    public List<PackageInfo> getJavaInfo() throws IOException {
        File apiLocation = Config.apiLocation;

        File elementList = new File(apiLocation, "element-list");
        if (elementList.exists()) {
            return getModulePackages(elementList);
        }

        File packageList = new File(apiLocation, "package-list");
        if (packageList.exists()) {
            return getPackageList(packageList);
        }

        throw new IOException("Neither 'element-list' nor 'package-list' was found in " + apiLocation
                + " - is it really a javadoc output directory?");
    }

    // ------------------------------------------------------------------
    // javadoc 9+ : element-list  (  module:NAME  /  pkg  /  pkg ...  )
    // ------------------------------------------------------------------

    private List<PackageInfo> getModulePackages(File resource) throws IOException {
        List<String> lines = FileUtils.readLines(resource, Config.encoding);

        // Preserve the order used by element-list.
        List<String> moduleNames = new ArrayList<String>();
        Map<String, List<String>> modulePackages = new HashMap<String, List<String>>();

        String currentModule = null;
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;

            if (line.startsWith("module:")) {
                currentModule = line.substring("module:".length()).trim();
                if (!modulePackages.containsKey(currentModule)) {
                    moduleNames.add(currentModule);
                    modulePackages.put(currentModule, new ArrayList<String>());
                }
            } else {
                if (currentModule == null) {
                    // element-list without any module header: flat layout.
                    currentModule = "";
                    moduleNames.add(currentModule);
                    modulePackages.put(currentModule, new ArrayList<String>());
                }
                modulePackages.get(currentModule).add(line);
            }
        }

        Map<String, PackageInfo> packageMaps = new HashMap<String, PackageInfo>(1024);
        List<PackageInfo> packages = new ArrayList<PackageInfo>(1024);
        for (String moduleName : moduleNames) {
            boolean moduleLess = moduleName.length() == 0;
            for (String packageName : modulePackages.get(moduleName)) {
                PackageInfo p = new PackageInfo();
                p.setName(packageName);
                p.setModuleName(moduleLess ? null : moduleName);
                if (moduleLess) {
                    p.setUrl(packageName.replace('.', '/') + "/package-summary.html");
                } else {
                    p.setUrl(moduleName + "/" + packageName.replace('.', '/') + "/package-summary.html");
                }
                packageMaps.put(p.getUrl(), p);
                packages.add(p);
            }
        }

        List<ClassInfo> classes = getAllClassesFromIndex(new File(Config.apiLocation, "allclasses-index.html"));
        int orphan = 0;
        int missing = 0;
        for (ClassInfo classinfo : classes) {
            // In sample mode the index still lists types whose page was not
            // copied; drop them so the table of contents stays resolvable.
            if (!new File(Config.apiLocation, classinfo.getUrl()).isFile()) {
                missing++;
                continue;
            }
            PackageInfo p = packageMaps.get(classinfo.getPackageUrl());
            if (p == null) {
                orphan++;
                continue;
            }
            p.addClass(classinfo);
        }
        if (missing > 0) {
            System.out.println("skipped " + missing + " class(es) whose page is not present");
        }
        if (orphan > 0) {
            System.out.println("warning: " + orphan + " class(es) belong to a package missing from element-list");
        }

        return packages;
    }

    private List<ClassInfo> getAllClassesFromIndex(File resource) throws IOException {
        if (!resource.exists()) {
            throw new IOException("allclasses-index.html not found: " + resource);
        }
        Pattern p = Config.style.getJavaClassRegex();
        Matcher m = p.matcher(FileUtils.readFileToString(resource, Config.encoding));

        List<ClassInfo> classes = new ArrayList<ClassInfo>(2048);
        while (m.find()) {
            String url = m.group(1);
            String title = Javadoc9Style.decode(m.group(2));      // "class in java.lang"
            String shortName = Javadoc9Style.decode(stripTags(m.group(3)));

            String packageName = StringUtils.substringAfter(title, " in ").trim();
            if (packageName.length() == 0 || shortName.length() == 0) continue;
            if (url.indexOf('/') < 0) continue;                   // not a class page

            ClassInfo info = new ClassInfo();
            info.setFullName(packageName + "." + shortName);
            info.setUrl(url);
            info.setShortName(shortName);
            String parent = StringUtils.substringBeforeLast(url, "/");
            info.setPackageUrl(parent + "/package-summary.html");
            classes.add(info);

            System.out.println("class: " + info.getUrl());
            addClassFieldMethod(info);
        }
        return classes;
    }

    // ------------------------------------------------------------------
    // javadoc 8 and earlier : package-list + allclasses-frame.html
    // ------------------------------------------------------------------

    private List<PackageInfo> getPackageList(File resource) throws IOException {
        List<String> lines = FileUtils.readLines(resource, Config.encoding);

        Map<String, PackageInfo> packageMaps = new HashMap<String, PackageInfo>(32);
        List<PackageInfo> packages = new ArrayList<PackageInfo>(50);
        for (String line : lines) {
            line = line.trim();
            if (line.length() != 0) {
                PackageInfo p = new PackageInfo();
                p.setName(line);
                p.setUrl(line.replace('.', '/') + "/package-summary.html");
                packageMaps.put(p.getName(), p);
                packages.add(p);
            }
        }

        // add all classes to package
        List<ClassInfo> classes = getAllClasses(new File(Config.apiLocation, "allclasses-frame.html"));
        for (ClassInfo classinfo : classes) {
            PackageInfo p = packageMaps.get(classinfo.getPackageName());
            if (p == null) {
                System.err.println("warning: package not found in package-list for " + classinfo.getUrl());
                continue;
            }
            p.addClass(classinfo);
        }

        return packages;
    }

    private List<ClassInfo> getAllClasses(File resource) throws IOException {
        Pattern p = Config.style.getJavaClassRegex();
        Matcher m = p.matcher(FileUtils.readFileToString(resource, Config.encoding));

        List<ClassInfo> classes = new ArrayList<ClassInfo>(128);
        while (m.find()) {
            ClassInfo info = new ClassInfo();
            info.setFullName(m.group(2).replace('/', '.'));
            info.setUrl(m.group(1));
            info.setShortName(m.group(3));
            classes.add(info);

            System.out.println("class: " + info.getUrl());
            addClassFieldMethod(info);
        }
        return classes;
    }

    // ------------------------------------------------------------------
    // members of a single class page
    // ------------------------------------------------------------------

    private void addClassFieldMethod(ClassInfo classinfo) throws IOException {
        File file = new File(Config.apiLocation, classinfo.getUrl());
        if (!file.exists()) {
            System.err.println("warning: class page not found: " + file);
            return;
        }
        String html = FileUtils.readFileToString(file, Config.encoding);

        Pattern p = Config.style.getJavaFieldRegex();
        Matcher m = p.matcher(html);

        Set<String> seen = new HashSet<String>();
        while (m.find()) {
            String name = Javadoc9Style.decode(stripTags(m.group(2)));
            String url = m.group(1);

            url = StringUtils.remove(url, "../");
            url = StringUtils.remove(url, "./");

            if (url.length() == 0 || name.length() == 0) continue;
            if (!seen.add(url)) continue; // a member may be linked more than once per page

            String anchor = StringUtils.substringAfter(url, "#");
            String linkUrl = StringUtils.substringBefore(url, "#") + "#" + AnchorNameManager.getNewAnchorName(anchor);

            if (Config.style.isMethod(url)) {
                MethodInfo info = new MethodInfo();
                info.setName(name);
                info.setFullName(Config.style.getMethodFullName(url));
                info.setUrl(linkUrl);
                classinfo.addMethod(info);
            } else {
                FieldInfo info = new FieldInfo();
                info.setName(name);
                info.setUrl(linkUrl);
                classinfo.addField(info);
            }
        }
    }

    /** Removes inline markup such as {@code <wbr>} or {@code <span>} from a snippet. */
    private static String stripTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").trim();
    }
}
