"""Cezmec v2: deterministic, unit-scale 3D stimulus definitions (Z is up).

No rendering library is required to sample or test the geometry. Blender consumes
these same objects and trajectories; there is no separate 2D surrogate model.
All coordinates are metres for reproducibility, not claims about stimulus scale.
"""
from math import sin, cos, pi

VERSION = '2'
FPS = 24
FRAMES = 96
RADIUS = 0.45
KINDS = ('enter exit inside through approach reach depart near on onto off above '
         'overpass under underpass front behind around along contact attach detach '
         'among into-mass out-of-mass between through-gap rise fall admit block push '
         'pull carry cover uncover').split()
STATIC = {'inside', 'near', 'on', 'above', 'under', 'front', 'behind', 'contact', 'among', 'between'}
CAVITY = {'enter', 'exit', 'inside', 'admit', 'block'}


def clamp(x):
    return max(0., min(1., x))


def mix(a, b, u):
    return a + (b - a) * u


def sample(kind, p):
    if kind not in KINDS:
        raise ValueError('Unknown stimulus: ' + kind)
    if not isinstance(p, (int, float)) or not -float('inf') < p < float('inf'):
        raise ValueError('Progress must be finite')
    u = clamp((p - .12) / .76)
    f = [-3.2, 0., RADIUS]
    offset = [0., 0., 0.]
    gate = 1.
    if kind in CAVITY:
        f[0] = {'enter': mix(-3.2, 2.1, u), 'exit': mix(2.1, -3.2, u),
                'inside': 2.1, 'admit': mix(-3.2, 2.1, clamp((u-.35)/.65)),
                'block': mix(-3.2, -.28, u)}[kind]
        gate = clamp(u/.28) if kind == 'admit' else (0. if kind == 'block' else 1.)
    elif kind == 'through':
        f[0] = mix(-3.2, 4.3, u)
    elif kind in {'approach', 'reach', 'depart', 'near'}:
        f[0] = {'approach': mix(-3.2, .2, u), 'reach': mix(-3.2, .89, u),
                'depart': mix(.2, -3.2, u), 'near': .2}[kind]
    elif kind in {'on', 'onto', 'off', 'above', 'overpass'}:
        if kind in {'on', 'above'}:
            f = [1.6, 0., 1.45 if kind == 'on' else 2.6]
        elif kind == 'overpass':
            f = [mix(-3.2, 4.4, u), 0., .45 + 2.9*sin(pi*u)]
        else:
            t = 1-u if kind == 'off' else u
            f = [mix(-3.2, 1.6, t), 0., mix(.45, 1.45, t) + 1.6*sin(pi*t)]
    elif kind in {'under', 'underpass'}:
        f[0] = 1.5 if kind == 'under' else mix(-3.2, 4.3, u)
    elif kind in {'front', 'behind'}:
        f = [1., -1.2 if kind == 'front' else 1.2, .45]
    elif kind == 'around':
        f = [1 + 1.9*cos(pi+2*pi*u), 1.9*sin(pi+2*pi*u), .45]
    elif kind == 'along':
        f[0] = mix(-3.2, 4.3, u)
    elif kind in {'contact', 'attach', 'detach'}:
        f = [{'contact': .89, 'attach': mix(-3.2, .89, u),
              'detach': mix(.89, -3.2, u)}[kind], 0., 1.1]
    elif kind in {'among', 'into-mass', 'out-of-mass'}:
        f[0] = {'among': 1.6, 'into-mass': mix(-3.2, 1.6, u),
                'out-of-mass': mix(1.6, -3.2, u)}[kind]
    elif kind in {'between', 'through-gap'}:
        f[0] = 1.6 if kind == 'between' else mix(-3.2, 4.3, u)
    elif kind in {'rise', 'fall'}:
        f = [-.7, 0., mix(.45, 2.9, u if kind == 'rise' else 1-u)]
    elif kind == 'push':
        # The block closes the initial gap; only after contact does the ball move.
        movement = 3.0*clamp((u-.18)/.82)
        offset[0] = -2.4 + .4*clamp(u/.18) + movement
        f = [-1.0 + movement, 0., .45]
    elif kind == 'pull':
        offset[0] = .4 + 2.7*u
        f = [offset[0]-2.2, 0., .45]
    elif kind == 'carry':
        offset[0] = mix(-2.4, 2.7, u)
        f = [offset[0], 0., .9]
    elif kind in {'cover', 'uncover'}:
        offset[0] = mix(-3.4, 0., u if kind == 'cover' else 1-u)
        f = [1.5, 0., .45]
    return {'figure': f, 'offset': offset, 'gate': gate, 'u': u}


def objects(kind):
    """Meshes are local to their motion track. A compound landmark shares one role."""
    if kind not in KINDS:
        raise ValueError(kind)
    out = []
    def box(name, at, size, track='fixed', solid=True):
        out.append(dict(name=name, shape='box', at=at, size=size, track=track,
                        solid=solid, role='landmark'))
    def sphere(name, at, radius):
        out.append(dict(name=name, shape='sphere', at=at, radius=radius,
                        track='fixed', solid=True, role='landmark'))
    if kind in CAVITY:
        # A genuine doorway in the X-facing wall. Roof / near wall are sectioned,
        # NOT opaque surfaces through which the sphere is allowed to pass.
        box('door-jamb-near', (.4, -1.13, 1.3), (.28, .66, 2.6))
        box('door-jamb-far', (.4, 1.13, 1.3), (.28, .66, 2.6))
        box('door-lintel', (.4, 0., 2.3), (.28, 1.6, .6))
        box('far-wall', (2., 1.55, 1.3), (3.5, .18, 2.6))
        box('back-wall', (3.66, 0., 1.3), (.18, 3.28, 2.6))
        box('near-wall-section', (2., -1.55, .15), (3.5, .18, .3))
        if kind in {'admit', 'block'}:
            box('sliding-gate', (.25, 0., 1.), (.16, 1.6, 2.), 'gate')
    elif kind == 'through':
        box('tunnel-far', (1.4, 1.25, 1.2), (3.2, .3, 2.4))
        box('tunnel-near-section', (1.4, -1.25, .16), (3.2, .3, .32))
        # End portal beams make entry and exit physically distinct.
        for x in [-.2, 3.]:
            box('portal-'+str(x), (x, 0., 2.25), (.22, 2.8, .3))
            for y in [-1.25, 1.25]:
                box('post-'+str((x,y)), (x,y,1.2), (.22,.3,2.4))
    elif kind in {'approach','reach','depart','near','rise','fall','contact','attach','detach'}:
        box('wall', (1.5, 0., 1.2), (.32, 3.1, 2.4))
    elif kind in {'on','onto','off','above','overpass'}:
        box('platform', (1.6, 0., .5), (2.8, 2., 1.))
    elif kind in {'under','underpass'}:
        box('bridge-deck', (1.5, 0., 2.3), (3., 3.2, .3))
        for x in [.2, 2.8]:
            for y in [-1.35, 1.35]:
                box('bridge-leg-'+str((x,y)), (x,y,1.075), (.25,.25,2.15))
    elif kind in {'front','behind'}:
        box('screen', (1., 0., .8), (1.25, .35, 1.6))
    elif kind == 'around':
        out.append(dict(name='column',shape='cylinder',at=(1.,0.,.85),radius=.65,
                        depth=1.7,track='fixed',solid=True,role='landmark'))
    elif kind == 'along':
        box('long-wall', (1., 1.3, 1.1), (5.5, .25, 2.2))
    elif kind in {'among','into-mass','out-of-mass'}:
        # Granular aggregate: no intersections, with a narrow central passage.
        for ix,x in enumerate([.2,.85,1.5,2.15,2.8]):
            for iy,y in enumerate([-.95,.95]):
                for iz,z in enumerate([.22,.67,1.12]):
                    sphere('grain-%s-%s-%s'%(ix,iy,iz), (x,y,z), .215)
        for x in [.35,1.2,2.05,2.7]:
            for y in [-1.5,1.5]:
                sphere('outer-grain-'+str((x,y)), (x,y,.25), .25)
    elif kind in {'between','through-gap'}:
        for y in [-1.3,1.3]:
            box('upright-'+str(y), (1.6,y,1.1), (.7,.55,2.2))
            box('upper-rail-'+str(y), (2.2,y,2.3), (1.9,.25,.2))
        box('rear-connection', (3.,0.,2.3), (.25,2.85,.2))
    elif kind in {'push','pull'}:
        box('moving-block', (0.,0.,.55), (1.1,1.1,1.1), 'landmark')
        if kind == 'pull':
            box('taut-link', (-1.15,0.,.45), (1.2,.04,.04), 'landmark', False)
    elif kind == 'carry':
        box('carrier', (0.,0.,.3), (2.,1.5,.3), 'landmark')
        for x in [-.7,.7]:
            for y in [-.5,.5]:
                box('carrier-foot-'+str((x,y)), (x,y,.075), (.15,.15,.15), 'landmark')
    elif kind in {'cover','uncover'}:
        box('cover-panel', (1.5,0.,1.1), (2.2,2.2,.2), 'landmark')
    return out


def pose(obj, s):
    xyz = list(obj['at'])
    if obj['track'] == 'landmark':
        xyz = [a+b for a,b in zip(xyz,s['offset'])]
    elif obj['track'] == 'gate':
        xyz[2] += 2.7*s['gate']
    return xyz


def camera(kind):
    if kind in {'cover','uncover'}:
        return {'location': (.5,-3.,12.), 'target': (.5,0.,.4), 'scale': 9.2}
    return {'location': (-7.8,-12.5,8.8), 'target': (.5,0.,1.), 'scale': 10.5}


def collision(obj, xyz, ball):
    """Strict overlap only: intended tangential contact is allowed."""
    if not obj['solid']:
        return False
    if obj['shape'] == 'box':
        d2 = sum(max(abs(a-b)-size/2,0.)**2 for a,b,size in zip(ball,xyz,obj['size']))
        return d2 < (RADIUS-1e-6)**2
    if obj['shape'] == 'sphere':
        return sum((a-b)**2 for a,b in zip(ball,xyz)) < (RADIUS+obj['radius']-1e-6)**2
    # Finite upright cylinder, using radial / vertical distance to the solid.
    radial = max(((ball[0]-xyz[0])**2+(ball[1]-xyz[1])**2)**.5-obj['radius'],0.)
    height = max(abs(ball[2]-xyz[2])-obj['depth']/2,0.)
    return radial**2+height**2 < (RADIUS-1e-6)**2


def verify():
    samples = 0
    for kind in KINDS:
        meshes = objects(kind)
        assert meshes, kind
        assert len({o['name'] for o in meshes}) == len(meshes), kind
        for i in range(301):
            s = sample(kind, i/300)
            assert s['figure'][2] >= RADIUS-1e-6, (kind,'below ground')
            for obj in meshes:
                assert not collision(obj,pose(obj,s),s['figure']), (kind,i,obj['name'],s['figure'])
            samples += 1
    return samples


if __name__ == '__main__':
    print('3D geometry verified: %d kinds; %d sampled states.' % (len(KINDS), verify()))
