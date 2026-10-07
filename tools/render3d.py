"""Run with Blender, not system Python. See docs/3D-PIPELINE.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

import bpy
from mathutils import Vector
sys.path.insert(0, str(Path(__file__).resolve().parent))
from scene3d import KINDS, CAVITY, STATIC, VERSION, FPS, FRAMES, RADIUS, objects, sample, pose, camera, verify

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT/'src/main/resources/static/media'/('v'+VERSION)
MODELS = ROOT/'models/blender'/('v'+VERSION)
COLORS = {'G': '23945b', 'R': 'd95750', 'floor': 'dfe5e6', 'link': '69767b'}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def linear(n):
    return n/12.92 if n <= .04045 else ((n+.055)/1.055)**2.4


def material(name):
    mat = bpy.data.materials.new(name)
    h = COLORS[name]
    rgba = tuple(linear(int(h[i:i+2],16)/255.) for i in (0,2,4))+(1.,)
    mat.diffuse_color = rgba
    mat.use_nodes = True
    node = mat.node_tree.nodes.get('Principled BSDF')
    node.inputs['Base Color'].default_value = rgba
    node.inputs['Roughness'].default_value = .43
    return mat


def mesh(spec, mat):
    shape = spec['shape']
    if shape == 'box':
        bpy.ops.mesh.primitive_cube_add(size=1, location=spec['at'])
        obj = bpy.context.object
        obj.dimensions = spec['size']
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        bevel = obj.modifiers.new('Rounded physical edges', 'BEVEL')
        bevel.width = min(.035, min(spec['size'])*.08)
        bevel.segments = 3
        obj.modifiers.new('Face normals', 'WEIGHTED_NORMAL')
    elif shape == 'sphere':
        bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16,
                                           radius=spec['radius'], location=spec['at'])
        obj = bpy.context.object
        for poly in obj.data.polygons:
            poly.use_smooth = True
    else:
        bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=spec['radius'],
                                           depth=spec['depth'], location=spec['at'])
        obj = bpy.context.object
        bevel = obj.modifiers.new('Rounded rims', 'BEVEL')
        bevel.width = .03
        bevel.segments = 3
        obj.modifiers.new('Face normals', 'WEIGHTED_NORMAL')
    obj.name = spec['name']
    obj.data.materials.append(mat)
    obj['semantic_role'] = spec.get('role', 'neutral')
    obj['motion_track'] = spec.get('track', 'fixed')
    return obj


def configure(kind):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ['BLENDER_WORKBENCH', 'BLENDER_WORKBENCH_NEXT']:
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    else:
        raise RuntimeError('This Blender does not provide the Workbench renderer')
    scene.render.resolution_x = 960
    scene.render.resolution_y = 540
    scene.render.resolution_percentage = 100
    scene.render.fps = FPS
    scene.frame_start = 1
    scene.frame_end = FRAMES
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.image_settings.compression = 15
    scene.render.film_transparent = False
    scene.view_settings.view_transform = 'Standard'
    scene.display.render_aa = '8'
    shade = scene.display.shading
    shade.light = 'STUDIO'
    shade.color_type = 'MATERIAL'
    shade.show_shadows = True
    shade.show_cavity = True
    shade.cavity_type = 'BOTH'
    shade.curvature_ridge_factor = 1.3
    shade.cavity_ridge_factor = 1.1
    shade.cavity_valley_factor = 1.
    shade.show_specular_highlight = True
    shade.show_object_outline = False
    shade.background_type = 'VIEWPORT'
    shade.background_color = (.86,.9,.92)
    scene.display.light_direction = (-.4,-.5,1.)
    mats = {key: material(key) for key in COLORS}
    mesh(dict(name='Neutral ground',shape='box',at=(0.,0.,-.08),size=(200.,200.,.16)), mats['floor'])
    c = camera(kind)
    bpy.ops.object.camera_add(location=c['location'])
    cam = bpy.context.object
    cam.name = 'Fixed research camera'
    cam.rotation_euler = (Vector(c['target'])-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = c['scale']
    scene.camera = cam
    return scene, mats


def bake(kind, suffix):
    scene, mats = configure(kind)
    identity = kind+'-'+suffix
    focal = 'G' if suffix == 'g' else 'R'
    landmark = 'R' if suffix == 'g' else 'G'
    specs = objects(kind)
    nodes = []
    for spec in specs:
        role = 'link' if spec['name'] == 'taut-link' else landmark
        obj = mesh(spec,mats[role])
        obj['referent'] = role if role != 'link' else 'unnamed connector'
        nodes.append((spec,obj))
    ball = mesh(dict(name='Sphere',shape='sphere',at=(0.,0.,RADIUS),radius=RADIUS,
                     role='figure',track='figure'),mats[focal])
    ball['referent'] = focal
    track = []
    for i in range(FRAMES):
        state = sample(kind,i/(FRAMES-1))
        track.append(state)
        ball.location = state['figure']
        ball.keyframe_insert(data_path='location',frame=i+1)
        for spec,obj in nodes:
            if spec['track'] != 'fixed':
                obj.location = pose(spec,state)
                obj.keyframe_insert(data_path='location',frame=i+1)
    # Frame sampling is the definition; no Bezier overshoot between baked keys.
    for action in bpy.data.actions:
        if hasattr(action,'fcurves'):
            for curve in action.fcurves:
                for key in curve.keyframe_points:
                    key.interpolation = 'LINEAR'
    scene['stimulus_id'] = identity
    scene['stimulus_version'] = VERSION
    scene['geometry_definition'] = 'tools/scene3d.py'
    scene['sectioned'] = kind in CAVITY or kind == 'through'
    scene['motion_model'] = 'kinematic, not a rigid-body dynamics simulation'
    geometry = {'objects':specs,'states':track,'camera':camera(kind)}
    geometry_hash = hashlib.sha256(json.dumps(geometry,sort_keys=True).encode()).hexdigest()
    model = MODELS/(identity+'.blend')
    output = MEDIA/identity
    output.mkdir(parents=True,exist_ok=True)
    MODELS.mkdir(parents=True,exist_ok=True)
    scene.frame_set(1)
    scene.render.filepath = '//renders/'+identity+'/frame_'
    bpy.ops.wm.save_as_mainfile(filepath=str(model),compress=True)
    with tempfile.TemporaryDirectory(prefix='cezmec-') as temp:
        scene.render.filepath = str(Path(temp)/'frame_')
        if kind in STATIC:
            scene.render.filepath = str(Path(temp)/'frame_0001.png')
            bpy.ops.render.render(write_still=True)
            for index in [40,FRAMES]:
                shutil.copyfile(Path(temp)/'frame_0001.png',Path(temp)/('frame_%04d.png'%index))
            input_args = ['-loop','1','-framerate',str(FPS),'-i',str(Path(temp)/'frame_0001.png')]
        else:
            bpy.ops.render.render(animation=True)
            input_args = ['-framerate',str(FPS),'-start_number','1','-i',str(Path(temp)/'frame_%04d.png')]
        subprocess.run(['ffmpeg','-y','-loglevel','error',*input_args,'-frames:v',str(FRAMES),
                        '-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p',
                        '-movflags','+faststart','-g','12','-an',str(output/'animation.mp4')],check=True)
        subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(Path(temp)/'frame_0040.png'),
                        '-frames:v','1','-q:v','2',str(output/'poster.jpg')],check=True)
        shutil.copyfile(Path(temp)/'frame_0001.png',output/'start.png')
        shutil.copyfile(Path(temp)/('frame_%04d.png'%FRAMES),output/'end.png')
    meta = dict(id=identity,kind=kind,sceneVersion=VERSION,figureRole=focal,landmarkRole=landmark,
                fps=FPS,frames=FRAMES,duration=FRAMES/FPS,width=960,height=540,
                sectioned=scene['sectioned'],camera=camera(kind),geometrySha256=geometry_hash,
                sourceCommit=os.environ.get('GITHUB_SHA','local'),blenderVersion=bpy.app.version_string,
                renderer=scene.render.engine,modelPath=str(model.relative_to(ROOT)),modelSha256=sha(model),
                files={name:sha(output/name) for name in ['animation.mp4','poster.jpg','start.png','end.png']})
    (output/'manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('BAKED',identity,geometry_hash,flush=True)


def main():
    args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument('--shard',type=int,default=0)
    parser.add_argument('--shards',type=int,default=1)
    config = parser.parse_args(args)
    assert 0 <= config.shard < config.shards
    print('Validated geometry samples:',verify(),flush=True)
    for index,kind in enumerate(KINDS):
        if index % config.shards == config.shard:
            for suffix in ['g','r']:
                bake(kind,suffix)


if __name__ == '__main__':
    main()
