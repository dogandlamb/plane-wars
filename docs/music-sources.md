# 电台音乐来源

原有三首原创循环音乐和七种音效全部保留，由 `tools/synthesize_audio.py` 生成。新增三首来自用户提供的本地视频，按用户要求将第一条音轨直接复制为 AAC/M4A；没有剪辑或重新编码。原视频不包含在工程中，已提取的 M4A 足以构建。

这些录音的权利属于其各自权利人，本工程未附带其再分发许可，也不将其声明为原创或以源码许可授权。上传者应自行确认公开发布这些录音所需的授权。

## Travelers’ Encore

- 来源文件：《星际拓荒_眼之回响》原声带「Travelers'_encore」百万级录音棚试听.mp4
- 音频：`music_travelers_encore.m4a`
- 原视频 SHA-256：`71c70be25ce552ffe91b0983b65b2ee43a0967e7f661cdbc2f1396cfabc0e7a1`
- 音频 SHA-256：`474716c5d72a00afa49087d378199a92c878400e2ac30953d066d8d654976a85`

## Hopes and Dreams（五周年混音）

- 来源文件：236363289-1-208.mp4
- 音频：`music_hopes_and_dreams.m4a`
- 原视频 SHA-256：`67af921530afa3d1392da2b0a9d31b0963870f0bd43d1399411e8346a0435cb2`
- 音频 SHA-256：`4a6a8427aac4e78f5d8bf9a5c81153d9f79127567b19484682fd665ea0674c8c`

## Undertale

- 来源文件：百万级录音棚听《传说之下》主题曲「Undertale」.mp4
- 音频：`music_undertale.m4a`
- 原视频 SHA-256：`cd7496280a5764df953ea7973335352e7adbdb68f38d8bd9e4e33b0fc208df14`
- 音频 SHA-256：`61756aecd375079e176af1590fd31b76788b51306ecdd9f98ddc01cb1af7e675`

可选重新提取命令（需要 FFmpeg；正常构建不需要）：

```text
python tools/import_radio.py <Travelers视频> <Hopes视频> <Undertale视频> --ffmpeg <ffmpeg路径>
```

提取工具默认不覆盖现有音频。
