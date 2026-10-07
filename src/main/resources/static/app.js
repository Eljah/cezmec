import {renderScene} from './scene.js';
const $=id=>document.getElementById(id);
const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
const state={scenes:[],languages:[],scene:null,lang:null,session:null,epoch:0,progress:0,playing:!matchMedia('(prefers-reduced-motion: reduce)').matches,last:0,examplesViewed:false,othersViewed:false,pending:null,editing:null};
function status(text,error=false){$('status').textContent=text;$('status').classList.toggle('error',error);}
async function api(path,method='GET',data){
  const headers={};if(data!==undefined)headers['Content-Type']='application/json';
  if(method!=='GET'&&state.session)headers[state.session.csrfHeader]=state.session.csrfToken;
  const r=await fetch('/api'+path,{method,headers,credentials:'same-origin',body:data===undefined?undefined:JSON.stringify(data)});
  let out;try{out=await r.json();}catch{out={};}
  if(!r.ok)throw new Error(out.error||(r.status===403?'Сессия истекла. Перезагрузите страницу: черновик сохранён.':`Ошибка сервера ${r.status}`));
  return out;
}
function safeRead(key){try{return JSON.parse(localStorage.getItem(key));}catch{return null;}}
function safeWrite(key,value){try{localStorage.setItem(key,JSON.stringify(value));}catch{status('Браузер не сохраняет черновики. Не закрывайте страницу до отправки.',true);}}
function key(){return 'cezmec:draft:v1:'+state.scene.id+':'+state.lang.code;}
function exposureKey(){return 'cezmec:seen:v1:'+state.scene.id+':'+state.lang.code;}
function markSeen(kind){state[kind]=true;safeWrite(exposureKey(),{examplesViewed:state.examplesViewed,othersViewed:state.othersViewed});}
function annotatedPreview(target,text){
  target.replaceChildren();const re=/\{([GR]):([^{}\r\n]{1,120})}/g;let end=0,m;
  while((m=re.exec(text))){target.append(document.createTextNode(text.slice(end,m.index)),el('span','role '+m[1],m[2]));end=re.lastIndex;}
  target.append(document.createTextNode(text.slice(end)));
}
function starter(){return state.lang.starterTemplate.replaceAll('{G}',`{G:${$('green-word').value}}`).replaceAll('{R}',`{R:${$('red-word').value}}`);}
const readings=[['OBSERVED','Наблюдаемое действие'],['STATE','Состояние / положение'],['PERMISSIVE','Разрешение / допуск'],['CAUSATIVE','Причинение / воздействие'],['OTHER','Другое / не уверен']];
function labeled(text,input){const l=el('label','',text);l.append(input);return l;}
function addVariant(data={}){
  if($('variants').children.length>=10){status('В одном ответе может быть до 10 вариантов.',true);return;}
  const box=el('section','variant'),head=el('div','variant-head'),title=el('strong','','Вариант'),remove=el('button','ghost','Убрать');remove.type='button';
  head.append(title,remove);const text=el('textarea','phrase');text.rows=2;text.maxLength=2000;text.required=true;text.value=data.annotatedText??starter();text.dir=state.lang.direction;text.setAttribute('aria-label','Формулировка');
  const preview=el('div','preview');preview.dir=state.lang.direction;annotatedPreview(preview,text.value);
  const controls=el('div','variant-controls'),range=el('input');range.type='range';range.min=0;range.max=100;range.value=data.weight??80;range.className='weight-input';range.setAttribute('aria-label','Точность формулировки');
  const output=el('output','',range.value),weightRow=el('div','weight-control');weightRow.append(range,output);const weight=labeled('Точность, 0–100',weightRow);weight.className='weight';
  const reading=el('select','reading');for(const [v,t] of readings){const o=el('option','',t);o.value=v;reading.append(o);}reading.value=data.reading??'OBSERVED';controls.append(weight,labeled('Что выражает фраза',reading));
  const implicit=el('div','implicit');for(const role of ['G','R']){const c=el('input','implicit-'+role);c.type='checkbox';c.checked=(data.implicitRoles??[]).includes(role);const l=el('label','check');l.append(c,document.createTextNode(`${role} не назван явно`));implicit.append(l);}
  const detail=el('details'),summary=el('summary','','Перевод и морфемный разбор — необязательно'),translation=el('input','translation'),gloss=el('input','gloss');translation.maxLength=1000;gloss.maxLength=1000;translation.value=data.translation??'';gloss.value=data.gloss??'';detail.append(summary,labeled('Перевод / пояснение',translation),labeled('Морфемный разбор (глосса)',gloss));
  box.append(head,text,preview,controls,implicit,detail);$('variants').append(box);
  remove.addEventListener('click',()=>{if($('variants').children.length===1){status('Оставьте хотя бы один вариант.',true);return;}box.remove();saveDraft();renumber();});
  box.addEventListener('input',()=>{annotatedPreview(preview,text.value);output.value=range.value;output.textContent=range.value;saveDraft();});renumber();
}
function renumber(){[...$('variants').children].forEach((b,i)=>b.querySelector('strong').textContent=`Вариант ${i+1}`);}
function alternatives(){return [...$('variants').children].map(b=>({annotatedText:b.querySelector('.phrase').value,weight:Number(b.querySelector('.weight-input').value),reading:b.querySelector('.reading').value,translation:b.querySelector('.translation').value,gloss:b.querySelector('.gloss').value,implicitRoles:['G','R'].filter(r=>b.querySelector('.implicit-'+r).checked)}));}
function saveDraft(){if(!state.scene||!state.lang)return;safeWrite(key(),{alternatives:alternatives(),dialect:$('dialect').value,proficiency:$('proficiency').value,greenWord:$('green-word').value,redWord:$('red-word').value,pending:state.pending});}
function restoreDraft(){
  const d=safeRead(key()),seen=safeRead(exposureKey())??{};state.examplesViewed=!!seen.examplesViewed;state.othersViewed=!!seen.othersViewed;state.pending=d?.pending??null;
  $('green-word').value=d?.greenWord??state.lang.greenLabel;$('red-word').value=d?.redWord??state.lang.redLabel;
  $('dialect').value=d?.dialect??'';$('proficiency').value=d?.proficiency??'UNSPECIFIED';$('consent').checked=false;
  $('variants').replaceChildren();for(const a of d?.alternatives?.length?d.alternatives:[{}])addVariant(a);
}
function renderCatalog(){
  $('scene-list').replaceChildren();const research=$('research').checked,family=$('family').value;
  const scenes=state.scenes.filter(s=>!research||!family||s.family===family);$('scene-count').textContent=String(scenes.length);$('family-label').hidden=!research;
  for(const s of scenes){const b=el('button','scene-button');b.dataset.scene=s.id;b.setAttribute('aria-current',String(s.id===state.scene?.id));
    const n=el('span','scene-number',String(s.number).padStart(2,'0')),t=el('span','scene-title',research?s.title:`Ситуация ${Math.ceil(s.number/2)}`);t.append(el('small','',`${s.focusFigure?'A · G — шар':'B · G — большое тело'}`));b.append(n,t);b.addEventListener('click',()=>choose(s.id,state.lang.code));$('scene-list').append(b);}
}
function renderResearch(){
  const s=state.scene,r=$('research').checked;$('scene-heading').textContent=r?s.title:`Ситуация ${Math.ceil(s.number/2)} · ${s.focusFigure?'A':'B'}`;
  $('scene-code').textContent=`СТИМУЛ ${String(s.number).padStart(3,'0')} / ${state.scenes.length} · v${s.version}`;
  $('research-detail').hidden=!r;$('research-detail').replaceChildren(el('p','',`${s.family} · ${s.place} / ${s.direction} · контакт: ${s.contact}`),el('p','',s.realExample),el('p','muted',s.researchNote));
}
function choose(sceneId,languageCode,initial=false){
  if(!initial)saveDraft();state.scene=state.scenes.find(s=>s.id===sceneId)??state.scenes[0];state.lang=state.languages.find(l=>l.code===languageCode)??state.languages[0];state.epoch++;
  $('language').value=state.lang.code;$('language-note').textContent=state.lang.notes;$('edit-language').hidden=!state.lang.editable;
  $('answers').replaceChildren();$('example-list').replaceChildren();$('examples').open=false;$('show-others').textContent='Показать ответы';
  restoreDraft();renderCatalog();renderResearch();state.progress=0;state.last=0;renderFrame();
  history.replaceState(null,'','#'+new URLSearchParams({scene:state.scene.id,lang:state.lang.code}));
}
function renderFrame(){if(!state.scene)return;renderScene($('stage'),state.scene,state.progress);$('timeline').value=String(Math.round(state.progress*1000));$('play').textContent=state.playing?'Пауза':'Воспроизвести';$('play').setAttribute('aria-label',state.playing?'Пауза анимации':'Воспроизвести анимацию');}
function tick(now){const delta=state.last?Math.min(now-state.last,100):0;state.last=now;
  if(state.playing&&!document.hidden&&state.scene){state.progress+=delta/5000*Number($('speed').value);if(state.progress>=1){if($('loop').checked)state.progress=0;else{state.progress=1;state.playing=false;}}renderFrame();}requestAnimationFrame(tick);}
async function refreshStats(){const s=await api('/stats');$('stats').replaceChildren(el('strong','',`${s.scenes} сцен`),el('span','',`${s.languages} языка / языковых варианта`),el('span','',`${s.expressions} ответов · ${s.ratings} оценок`));}
function renderLanguages(){const selected=state.lang?.code;$('language').replaceChildren();for(const l of state.languages){const o=el('option','',l.name+(l.nativeName&&l.nativeName!==l.name?' · '+l.nativeName:'')+` [${l.code}]`);o.value=l.code;$('language').append(o);}if(selected)$('language').value=selected;}
async function loadAnswers(examples=false){
  const epoch=state.epoch,scene=state.scene.id,lang=state.lang.code;markSeen(examples?'examplesViewed':'othersViewed');saveDraft();
  const rows=await api(`/expressions?${new URLSearchParams({sceneId:scene,languageCode:lang,examples:String(examples)})}`);if(epoch!==state.epoch)return;
  const target=examples?$('example-list'):$('answers');target.replaceChildren();if(!rows.length)target.append(el('p','muted',examples?'Для этой пары нет примеров. Напишите свою формулировку.':'Пока нет ответов. Ваш вариант может стать первым.'));
  for(const row of rows){const card=el('article','answer'),phrase=el('div','preview');phrase.dir=state.lang.direction;annotatedPreview(phrase,row.annotatedText);card.append(phrase);
    if(row.translation)card.append(el('p','',row.translation));if(row.gloss)card.append(el('p','muted small',row.gloss));
    card.append(el('p','answer-meta',examples?'Пример автора задания · не проверен носителями':`Точность: ${row.averageWeight===null?'нет оценок':row.averageWeight.toFixed(1)+' / 100'} · оценок: ${row.ratingsCount}${row.mine?' · ваш ответ':''}${row.dialect?' · '+row.dialect:''}`));
    if(!examples){const actions=el('div','answer-controls'),weight=el('input');weight.type='number';weight.min=0;weight.max=100;weight.value=row.myWeight??80;weight.setAttribute('aria-label','Моя оценка');const vote=el('button','ghost','Оценить'),report=el('button','ghost','Сообщить об ошибке');
      vote.addEventListener('click',()=>withButton(vote,async()=>{if(!weight.checkValidity()||weight.value==='')throw new Error('Введите целое число от 0 до 100');await api(`/expressions/${row.id}/rating`,'PUT',{weight:Number(weight.value)});if(epoch===state.epoch)await loadAnswers();await refreshStats();status('Оценка сохранена. Повторное голосование заменяет вашу оценку.');}));
      report.addEventListener('click',()=>{const reason=prompt('Что нужно проверить? Не указывайте личные данные.');if(reason?.trim())withButton(report,async()=>{await api(`/expressions/${row.id}/report`,'POST',{reason});status('Замечание отправлено модератору.');});});
      actions.append(weight,vote,report);if(row.mine){const remove=el('button','ghost','Удалить мой ответ');remove.addEventListener('click',()=>{if(confirm('Удалить этот ответ и относящиеся к нему оценки?'))withButton(remove,async()=>{await api(`/expressions/${row.id}`,'DELETE');if(epoch===state.epoch)await loadAnswers();await refreshStats();status('Ответ удалён с сервера. Ранее скачанные копии корпуса отозвать нельзя.');});});actions.append(remove);}card.append(actions);
    }target.append(card);
  }if(!examples)$('show-others').textContent='Обновить ответы';
}
async function withButton(button,work){button.disabled=true;try{await work();}catch(e){status(e.message,true);}finally{button.disabled=false;}}
function openLanguage(edit=false){const form=$('language-form');form.reset();state.editing=edit?state.lang:null;$('language-error').textContent='';$('language-dialog-title').textContent=edit?'Изменить мой шаблон':'Добавить язык';
  form.elements.namedItem('code').readOnly=edit;if(edit)for(const k of ['code','name','nativeName','direction','greenLabel','redLabel','starterTemplate','notes'])form.elements.namedItem(k).value=state.lang[k];$('language-dialog').showModal();}
function bind(){
  $('language').addEventListener('change',()=>choose(state.scene.id,$('language').value));$('family').addEventListener('change',renderCatalog);$('research').addEventListener('change',()=>{renderCatalog();renderResearch();});
  $('pair').addEventListener('click',()=>choose(state.scene.pairId,state.lang.code));$('play').addEventListener('click',()=>{state.playing=!state.playing;if(state.progress===1)state.progress=0;renderFrame();});$('replay').addEventListener('click',()=>{state.progress=0;renderFrame();});$('timeline').addEventListener('input',()=>{state.playing=false;state.progress=Number($('timeline').value)/1000;renderFrame();});
  $('add-variant').addEventListener('click',()=>{addVariant();saveDraft();});$('use-labels').addEventListener('click',()=>{addVariant();saveDraft();});for(const id of ['dialect','proficiency','green-word','red-word'])$(id).addEventListener('input',saveDraft);
  $('show-others').addEventListener('click',()=>withButton($('show-others'),()=>loadAnswers()));$('examples').addEventListener('toggle',()=>{if($('examples').open)loadAnswers(true).catch(e=>status(e.message,true));});
  $('contribution').addEventListener('submit',event=>{event.preventDefault();withButton($('save'),async()=>{
    const scene=state.scene.id,language=state.lang.code,epoch=state.epoch,draftKey=key();
    const payload={sceneId:scene,sceneVersion:state.scene.version,languageCode:language,alternatives:alternatives(),dialect:$('dialect').value,proficiency:$('proficiency').value,consent:$('consent').checked,examplesViewed:state.examplesViewed,othersViewed:state.othersViewed};
    const canonical=JSON.stringify(payload);if(!state.pending||state.pending.canonical!==canonical)state.pending={canonical,requestId:crypto.randomUUID()};saveDraft();
    const out=await api('/contributions','POST',{...payload,requestId:state.pending.requestId});try{localStorage.removeItem(draftKey);}catch{}
    if(epoch===state.epoch){state.pending=null;$('variants').replaceChildren();addVariant();$('consent').checked=false;await loadAnswers();}
    await refreshStats();status(`Сохранено вариантов: ${out.ids.length}. Спасибо за вклад!`);
  });});
  $('add-language').addEventListener('click',()=>openLanguage());$('edit-language').addEventListener('click',()=>openLanguage(true));$('close-language').addEventListener('click',()=>$('language-dialog').close());
  $('language-form').addEventListener('submit',async e=>{e.preventDefault();const button=e.currentTarget.querySelector('[type=submit]');button.disabled=true;try{const input=Object.fromEntries(new FormData(e.currentTarget));input.code=input.code.toLowerCase();const edit=state.editing;await api(edit?`/languages/${edit.code}?revision=${edit.revision}`:'/languages',edit?'PUT':'POST',input);state.languages=await api('/languages');renderLanguages();$('language-dialog').close();choose(state.scene.id,input.code);await refreshStats();status('Языковой шаблон сохранён.');}catch(error){$('language-error').textContent=error.message;}finally{button.disabled=false;}});
  $('export').addEventListener('click',()=>withButton($('export'),async()=>{
    const lang=state.lang.code,rows=[];let offset=0;do{const page=await api(`/export?${new URLSearchParams({languageCode:lang,offset:String(offset),limit:'1000'})}`);rows.push(...page.rows);offset=page.nextOffset;}while(offset!==-1);
    const data={schemaVersion:1,exportedAt:new Date().toISOString(),language:state.languages.find(l=>l.code===lang),scenes:state.scenes,rows,note:'Публичные непроверенные ответы. Примеры исключены. Выгрузка страниц не является транзакционным снимком.'};
    const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json;charset=utf-8'})),a=el('a');a.href=url;a.download=`cezmec-${lang}.json`;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);status(`Экспортировано ответов: ${rows.length}.`);
  }));
  window.addEventListener('beforeunload',saveDraft);
}
async function start(){try{
  state.session=await api('/session');[state.scenes,state.languages]=await Promise.all([api('/scenes'),api('/languages')]);renderLanguages();
  for(const family of new Set(state.scenes.map(s=>s.family))){const o=el('option','',family);o.value=family;$('family').append(o);}
  bind();const params=new URLSearchParams(location.hash.slice(1));choose(params.get('scene')??'enter-g',params.get('lang')??'ru',true);await refreshStats();requestAnimationFrame(tick);
}catch(e){status('Не удалось запустить интерфейс: '+e.message,true);}}
start();
