import test from 'node:test';
import assert from 'node:assert/strict';
import {KINDS,sampleScene} from '../src/main/resources/static/scene.js';
for(const kind of KINDS)test(`${kind}: finite geometry and colour-only inverse`,()=>{
 for(const p of [0,.15,.25,.5,.75,.85,1]) {
  const a=sampleScene(kind,p,true),b=sampleScene(kind,p,false);
  assert.deepEqual(a.figure,b.figure);assert.deepEqual(a.landmark,b.landmark);
  assert.notEqual(a.figureRole,b.figureRole);
  for(const obj of [a.figure,a.landmark])for(const n of Object.values(obj))assert.ok(Number.isFinite(n));
 }
});
test('entry fits the open corridor and ends inside the chamber',()=>{
 const start=sampleScene('enter',0),end=sampleScene('enter',1);assert.ok(start.figure.x+18<430);
 assert.ok(end.figure.x-18>475 && end.figure.x+18<610);assert.ok(end.figure.y+18<=285);
});
test('through reaches the other side rather than stopping inside',()=>assert.ok(sampleScene('through',1).figure.x-18>640));
test('approach differs from actual contact',()=>{assert.ok(sampleScene('approach',1).figure.x+18<510);assert.equal(sampleScene('reach',1).figure.x+18,510);});
test('closed gate prevents entry at every phase',()=>{for(let p=0;p<=1;p+=.01)assert.ok(sampleScene('block',p).figure.x+18<=430);});
test('admit does not pass a closed gate',()=>{for(let p=0;p<=1;p+=.01){const s=sampleScene('admit',p);if(s.figure.x+18>=430)assert.equal(s.gate,1);}});
test('support and carry have exact contact',()=>{assert.equal(sampleScene('on',1).figure.y+18,240);for(const p of [0,.5,1]){const s=sampleScene('carry',p);assert.equal(s.figure.y+18,248);assert.equal(s.figure.x-s.landmark.x,390);}});
test('push contact does not interpenetrate',()=>{for(let p=0;p<=1;p+=.01){const s=sampleScene('push',p);assert.ok(s.figure.x-18>=s.landmark.x+35-1e-8);}});
test('explicit unknown scene rejection',()=>assert.throws(()=>sampleScene('not-a-scene',.5)));
