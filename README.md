# 日记

装在自己手机上的日记。可以连上自己的日记本（journal）：记下的话会同步过去，打开某一天能看到陪伴的话和心情。没填口令时，文字和照片仍只留在这台设备里。

一天从北京时间（Asia/Shanghai）凌晨 4 点切开，和日记本一致。需要的时候，仍可以用 DeepSeek 给这一天写一段短摘要；陪伴优先看日记本。

可以写成网页上下载的 APK，不经过应用商店。装好之后，设置里点「检查并更新」会查看 GitHub 上的 `version.json`，有新版本就下载并交给系统安装器。地址也可以改成你自己托管的清单。

## 功能

- 今天：写文字，拍照或从相册加图片，图片可以写一句说明。连上日记本后，这些会送到服务器。
- 陪伴：当天卡片上的 `companion`、心情回声 `mood.line`，以及它说过的话，会显示在这一天里。空的就不显示。可以「问它」。
- 日历：按月查看，点开某一天可以补记。补记先留在手机上，因为日记本只按它现在的时间归天。
- 当日摘要：可选。把这一天的文字和图片说明发给 DeepSeek（`deepseek-chat`）。图片文件不会发给 DeepSeek。
- 设置：日记本地址和口令、DeepSeek API Key、检查并更新、导出全部日记、关于。
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

装好这个 APK 之后，打开「设置 → 更新」，点 **检查并更新**。如果这台手机从未保存过版本清单地址，默认就是：

`https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json`

有新版本时弹出对话框，再点「下载并安装」，交给系统安装器（`ACTION_VIEW`，类型 `application/vnd.android.package-archive`）。启动时也会用同一地址静默看一眼。检查失败不会打断启动，只有手动点按钮才会说明原因。按钮下面会显示一行状态：正在检查、已是最新版本，或正在下载的百分比。

设置里保存过别的地址时，以保存的为准，不会被默认地址盖掉。把地址清空再保存，会回到上面的 GitHub 地址。

以后发新版：提高 `versionCode` 和 `versionName`（`app/build.gradle.kts` 与 `release/version.json` 保持一致），用同一把密钥重新打包，把新的 `release/diary.apk` 和 `release/version.json` 提交到 `main` 的同样两个路径。已经装过的手机不用改设置里的地址。

也可以把整个 `release` 目录放到自己的网站或电脑上，再在设置里改地址：

```bash
cd release
python3 -m http.server 8080
```

自建时把 `apkUrl` 改成那个 `diary.apk` 的真实网址，并在设置里保存 `http://电脑IP:8080/version.json`（或你的 https 地址）。构建机器上如果设置了 `UPDATE_MANIFEST_URL`，它只作为「设置里还没保存过地址」时的后备，用来替换上面的 GitHub 默认地址。

第一次安装更新时，系统会要求允许「日记」安装未知应用。允许后回到应用再点一次「安装」。

局域网可以用 http。DeepSeek 始终走 https。

## 日记本

默认地址是 `http://39.96.68.27:10002`。这是明文 HTTP。安装包允许明文，是因为这个地址是裸 IP，系统的域名配置匹配不了它；局域网自建的更新清单也要用 HTTP。

1. 打开「设置 → 日记本」。地址空着就用上面的默认地址，改过可以保存，清空再保存会回到默认。
2. 把网页登录用的那一个口令填进 **日记本口令**，点「保存口令」。界面不会再把整段口令显示出来。
3. 点「检查连接」。口令对了会说「日记本已连上」。

连上之后：

- 「记下」会把文字，以及每张图，送到日记本。没网时先记在手机上。
- 保存口令之后，会自动把手机上还没同步的文字和照片用 `POST /api/entries` 记过去。设置里也可以再点「把手机上的日记记到日记本」。已经记下服务器 id 的文字和照片不会再传。日记本只按它现在的时间归天，所以较早的日子在手机上还留着。
- 当天如果听得出语气，会显示一句陪伴。卡片上的陪伴和 `mood.line` 都不是空时都会显示；两句一样只显示一句。空的就什么都不加。
- 它说过的话（回答，或收口时轻问的一句）出现在时间线里。
- 「问它」会调用日记本的提问接口，回答出现在同一天。
- 第一次使用如果日记本还在问引导题，当天页面会一次一题，可以回答或跳过。

口令不要写进仓库，也不要打进 APK。构建脚本不会读取 `JOURNAL_APP_TOKEN`。

## DeepSeek API Key

不要把密钥写进仓库。

1. 在 [platform.deepseek.com](https://platform.deepseek.com) 创建密钥。
2. 打开应用「设置」，填到 **DeepSeek API Key**，点保存。密钥进入这台手机的加密存储，界面不会再把整段密钥显示出来。
3. 也可以在构建机器上提供环境变量，或复制 `.env.example` 为 `.env`：

```bash
DEEPSEEK_API_KEY=sk-...
UPDATE_MANIFEST_URL=https://你的域名/diary/version.json
```

应用先用设置里保存的密钥。没有保存过时，才使用构建时注入的值，方便 CI 或演示。两种都没有时，日记照常可写，只是不能生成摘要。

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
- 更新地址、本机接口开关：普通 SharedPreferences
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

设置里打开「本机接口」之后，仅在应用进程还活着的时候监听 `127.0.0.1:18765`。默认关闭。同一台手机上的其他应用也能访问回环地址，所以每个请求都要带令牌：

```http
Authorization: Bearer <设置里显示的令牌>
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

没填日记本口令时，日记正文不会自动上传。只有你点「生成摘要」时，当天的文字和图片说明才会发到 DeepSeek。请使用你自己的 API Key，并遵守 DeepSeek 的条款。

填了口令之后，你写下的文字和图片会送到日记本服务器（默认 `http://39.96.68.27:10002`）。口令本身仍只留在这台手机的加密存储里。
