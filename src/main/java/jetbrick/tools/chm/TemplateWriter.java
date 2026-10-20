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

import java.io.*;
import java.util.Collection;
import java.util.Map;
import jetbrick.tools.chm.reader.KeyManager;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

public class TemplateWriter {
    private final VelocityEngine ve;

    public TemplateWriter() {
        ve = new VelocityEngine();
        ve.init();
    }

    public void apply(Map<String, Object> context) throws Exception {
        context.put("encoding", Config.encoding);

        // write redir html (they live next to the api pages).
        @SuppressWarnings("unchecked")
        Collection<KeyManager> keyManagers = (Collection<KeyManager>) context.get("keyManagers");
        for (KeyManager keyManager : keyManagers) {
            if (keyManager.isRepeat()) {
                keyManager.sort();
                context.put("keyManager", keyManager);
                renderTemplate(Config.getChmRoot(), keyManager.getUrl(), context, "/template/redir.vm");
            }
        }

        // write project files into the CHM root, so that the [FILES] paths are
        // relative to it and both api/ and its sibling folders are compiled in.
        File root = Config.getChmRoot();
        renderTemplate(root, "htmlhelp.hhp", context, "/template/hhp.vm");
        renderTemplate(root, "htmlhelp.hhk", context, "/template/hhk.vm");
        renderTemplate(root, "htmlhelp.hhc", context, "/template/hhc.vm");
        renderTemplate(root, "build.bat", context, "/template/build.vm");

        // write hhc.exe, hha.dll
        outputBinaryFile(root, "hhc.exe", "/bin/hhc.exe");
        outputBinaryFile(root, "hha.dll", "/bin/hha.dll");
    }

    private void renderTemplate(File baseDir, String fileName, Map<String, Object> context, String templateName) throws Exception {
        File file = new File(baseDir, fileName);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        System.out.println(file);

        String templateContent = IOUtils.toString(getClass().getResourceAsStream(templateName), "utf-8");

        VelocityContext ctx = new VelocityContext(context);
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), Config.encoding);
        ve.evaluate(ctx, writer, "", templateContent);
        writer.close();
    }

    private void outputBinaryFile(File baseDir, String fileName, String templateName) throws Exception {
        File file = new File(baseDir, fileName);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        System.out.println(file);

        InputStream is = getClass().getResourceAsStream(templateName);
        OutputStream os = new FileOutputStream(file);
        IOUtils.copy(is, os);
        IOUtils.closeQuietly(is);
        IOUtils.closeQuietly(os);
    }
}
