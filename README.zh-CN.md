# MC Audio Share（Fabric 26.2，Windows 实验版）

在游戏中按反斜杠（\\）打开界面，选择“全部系统声音”或筛选、切换到一个指定程序，调整分享音量后点击开始。按 F8 可快速开启或停止分享上次使用的音源；首次使用时默认分享除 Minecraft 外的系统声音。两个按键都能在游戏键位设置中修改。分享声音通过 Simple Voice Chat 的 `MergeClientSoundEvent` 混入麦克风帧，玩家说话和分享声音可同时存在；不启动独立进程，也不需要额外服务器插件。关闭设置界面不会停止分享。

仅支持 Windows x64，最低 Windows 10 Build 20348。系统声音采集使用 Windows WASAPI Process Loopback。全部声音模式排除 Minecraft 进程及子进程，避免游戏内回声。指定程序模式包含目标程序及其子进程。macOS 还未实现。

运行需要 Minecraft 26.2、Fabric Loader 0.19.3+、Fabric API 0.156.0+26.2+、Simple Voice Chat API 2.6.20+ 对应的游戏模组。必须让 Simple Voice Chat 正常连接到服务器。此插件不绕过 Simple Voice Chat 的按键说话、静音或服务端规则；按键说话模式下可能需要按住说话键才能发送混音。

常规构建：在仓库根目录运行 `./gradlew.bat -p versions/26.2 build englishJar`。中文版为常规 `remapJar` 产物；英文版为 `englishJar` 产物，均在 `versions/26.2/build/libs/`。仓库已经包含 Windows x64 原生 DLL，普通 Java 构建无需 C++ 编译器。若要重建 DLL，可编译 `native/windows/loopback.cpp` 并输出到 `src/main/resources/native/windows-x64/mc_audio_share.dll`，链接 `mmdevapi`、`ole32`、`uuid`。运行时会从 JAR 自动提取到系统临时目录加载。

v0.1.1 起纯静音帧不会发送到 Simple Voice Chat；界面和 `latest.log` 可查看采集、有声、混音、丢弃帧计数。全部声音模式排除了 Minecraft，所以只播放游戏自己的声音时显示静音是预期现象。

v0.1.2 曾尝试加入 HUD 图标，但方向和贴图取样尺寸有误，且会覆盖 SVC 的 HUD 位置设置。该版本不应继续使用。

v0.1.3 修正了上述方向和贴图裁切错误：不再更改 Simple Voice Chat 图标的位置，共享图标始终在其右侧并对齐上下边；读取 SVC 当前 HUD 位置和缩放来适配用户设置。请不要继续使用 0.1.2。

v0.1.4 使用新的共享/停止共享图标，并提供中文与英文两个独立 JAR。两者的模组 ID 都是 `mc_audio_share`，安装时只能选一个，不能同时放入 `mods` 文件夹。英文版固定使用英文界面文案，并且不包含中文键位翻译；中文版使用中文界面文案。

v0.1.5 将打开界面的默认按键改为反斜杠（\\），增加默认 F8 的快速分享按键。共享状态图标遵循 Simple Voice Chat 的“显示 HUD 图标”和“隐藏图标”设置，无需在本模组界面再设一个显示开关。

本次仅做编译和打包检查，不进入游戏环境测试。当前工作环境的 Gradle Loom 在写入 Minecraft 缓存 JAR 时遇到 Windows `AccessDeniedException`，交付 JAR 是以已缓存的 26.2 依赖直接编译 Java，并封装仓库自带 DLL。请按实际游戏测试结果反馈。
