import {test,expect} from '@playwright/test';
async function slider(locator,value){await locator.evaluate((n,v)=>{n.value=v;n.dispatchEvent(new Event('input',{bubbles:true}));},String(value));}
async function ready(page){await page.goto('/');await expect(page.locator('.scene-button')).toHaveCount(72);await expect(page.locator('#variants .phrase')).toHaveCount(1);await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');}
test('72 scenes, role reversal, animation seeking and desktop screenshot',async({page})=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));await ready(page);
 await expect(page.locator('.phrase')).toHaveValue('{G:Объект} {R:субъект}');
 await expect(page.locator('.preview .G').first()).toHaveText('Объект');
 await slider(page.locator('#timeline'),1000);await expect(page.locator('#stage')).toHaveAttribute('data-progress','1.000');
 await expect(page.locator('#stage')).toHaveAttribute('data-figure-role','G');
 await page.locator('#pair').click();await expect(page.locator('#stage')).toHaveAttribute('data-figure-role','R');await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
 await page.screenshot({path:'test-results/cezmec-desktop.png',fullPage:true});expect(errors).toEqual([]);
});
test('Tatar alternatives, weights, persistence and safe plain-text rendering',async({page})=>{
 await ready(page);await page.locator('#language').selectOption('tt');await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');
 await page.locator('.phrase').fill('{G:объект} {R:субъектка} керә');
 await slider(page.locator('.weight-input'),95);await page.locator('#add-variant').click();
 const second=page.locator('.variant').nth(1);await second.locator('.phrase').fill('{G:объект} <img src=x onerror=alert(1)> {R:субъектка} керә');await slider(second.locator('.weight-input'),35);
 await page.locator('#consent').check();await page.locator('#save').click();await expect(page.locator('#status')).toContainText('Сохранено вариантов: 2');
 await expect(page.locator('#answers .answer')).toHaveCount(2);await expect(page.locator('#answers img')).toHaveCount(0);
 await expect(page.locator('#answers')).toContainText('95.0 / 100');await page.reload();await page.locator('#show-others').click();await expect(page.locator('#answers .answer')).toHaveCount(2);
 await page.screenshot({path:'test-results/cezmec-tatar.png',fullPage:true});
});
test('add and edit RTL language, draft restoration and export',async({page})=>{
 await ready(page);await page.locator('#add-language').click();const dialog=page.locator('#language-form');const code='qaa-x-'+Date.now().toString(36);
 await dialog.locator('[name=code]').fill(code);await dialog.locator('[name=name]').fill('Проверочный RTL');await dialog.locator('[name=nativeName]').fill('لغة');await dialog.locator('[name=greenLabel]').fill('أ');await dialog.locator('[name=redLabel]').fill('ب');await dialog.locator('[name=direction]').selectOption('rtl');await dialog.locator('[type=submit]').click();
 await expect(page.locator('#language')).toHaveValue(code);await expect(page.locator('.phrase')).toHaveAttribute('dir','rtl');
 await page.locator('.phrase').fill('{G:أ} قرب {R:ب}');await page.reload();await expect(page.locator('.phrase')).toHaveValue('{G:أ} قرب {R:ب}');
 await page.locator('#edit-language').click();await dialog.locator('[name=notes]').fill('Проверка редактирования');await dialog.locator('[type=submit]').click();await expect(page.locator('#language-note')).toHaveText('Проверка редактирования');
 const downloadPromise=page.waitForEvent('download');await page.locator('#export').click();const download=await downloadPromise;expect(download.suggestedFilename()).toBe(`cezmec-${code}.json`);
});
test('every stimulus renders, examples are opt-in and mobile layout fits',async({page})=>{
 await ready(page);await expect(page.locator('#example-list .answer')).toHaveCount(0);
 for(const id of await page.locator('.scene-button').evaluateAll(nodes=>nodes.map(n=>n.dataset.scene))){await page.locator(`.scene-button[data-scene="${id}"]`).click();await expect(page.locator('#stage')).toHaveAttribute('data-kind',id.slice(0,-2));expect(await page.locator('#stage').innerHTML()).not.toMatch(/NaN|Infinity/);}
 await page.locator('.scene-button[data-scene="enter-r"]').click();await page.locator('#examples summary').click();await expect(page.locator('#example-list .answer')).toHaveCount(2);
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:'test-results/cezmec-mobile.png',fullPage:true});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(391);
});
