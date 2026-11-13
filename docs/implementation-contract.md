# 晴空小队：实施约定

目标是可安装、可完整游玩的 Android 飞机大战。保留旧版拖动与自动射击、五类敌机、五种掉落、四技能、Boss 二阶段、暂停/重开、音乐电台、音量设置与分难度排行榜，新增五关剧情战役、开发者选关和机库夜班番外。旧版无尽玩法另留入口。

用户已确认：明亮圆润的玩具街机画风，竖屏；三个难度共用五关但分别解锁，前五关约一分钟、夜班保留四分钟；失败重打当前关，进度自动保存；双人合作和比分对战共存；局域网和异地联机；开发者房间统一开启无限生命与技能、不计普通榜；普通模式也能直接进入第六关，拿起/收起平板查看三个监控，另有、门、灯、电量与非恐怖机器人。每次进入关卡前播放可跳过的动画，能够回看。

轻松冒险剧情：暴风雨打乱岛屿航线，飞行员小夏与维修机器人豆丁找回五枚航标。海邮港、风车原野、积雨云海、星灯工厂、中央运输舰各有新敌人和机制。最终修复失控运输舰，第六关在修复后的机库值夜班。合作阵亡采用靠近救援、全员阵亡当前关失败。

公网服务未配置。实现可独立部署的服务端和可配置公网/TLS连接。不得将本机AI当作双端测试，也不得将双模拟器本地链路当作真实公网或实体手机实测。持续在线公网部署需要可用服务器，尚为外部待办，不影响其余代码实施。

## 共同边界

`shared` 是不依赖 Android 的 Java 11 核心。`core` 管战斗、时间、五关、番外与状态；`net` 管房间、协议和权威服务器。Android UI 只消费脱离引擎的 GameState 快照。固定逻辑世界 480×800；画面等比缩放，控制区不遮挡战场。逻辑60Hz、渲染目标60Hz、网络20Hz。性能最终以记录的实测为准。

### 核心 API

`BattleEngine(int stage, Difficulty difficulty, Mode mode, boolean developer, long seed)`；`void accept(InputCommand)`；`void step(float seconds)`（实际固定1/60）；`GameState snapshot()`（深拷贝）。COOP两名玩家(id0/1)，其他模式引擎一人(id0)。VERSUS由服务器运行两份同种子引擎。Stage6运行NightWatch，仍由同一引擎入口调用。阶段从RUNNING到VICTORY/DEFEAT，胜利不循环刷Boss。开发者模式不自动击杀敌人或跳过关卡。

`StageCatalog.get(int)` 返回含 `number,title,subtitle,duration,color` 的 `StageCatalog.Stage`。`StageCatalog.all()`返回五关，番外可单取6。所有难度使用同一数据，差异来自Difficulty参数。

GameState、InputCommand及枚举定义在shared/core，Android与服务端一起升级。2.1协议版本为3，增加TABLET输入与tabletOpen快照字段；旧客户端连接会明确拒绝。快照之外不泄露核心可变对象。效果age从0递增，以radius、owner、kind供绘制。

### 网络 API

包`edu.hitsz.sky.net`：

- `RoomOptions(int stage,Difficulty difficulty,Mode mode,boolean developer,long seed)`，对应 public final字段；mode只能COOP或VERSUS。
- `Lobby` public字段 `String code; RoomOptions options; int yourId; boolean host; List<String> players`。
- `NetClient.Listener`：`onLobby(Lobby)`、`onStory(RoomOptions)`、`onFrame(GameState state,GameState rival)`、`onError(String)`、`onDisconnected(String)`。回调在网络线程，Android调用方切主线程。
- `NetClient(Listener)`；`connect(String host,int port,boolean tls,String name)`（阻塞、调用方放后台）；`create(RoomOptions)`、`join(String code)`、`start()`、`ready()`、`send(InputCommand)`、`close()`。除connect外的方法不在UI线程阻塞，使用单写线程。大厅首次连接通知的Lobby code为空。
- `RoomServer(int port)`、`start()`、`port()`、`close()`。支持Android内嵌与headless Java。房主start广播onStory并冻结模拟，双方ready后才启动；途中掉线明确暂停/结束，不得静默继续。退出释放连接。房间最多2人，房间码创建/加入、双方配置完全一致。
- 网络协议限制消息长度、版本和字段，使用显式二进制编码，不使用Java对象反序列化。网络输入必须绑定连接玩家身份。TLS校验主机名。单一权威固定步长引擎，快照只读。

### Android 组件 API

包`edu.hitsz.sky.android`：

- `GamePreferences(Context)`：`boolean developer()/muted()/vibration()/effects()`对应`setDeveloper/setMuted/setVibration/setEffects(boolean)`；`float bgmVolume()/sfxVolume()`对应set方法；`Difficulty difficulty()/setDifficulty(Difficulty)`；`int unlocked(Difficulty)`；`void complete(int stage,Difficulty,boolean developer)`；`void record(String name,int score,int stage,Difficulty,boolean developer,Mode)`；`List<GamePreferences.Record> records(Difficulty,boolean developer)`（Record字段name,score,stage,date,mode）；`void deleteRecord(Difficulty,boolean developer,int index)`。
- `MenuScreens(Context,GamePreferences,Actions)`：`View home()`、`View campaign()`、`View settings()`、`View scores()`。`Actions`含`showHome/showCampaign/showOnline/showSettings/showScores/showRadio()`、`startSolo(int,Difficulty,boolean endless)`、`replayStory(int)`；难度状态存GamePreferences。
- `StoryView(Context,int stage,Runnable finished)`：动画每关不同、3段短对白、自动推进/触摸推进/跳过、onDetached取消动画；不要使用静态图冒充动画。
- `SoundRack(Context,GamePreferences)`：`startMusic(boolean boss)`、`pauseMusic()`、`resumeMusic()`、`play(int effect)`、`nextTrack()`、`previousTrack()`、`String trackName()`、`close()`；音效编号SHOT=0,HIT=1,EXPLOSION=2,PICKUP=3,SKILL=4,WIN=5,FAIL=6。原有三首背景音乐与七种音效由本项目合成原创，另加入用户提供的三首录音，支持独立音量与静音，保留电台切曲。
- `GameSurface(Context,Callback)`（仅渲染和输入）：`setFrame(GameState,GameState rival)`、`setLocalPlayer(int)`、`setLowEffects(boolean)`、`setConnectionLabel(String)`、`dispose()`；`Callback`含`onInput(InputCommand)`、`onPause()`、`onRadio()`。支持多点触摸拖动+四技能+炸弹；夜班监控/左右门灯；Choreographer目标60Hz；HUD中文短文案，胜败由Activity处理。

## 验证与完成条件

纯JVM测试覆盖：五关共用数据、各难度、Boss胜利、火力/冰冻/炸弹实际效果、四技能、开发者无限、合作救援、夜班胜败、电量、输入边界。网络用真实socket验证建房/加入/动画ready屏障/双端移动技能/阶段与难度一致/退出掉线。Android用两个独立模拟器安装同一APK实测，日志与状态证据不替代玩法观察。性能记录帧时间、内存、稳定性及未能验证的设备范围。

保留原工程不动，所有修改仅在本工作区副本。最终交付签名APK、可重现源码、可部署服务端、测试报告及素材说明；不交付签名密码和私钥。
