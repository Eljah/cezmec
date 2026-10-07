import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';

async function ready(page){
  await page.goto('/');
  await expect(page.locator('#stage')).toHaveAttribute('data-renderer','blender-video');
  await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
}
async function seek(page,progress){
  await page.locator('#timeline').evaluate((n,value)=>{n.value=String(value);n.dispatchEvent(new Event('input',{bubbles:true}));},Math.round(progress*1000));
  await expect.poll(()=>page.locator('#stage').evaluate(v=>v.seeking)).toBe(false);
  await expect.poll(()=>page.locator('#stage').evaluate(v=>v.currentTime)).toBeCloseTo(progress*(4-1/24),2);
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
}
async function pixels(page){
  return page.locator('#stage').evaluate(v=>{
    const c=document.createElement('canvas');c.width=v.videoWidth;c.height=v.videoHeight;
    const ctx=c.getContext('2d');ctx.drawImage(v,0,0);const bytes=ctx.getImageData(0,0,c.width,c.height).data;
    let green=0,red=0,hash=2166136261;
    for(let i=0;i<bytes.length;i+=4){
      const [r,g,b]=bytes.slice(i,i+3);
      if(g>r*1.18&&g>b*1.1&&g>35)green++;
      if(r>g*1.25&&r>b*1.15&&r>40)red++;
      hash=Math.imul(hash^r,16777619);hash=Math.imul(hash^g,16777619);hash=Math.imul(hash^b,16777619);
    }
    return {green,red,hash:hash>>>0,width:c.width,height:c.height};
  });
}

test('actual decoded 3D frames, movement, pause, seeking and playback speed',async({page})=>{
  await ready(page);await seek(page,.18);const a=await pixels(page);
  expect(a.width).toBe(960);expect(a.height).toBe(540);expect(a.green).toBeGreaterThan(100);expect(a.red).toBeGreaterThan(1000);
  await page.locator('#play').click();
  await expect.poll(()=>page.locator('#stage').evaluate(v=>v.currentTime)).toBeGreaterThan(.95);
  await page.locator('#play').click();const paused=await page.locator('#stage').evaluate(v=>v.currentTime);
  await page.waitForTimeout(250);expect(await page.locator('#stage').evaluate(v=>v.currentTime)).toBeCloseTo(paused,2);
  await seek(page,.85);expect((await pixels(page)).hash).not.toBe(a.hash);
  await page.locator('#speed').selectOption('1.5');expect(await page.locator('#stage').evaluate(v=>v.playbackRate)).toBe(1.5);
  await page.locator('#loop').uncheck();expect(await page.locator('#stage').evaluate(v=>v.loop)).toBe(false);
});

test('role reversal swaps real rendered colours, not just DOM labels',async({page})=>{
  await ready(page);await seek(page,.3);const a=await pixels(page);
  await page.locator('#pair').click();await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
  await seek(page,.3);const b=await pixels(page);
  expect(b.green).toBeGreaterThan(a.green*2);expect(a.red).toBeGreaterThan(b.red*2);
  const pair=await page.evaluate(async()=>Promise.all(['enter-g','enter-r'].map(async id=>(await fetch(`/media/v2/${id}/manifest.json`)).json())));
  expect(pair[0].geometrySha256).toBe(pair[1].geometrySha256);
});

test('all 72 packaged videos decode and yield genuine browser viewport captures',async({page})=>{
  test.setTimeout(180000);await ready(page);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await fs.mkdir('test-results/scenes',{recursive:true});
  const ids=await page.locator('.scene-button').evaluateAll(nodes=>nodes.map(n=>n.dataset.scene));
  expect(ids).toHaveLength(72);
  for(const id of ids){
    await page.locator(`.scene-button[data-scene="${id}"]`).click();
    await expect(page.locator('#stage')).toHaveAttribute('data-scene',id);
    await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
    expect(await page.locator('#stage').evaluate(v=>v.videoWidth)).toBe(960);
    await seek(page,.42);
    await page.locator('#stage').screenshot({path:`test-results/scenes/${id}.png`});
  }
  expect(errors).toEqual([]);
});

test('desktop and mobile screenshots come from the running Spring Boot site',async({page})=>{
  await ready(page);await page.locator('#research').check();
  await page.locator('.phrase').fill('{G:Объект} входит в {R:субъект}');
  await seek(page,.3);
  await page.screenshot({path:'test-results/cezmec-3d-desktop.png',fullPage:true});
  await page.locator('.scene-card').screenshot({path:'test-results/cezmec-3d-scene.png'});
  await seek(page,.86);await page.locator('.scene-card').screenshot({path:'test-results/cezmec-3d-inside.png'});
  await page.locator('#pair').click();await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
  await seek(page,.3);await page.locator('.scene-card').screenshot({path:'test-results/cezmec-3d-reversed.png'});
  await page.setViewportSize({width:390,height:844});
  await page.locator('.scene-card').screenshot({path:'test-results/cezmec-3d-mobile.png'});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(391);
});

test('failed media does not masquerade as a working animation',async({page})=>{
  await page.route('**/media/v2/**/animation.mp4',route=>route.abort());
  await page.goto('/');await expect(page.locator('#stage')).toHaveAttribute('data-ready','error');
  await page.locator('.phrase').fill('{G:G} {R:R}');await page.locator('#consent').check();
  await page.locator('#save').click();await expect(page.locator('#status')).toContainText('Дождитесь загрузки 3D-ролика');
});
