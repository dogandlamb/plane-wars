"""Create the original Sky Squad score and effects (no external samples).

Only Python's standard library is needed. Output is mono 22.05 kHz PCM WAV.
The short looping scores use triangle voices, a soft bass and quiet percussion.
"""
from array import array
from math import sin, pi, tanh
from pathlib import Path
from random import Random
import wave

RATE = 22050
OUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/sky-audio"


def frequency(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def voice(phase):
    return (sin(phase) + sin(phase * 3) / 9 + sin(phase * 5) / 25) / 1.16


def write(name, values):
    samples = array("h", (round(max(-1, min(1, v)) * 32767) for v in values))
    with wave.open(str(OUT / name), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(samples.tobytes())


def score(name, melody, roots, step, energy):
    count = len(melody) * 2
    duration = count * step
    values = [0.] * round(duration * RATE)
    rng = Random(41)
    for note_index in range(count):
        note = melody[note_index % len(melody)]
        root = roots[(note_index // 8) % len(roots)]
        start = round(note_index * step * RATE)
        length = min(round(step * RATE), len(values) - start)
        lead_frequency = frequency(note)
        bass_frequency = frequency(root - 24)
        harmony_note = root + (0, 7, 12, 7)[note_index % 4]
        harmony_frequency = frequency(harmony_note)
        for i in range(length):
            t = i / RATE
            envelope = min(1, t / .012) * max(0, 1 - t / (step * .94)) ** .65
            lead = voice(2 * pi * lead_frequency * t) * envelope * .22
            bass_env = min(1, t / .018) * max(0, 1 - t / step)
            bass = sin(2 * pi * bass_frequency * t) * bass_env * .15
            harmony = sin(2 * pi * harmony_frequency * t) * envelope * .055
            percussion = 0
            if note_index % 2 == 0:
                percussion = sin(2 * pi * (61 * t - 43 * t * t)) * max(0, 1 - t / .11) ** 3 * .13
            if note_index % 4 == 2:
                percussion += (rng.random() * 2 - 1) * max(0, 1 - t / .065) ** 3 * .09 * energy
            values[start + i] = tanh((lead + bass + harmony + percussion) * .88)
    write(name, values)


def effect(name, duration, render):
    values = []
    rng = Random(17)
    for i in range(round(duration * RATE)):
        t = i / RATE
        edge = min(1, t / .004) * min(1, (duration - t) / .012)
        values.append(render(t, rng) * edge)
    write(name, values)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    score("music_0.wav", [72, 76, 79, 76, 74, 76, 72, 69, 67, 72, 74, 76, 79, 76, 74, 72,
                          69, 72, 76, 79, 77, 76, 74, 72, 67, 69, 72, 74, 76, 74, 72, 67],
          [60, 57, 65, 67], .28, .6)
    score("music_1.wav", [74, 78, 81, 78, 76, 78, 74, 71, 69, 74, 78, 81, 83, 81, 78, 76,
                          71, 74, 78, 81, 79, 78, 76, 74, 69, 73, 76, 79, 78, 76, 74, 69],
          [62, 59, 67, 69], .235, .85)
    score("music_boss.wav", [69, 72, 76, 79, 76, 72, 71, 74, 69, 72, 77, 81, 79, 77, 76, 72,
                             67, 71, 74, 79, 77, 74, 71, 67, 64, 68, 71, 76, 74, 71, 68, 64],
          [57, 65, 55, 64], .20, 1.1)
    effect("shot.wav", .09, lambda t, r: sin(2 * pi * (1100 * t - 3800 * t * t)) * (1 - t / .09) * .25)
    effect("hit.wav", .12, lambda t, r: (sin(2 * pi * 180 * t) * .45 + (r.random() - .5) * .2) * (1 - t / .12) * .4)
    effect("explosion.wav", .42, lambda t, r: ((r.random() * 2 - 1) * .35 + sin(2 * pi * (75 * t - 40 * t * t)) * .3) * (1 - t / .42) ** 2)
    effect("pickup.wav", .38, lambda t, r: voice(2 * pi * frequency((76, 79, 84)[min(2, int(t / .12))]) * t) * .25 * (1 - t / .42))
    effect("skill.wav", .48, lambda t, r: (sin(2 * pi * (160 * t + 650 * t * t)) * .4 + sin(2 * pi * 660 * t) * .08) * (1 - t / .48))
    effect("win.wav", 1.05, lambda t, r: voice(2 * pi * frequency((72, 76, 79, 84)[min(3, int(t / .21))]) * t) * .24 * min(1, (1.05 - t) / .22))
    effect("fail.wav", .70, lambda t, r: voice(2 * pi * frequency((72, 69, 67)[min(2, int(t / .22))]) * t) * .23 * (1 - t / .7))
    print(f"Created {len(list(OUT.glob('*.wav')))} original WAV files in {OUT}")


if __name__ == "__main__":
    main()
