"""One-time, assertion-checked upgrade. Actions commits the resulting real files.
Safe to run repeatedly; does not touch database files or rewrite existing answers.
"""
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[1]

def patch(path,old,new):
    file=ROOT/path
    text=file.read_text(encoding='utf-8')
    if new in text:
        return
    if text.count(old)!=1:
        raise RuntimeError('Expected one upgrade target in '+path+': '+old[:80])
    file.write_text(text.replace(old,new),encoding='utf-8')


def append(path,content):
    file=ROOT/path
    text=file.read_text(encoding='utf-8')
    if content not in text:
        file.write_text(text+'\n'+content,encoding='utf-8')


S='src/main/resources/static/'
J='src/main/java/org/cezmec/'
T='src/test/java/org/cezmec/CorpusIntegrationTest.java'
shutil.copyfile(ROOT/'tools/ui/player3d.js',ROOT/S/'player3d.js')
shutil.copyfile(ROOT/'tools/ui/video.spec.js',ROOT/'tests/e2e/video.spec.js')
# Keep the original SVG module as the executable definition of historical v1.
patch(S+'app.js',"import {renderScene} from './scene.js';","import {Player3D} from './player3d.js';\nlet player;")
patch(S+'app.js',"'cezmec:draft:v1:'+state.scene.id+':'+state.lang.code", "'cezmec:draft:v2:'+state.scene.id+':'+state.scene.version+':'+state.lang.code")
patch(S+'app.js',"'cezmec:seen:v1:'+state.scene.id+':'+state.lang.code", "'cezmec:seen:v2:'+state.scene.id+':'+state.scene.version+':'+state.lang.code")
patch(S+'app.js',
      'restoreDraft();renderCatalog();renderResearch();state.progress=0;state.last=0;renderFrame();',
      "restoreDraft();renderCatalog();renderResearch();state.progress=0;state.last=0;\n  player.configure($('speed').value,$('loop').checked);player.setScene(state.scene,state.playing);\n  $('clip-link').href=player.url;$('cutaway-note').hidden=!['enter','exit','inside','through','admit','block'].includes(state.scene.kind);\n  updatePlayback();")
file=ROOT/S/'app.js'
text=file.read_text()
start=text.index('function renderFrame()') if 'function renderFrame()' in text else -1
if start>=0:
    end=text.index('async function refreshStats()',start)
    text=text[:start]+'''function updatePlayback(){
  if(!player)return;
  state.progress=player.progress;state.playing=player.playing;
  $('timeline').value=String(Math.round(state.progress*1000));
  $('play').textContent=state.playing?'Пауза':'Воспроизвести';
  $('play').setAttribute('aria-label',state.playing?'Пауза анимации':'Воспроизвести анимацию');
  $('clip-time').textContent=`${player.video.currentTime.toFixed(2)} / 4,00 с`;
}
function tick(){updatePlayback();requestAnimationFrame(tick);}
'''+text[end:]
    file.write_text(text)
patch(S+'app.js',
      "$('pair').addEventListener('click',()=>choose(state.scene.pairId,state.lang.code));$('play').addEventListener('click',()=>{state.playing=!state.playing;if(state.progress===1)state.progress=0;renderFrame();});$('replay').addEventListener('click',()=>{state.progress=0;renderFrame();});$('timeline').addEventListener('input',()=>{state.playing=false;state.progress=Number($('timeline').value)/1000;renderFrame();});",
      "$('pair').addEventListener('click',()=>choose(state.scene.pairId,state.lang.code));$('play').addEventListener('click',()=>{if(player.playing)player.pause();else player.play();updatePlayback();});$('replay').addEventListener('click',()=>{player.seek(0);updatePlayback();});$('timeline').addEventListener('input',()=>{player.pause();player.seek(Number($('timeline').value)/1000);updatePlayback();});\n  for(const id of ['speed','loop'])$(id).addEventListener('change',()=>player.configure($('speed').value,$('loop').checked));")
patch(S+'app.js',
      'const scene=state.scene.id,language=state.lang.code,epoch=state.epoch,draftKey=key();',
      "if(!player.ready)throw new Error('Дождитесь загрузки 3D-ролика перед отправкой ответа.');\n    const scene=state.scene.id,language=state.lang.code,epoch=state.epoch,draftKey=key();")
patch(S+'app.js',
      "bind();const params=new URLSearchParams(location.hash.slice(1));",
      "player=new Player3D($('stage'),status);bind();const params=new URLSearchParams(location.hash.slice(1));")
patch(S+'app.js','scenes:state.scenes,rows,note:',
      "scenes:state.scenes,stimulusVersions:{'1':{renderer:'svg',definition:'/scene.js'},'2':{renderer:'blender-video',manifestPattern:'/media/v2/{sceneId}/manifest.json'}},rows,note:")
patch(S+'index.html',
      '<svg id="stage" viewBox="0 0 760 380" role="img" aria-label="Анимация двух участников: зелёный G и красный R"></svg>',
      '<div class="stage-wrap"><video id="stage" width="960" height="540" muted playsinline preload="auto" aria-label="3D-анимация двух участников: зелёный G и красный R"></video><span class="stage-badge">BLENDER · 3D</span></div><p id="cutaway-note" class="muted small" hidden>Сцена показана в разрезе: крыша и часть ближней стены убраны, чтобы видеть внутреннее пространство.</p><div class="clip-info"><span id="clip-time">0,00 / 4,00 с</span><a id="clip-link" download>Скачать ролик MP4</a></div>')
append(S+'style.css','''.stage-wrap{position:relative;max-width:100%;overflow:hidden;border-radius:10px;background:#676b6d}
#stage{width:100%;height:auto;max-height:none;aspect-ratio:16/9;object-fit:contain;display:block;background:#676b6d}
.stage-badge{position:absolute;right:12px;top:12px;color:white;background:#182a34bf;border-radius:6px;padding:6px 10px;font-size:10px;letter-spacing:1px;pointer-events:none}
.clip-info{display:flex;justify-content:space-between;gap:12px;font-size:11px;color:#526666;margin-top:8px}.clip-info a{color:#226548}
''')
patch(J+'SceneCatalog.java','public static final String VERSION = "1";', 'public static final String VERSION = "2";')
patch(J+'SceneCatalog.java','Полупрозрачность служит проверке глубины, не свойство предмета.','Фиксированная камера видит часть шара за невысоким экраном; прозрачность не используется.')
patch(J+'SceneCatalog.java','Глубина меняет порядок отрисовки.','Глубина и перекрытие рассчитываются 3D-рендерером.')
patch(J+'SceneCatalog.java','Рама показана в плане; проход не сквозь материал.','Реальная 3D-рама; траектория проходит в свободном промежутке между стойками.')
patch(J+'SecurityConfig.java',"img-src 'self' data:; connect-src 'self';", "img-src 'self' data:; media-src 'self'; connect-src 'self';")
# Examples from the prompt remain historical v1 examples. Real corpus responses
# shown for a current stimulus must use the current stimulus version.
patch(J+'CorpusService.java',
      "WHERE e.scene_id=? AND e.language_code=? AND e.visibility='VISIBLE' AND e.is_example=?",
      "WHERE e.scene_id=? AND e.language_code=? AND e.visibility='VISIBLE' AND e.is_example=?\n              AND (e.is_example=TRUE OR e.scene_version=?)")
patch(J+'CorpusService.java','visitor,scene,language,examples);','visitor,scene,language,examples,SceneCatalog.VERSION);')
file=ROOT/T
text=file.read_text()
text=text.replace('"sceneVersion":"1"','"sceneVersion":"2"')
text=text.replace(r'\"sceneVersion\":\"1\"',r'\"sceneVersion\":\"2\"')
text=text.replace('assertEquals("1",s.version());','assertEquals("2",s.version());')
if 'oldVersionIsNotMixedIntoCurrentAnswersButRemainsExportable' not in text:
    pos=text.rfind('}')
    text=text[:pos]+'''    @Test void oldVersionIsNotMixedIntoCurrentAnswersButRemainsExportable() throws Exception {
        Cookie c=visitor();String code=language(c);String old=submit(c,code,80);
        db.update("UPDATE expressions SET scene_version='1' WHERE id=?",old);
        submit(c,code,90);
        mvc.perform(get("/api/expressions").cookie(c).param("sceneId","enter-g").param("languageCode",code))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sceneVersion").value("2"));
        mvc.perform(get("/api/export").param("languageCode",code))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(2));
    }
'''+text[pos:]
file.write_text(text)
# Keep all previous browser coverage, but assert actual video readiness rather
# than SVG circles. Pixel-level assertions are in video.spec.js.
file=ROOT/'tests/e2e/app.spec.js'
text=file.read_text()
text=text.replace("await expect(page.locator('#variants .phrase')).toHaveCount(1);}","await expect(page.locator('#variants .phrase')).toHaveCount(1);await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');}")
text=text.replace("await expect(page.locator('#stage circle').last().locator('..')).toHaveAttribute('fill','#29a66e');","await expect(page.locator('#stage')).toHaveAttribute('data-figure-role','G');")
text=text.replace("await expect(page.locator('#stage circle').last().locator('..')).toHaveAttribute('fill','#df625e');","await expect(page.locator('#stage')).toHaveAttribute('data-figure-role','R');await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');")
text=text.replace("await page.locator('#language').selectOption('tt');","await page.locator('#language').selectOption('tt');await expect(page.locator('#stage')).toHaveAttribute('data-ready','true');")
file.write_text(text)
# Ordinary future CI runs verify the 3D definitions and persisted resources too.
ci=ROOT/'.github/workflows/ci.yml'
text=ci.read_text()
if 'Verify 3D assets' not in text:
    text=text.replace('      - name: Build and test Java', '''      - name: Verify 3D assets
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y --no-install-recommends ffmpeg
          python3 tools/verify_media.py
      - name: Build and test Java''')
ci.write_text(text)
append('README.md','''## 3D update (stimulus version 2)

The running site now plays 960 x 540, 24 fps MP4 clips rendered from real
Blender meshes with baked location keyframes (96 frames / 4 seconds).
The Java application does not need Blender or Python installed.

Editable sources: `models/blender/v2/*.blend`. Reproducible geometry and
trajectories: `tools/scene3d.py`; rendering: `tools/render3d.py`.
Media and per-scene provenance: `src/main/resources/static/media/v2/`.
See [3D pipeline](docs/3D-PIPELINE.md).

Historical v1 responses are preserved and exported with `scene_version=1`,
but are not displayed as answers to v2. The SVG definition remains in `scene.js`
as an archive; the active app imports `player3d.js` instead.
''')
print('3D frontend, version separation and tests upgraded successfully.')
