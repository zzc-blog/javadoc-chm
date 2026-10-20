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
package jetbrick.tools.chm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java 9+ module, e.g. {@code java.base}. Used to build a
 * "module -> package -> class" table of contents.
 */
public class ModuleInfo {

    private String name;
    private String url;
    private List<PackageInfo> packages = new ArrayList<PackageInfo>();

    public ModuleInfo() {
    }

    public ModuleInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<PackageInfo> getPackages() {
        return packages;
    }

    public void addPackage(PackageInfo packageInfo) {
        packages.add(packageInfo);
    }

    /** @return how many classes are documented inside this module */
    public int getClassCount() {
        int count = 0;
        for (PackageInfo packageInfo : packages) {
            count += packageInfo.getClasses().size();
        }
        return count;
    }
}
