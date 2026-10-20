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
import java.io.IOException;
import java.util.Map;
import jetbrick.tools.chm.reader.ApiReader;
import jetbrick.tools.chm.style.*;
import org.apache.commons.io.FileUtils;

public class Application {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Config.apiLocation = new File(args[0]).getAbsoluteFile();
        }
        if (args.length > 1) {
            Config.encoding = args[1];
        }
        if (args.length > 2) {
            Config.chmRoot = new File(args[2]).getAbsoluteFile();
        } else {
            // Default: compile from the parent of the api directory, so that the
            // sibling folders (legal/, resources/, specs/) are reachable.
            File parent = Config.apiLocation.getParentFile();
            if (parent != null
                    && new File(parent, "index.html").exists()
                    && !parent.equals(Config.apiLocation)) {
                Config.chmRoot = parent;
            } else {
                Config.chmRoot = Config.apiLocation;
            }
        }

        if (!Config.apiLocation.isDirectory()) {
            System.err.println("Not a directory: " + Config.apiLocation);
            System.exit(1);
        }
        if (!Config.getChmRoot().isDirectory()) {
            System.err.println("CHM root is not a directory: " + Config.getChmRoot());
            System.exit(1);
        }

        System.out.println("api   : " + Config.apiLocation);
        System.out.println("root  : " + Config.getChmRoot() + " (api prefix: '" + Config.getApiPrefix() + "')");

        // Sample mode: build a small copy and run everything on the copy, so the
        // original documentation is never touched.
        if (Config.sampleLimit > 0) {
            File work = Config.sampleDir;
            System.out.println("sample mode: copying " + Config.sampleLimit
                    + " page(s) into " + work);
            new SampleBuilder().build(Config.apiLocation, work, Config.sampleLimit);

            Config.apiLocation = work;
            Config.chmRoot = work;
        }

        Config.style = detectStyle(Config.apiLocation);
        System.out.println("style: " + Config.style.getClass().getSimpleName());

        clean();

        ApiReader api = new ApiReader();
        Map<String, Object> apiContext = api.getApiContext();

        // The HTML Help viewer cannot run the javadoc JavaScript, so neutralise it
        // before writing the project files. The page skeleton, the stylesheet and
        // the flexbox "fake tables" all need the same treatment.
        new LayoutNeutralizer().apply(Config.apiLocation);
        new TableNeutralizer().apply(Config.apiLocation);
        new CssNeutralizer().apply(Config.apiLocation);
        new ScriptNeutralizer().apply(Config.apiLocation);

        TemplateWriter writer = new TemplateWriter();
        writer.apply(apiContext);
    }

    /**
     * Picks the parser for the javadoc flavour found in {@code apiLocation}.
     *
     * <p>{@code element-list} / {@code allclasses-index.html} only exist from
     * javadoc 9 on, which is also the generation that introduced {@code id="..."}
     * anchors and the {@code module/package/Class.html} layout.
     */
    private static JavadocStyle detectStyle(File apiLocation) {
        if (new File(apiLocation, "element-list").exists() || new File(apiLocation, "allclasses-index.html").exists()) {
            return new Javadoc9Style();
        }
        if (new File(apiLocation, "resources/fonts").exists()) {
            return new Javadoc8Style();
        }
        if (new File(apiLocation, "resources/background.gif").exists()) {
            return new Javadoc7Style();
        }
        return new JavadocStyle();
    }

    private static void clean() throws IOException {
        File root = Config.getChmRoot();
        deleteIfExists(new File(root, "htmlhelp.hhp"));
        deleteIfExists(new File(root, "htmlhelp.hhc"));
        deleteIfExists(new File(root, "htmlhelp.hhk"));
        deleteIfExists(new File(root, "build.bat"));
        deleteIfExists(new File(root, "hhc.exe"));
        deleteIfExists(new File(root, "hha.dll"));

        // Also remove the copies an older version may have left inside api/.
        for (File dir : new File[] { Config.apiLocation, root }) {
            for (String name : new String[] { "htmlhelp.hhp", "htmlhelp.hhc", "htmlhelp.hhk",
                    "build.bat", "hhc.exe", "hha.dll" }) {
                deleteIfExists(new File(dir, name));
            }
        }

        File redirs = new File(Config.apiLocation, "redirs/");
        if (redirs.exists()) FileUtils.deleteDirectory(redirs);
    }

    private static void deleteIfExists(File file) {
        if (file.isFile()) {
            if (!file.delete()) {
                System.err.println("warning: could not delete " + file);
            }
        }
    }

}
