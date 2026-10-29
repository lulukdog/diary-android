# 日记

装在自己手机上的日记。可以连上自己的日记本（journal）：记下的话会同步过去，打开某一天能看到陪伴的话和心情。没连接时，文字和照片仍只留在这台设备里。

一天从北京时间（Asia/Shanghai）凌晨 4 点切开，和日记本一致。浅色和深色跟随系统，Android 12 起主色还会用系统动态色。首页可以翻到昨天和更早的日子，看那一天的日记和小结。记下之后，如果听得出累、难过、孤单、紧张或高兴，会先接一句，不必再去问。

可以写成网页上下载的 APK，不经过应用商店。装好之后，设置里点「检查更新」会查看 GitHub 上的 `version.json`，有新版本就下载并交给系统安装器。更新地址和日记本地址都用应用里的默认值，设置里不再填写。

## 功能

- 今天：写文字，拍照或从相册加图片，图片可以写一句说明。左右可以翻到昨天和更早的日子。连上日记本后，这些会送到服务器。
- 陪伴：记下之后，本地先听语气，接一句短的。同步之后，当天卡片上的 `companion`、心情回声 `mood.line`，以及它说过的话，会显示在这一天里。平常的记事不接话。可以「问它」。
- 小结：翻到有内容的一天时，如果日记本还没有卡片标题，会当场整理一段。有密钥时用 DeepSeek；没有密钥或连不上时，从当天原文摘 1 到 3 句，不再空白。
- 日历：按月查看，点开某一天可以补记。补记先留在手机上，因为日记本只按它现在的时间归天。
- 当日摘要：日记本的一天卡片优先。只有这台设备上已经有 DeepSeek 密钥时，才会在背后再整理一段。设置里不再填写密钥。图片文件不会发给 DeepSeek。
- 设置：连接状态、检查连接、立即同步、检查更新、导出、隐私说明。没有地址和密钥输入框。
- 导出：一个 zip，里面是 `diary-export.json` 和 `photos/`。
- 本机接口（默认关闭）：只监听 `127.0.0.1`，给以后的插件留一个口子。

## 技术

- Kotlin、Jetpack Compose、Material 3、Room
- minSdk 26，targetSdk / compileSdk 37
- 密钥放在 `EncryptedSharedPreferences`
- 发布包用仓库里的演示密钥签名，方便旁加载后继续更新

没有自己的账号体系。连上日记本时用那一个口令（`JOURNAL_APP_TOKEN`），和网页登录相同。口令只存在手机的加密存储里，仓库和安装包都不带口令。不做 iOS。

## 构建

需要 JDK 17 或更高，以及 Android SDK（本仓库用的是 platform 37 和 build-tools 37）。

```bash
export ANDROID_HOME="$HOME/android-sdk"   # 改成你的 SDK 路径
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

或者：

```bash
chmod +x scripts/build-release.sh
./scripts/build-release.sh
```

脚本会核对 `app/build.gradle.kts` 和 `release/version.json` 的版本号，编出 release 包，复制到 `release/diary.apk`。

`local.properties` 不要提交。`.env` 也不要提交。

## 签名

演示密钥在 `keystore/diary-release.jks`。

| 项 | 值 |
| --- | --- |
| 别名 | `diary` |
| 密钥库密码 | `diary-demo` |
| 密钥密码 | `diary-demo` |
| 类型 | PKCS12 |

这把密钥的密码写在仓库里，只适合个人旁加载演示。换成自己的密钥之后，已经装过演示包的手机必须卸载重装，否则系统会拒绝更新。自动更新要求前后两个 APK 用同一把密钥签名。

请安装 `release/diary.apk`（release 签名）。debug 包签名不同，不能被后来的 release 包覆盖更新。

## 网页安装和自动更新

`release/` 里有三样东西：

- `index.html`：中文安装页，含 vivo 允许未知来源的步骤
- `version.json`：应用内更新要读的清单
- `diary.apk`：已签名的安装包

`version.json` 的形状：

```json
{
  "versionCode": 3,
  "versionName": "1.0.2",
  "apkUrl": "https://raw.githubusercontent.com/lulukdog/diary-android/main/release/diary.apk",
  "changelog": "这一版改了什么"
}
```

`apkUrl` 固定指向本仓库 `main` 上的 `release/diary.apk`。安装页上的下载按钮始终指向同目录的 `diary.apk`，不走这个字段。

装好这个 APK 之后，打开「设置」，点 **检查更新**。清单地址固定为：

`https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json`

有新版本时弹出对话框，再点「下载并安装」，交给系统安装器（`ACTION_VIEW`，类型 `application/vnd.android.package-archive`）。启动时也会用同一地址静默看一眼。检查失败不会打断启动，只有手动点按钮才会说明原因。按钮下面会显示一行状态：正在检查、已是最新版本，或正在下载的百分比。

以前在设置里保存过别的地址，这一版也不再使用。

以后发新版：提高 `versionCode` 和 `versionName`（`app/build.gradle.kts` 与 `release/version.json` 保持一致），用同一把密钥重新打包，把新的 `release/diary.apk` 和 `release/version.json` 提交到 `main` 的同样两个路径。已经装过的手机不用改地址。

也可以把整个 `release` 目录放到自己的网站或电脑上。应用内更新仍请求编译进去的清单地址；自建时需要在构建机器上设置 `UPDATE_MANIFEST_URL` 再打包：

```bash
cd release
python3 -m http.server 8080
```

自建时把 `apkUrl` 改成那个 `diary.apk` 的真实网址，并在构建时把 `UPDATE_MANIFEST_URL` 设成 `http://电脑IP:8080/version.json`（或你的 https 地址）。应用不再从设置里改这个地址。

第一次安装更新时，系统会要求允许「日记」安装未知应用。允许后回到应用再点一次「安装」。

局域网可以用 http。DeepSeek 始终走 https。

## 日记本

默认地址是 `http://39.96.68.27:10002`。这是明文 HTTP。安装包允许明文，是因为这个地址是裸 IP，系统的域名配置匹配不了它；局域网自建的更新清单也要用 HTTP。

1. 打开「设置」。未连接时点 **连接日记本**，把网页登录用的那一个口令填进对话框。界面不会把口令留在设置页上。
2. 日记本地址固定是上面的默认地址，不能在设置里改。
3. 点「检查连接」。口令对了会显示「已连接」。也可以点「立即同步」。

连上之后：

- 「记下」会把文字，以及每张图，送到日记本。没网时先记在手机上。
- 保存口令之后，会自动把手机上还没同步的文字和照片用 `POST /api/entries` 记过去。设置里也可以再点「立即同步」。已经记下服务器 id 的文字和照片不会再传。日记本只按它现在的时间归天，所以较早的日子在手机上还留着。
- 记下之后，手机先按语气接一句（累、撑、难过、孤单、紧张、高兴）。平常的记事不接。连上日记本后，卡片上的陪伴和 `mood.line` 优先显示；两句一样只显示一句。
- 它说过的话（回答，或收口时轻问的一句）出现在时间线里。
- 「问它」会调用日记本的提问接口，回答出现在同一天。
- 第一次使用如果日记本还在问引导题，当天页面会一次一题，可以回答或跳过。

口令不要写进仓库，也不要打进 APK。构建脚本不会读取 `JOURNAL_APP_TOKEN`。

## DeepSeek API Key

不要把密钥写进仓库，也不要在设置里填写。

每天的小结优先用日记本当天卡片（`headline`、`companion`、`mood.line`）。设备上如果已经存过密钥，或构建时注入了密钥，生成小结时才会在背后调用 DeepSeek。两种都没有时，日记照常可写，只是不能在手机上另外生成一段摘要。

构建机器上可以提供环境变量，或复制 `.env.example` 为 `.env`：

```bash
DEEPSEEK_API_KEY=sk-...
UPDATE_MANIFEST_URL=https://你的域名/diary/version.json
```

已经保存在手机加密存储里的密钥仍然有效，只是设置里不再显示输入框。没有保存过时，才使用构建时注入的值。

摘要请求发往 `https://api.deepseek.com/chat/completions`，模型 `deepseek-chat`。正文是当天的文字和图片说明。

## vivo 安装提示

针对 vivo S30 Pro Mini 这一类 OriginOS 手机：

1. 用系统浏览器打开安装页，下载 `diary.apk`。
2. 在下载记录或文件管理里点开它。
3. 出现风险提示时选「继续安装」或「仍要安装」。
4. 若无法安装：设置 → 安全（或安全与隐私）→ 更多安全设置 → 安装未知应用，允许刚才那个浏览器或文件管理。
5. 纯净模式、i 管家也可能拦截。纯净模式一般在设置 → 安全里，对这一次选择允许。
6. 安装时确认应用名是「日记」。

菜单名称会随 OriginOS 版本略有差别，找「未知应用 / 外部来源」即可。

## 数据放在哪里

- 日记数据库：应用私有目录里的 `diary.db`（Room / SQLite）
- 图片：应用私有目录 `files/photos/`，保存前会压到较长边 1600 像素的 JPEG
- API Key 和本机接口令牌：加密 SharedPreferences
- 本机接口开关：普通 SharedPreferences。更新地址和日记本地址不再读取以前保存的值，固定用默认配置
- `android:allowBackup` 为 false，系统备份不会把日记拷走
- 卸载或清除数据会删除日记。需要副本时先在设置里导出

一天的分界是北京时间凌晨 4 点，不是零点，也不是手机设置的时区。凌晨 4 点之前写下的话仍算前一天。

## 给以后的 DSH 插件

v1 **不内置、也不连接** DSH Runtime、Foundry 或 TrueNorth Gateway。没有官方的移动端 DSH SDK，所以摘要只直接调用 DeepSeek OpenAPI。不配 DSH，应用也是完整的。

以后如果要接到 DSH，建议的路径是：本应用导出或本机接口 → 你自己的薄后端 → TrueNorth Gateway / DSH。不要把 DSH 运行时放进这个 APK。

### 导出格式 `diary.export.v1`

示例见 `schema/diary.export.v1.sample.json`。设置里的 zip 带有同样的 JSON，且 `photosIncluded` 为 true，图片在 zip 的 `photos/<fileName>`。

字段：

| 字段 | 含义 |
| --- | --- |
| `schema` | 固定为 `diary.export.v1` |
| `exportedAt` | 导出时刻，带 `+08:00` |
| `timezone` | 固定 `Asia/Shanghai` |
| `photosIncluded` | zip 里为 true；本机接口的 JSON 为 false，只有文件名 |
| `app.id` | `app.diary` |
| `days[].day` | `yyyy-MM-dd` |
| `days[].entries[]` | `id`、`body`、`createdAt`、`updatedAt`、`photos` |
| `days[].entries[].photos[]` | `id`、`fileName`、`caption`、`createdAt` |
| `days[].summary` | 没有摘要时为 `null`。有则含 `text`、`generatedAt`、`sourceHash` |

`sourceHash` 是当天正文和图片说明的 SHA-256。日记改过之后，应用会提示摘要可能过时。

### 本机接口

本机接口默认关闭，设置里不再提供打开开关。如果这台手机以前打开过，设置里可以点「关闭本机接口」。开着的时候只在应用进程还活着时监听 `127.0.0.1:18765`。同一台手机上的其他应用也能访问回环地址，所以每个请求都要带令牌。令牌不再显示在设置页上，它仍在这台手机的加密存储里：

```http
Authorization: Bearer <这台手机加密存储里的令牌>
```

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/v1/health` | `{"ok":true,"schema":"diary.export.v1","service":"diary-local","dsh":false}` |
| GET | `/v1/days` | 有日记的日期，新的在前 |
| GET | `/v1/days/2026-10-28` | 这一天的条目和摘要 |
| GET | `/v1/export` | 全量 JSON，不含图片字节 |
| POST | `/v1/entries` | 只写入文字。正文 `{"day":"2026-10-28","body":"..."}`，`day` 可省略，省略则为今天。图片请在应用里添加 |

`dsh` 固定为 false，表示这不是 DSH 服务。

## 目录

```
app/                 Android 工程
keystore/            演示签名
release/             安装页、version.json、构建出的 APK
schema/              导出格式示例
scripts/build-release.sh
.env.example
```

## 隐私

没连接日记本时，日记正文不会自动上传。点「生成今日小结」时，只有这台设备已经有密钥，才会把当天的文字和图片说明发给 DeepSeek。

连接之后，写下的文字和图片会送到日记本服务器（默认 `http://39.96.68.27:10002`）。口令本身仍只留在这台手机的加密存储里。
