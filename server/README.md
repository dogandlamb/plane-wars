# 晴空小队联机服务端

此程序和 Android 单机共用 `shared` 的战斗核心。手机内嵌服务端与独立服务端使用相同协议；合作模式共享一个世界，比分模式各自运行同种子的世界。每个房间两人，模拟60Hz、快照20Hz。房主点出发后双方先看过场，双方就绪才开始计时。任一玩家暂停会暂停整间房；途中掉线会结束本局并通知另一端。

## 构建与运行

交付目录里的 `sky-room-server.jar` 可以独立运行，不需要 Android SDK。将终端切到 JAR 所在目录，使用 Java 11 或以上版本执行：

```powershell
java -jar sky-room-server.jar --port 8888
```

停止服务时按 Ctrl+C；服务重启会结束当前房间。客户端2.1与本服务端使用协议3，须一起升级。旧2.0客户端连接此服务端会断开，旧端只显示普通断线，暂不显示专门的升级提示。普通模式也支持机库夜班，电量有限。

需要 JDK 11 或更高版本。项目根目录运行：

```powershell
./gradlew.bat :server:jar
java -jar server/build/libs/sky-room-server.jar --port 8888
```

也可在 Windows 使用不需要 Android SDK 或 Gradle 的构建脚本：

```powershell
./server/build-server.ps1
java -jar server/build/standalone/sky-room-server.jar --port 8888
```

在同一 Wi-Fi 或手机热点下，客户端填写运行此程序的电脑局域网地址及端口8888。允许系统防火墙通过该端口。房主创建房间后，把六位房间码给另一位玩家。手机内嵌开房时无需运行本程序。

## 公网部署

异地联机需要持续运行、可从互联网访问的服务器。客户端的服务端地址和端口可配置，不依赖写死在 APK 中的域名。部署前准备服务器、域名和受系统信任的 TLS 证书。服务端房间只保存在内存中，重启会结束现有对局。

TLS模式使用含证书链与私钥的PKCS12密钥库，通过运行环境传入，私钥不放在源码或镜像中：

```powershell
$env:SKY_KEYSTORE_PATH = 'C:\certs\sky-server.p12'
$env:SKY_KEYSTORE_PASSWORD = '<通过你的密钥管理方式提供>'
java -jar server/build/standalone/sky-room-server.jar --port 8888 --tls
```

客户端填写证书对应的域名并开启安全连接。客户端使用系统信任库，检查证书与主机名，不接受忽略证书错误的连接。明文模式用于局域网或已有安全隧道的环境。

支持环境变量 `SKY_PORT`（或 `PORT`）、`SKY_TLS=true`、`SKY_KEYSTORE_PATH`、`SKY_KEYSTORE_PASSWORD`，可选 `SKY_KEYSTORE_TYPE`（默认PKCS12）。服务端默认监听所有网卡。最多32条连接、16个房间。

Docker从项目根目录构建，不需要把Android SDK放进镜像：

```sh
docker build -f server/Dockerfile -t sky-room-server .
docker run --rm -p 8888:8888 sky-room-server
```

公网TLS容器需只读挂载密钥库，再通过运行环境或平台密钥机制提供上述TLS设置。默认非root用户运行，挂载文件必须可被该用户读取。

## 验证范围

`shared/src/test/java/edu/hitsz/sky/net/` 包含协议与真实socket集成测试。双Android模拟器、实体手机与公网部署是不同验证环境，应分别记录结果。未配置公网服务器时，不能把本机AI、回环socket或模拟器到宿主机连接写成公网联机通过。
