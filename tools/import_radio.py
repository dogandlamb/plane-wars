"""Extract user-supplied audio streams; normal builds use the included M4A files."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('travelers', type=Path)
parser.add_argument('hopes', type=Path)
parser.add_argument('undertale', type=Path)
parser.add_argument('--ffmpeg', default='ffmpeg')
args = parser.parse_args()
output = Path(__file__).resolve().parents[1] / 'app/src/main/assets/sky-audio'
for source, name in zip((args.travelers, args.hopes, args.undertale),
                        ('music_travelers_encore', 'music_hopes_and_dreams', 'music_undertale')):
    subprocess.run([args.ffmpeg, '-nostdin', '-n', '-i', str(source), '-map', '0:a:0',
                    '-vn', '-c:a', 'copy', '-map_metadata', '-1', '-movflags', '+faststart',
                    str(output / (name + '.m4a'))], check=True)
