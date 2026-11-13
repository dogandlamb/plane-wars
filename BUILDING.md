# 构建这份工程

需要 JDK 21、Android SDK Platform 36 和 Build Tools 36.0.0。最低运行版本为 Android 8.0。Gradle Wrapper 固定为 8.14，Android 插件固定为 8.11.0；首次构建需要联网下载依赖，之后可使用 `--offline`。

设置 `JAVA_HOME` 和 `ANDROID_HOME`；也可在本机创建 `local.properties` 设置 `sdk.dir`，不要提交它。

```powershell
./gradlew.bat :shared:check :app:check :app:assembleDebug :app:assembleRelease :app:lintRelease :server:jar
./tools/verify.ps1 -LongNetwork
```

macOS/Linux 将 `./gradlew.bat` 换成 `sh ./gradlew`。PowerShell 验证脚本还会临时生成证书，验证真实 TLS 连接及错误主机名拒绝；Gradle 的 check 已包含核心、真实 socket、客户端适配器检查。

- 调试安装包：`app/build/outputs/apk/debug/app-debug.apk`，使用本机自动生成的调试签名。
- 未提供私钥时，发布输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- 正式成品：`release/SkySquad-2.1.apk`，已使用原项目签名；SHA-256 见同目录。clone 后即可取得这一份 APK。
- 独立服务端：`server/build/libs/sky-room-server.jar`。不需要 Android 的构建方式见 [服务端说明](server/README.md)。

重新签署发布包时，提供 `AIRCRAFT_KEYSTORE_PATH`、`AIRCRAFT_STORE_PASSWORD`、`AIRCRAFT_KEY_ALIAS`、`AIRCRAFT_KEY_PASSWORD` 四个环境变量。四项齐备才启用发布签名，私钥和密码不在仓库。源码可以重建同样的程序；相同发布签名需要原私钥，构建时间、工具链或签名不同也可能改变整个 APK 的字节哈希。

音频和矢量绘图代码已在工程中。正常构建不需要原视频、FFmpeg、Python、原工程或开发者机器的绝对路径。`tools/synthesize_audio.py` 可再生成原有音乐和音效；`tools/import_radio.py` 仅供从自己的视频重新提取新增录音。

`tools/package_source.py` 按白名单导出工程，包括一个正式 APK，不包含 Gradle/Android 缓存、build 目录、测试截图、日志、Git 历史、机器配置和签名文件。`SOURCE-MANIFEST.json` 逐文件记录实际交付内容，不能仅用 Git 提交号代表包含未提交修改的源码。
