# javadoc.chm

把 javadoc 生成的 HTML 文档树编译成单个 `.chm`（Compiled HTML Help），
带**目录树、关键字索引和全文检索**，并对 **CHM 内置的 IE7 内核**做了完整渲染兼容 ——
双击就能用的离线 API 手册。

支持 javadoc 7 / 8 / **9+（模块化，实测 JDK 21）**，中英文文档均可。

> 本项目基于 [subchen/javadoc.chm](http://subchen.github.io/javadoc.chm/)（作者 Guoqiang Chen）修改而来。
> 原项目支持到 javadoc 8；本分支扩展了 **javadoc 9+（JDK 21）** 支持，
> 并补齐了对 CHM 内置 IE7 引擎的完整兼容层。原项目与本项目均采用 Apache License 2.0。

---

## 它能做什么

```
javadoc 输出的 HTML 目录（api/）
        │
        │  解析 element-list / allclasses-index / index-files
        │  得到「模块 → 包 → 类 → 成员」结构
        ▼
   生成 .hhp / .hhc / .hhk 工程文件
        │
        │  hhc.exe 编译
        ▼
   单个 .chm（目录树 + 索引 + 全文搜索）
```

## 为什么需要改造

CHM 查看器用的是 **IE7 内核**（`documentMode === 7`），比现代浏览器落后十几年。
直接拿 javadoc 9+ 的输出去编译，会撞上一堆问题：

| 问题 | 原因 |
|---|---|
| 页面没有滚动条 / 布局塌陷 | javadoc 9+ 用 flexbox 排版，IE7 不支持 |
| 表格列错位、宽窄不一 | javadoc 9+ 用 div + grid 伪造表格 |
| 搜索框敲字就卡死 | 每次按键扫描 5 万条索引并渲染 300 个节点 |
| 弹「找不到成员」脚本错误 | jQuery `.attr("aria-label")` 在 IE7 抛异常 |
| 标题后有小方块 | SVG 图标无法解码 / `opacity:0` 不生效 |
| 索引双击进「occurrences」列表 | 类名与构造器名相同，被误判为重复关键字 |

本项目把这些逐条处理掉，产物在真实 IE7 引擎下**零脚本错误**。
每个坑的根因和修复代码见 **[IE7-CHM-GUIDE.md](IE7-CHM-GUIDE.md)**。

## 环境要求

- **JDK 8+**（构建需要 JDK 8+，运行 `java -jar` 即可）
- **Windows**（CHM 是 Windows 专有格式）
- `hhc.exe`：[HTML Help Workshop](https://www.microsoft.com/en-us/download/details.aspx?id=21138)
  —— 项目已内置一份（`src/main/resources/bin/`），编译时会自动复制到输出目录

## 构建

依赖都在 `lib/`，用 ant 或直接 javac：

```powershell
ant dist

# 或手动
javac -encoding UTF-8 -d build/classes -cp "lib/*" `
  (Get-ChildItem src/main/java -Recurse -Filter *.java).FullName
```

## 使用

### 快速预览（样本模式，约 5 秒 + 4 分钟编译）

只处理 20 个代表性页面，**原文档不动**（自动复制到样本目录）：

```powershell
java -Xmx2g `
  -Djavadoc.chm.sample=20 `
  -Djavadoc.chm.sampleDir=C:\work\sample `
  -Djavadoc.chm.title="jdk21 API-en" `
  -jar dist\javadoc.chm-3.0.0.jar `
  "C:\work\docs\api" UTF-8

cd C:\work\sample
.\hhc.exe htmlhelp.hhp
```

### 全量生成

```powershell
java -Xmx3g `
  -Djavadoc.chm.title="jdk21 API-en" `
  -jar dist\javadoc.chm-3.0.0.jar `
  "C:\work\docs\api" UTF-8

cd C:\work\docs
.\hhc.exe htmlhelp.hhp
```

生成 CHM 后，再用 `hhc.exe` 编译（或双击生成的 `build.bat`）。

### 中文文档

额外加 `-Djavadoc.chm.cjk=true`，会注入中文优先的字体栈和更宽松的行高：

```powershell
java -Xmx3g `
  -Djavadoc.chm.title="jdk21 API-zh" `
  -Djavadoc.chm.cjk=true `
  -jar dist\javadoc.chm-3.0.0.jar `
  "C:\work\docs-zh\api" UTF-8 "C:\work\docs-zh"
```

> 注意第三个参数：当 docs 目录下**没有**顶层 `index.html` 时（例如只有 `api/`），
> 要显式指定 CHM 根目录，否则工程文件会写到 `api/` 里面。

## 配置项（`-D` 系统属性）

| 属性 | 默认 | 说明 |
|---|---|---|
| `javadoc.chm.title` | 空（取 javadoc `<title>`） | CHM 窗口标题 / 文件名 |
| `javadoc.chm.sample` | `0` | >0 进入样本模式，只处理 N 个页面 |
| `javadoc.chm.sampleDir` | `build/sample` | 样本副本输出目录 |
| `javadoc.chm.skipClassUse` | `true` | 跳过 `class-use/` 页面（约占 22% 体积） |
| `javadoc.chm.fullTextSearch` | `true` | 全文检索 |
| `javadoc.chm.binaryIndex` | `false` | 二进制索引（**32 位 hhc 会溢出，保持 false**） |
| `javadoc.chm.binaryToc` | `false` | 二进制目录 |
| `javadoc.chm.cjk` | `false` | 中文排版：中文字体栈 + 行高 |

## 已知限制

`hhc.exe` 是 **32 位程序**，`[FILES]` 条目超过约 **12270** 条时会崩溃并报
`runtime error R6002`，产出一个截断的 CHM。所以：

- `skipClassUse` 默认 `true`（JDK 21 含 class-use 会到 17069 条）
- 冗余的 `module-graph.png` 会被转成文字标记并从文件列表剔除

JDK 21 全量实测：**12275 条 → 18 分钟 → 57 MB CHM**。
详细分析见指南第 5 节。

## 文档

| 文件 | 内容 |
|---|---|
| [IE7-CHM-GUIDE.md](IE7-CHM-GUIDE.md) | 完整实战笔记：用法、IE7 能力矩阵、13 个坑的根因与修复、验证方法、hhc 上限 |
| [CHANGELOG.md](CHANGELOG.md) | 变更记录 |

## 许可

Apache License 2.0 —— 见 [LICENSE](LICENSE)。

本项目是 [subchen/javadoc.chm](http://subchen.github.io/javadoc.chm/) 的衍生作品：

- 原始代码：Copyright 2010-2014 Guoqiang Chen
- 本项目修改：Copyright 2026 zzc-blog

依据 Apache License 2.0 第 4(b) 条，修改过的源文件头部均已标注改动说明。
随附的第三方组件（Velocity、Commons、log4j、jQuery、jQuery UI、hhc.exe 等）
及其许可信息见 [NOTICE](NOTICE)。
