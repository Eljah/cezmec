"""Check exact catalog coverage, paired geometry, binary hashes and decoded video."""
import hashlib
import json
from pathlib import Path
import subprocess
from scene3d import KINDS, VERSION, FPS, FRAMES, verify

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT/'src/main/resources/static/media'/('v'+VERSION)
MODELS = ROOT/'models/blender'/('v'+VERSION)


def main():
    samples = verify()
    seen = {}
    for kind in KINDS:
        for suffix in ['g','r']:
            identity = kind+'-'+suffix
            folder = MEDIA/identity
            m = json.loads((folder/'manifest.json').read_text())
            assert m['id'] == identity and m['sceneVersion'] == VERSION
            assert m['figureRole'] == ('G' if suffix == 'g' else 'R')
            assert m['landmarkRole'] != m['figureRole']
            for name,digest in m['files'].items():
                assert hashlib.sha256((folder/name).read_bytes()).hexdigest() == digest, (identity,name)
            model = ROOT/m['modelPath']
            assert hashlib.sha256(model.read_bytes()).hexdigest() == m['modelSha256'], identity
            stream = json.loads(subprocess.check_output([
                'ffprobe','-v','error','-select_streams','v:0','-show_streams','-of','json',
                str(folder/'animation.mp4')]))['streams'][0]
            assert int(stream['nb_frames']) == FRAMES, identity
            assert stream['avg_frame_rate'] == str(FPS)+'/1', identity
            assert stream['width'] == 960 and stream['height'] == 540, identity
            assert abs(float(stream['duration'])-FRAMES/FPS) < .02, identity
            subprocess.run(['ffmpeg','-v','error','-i',str(folder/'animation.mp4'),'-f','null','-'],check=True)
            if kind in seen:
                assert m['geometrySha256'] == seen[kind], kind
            seen[kind] = m['geometrySha256']
    assert len(list(MEDIA.glob('*/animation.mp4'))) == 72
    assert len(list(MODELS.glob('*.blend'))) == 72
    report = dict(version=VERSION,clips=72,models=72,geometrySamples=samples,
                  pairedGeometry=True,decodedVideos=True,width=960,height=540,fps=FPS,frames=FRAMES)
    (MODELS/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__ == '__main__':
    main()
