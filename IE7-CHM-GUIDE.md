# JDK 21 Javadoc → CHM（IE7 兼容）实战笔记

> 把 `javadoc` 生成的 HTML 文档树编译成单个 `.chm`，并让它能在 CHM 内置的
> **IE7 内核**里正常显示、正常搜索。
>
> 本文记录的不只是"怎么用"，更是**踩过的每一个坑和它的根因**——CHM 的 IE7 引擎
> 与现代浏览器差异极大，很多问题只有装进 CHM、用 `mk:@MSITStore:` 打开才会暴露。

---

## 一、这个项目怎么用

### 1.1 它是一个 fat jar，不需要外部依赖

`dist/javadoc.chm-3.0.0.jar`（或自己构建的 `build/javadoc-chm-dev.jar`，约 2.6 MB）
**已经把全部依赖打进去了**：

| jar 内路径 | 内容 |
|---|---|
| `jetbrick/tools/chm/**` | 工具自身所有 class |
| `org/apache/{velocity,commons,log4j}/**` | 模板引擎 + 工具库 |
| `template/*.vm` | 生成 `.hhp / .hhc / .hhk` 的 Velocity 模板 |
| `bin/hhc.exe`, `bin/hha.dll` | **HTML Help 编译器**（会复制到输出目录） |
| `script-dir/jquery-1.12.4.min.js` | IE7 可用的 jQuery |
| `script-dir/jquery-ui-1.12.1.min.js` | IE7 可用的 jQuery-UI |
| `stylesheet-base.css` | 基础样式表 |

所以只跑 Java 部分时：

```powershell
java -jar javadoc-chm-dev.jar "C:\path\to\docs\api" UTF-8
```

> **但编译成 CHM 还需要 `hhc.exe`。** jar 会把它自动复制到输出目录，所以
> 命令行里仍然要调用它一次（或双击生成的 `build.bat`）：
>
> ```powershell
> cd <输出目录>
> .\hhc.exe htmlhelp.hhp      # 或双击 build.bat
> ```
>
> 也可以直接装 [HTML Help Workshop](https://www.microsoft.com/en-us/download/details.aspx?id=21138)，
> 用 `C:\Program Files (x86)\HTML Help Workshop\hhc.exe`（两者版本相同，都是 4.74.8702）。

### 1.1.1 内置的 hhc.exe 是哪个版本

CHM 的编译结果、能承受多大的工程、以及 `R6002` 崩溃阈值，全都取决于
`hhc.exe` 的版本，所以这里把实际使用的二进制记录清楚。

| 项目 | 值 |
|---|---|
| 文件名 | `hhc.exe`（配套 `hha.dll`） |
| FileVersion | **4.74.8702** |
| ProductVersion | 4.74.8702 |
| FileDescription | Microsoft® HTML Help Compiler |
| CompanyName | Microsoft Corporation |
| 架构 | **x86（32 位）** ← 这就是 `R6002` 的来源，见第 5 节 |
| 大小 | `hhc.exe` 51,472 字节 / `hha.dll` 837,904 字节 |
| 来源 | Microsoft HTML Help Workshop（随产品分发的官方二进制） |
| 存放位置 | `src/main/resources/bin/`（打包后被复制到输出目录） |

与系统安装的版本**逐字节相同**（SHA-256 一致），所以用内置的还是用
`C:\Program Files (x86)\HTML Help Workshop\hhc.exe` 结果完全一样：

```powershell
# 自行核对
$a = Get-FileHash .\src\main\resources\bin\hhc.exe -Algorithm SHA256
$b = Get-FileHash "C:\Program Files (x86)\HTML Help Workshop\hhc.exe" -Algorithm SHA256
$a.Hash -eq $b.Hash      # True
```

用 `-v` 之类参数看不到版本，要读 PE 头里的版本资源：

```powershell
(Get-Item .\src\main\resources\bin\hhc.exe).VersionInfo |
    Select-Object FileVersion, ProductVersion, FileDescription, CompanyName

# 判断 32/64 位
$b = [System.IO.File]::ReadAllBytes(".\src\main\resources\bin\hhc.exe")
$pe = [BitConverter]::ToInt32($b, 0x3C)
'{0:X4}' -f [BitConverter]::ToUInt16($b, $pe + 4)     # 014C = x86
```

> **4.74 是微软最后一版 HTML Help Workshop**（2003 年发布，之后再没更新），
> 所以不存在"换个新版本也许就不崩了"这条路——只能按第 5 节的办法控制工程规模。
> 它同时也是一个 32 位程序，2 GB 地址空间是硬上限。

### 1.2 命令行参数

```powershell
java -Xmx3g `
  -Djavadoc.chm.title="jdk21 API-en" `
  -Djavadoc.chm.skipClassUse=false `
  -jar javadoc-chm-dev.jar `
  "C:\Data\dsh\work\full\api" UTF-8
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `args[0]` | — | javadoc 的 `api` 目录（含 `element-list`、`index.html`） |
| `args[1]` | 系统编码 | 读写 HTML 用的编码，**JDK 文档固定 `UTF-8`** |
| `args[2]` | api 的父目录 | CHM 根目录；用于把同级的 `legal/`、`specs/` 一起打包 |
| `-Djavadoc.chm.title` | 取 `<title>` | CHM 窗口标题 + 文件名 |
| `-Djavadoc.chm.skipClassUse` | `true` | 跳过 `class-use/` 页面（约 22% 体积） |
| `-Djavadoc.chm.fullTextSearch` | `true` | 全文检索 |
| `-Djavadoc.chm.binaryIndex` | `false` | 二进制索引（**32 位 hhc 会内存溢出，保持 false**） |
| `-Djavadoc.chm.binaryToc` | `false` | 二进制目录 |
| `-Djavadoc.chm.sample` | `0` | >0 进入样本模式，只处理 N 个代表性页面 |
| `-Djavadoc.chm.sampleDir` | `build/sample` | 样本副本输出目录 |

### 1.3 两种工作流

**快速验证（样本模式，约 5 秒 + 4 分钟编译）**

样本模式会把页面**复制**到 `sampleDir` 再处理，原文档不动：

```powershell
java -Xmx2g `
  -Djavadoc.chm.sample=20 `
  -Djavadoc.chm.sampleDir=C:\Data\dsh\work\sample `
  -Djavadoc.chm.title="jdk21 API-en" `
  -Djavadoc.chm.skipClassUse=false `
  -jar javadoc-chm-dev.jar "C:\Data\dsh\work\docs\api" UTF-8

cd C:\Data\dsh\work\sample
.\hhc.exe htmlhelp.hhp
```

20 个样本页 → ~7.9 MB CHM，4 分钟编译。**改完代码先用这个验证，别直接跑全量。**

**全量生成（10607 文件 / 282 MB）**

```powershell
java -Xmx3g `
  -Djavadoc.chm.title="jdk21 API-en" `
  -Djavadoc.chm.skipClassUse=false `
  -jar javadoc-chm-dev.jar "C:\Data\dsh\work\full\api" UTF-8
# Java 转换阶段：约 1 分钟

cd C:\Data\dsh\work\full
.\hhc.exe htmlhelp.hhp
```

> ⚠️ **改的是副本，不是原文档。** 处理过程会原地重写 HTML/CSS/JS，
> 所以务必先 `Copy-Item` 一份干净的文档树再跑。原 zip `jdk21doc_ie7.zip`
> 解压后最好保留一份 `pristine` 快照。

### 1.4 从源码构建

```powershell
$proj = 'C:\Data\dsh\javadoc.chm'
$out  = "$proj\build\classes-out"
$cp   = (Get-ChildItem "$proj\lib" -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
$srcs = Get-ChildItem "$proj\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -nowarn -d $out -cp $cp @srcs

# 组装 fat jar：classes + resources + 所有依赖 jar 解包
$res = "$proj\build\res"; $stage = "$proj\build\stage"; $fat = "$proj\build\javadoc-chm-dev.jar"
Remove-Item $res,$stage -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $out $res -Recurse
Copy-Item "$proj\src\main\resources\*" $res -Recurse -Force
Copy-Item $res $stage -Recurse
Get-ChildItem "$proj\lib" -Filter *.jar | ForEach-Object { Push-Location $stage; & jar xf $_.FullName; Pop-Location }
Remove-Item "$stage\META-INF\MANIFEST.MF" -ErrorAction SilentlyContinue
& jar cfe $fat jetbrick.tools.chm.Application -C $stage .
```

**注意**：`javac @文件列表` 用 `@` 参数文件时，Windows 上每行不能带 BOM，否则会报
`无效文件名: ?C:\...`。直接用参数数组传更稳：

```powershell
$args = @('-encoding','UTF-8','-nowarn','-d',$out,'-cp',$cp) + $srcs
& javac @args
```

另外用 `Set-Content` 写 Java 源文件会加 UTF-8 BOM，`javac` 直接报
`非法字符: '\ufeff'`。要这样写：

```powershell
[System.IO.File]::WriteAllText($f, $content, (New-Object System.Text.UTF8Encoding($false)))
```

---

## 修复速查表

遇到问题先查这张表，再跳到第三部分的详细分析。

| 现象 | 根因关键词 | 修复位置 |
|---|---|---|
| 弹出"脚本错误 / 找不到成员"对话框 | `jQuery.attr("aria-*")` | `ScriptNeutralizer.fixAriaAttr` §3.1 |
| 搜索框输入卡顿 | 扫描 51k 条 + 渲染 300 节点 | `ScriptNeutralizer.tuneSearchPerf` §3.2 |
| 表格宽窄不一、不铺满 | 转换后 `<table>` 无宽度规则 | `CssNeutralizer.CHM_TABLE_BLOCK` §3.3 |
| Module Graph 显示破图 | 样本模式没拷 `module-graph.png` | `SampleBuilder` §3.4 |
| 标签切换后出现空行 | IE7 里 `display:none` 的单元格仍占行高 | `ScriptShim.show` §3.5 |
| 标题后面有小方块 | anchor-link 图标（`opacity:0` 在 IE7 无效） | `ScriptNeutralizer.removeAnchorLinks` §3.6 |
| 搜索页报 `push` 错 | `for..in` 枚举到 `Array.prototype` 上的 shim | `ScriptNeutralizer.fixForInLoops` §3.7 |
| redirs 落在 `api/api/` | 样本/全量路径基准不同，前缀加了两次 | `ApiReader` + `TemplateWriter` §3.8 |
| 搜索页表头冒破图方块 | `div.page-search-info` 在 IE7 藏不住 | `CssNeutralizer.CHM_OFFLINE_HIDE` §3.9 |
| 标签切几次就整表空白 | 读写 `className` 打架 + 隐藏行不恢复 | `ScriptShim.snapshotTable` §3.10 |
| 下拉框跑到左边压住正文 | 移除 flex 后 `offsetParent` 链变化 | `ScriptShim.placeAutocomplete` §3.11 |
| 索引双击进 occurrences 页 | 类名=构造器名 + `putAll` 覆盖 | `KeyManager`/`IndexesReader`/`mergeIndexes` §3.12 |
| 编译报 `runtime error R6002` | hhc 的 `[FILES]` 条目上限约 12270 | 剔除 `module-graph.png` §5 |
| 一打开就弹脚本错误，URL 是 googlesyndication | 源文档被注入广告脚本 | 清理 HTML 里的注入块 §3.13 |

---

## 二、CHM 的 IE7 内核：先搞清楚它到底是什么

### 2.1 确认渲染模式

`hh.exe`（CHM 查看器）在装过 IE11 的机器上**默认跑 IE7 模式**（`documentMode === 7`）。
不要假设它是"现代 IE"，也不要假设它有 ES5。

判断方法——做一个测试 CHM：

```html
<!DOCTYPE html>
<html>
<head><title>CHM 渲染模式检测</title></head>
<body>
  <h3>CHM 实际渲染模式：</h3>
  <p id="result"></p>
  <script>
    document.getElementById('result').innerHTML =
      typeof document.documentMode !== 'undefined'
        ? "IE " + document.documentMode
        : "不在 IE/Trident 环境下";
  </script>
</body>
</html>
```

**关键坑**：不要加 `<meta http-equiv="X-UA-Compatible" content="IE=edge">`。
加了会强制进 IE11 模式，测出来的数不代表真实 CHM 用户看到的效果。
（我第一版测试就加了这个，得到 IE11，导致后面所有验证都跑偏。）

### 2.2 IE7 能力矩阵（实测）

| 能力 | IE7 支持 | 说明 |
|---|---|---|
| `JSON.parse` | ❌ | `"JSON" 未定义` |
| `Object.keys` | ❌ | `对象不支持 "keys" 属性或方法` |
| `Array.prototype.forEach/map/filter/reduce` | ❌ | 需 `compat.js` 补 |
| `addEventListener` | ❌ | 用 `attachEvent` |
| `querySelectorAll` | ❌ | 用 `getElementsByTagName` + className 过滤 |
| `classList` | ❌ | 用 `className` 字符串处理 |
| `document.createRange` | ❌ | — |
| `const` / `let` / 箭头函数 / 模板字符串 | ❌ | 语法直接报错 |
| `opacity` | ❌ | 用 `filter: alpha(opacity=..)` |
| SVG（`<img src="*.svg">`） | ❌ | 显示为破图方块 |
| `flex` / `grid` | ❌ | 布局collapse |
| CSS 变量 `var(--x)` | ❌ | 需展开 |
| `::before` / `::after` | ❌ | 用单冒号 `:before`，且旧引擎对 `content` 支持有限 |
| `:nth-child` | ❌ | 需 JS 补 class |
| `table-layout: fixed` | ✅ | 表格布局可靠 |
| `setAttribute("aria-*")` | ✅ | 原生可以用 |
| jQuery `.attr("aria-*")` | ❌⚠️ | **见 3.1，这是最大坑** |

---

## 三、十三个坑，逐个拆

下面每条都是"现象 → 根因 → 修复"，代码位置都在
`src/main/java/jetbrick/tools/chm/`。

### 3.1 脚本错误对话框：`找不到成员`（0x80020006）

**现象**：打开任意类页面弹出脚本错误，指向 `script-dir/jquery-1.12.4.min.js`，
`行 4 字符 12600`。页面本身显示正常。

**根因**：jQuery 1.12.4 的 `.attr("aria-label", value)` 在 IE7 下会走它给
IE<8 准备的通用 hook，最终执行 `elem["aria-label"] = value` 这种
**未知属性方括号赋值**——IE7 对此抛 `找不到成员`。

有意思的是：**并不是所有 aria 属性都挂**。实测：

| 失败 | 成功 |
|---|---|
| `aria-label`, `aria-atomic`, `aria-autocomplete`, `aria-sort`, `aria-busy`, `aria-grabbed`, `aria-dropeffect` | `aria-hidden`, `aria-expanded`, `aria-controls`, `aria-owns`, `aria-haspopup`, `aria-describedby`, `aria-live`, `aria-labelledby` |

规律：名字里含 `hidden`/`controls` 这类 jQuery `rboolean` 关键词的走 boolean hook 就没事，
其余走通用 hook 就炸。而**原生 `setAttribute("aria-*")` 对所有名字都正常**。

**修复**（`ScriptNeutralizer.fixAriaAttr`）：

```js
// 把
li.attr("aria-label", item.l);
// 改成
li.each(function(){this.setAttribute("aria-label", item.l)});
```

要处理的三个文件：
- `search.js`（5 处）
- `search-page.js`
- `script-dir/jquery-ui.min.js`（**39 处**，很容易漏）

**实现要点**：用括号平衡扫描找 `.attr(..., <参数>)` 的结束位置，
参数可能是字符串拼接或多个函数调用，不能用正则一把梭。

```java
// findCallClose 必须从"("之后开始，初始 depth = 1
private int findCallClose(String js, int afterOpen) {
    int depth = 1;              // 关键：调用方已消耗掉 "("
    boolean inStr = false; char quote = 0;
    for (int i = afterOpen; i < js.length(); i++) {
        char c = js.charAt(i);
        if (inStr) { if (c=='\\') { i++; continue; } if (c==quote) inStr=false; continue; }
        if (c=='\'' || c=='"') { inStr = true; quote = c; continue; }
        if (c=='(') depth++;
        else if (c==')') { depth--; if (depth==0) return i; }
    }
    return -1;
}
```

> 我第一版把 `depth` 从 0 开始，导致 `)` 一出现就 `depth=-1` 永远不命中，
> 后续字符串被截断。这类改写逻辑**一定要写单元测试**：
> 用反射调私有方法，喂几行代表性 JS，肉眼看输出对不对。

### 3.2 搜索框输入卡半天

**现象**：搜索框敲字后界面像死掉一样。

**根因**：每次按键 `doSearch` 都要正则扫描 **51208** 条成员索引，
然后 jQuery-UI 为最多 **300** 条结果各建一个 DOM 节点。IE7 的 JS 引擎比现代 V8 慢
50 倍以上，300 个节点的渲染才是卡顿主因。

实测单次 `doSearch`：`cl` → 91ms、`string` → 172ms、`con` → 539ms（6 次累计）。
单次扫描不算慢，但**渲染 300 个带 `setAttribute` 的 `<li>`** 是灾难。

**修复**（`ScriptNeutralizer.tuneSearchPerf` + `tunePageSearchPerf`）：

```java
// search.js：自动补全
js.replace("var MAX_RESULTS = 300;", "var MAX_RESULTS = 50;")
  .replace("delay: 200,", "delay: 350,");

// search-page.js：独立搜索页
js.replace("doSearch({ term: term, maxResults: 1200 }, renderResults);",
           "doSearch({ term: term, maxResults: 200 }, renderResults);");
```

### 3.3 表格有窄有宽，不铺满

**现象**：Field Summary / Constructor Summary 表格宽度不一致，有的缩成一团。

**根因**（两层）：
1. `TableNeutralizer` 把 javadoc 9+ 的 div 伪表格转成真 `<table class="memberSummary chm-table">`，
   但**没有给任何宽度规则**，IE7 按内容收缩。
2. `script-dir/ie7-fix.css` 里的列宽规则针对的是 **转换前的 div 布局**
   （`.summary-table > .col-first { width: 20% }`），对真表格完全不生效。

**修复**：

(a) `TableNeutralizer.buildTable()` —— 保住原 div 的布局类，让它能被打样式：

```java
// 原 <div class="summary-table three-column-summary">
// → <table class="memberSummary chm-table three-column-summary" ... width="100%">
String extraClasses = layoutClassesOf(openTag);   // 挑出含 "column" 或结尾 "-summary" 的
sb.append("<table class=\"memberSummary ").append(MARKER);
if (extraClasses.length() > 0) sb.append(' ').append(extraClasses);
sb.append("\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n");
```

(b) `CssNeutralizer.CHM_TABLE_BLOCK` —— 追加到 `stylesheet.css`：

```css
table.memberSummary {
    width: 100%;
    table-layout: fixed;      /* IE7 对表格算法支持很好，这是关键 */
    border-collapse: collapse;
}
/* 两列：30/70  三列：22/30/48  release-note：15/15/70 */
table.three-column-summary td.colFirst  { width: 22%; }
table.three-column-summary td.colSecond { width: 30%; }
table.three-column-summary td.colLast   { width: 48%; }
```

`layoutClassesOf` 就是从原 `div` 的 class 里挑：
```java
for (String token : cls.split("\\s+")) {
    if (token.indexOf("column") >= 0 || token.endsWith("-summary")) { ... }
}
```

### 3.4 Module Graph 破图

**现象**：模块页 "Module Graph:" 显示破图。

**根因**：**样本模式**的 `SampleBuilder` 只复制了 `.html` 页面和若干共享文件，
没复制 `module-graph.png` / `module-graph.svg`。全量模式不会有这问题。

**修复**：`SampleBuilder` 里为每个被复制的模块补拷 graph 图：

```java
for (String module : modules) {
    copyFile(source, target, module + "/module-graph.png");
    copyFile(source, target, module + "/module-graph.svg");
}
```

### 3.5 标签切换后出现空行

**现象**：Overview 页点 "JDK" 标签，表头和第一行数据之间多出一条空白带。

**根因**：shim 的 `show()` 只把不匹配的 `<td>` 设 `display:none`，
但 **IE7 里整行都是 display:none 的单元格仍然占一行高度**。
切到 JDK 时前面 22 个 `java.*` 行全变成了空白带。

**修复**（`ScriptShim.show()`）：按 `parentNode` 把单元格分组到行，
整行无可见单元格时把 `<tr>` 也隐藏：

```js
var lastRow = null, rowVisible = false;
for (var j = 0; j < cells.length; j++) {
  var cell = cells[j], row = cell.parentNode;
  if (row !== lastRow) {
    if (lastRow && !rowVisible) { lastRow.style.display = 'none'; }
    lastRow = row; rowVisible = false; rowIndex++;
  }
  if (hasClass(cell, selected) || selected === tableId) { cell.style.display=''; rowVisible = true; }
  else { cell.style.display = 'none'; }
}
if (lastRow && !rowVisible) { lastRow.style.display = 'none'; }
```

### 3.6 标题后面的破图方块

**现象**："Methods declared in class java.lang.Object ⃞" 标题后有个小方块。

**根因**：`search.js` 会给每个带 `id` 的标题追加一个锚点图标：

```js
hdr.append(" <a class='anchor-link'>"
  + "<img src='" + pathtoroot + (window.__ie7Compat ? "link.png" : "link.svg") + "'>"
  + "</a>");
```

正常浏览器用 `a.anchor-link { opacity: 0 }` 隐藏、悬停才显示。但：
1. **IE7 不支持 `opacity`** → 图标常驻显示；
2. 没人设置 `__ie7Compat` → 走 `link.svg` 分支 → IE7 无法解码 SVG；
3. 即使换成 `link.png`，在 `mk:@MSITStore:` 协议下也加载失败（`complete=LOADING`）。

**修复**（`ScriptNeutralizer.removeAnchorLinks`）：把整块生成逻辑干掉。
锚点链接在离线 CHM 里没价值（悬停交互在 IE7 也基本不可用）：

```java
// 定位 $("section[id] > :header, ...").each(function(){...}); 整句，替换成注释
int sel = js.indexOf("section[id] > :header");
int start = js.lastIndexOf("$(", sel);
int close = findCallClose(js, start + 2);
int eachAt = js.indexOf(".each(", close);
// ... 再把 .each(...) 也吃掉，包括结尾分号
```

> 中途试过只设 `window.__ie7Compat = true` 换成 png，结果图标常驻可见
> （因为 opacity 不生效），还是难看。直接删掉才对。

### 3.7 搜索页报错 `无法获取未定义或 null 引用的属性 "push"`

**现象**：`search.html` 输入 "c"，`search-page.js` 行 154 `arr.push(item)` 报错。

**根因**：这是最隐蔽的一个。

`search-page.js` 用 `for (var i in result)` 遍历数组。而 `compat.js` 为了补 ES5
给 `Array.prototype` 加了 `forEach`/`map`/`filter`/`indexOf`/`reduce`。
IE7 的 JScript 5.7 **没有 `Object.defineProperty`，无法让它们不可枚举**，于是：

```js
for (var i in result)     // i 会依次是 "0","1",..., 然后是 "forEach","map","filter"...
    var arr = r[item.category];   // item 是函数时 category = undefined
    arr.push(item);               // r[undefined] → undefined → 炸
```

**修复**（`ScriptNeutralizer.fixForInLoops`）：两处 `for..in` 都换成索引循环：

```js
// for (var i in result)  →
for (var i = 0; i < result.length; i++)

// for (var key in r)  →
var RESULT_KEYS = ["types","members","packages","modules","searchTags"];
for (var ki = 0; ki < RESULT_KEYS.length; ki++) { var key = RESULT_KEYS[ki]; ... }
```

**教训**：在给老引擎补 ES5 的环境里，**任何对 Array 的 `for..in` 都是雷**。

### 3.8 全量模式的路径 bug（双 `api/` 前缀）

**现象**：全量编译后 `redirs` 文件落在 `api/api/redirs/`，hhp 里也是双前缀。

**根因**：样本模式 `chmRoot == apiLocation`，所以 `getApiPrefix()` 是空串，
bug 被掩盖。全量模式 `chmRoot` 是 `docs/`、`apiLocation` 是 `docs/api/`，
前缀是 `"api/"`，于是被处理了两次：

```java
// ApiReader 里
keyManager.prefixUrl("api/");      // url: "redirs/x.html" → "api/redirs/x.html"
files.add(prefix + keyManager.getUrl());   // "api/" + "api/redirs/x.html" ❌
```

**修复**（两处）：

```java
// ApiReader.getApiContext()
files.add(keyManager.getUrl());    // getUrl() 已经带前缀了

// TemplateWriter.apply() —— 写文件也要按 chmRoot 为基准
renderTemplate(Config.getChmRoot(), keyManager.getUrl(), context, "/template/redir.vm");
```

### 3.9 搜索页表头上多出一个破图方块

**现象**：`search.html` 的结果表格，表头 `Package | Class` 右侧冒出一个 17×17 的破图方块，
压在表格上。

**根因**：`search.html` 里有这样一个面板：

```html
<div class="page-search-info">
  <span id="page-search-link">link</span>
  <button class="copy" id="page-search-copy">
    <img src="copy.png" alt="Copy URL"><span>Copy</span>
  </button>
</div>
```

原样式表用 `height: 0; overflow: hidden` 把它折叠起来，**只在该区域悬停时才展开**。
IE7 不会因为 `height:0` + `overflow:hidden` 就把里面的按钮藏住（它缺少现代浏览器
对该组合的处理），于是 `copy.png` 一直露在外面。

**修复**（`CssNeutralizer.CHM_OFFLINE_HIDE`）：离线 CHM 里没有值得复制的 URL，
直接隐藏整块：

```css
div.page-search-info { display: none; }
button.snippet-copy  { display: none; }
```

> 排查捷径：与其盯着 CSS 猜，不如在 IE7 探针里把所有 `<img>` 的
> `src / width / height` 打出来。一眼就能看到 `IMG copy.png 17x17`，
> 直接锁定嫌疑元素。

### 3.10 标签页切几次之后就"废了"

**现象**：类页面 Method Summary 上方有 `All Methods / Static Methods / Instance Methods /
Concrete Methods / Deprecated Methods` 一排标签，来回切换几次后整张表变成空白，再也切不回来。

**根因**（两个叠在一起）：

1. **标签归属标记被自己改掉了**。`show()` 原本这样判断成员资格：

   ```js
   if (hasClass(cell, selected) || selected === tableId) { ... }
   ```

   而同一个循环里又会改写 `cell.className` 做行色带（`odd-row-color` /
   `even-row-color`）。`className` 被重写后再读，`method-summary-table-tab2`
   这类归属标记可能已经不在原位置，第二次切换就匹配不到任何单元格 → 全隐藏。

2. **隐藏过的行永远不会回来**。行被设成 `display:none` 后，切换回原来的标签时
   没有任何代码把它恢复，于是"越切越空"。

**修复**（`ScriptShim`）：把"归属判定"与"显隐/色带"彻底分开。

- 首次进入某张表时，把所有 tab 归属类**快照**到自定义属性里（IE7 的
  `setAttribute` 对普通名字工作正常，只有 `aria-*` 才会炸，见 3.1）：

  ```js
  function snapshotTable(tableId) {
    var cells = byClass('td', tableId);
    for (var i = 0; i < cells.length; i++) {
      var cell = cells[i];
      if (cell.getAttribute('data-chm-tabs') !== null) continue;
      var keep = [];
      var parts = String(cell.className).split(/\s+/);
      for (var k = 0; k < parts.length; k++) {
        if (parts[k].indexOf('-tab') >= 0) keep.push(parts[k]);
      }
      cell.setAttribute('data-chm-tabs', keep.join(' '));
    }
  }
  ```

- 每次切换都从快照重建，并且同时管好行状态：

  ```js
  var member = (selected === tableId) || tabMemberOf(cell, selected);
  ...
  if (row !== lastRow) {
    if (lastRow) lastRow.style.display = rowVisible ? '' : 'none';
    ...
  }
  ```

**教训**：凡是"读取 className → 改写 className"写在同一个循环里的代码，在
反复执行的场景下都不可靠。**归属信息要存到不被自己修改的地方**。

### 3.11 搜索下拉框跑到页面左边，压住正文

**现象**：在搜索框输入关键字，自动补全列表不是出现在输入框正下方，而是跑到页面
左侧、盖住正文段落。

**根因**：`jQuery-UI` 的 `position.js` 用 `offsetParent` 链累加来算坐标。
我们为了让 IE7 正常滚动而移除了 `flex-box` / `flex-content` 包裹层（见
`LayoutNeutralizer`），页面结构一换，`offsetParent` 链就变了，算出来的左边距
**差了 600 多像素**：

```
输入框实际位置  left = 1086
下拉框被放到    left = 472     ← 直接压到正文上
```

**修复**（`ScriptShim.placeAutocomplete`）：不依赖 jQuery-UI 的定位，
自己按页面坐标锚定，并在输入框事件里持续校正：

```js
function placeAutocomplete() {
  var lists = document.getElementsByTagName('ul');
  for (var i = 0; i < lists.length; i++) {
    var list = lists[i];
    if (!hasClass(list, 'ui-autocomplete') || list.style.display === 'none') continue;
    var input = document.getElementById('search-input');
    if (!input) return;
    var left = 0, top = 0, el = input;
    while (el) { left += el.offsetLeft; top += el.offsetTop; el = el.offsetParent; }
    list.style.left  = left + 'px';
    list.style.top   = (top + input.offsetHeight) + 'px';
    list.style.width = input.offsetWidth + 'px';
  }
}
```

配套：给 `ul.ui-autocomplete` 补 `z-index` / 白底 / 边框（原样式表只管
`position` 和 `max-height`，在 CHM 里会透出下面的正文）。

**教训**：改动了 DOM 骨架之后，任何依赖 `offsetParent` / `getBoundingClientRect`
的第三方库都要重新验证。IE7 上最稳的是**自己累加 `offsetLeft/offsetTop`**。

### 3.12 索引双击进的是 "occurrences" 列表页，不是类页面

**现象**：在 CHM 索引里双击 `SerialClob`，打开的是一个
`SerialClob occurences:` 列表页，还要再点一次才到类页面。

**根因**：两层叠加。

1. **类名与构造器名相同**。javadoc 的索引里 `SerialClob` 有三条：
   ```html
   <a href=".../SerialClob.html"        class="type-name-link">SerialClob</a>       <!-- 类 -->
   <a href="...#%3Cinit%3E(char[])"     class="member-name-link">SerialClob(char[])</a>
   <a href="...#%3Cinit%3E(java.sql.Clob)" class="member-name-link">SerialClob(Clob)</a>
   ```
   我们的 `baseTypeKey()` 把 `SerialClob(char[])` 截成 `SerialClob`，
   于是三条合并成同一个关键字，`KeyManager.isRepeat()` 判定"重复"→ 生成列表页，
   **把原来的类页目标覆盖掉了**。

2. **多文件索引合并时后者覆盖前者**。javadoc 的索引分散在
   `index-files/index-1.html ... index-27.html`，`ApiReader` 用
   `indexesMap.putAll(...)` 合并，后处理的文件会悄悄覆盖先处理的文件的正确目标。

**修复**（三处）：

- `KeyManager` 记录每次命中的页面 URL，区分"同一页多个重载"和"真的跨多页"：
  ```java
  String page = stripAnchor(url);          // 去掉 #fragment 再比较
  if (commonPage == null)      commonPage = page;
  else if (!commonPage.equals(page)) commonPage = "";   // 跨页了

  public boolean isRepeat() {
      return urls.size() > 1 && commonPage != null && commonPage.length() > 0;
  }
  ```

- `IndexesReader` 维护 `typeKeys`（登记为类型的名字），类型关键字永远不被替换：
  ```java
  if (indexMaps.containsKey(key) && typeKeys.contains(key)) return;   // 类型优先
  ```

- `ApiReader.mergeIndexes()` 取代 `putAll()`：**真实页面优先于 occurrences 页**：
  ```java
  boolean existingIsRedir = existing.indexOf("redirs/") >= 0;
  boolean valueIsRedir    = value.indexOf("redirs/")    >= 0;
  if (existingIsRedir && !valueIsRedir) target.put(key, value);        // 真页面胜出
  else if (!existingIsRedir && !valueIsRedir
           && indexesReader.isTypeKey(key) && value.indexOf('#') < 0) {
      target.put(key, value);                                          // 类页优先
  }
  ```

**效果**：occurrences 页从 **6446 个降到 1703 个（-73%）**。
`SerialClob`、`AbsentInformationException`、`AbstractAction`、`String`、`Class`
等直接打开类页面；剩下的是真正跨类的方法名（如 `abortRequested` 同时在
`ImageReader` 和 `ImageWriter` 里），保留列表页更合理。

**教训**：索引这类"多来源合并"的场景，`putAll` 几乎总是错的——
**必须显式定义优先级**（真实页面 > 聚合页；类型 > 成员）。

### 3.13 打开 CHM 就弹"脚本错误"，URL 指向 googlesyndication

**现象**：CHM 一打开任何页面就弹脚本错误，`行 0 / Char 0 / Script error.`，
URL 指向：

```
https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-xxxxxxxx
```

**根因**：**不是工具的问题，是源文档被广告脚本污染了。**

拿到手的「中文版 javadoc」压缩包里，几乎每个 HTML 的 `<head>` 都被塞进了
418 字节的固定块（AdSense + Google Analytics）。CHM 查看器是断网运行的，
这两个外链脚本加载失败，IE7 就弹出「Script error.」对话框。
英文版文档是干净的，所以只有中文版有这个现象。

**排查方法**（不要靠猜，直接扫）：

```powershell
$zh = 'C:\Data\dsh\doc-zh_ie7'

# 1) 确认污染面
Get-ChildItem $zh -Recurse -Include '*.html','*.js' -File |
  Select-String -Pattern 'googlesyndication|adsbygoogle|ca-pub-' -List |
  Measure-Object | Select-Object -ExpandProperty Count

# 2) 精确特征排查（避免误报）
foreach ($p in 'googlesyndication','adsbygoogle','googletagmanager',
               'google-analytics\.com','hm\.baidu\.com') {
    '{0,-24}: {1}' -f $p,
      (Get-ChildItem $zh -Recurse -Include '*.html','*.js' -File |
         Select-String -Pattern $p).Count
}
```

> ⚠️ **别用 `doubleclick`、`tag`、`pagead` 这类过宽的关键字。**
> 实测 `BasicFileChooserUI.DoubleClickListener`、`findElementMatchingTag`
> 会被误判，一查就"71 处命中"，全是 javadoc 自己的类名。
> 要用 `googlesyndication`、`ca-pub-`、`G-XXXX` 这种唯一标识才准。
>
> 另外一个 PowerShell 陷阱：`Select-String -List` 配合 `$_.Matches` 取到的
> 是空字符串，要看真实命中内容得去掉 `-List` 再取 `$_.Line`。

**清理脚本**（按起止标记定长切割，写回时保持无 BOM）：

```powershell
$zh        = 'C:\Data\dsh\doc-zh_ie7'
$enc       = New-Object System.Text.UTF8Encoding($false)
$startMark = '<script async src="https://pagead2.googlesyndication.com'
$endMark   = "gtag('config', 'G-XXXX');</script>"
$clean = 0; $skip = 0; $fail = 0

foreach ($f in Get-ChildItem $zh -Recurse -Include '*.html' -File) {
    $c = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $s = $c.IndexOf($startMark)
    if ($s -lt 0) { $skip++; continue }
    $m = $c.IndexOf($endMark, $s)
    if ($m -lt 0) { $fail++; continue }
    $e = $m + $endMark.Length
    [System.IO.File]::WriteAllText(
        $f.FullName, $c.Substring(0, $s) + $c.Substring($e), $enc)
    $clean++
}
"清理 $clean / 跳过 $skip / 失败 $fail"
```

实测结果：**10227 个文件清理成功、0 失败**；
体积 313.4 MB → 309.4 MB（10227 × 418 字节 ≈ 4 MB，数字完全对得上）。

**清理后必须验证三件事**：

1. 广告特征零残留（`googlesyndication` / `adsbygoogle` / `googletagmanager`）
2. HTML 里不再有任何外部 `src="https?://..."` 脚本引用
3. 结构没被切坏：`</head>` / `</html>` 配对齐全，中文内容仍在
   （例如 `String.html` 里还能找到「所有已实现的接口」）

**教训**：从网上下载的"现成文档包"要当成**不可信输入**。编译前先扫一遍
`googlesyndication / adsbygoogle / gtag / 百度统计 / CNZZ` 这类特征，
否则做出来的 CHM 一打开就是脚本错误，还容易让人误判成工具本身有 bug。

---

## 四、验证方法（很重要）

### 4.1 不要用现代浏览器验证 CHM

必须用 **真实 IE7 模式 + CHM 协议**。可复用的探针程序（命令行 exe）：

```powershell
# 加 IE7 强制模式（第一次运行有效，进程启动时读取注册表）
$reg = 'HKCU:\SOFTWARE\Microsoft\Internet Explorer\MAIN\FeatureControl\FEATURE_BROWSER_EMULATION'
New-ItemProperty -Path $reg -Name 'IE7Probe.exe' -Value 7000 -PropertyType DWord -Force
```

`Program.cs` 关键点：

```csharp
[STAThread]                          // WebBrowser 必须 STA，否则 ActiveX 实例化失败
static void Main(string[] args) {
    var form = new Form();
    var wb = new WebBrowser { Dock = DockStyle.Fill, ScriptErrorsSuppressed = true };
    wb.DocumentCompleted += (s,e) => { if (wb.ReadyState == WebBrowserReadyState.Complete) done = true; };
    form.Controls.Add(wb); form.Show();
    wb.Navigate(url);                 // 用 mk:@MSITStore:C:\path\x.chm::/page.html
    while (!done) { Application.DoEvents(); Thread.Sleep(100); }
    // 用 wb.Document.InvokeScript("eval", new object[]{expr}) 取运行时状态
}
```

编译：

```powershell
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $csc /nologo /target:exe /platform:x64 /out:IE7Probe.exe `
       /r:System.Windows.Forms.dll /r:System.Drawing.dll Program.cs
```

### 4.2 两个必知的坑

**(a) `X-UA-Compatible: IE=edge` 会毁掉 IE7 模式测试。**
必须有一个**不带这个 meta** 的页面（`plain.html`）来触发 IE7，否则永远测出 IE11。

**(b) IE7 模式下 `WebBrowser.DrawToBitmap()` 拍出空白图。**
用 `Graphics.CopyFromScreen(form.Bounds)` 截屏代替，并且窗口要 `TopMost` + 可见。

### 4.3 CHM 内协议

```
mk:@MSITStore:C:\path\to\file.chm::/java.base/java/lang/String.html
```

`hh.exe -decompile` 在当前机器上**不产出文件**（0 files），别指望用它验证内容。
直接读处理后的目录 + 用 mk 协议加载更可靠。

### 4.4 运行时自检脚本片段

```js
// 一次性确认一堆状态
"documentMode=" + document.documentMode
"jQuery=" + (typeof window.jQuery)
"jQueryUI=" + (window.jQuery && jQuery.ui ? jQuery.ui.version : 'n/a')
"memberIdx=" + (window.memberSearchIndex ? window.memberSearchIndex.length : 'missing')
"tables=" + document.getElementsByTagName('table').length
"anchorLinks=" + /* 应该是 0 */ ...
"__ie7Compat=" + window.__ie7Compat
```

---

## 五、全量编译的一个硬限制：hhc 的 R6002

`hhc.exe` 是 **32 位程序**。JDK 21 全量编译时遇到过：

```
runtime error R6002
- floating point not loaded
exit code 255
```

并产出一个**截断的 CHM**（15-33 MB，正常应 57 MB）。

### 5.1 不是单纯的"文件太多"，是条目数上限

逐项二分实测（.html 固定 12040 条）：

| [FILES] 组成 | 总条目 | 结果 |
|---|---|---|
| 只 .html | 12040 | ✅ 2.2 min，37.4 MB |
| + css/js/woff/gif/jpg/md/svg | 12234 | ✅ 42 MB |
| + 36 个 png | 12270 | ✅ 42.1 MB |
| + 37 个 png | 12271 | ❌ R6002 |
| 全量含 class-use | 17069 | ❌ R6002 |

阈值精确卡在 **12270/12271 条**。崩溃点还随内容漂移（有时报
`module-graph.png : The HTML tag "二进制乱码..."`），是 hhc 内部表满员的表现，
不是某个文件损坏——所有 png 头尾签名都验证过是好的。

### 5.2 解法：把 module-graph.png 转成文字标记

每个模块页都有一张 `module-graph.png`（JDK 21 有 59 张），其信息在页面文字里
本来就有。处理两处：

**`TableNeutralizer.replaceSvgImages()`** —— `<img>` 转文字标记：

```java
if (html.indexOf("module-graph.") >= 0) {
    Matcher gm = MODULE_GRAPH_IMG.matcher(html);   // module-graph.(png|svg)
    while (gm.find()) {
        gm.appendReplacement(gsb, Matcher.quoteReplacement(
            "<span class=\"chm-svg-fallback\">[module graph]</span>"));
    }
}
```

**`ApiReader.getAllFiles()`** —— 从 [FILES] 排除这些 png：

```java
if (filename.endsWith("/module-graph.png")) continue;
files.add(filename);
```

最终 12275 条 → **18 分钟编译出 57.1 MB 完整 CHM**
（含全文检索、12040 页、59 张图片、完整索引）。

> ⚠️ 必须同时跳过 class-use（默认 `-Djavadoc.chm.skipClassUse=true`），
> 不然 17069 条还是会崩。
>
> ⚠️ `hhc` 退出码 1 也可能成功（warning 计入退出码），
> **以 CHM 文件大小和有无 R6002 为准**。

### 5.3 一次踩坑记录

往 `ApiReader.java` 插代码时用 PowerShell 字符串拼接，`\n` 没转义成真换行，
整段 Java 挤在一行。`javac` 竟然编译通过（注释吞掉了后面的代码），
但 `files.add(filename)` 失效——hhp 里只剩 redirs 没有 html。
**教训：patch Java 源码必须用正规编辑方式，改完必须编译+检查产物。**

---

## 六、文件结构速查

```
javadoc.chm/
├── README.md
├── IE7-CHM-GUIDE.md                    ← 本文
├── src/main/java/jetbrick/tools/chm/
│   ├── Application.java                入口：解析参数、检测风格、串起所有 Neutralizer
│   ├── Config.java                     全部 -D 配置项
│   ├── LayoutNeutralizer.java          flex-box → 普通文档流（header/content 解包）
│   ├── TableNeutralizer.java           div 伪表格 → <table>（含布局类保留、SVG 兜底）
│   ├── CssNeutralizer.java             var() 展开、@media 删除、flex/grid 降级
│   │                                    ├ CHM_TABLE_BLOCK    转换表宽度/列宽
│   │                                    └ CHM_OFFLINE_HIDE   隐藏复制面板、下拉框样式
│   ├── ScriptNeutralizer.java          search.js / search-page.js / jquery-ui 适配
│   │                                    ├ es3ify               const → var
│   │                                    ├ fixAriaAttr          .attr("aria-*") → setAttribute
│   │                                    ├ tuneSearchPerf       MAX_RESULTS / delay
│   │                                    ├ tunePageSearchPerf   搜索页 maxResults
│   │                                    ├ removeAnchorLinks    删锚点图标
│   │                                    └ fixForInLoops        干掉 for..in 数组遍历
│   ├── ScriptShim.java                 替换 script.js
│   │                                    ├ show/snapshotTable  标签过滤+行显隐快照
│   │                                    ├ placeAutocomplete    下拉框页面坐标锚定
│   │                                    └ switchTab/loadScripts
│   ├── SampleBuilder.java              样本模式复制
│   ├── TemplateWriter.java             .hhp/.hhc/.hhk 生成
│   ├── reader/                         element-list / allclasses-index / index-files 解析
│   │                                    ├ KeyManager         commonPage 判断同页重载
│   │                                    ├ IndexesReader      typeKeys 类型关键字优先
│   │                                    └ ApiReader          mergeIndexes 显式优先级合并
│   └── style/                          Javadoc7/8/9 风格检测
├── src/main/resources/
│   ├── template/{hhp,hhc,hhk,build,redir}.vm
│   ├── script-dir/jquery-1.12.4.min.js   ← IE7 可用的 jQuery
│   ├── script-dir/jquery-ui-1.12.1.min.js
│   └── bin/{hhc.exe,hha.dll}
└── lib/                                编译期依赖（都已打进 fat jar）
```

---

## 七、经验清单（浓缩版）

1. **先确认 `documentMode`**，别猜。测试页不能带 `X-UA-Compatible: IE=edge`。
2. **修改代码后先用样本模式**（`-Djavadoc.chm.sample=20`）验证，5 秒处理 + 4 分钟编译。
3. **永远处理副本**，neutralizer 是原地改文件。
4. **IE7 里 `for (x in array)` 是雷** —— 补 ES5 shim 后 `Array.prototype` 可枚举。
5. **`jQuery.attr("aria-*")` 会炸出"找不到成员"**，换成原生 `setAttribute`。
6. **SVG 一律不能进 CHM**（`<img src="*.svg">` → 破图）。
7. **`opacity: 0` 在 IE7 不生效**，靠它隐藏的元素会常驻可见。
8. **`display:none` 的单元格仍占行高**，整行隐藏要隐藏 `<tr>`。
9. **转换后的 `<table>` 必须显式给宽度和列宽**，否则按内容收缩。
10. **32 位 hhc 有 2 GB 上限**，`R6002` 就是内存溢出，产物是截断的。
11. **验证要用 `mk:@MSITStore:` 加载**，`DrawToBitmap` 在 IE7 模式出空白图。
12. **写 Java 源文件别用 `Set-Content`**（BOM 会让 javac 失败）。
13. **改写 JS 的代码要写单元测试**（反射调私有方法），括号平衡是最容易错的地方。
14. 样本模式和全量模式的**路径基准不同**（`chmRoot` vs `apiLocation`），
    样本通过不代表全量通过。
15. **hhc 的 [FILES] 条目上限约 12270**（32 位内部表），超限报 `R6002` 且产出截断 CHM。
    剔除冗余二进制（如 module-graph.png）是唯一出路。
16. **`hhc` 退出码 1 不代表失败**，warning 也计入；以产物大小和有无 R6002 判断。
17. **PowerShell 补丁 Java 源码时 `\n` 不会自动转义**，错误拼接会让 javac
    "编译通过但代码失效"——改完必须编译并抽查产物。
18. **`height:0 + overflow:hidden` 在 IE7 藏不住里面的内容**，靠这招折叠的面板
    （如 `div.page-search-info`）会整块露出来，要显式 `display:none`。
19. **别在同一个循环里"读 className 再改 className"**，反复执行时标记会被自己擦掉；
    归属信息要快照到不被修改的属性（`data-*`）上。
20. **改动 DOM 骨架后，任何依赖 `offsetParent` 的第三方定位都会失效**
    （jQuery-UI 的下拉框偏移了 600px）。IE7 上自己累加
    `offsetLeft/offsetTop` 最稳。
21. **多来源索引合并时 `putAll()` 几乎总是错的**，必须显式定义优先级：
    真实页面 > 聚合页（occurrences），类型 > 成员。
22. 排查"莫名多出来的小图标/方块"，最快的方法是**遍历 DOM 打印所有
    `<img>` 的 src 和尺寸**，比盯着 CSS 猜快十倍。
23. **下载来的文档包一律当成不可信输入**：编译前先扫
    `googlesyndication / adsbygoogle / gtag / 百度统计 / CNZZ`。
    被注入的广告脚本在断网 CHM 里必然报脚本错误，很容易误判成工具 bug。
24. **扫注入别用过宽关键字**（`doubleclick`、`tag`、`pagead`），
    javadoc 自己的 `DoubleClickListener`、`findElementMatchingTag` 会大量误报；
    用 `googlesyndication`、`ca-pub-`、`G-XXXX` 这种唯一串。
25. **清理前后对账体积**：注入块大小 × 文件数 应该正好等于减小的字节数
    （实测 418 B × 10227 ≈ 4 MB），对得上才说明切割干净、没误删内容。
