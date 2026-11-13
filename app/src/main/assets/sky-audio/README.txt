晴空小队 · 原创合成音乐与音效

全部十个 WAV 均由 tools/synthesize_audio.py 使用 Python 标准库生成。
上述十个WAV不使用外部录音或采样。新增三个M4A来自用户提供的录音，见docs/music-sources.md。
格式：22,050 Hz，16 位，单声道 PCM。

music_0.wav     海风来信
music_1.wav     云上追逐
music_boss.wav  运输舰出发

shot / hit / explosion / pickup / skill / win / fail
分别用于射击、受击、爆炸、补给、技能、过关和本关失败。

运行 python tools/synthesize_audio.py 可以重现原有十个WAV。
