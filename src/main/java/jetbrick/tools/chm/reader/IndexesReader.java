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

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrick.tools.chm.Config;
import jetbrick.tools.chm.style.Javadoc9Style;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Reads the {@code index-files/index-N.html} (or, for javadoc 6/7,
 * {@code index-all.html}) pages and produces the kwargs of the CHM index.
 *
 * <p>Every kind of entry javadoc knows about gets indexed:
 * <ul>
 *   <li>types - class, interface, enum class, record class, exception class,
 *       annotation interface;</li>
 *   <li>members - method, constructor, static/instance variable;</li>
 *   <li>packages and modules.</li>
 * </ul>
 *
 * <p>Each of them is registered <b>twice</b>: once under its plain name
 * ({@code Class}, {@code charAt}) and once under its fully qualified name
 * ({@code java.lang.Class}, {@code java.lang.String.charAt}). The CHM keyword
 * pane does prefix matching on the keyword, so the plain entry is what makes a
 * bare "Class" search work at all.
 */
public class IndexesReader {

    private Map<String, KeyManager> keyManagerMaps = new HashMap<String, KeyManager>(4096);

    /**
     * Plain names that javadoc indexes as a <b>type</b> ("SerialClob").
     *
     * <p>Constructors share the type name ({@code SerialClob(char[])},
     * {@code SerialClob(Clob)}), so the member pass would otherwise turn the
     * type keyword into an "occurrences" list. A type keyword always keeps
     * pointing at its class page.
     */
    private Set<String> typeKeys = new HashSet<String>(4096);

    public Map<String, String> getIndexes(File resource) throws IOException {
        String content = FileUtils.readFileToString(resource, Config.encoding);

        boolean baseIsIndexFiles = resource.getParentFile() != null
                && "index-files".equalsIgnoreCase(resource.getParentFile().getName());

        Map<String, String> indexMaps = new HashMap<String, String>(32768);

        // 1) type entries
        // <dt><a href="../java.base/java/lang/Class.html" class="type-name-link"
        //        title="class in java.lang">Class&lt;T&gt;</a> - Class in <a ...>java.lang</a></dt>
        Pattern typePattern = Config.style.getJavaIndexTypeRegex();
        Matcher tm = typePattern.matcher(content);
        while (tm.find()) {
            String url = normalizeUrl(Javadoc9Style.decode(tm.group(1)), baseIsIndexFiles);
            String rawName = Javadoc9Style.stripTypeParameters(
                    Javadoc9Style.decode(Javadoc9Style.stripTags(tm.group(2))));
            String description = tm.groupCount() >= 3 ? Javadoc9Style.decode(tm.group(3)) : "";
            addTypeIndex(indexMaps, rawName, url, description);
        }

        // 2) member entries
        // <dt><a href="...#charAt(int)" class="member-name-link">charAt</a>
        //        - Method in class java.lang.<a ...>String</a></dt>
        Pattern p = Config.style.getIndexRegex();
        Matcher m = p.matcher(content);
        while (m.find()) {
            String url = normalizeUrl(Javadoc9Style.decode(m.group(1)), baseIsIndexFiles);
            String text = Javadoc9Style.decode(Javadoc9Style.stripTags(m.group(2))).trim();
            String description = m.groupCount() >= 3 ? Javadoc9Style.decode(m.group(3)) : "";
            addMemberIndex(indexMaps, text, url, description);
        }
        return indexMaps;
    }

    private String normalizeUrl(String url, boolean baseIsIndexFiles) {
        if (baseIsIndexFiles) {
            url = StringUtils.removeStart(url, "../");
        }
        url = StringUtils.removeStart(url, "./");
        return url;
    }

    /**
     * Constant fields such as {@code ABORTED_BY_SYSTEM}, {@code AA_COMPROMISE} or
     * {@code A0} flood the keyword pane: JDK 21 alone contributes ~4800 all-caps
     * entries, which pushes the actual class names down several screens. They are
     * still reachable from their class page, so the index keeps only real types,
     * methods and mixed-case fields.
     */
    private boolean isConstantNoise(String key) {
        if (key.length() < 2) return false;
        boolean hasLetter = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c >= 'a' && c <= 'z') return false;          // mixed case: keep
            if (c >= 'A' && c <= 'Z') hasLetter = true;
            else if (c != '_' && c != '0' && (c < '0' || c > '9')) return false;
        }
        return hasLetter;
    }

    /**
     * Registers a type under its plain name and its package qualified name.
     *
     * @param name  e.g. {@code Class}
     * @param url   e.g. {@code java.base/java/lang/Class.html}
     * @param description e.g. {@code "Class in java.lang."}
     */
    private void addTypeIndex(Map<String, String> indexMaps, String name, String url, String description) {
        if (name.length() == 0 || url.indexOf('#') >= 0) return;

        String key = AnchorNameManager.replaceAnchor(url);
        indexMaps.put(name, key);
        typeKeys.add(name);

        String qualified = getQualifiedTypeName(name, url, description);
        if (qualified != null && !qualified.equals(name)) {
            indexMaps.put(qualified, key);
            typeKeys.add(qualified);
        }
    }

    /**
     * Members are indexed by name ("charAt"), by {@code Type.member}
     * ("String.charAt") and by the fully qualified form
     * ("java.lang.String.charAt"). Overloads collapse into one keyword and the
     * CHM index links to an "occurrences" page listing every signature.
     */
    private void addMemberIndex(Map<String, String> indexMaps, String text, String url, String description) {
        // javadoc 9+ also indexes every section heading ("1.0 Background",
        // "Thread Safety", ...). Those make a huge and useless keyword list.
        if (description.toLowerCase().indexOf("search tag in") >= 0) return;
        if (text.length() == 0) return;

        if (url.indexOf('#') < 0) {
            // package / module entry
            indexMaps.put(text + " class", AnchorNameManager.replaceAnchor(url));
            return;
        }

        String typeName = StringUtils.removeEnd(
                StringUtils.substringAfterLast(StringUtils.substringBefore(url, "#"), "/"), ".html");
        String packageName = getPackageName(description, url);
        String qualifiedType = packageName.length() > 0 ? packageName + "." + typeName : typeName;

        // Registering the "Type.member" alias for every member produced ~10k extra
        // occurrence pages, which is what blew the 32 bit hhc.exe past its memory
        // limit. Only the plain name and the fully qualified name are indexed now:
        // "charAt" and "java.lang.String.charAt" both work, the latter already
        // disambiguates by type.
        register(baseTypeKey(text), qualifiedType + "." + text, text, url, indexMaps);
    }

    /** {@code charAt(int)} -> {@code charAt}; {@code Class<T>} -> {@code Class}. */
    private String baseTypeKey(String text) {
        String key = Javadoc9Style.stripTypeParameters(text);
        int index = key.indexOf('(');
        if (index >= 0) key = key.substring(0, index);
        return key.trim();
    }

    /**
     * Adds one keyword. When the same keyword shows up on more than one page
     * the keyword points at the per-keyword "occurrences" page instead.
     *
     * <p>A keyword that is also a <b>type name</b> never gets replaced: javadoc
     * indexes {@code SerialClob} both as the class and as its two constructors
     * {@code SerialClob(char[])} / {@code SerialClob(Clob)}, and collapsing
     * those into a list page made a double click on the index land on
     * "SerialClob occurrences:" instead of the class. The type entry wins.
     */
    private void register(String key, String qualifiedName, String display, String url, Map<String, String> indexMaps) {
        if (key.length() == 0) return;
        if (isConstantNoise(key)) return;

        KeyManager keyManager = keyManagerMaps.get(key);
        if (keyManager == null) {
            keyManager = new KeyManager(key);
            keyManager.add(qualifiedName, AnchorNameManager.replaceAnchor("../" + url));
            keyManagerMaps.put(key, keyManager);
            if (!indexMaps.containsKey(key)) {
                indexMaps.put(key, AnchorNameManager.replaceAnchor(url));
            } else if (typeKeys.contains(key)) {
                /* keep the type target, nothing to do */
            }
        } else {
            keyManager.add(qualifiedName, AnchorNameManager.replaceAnchor("../" + url));
            String common = keyManager.getCommonPage();
            if (common.length() > 0) {
                /* every hit is on one page (overloads): go straight there */
                String page = common;
                if (page.startsWith("../")) page = page.substring(3);
                if (!typeKeys.contains(key)) {
                    indexMaps.put(key, AnchorNameManager.replaceAnchor(page));
                }
            } else if (keyManager.isRepeat() && !typeKeys.contains(key)) {
                /* the keyword really spans several pages: build the list page */
                indexMaps.put(key, AnchorNameManager.replaceAnchor(keyManager.getUrl()));
            }
        }
    }

    /**
     * The index description only carries the package ("Method in class
     * java.lang."), the type is derived from the url.
     */
    private String getPackageName(String description, String url) {
        String packageName = StringUtils.substringAfter(description, " in ").trim();
        if (packageName.length() == 0) {
            // fall back to the url: module/pkg/Type.html -> pkg
            String path = StringUtils.substringBefore(url, "#");
            String dir = StringUtils.substringBeforeLast(path, "/");
            String[] parts = dir.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                // skip the module prefix (java.base, jdk.compiler, ...)
                if (i == 0 && parts.length > 1 && part.indexOf('.') >= 0 && !part.startsWith("javax")) {
                    continue;
                }
                if (sb.length() > 0) sb.append('.');
                sb.append(part.replace('/', '.'));
            }
            return sb.toString();
        }
        return StringUtils.removeEnd(packageName, ".");
    }

    /**
     * Builds {@code java.lang.Class} from the simple name plus the package
     * carried by the index description, so the qualified keyword exists too.
     */
    private String getQualifiedTypeName(String name, String url, String description) {
        String packageName = StringUtils.substringAfterLast(description, " ").trim();
        packageName = StringUtils.removeEnd(packageName, ".");
        if (packageName.length() == 0) {
            packageName = getPackageName(description, url);
        }
        if (packageName.length() == 0 || packageName.indexOf('.') < 0) return null;
        return packageName + "." + name;
    }

    /** @return true when the keyword is also a documented type name */
    public boolean isTypeKey(String key) {
        return typeKeys.contains(key);
    }

    public Collection<KeyManager> getKeyManagers() {
        return keyManagerMaps.values();
    }
}
