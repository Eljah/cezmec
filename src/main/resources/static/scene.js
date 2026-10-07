export const KINDS = ['enter','exit','inside','through','approach','reach','depart','near','on','onto','off','above','overpass','under','underpass','front','behind','around','along','contact','attach','detach','among','into-mass','out-of-mass','between','through-gap','rise','fall','admit','block','push','pull','carry','cover','uncover'];
const clamp = n => Math.max(0, Math.min(1,n));
const mix = (a,b,t) => a+(b-a)*t;
/** Geometry is independent of focal colour. p includes still start/end frames. */
export function sampleScene(kind,p,focusFigure=true) {
  if (!KINDS.includes(kind)) throw new Error(`Unknown scene: ${kind}`);
  if (!Number.isFinite(p)) throw new Error('Non-finite progress');
  const u=clamp((p-.15)/.7), s={kind,u,figure:{x:120,y:265},landmark:{x:0,y:0},gate:1,behind:false,
    figureRole:focusFigure?'G':'R',landmarkRole:focusFigure?'R':'G'};
  const f=s.figure,l=s.landmark;
  switch(kind) {
    case 'enter': f.x=mix(120,560,u); break;
    case 'exit': f.x=mix(560,120,u); break;
    case 'inside': f.x=560; break;
    case 'through': f.x=mix(120,710,u); break;
    case 'approach': f.x=mix(120,450,u); break;
    case 'reach': f.x=mix(120,492,u); break;
    case 'depart': f.x=mix(450,120,u); break;
    case 'near': f.x=450; break;
    case 'on': f.x=520;f.y=222;break;
    case 'onto': f.x=mix(120,520,u);f.y=mix(285,222,u)-115*Math.sin(Math.PI*u);break;
    case 'off': f.x=mix(520,120,u);f.y=mix(222,285,u)-115*Math.sin(Math.PI*u);break;
    case 'above': f.x=520;f.y=130;break;
    case 'overpass': f.x=mix(100,710,u);f.y=285-195*Math.sin(Math.PI*u);break;
    case 'under': f.x=520;f.y=275;break;
    case 'underpass': f.x=mix(100,710,u);f.y=275;break;
    case 'front': f.x=520;f.y=285;break;
    case 'behind': f.x=520;f.y=245;s.behind=true;break;
    case 'around': {const a=Math.PI+2*Math.PI*u;f.x=520+165*Math.cos(a);f.y=260+75*Math.sin(a);s.behind=f.y<260;break;}
    case 'along': f.x=mix(100,710,u);f.y=230;break;
    case 'contact': f.x=492;f.y=210;break;
    case 'attach': f.x=mix(120,492,u);f.y=210;break;
    case 'detach': f.x=mix(492,120,u);f.y=210;break;
    case 'among': f.x=540;f.y=235;break;
    case 'into-mass': f.x=mix(120,540,u);f.y=235;break;
    case 'out-of-mass': f.x=mix(540,120,u);f.y=235;break;
    case 'between': f.x=540;f.y=235;break;
    case 'through-gap': f.x=mix(120,710,u);f.y=235;break;
    case 'rise': f.x=350;f.y=mix(285,135,u);break;
    case 'fall': f.x=350;f.y=mix(135,285,u);break;
    case 'admit': s.gate=clamp(u/.28);f.x=mix(120,560,clamp((u-.35)/.65));break;
    case 'block': s.gate=0;f.x=mix(120,412,u);break;
    case 'push': l.x=300+250*u;l.y=265;f.x=370+Math.max(0,250*u-17);break;
    case 'pull': l.x=530+140*u;l.y=265;f.x=350+140*u;break;
    case 'carry': l.x=240*u;f.x=390+l.x;f.y=230;break;
    case 'cover': l.x=mix(100,440,u);f.x=540;f.y=250;s.behind=true;break;
    case 'uncover': l.x=mix(440,100,u);f.x=540;f.y=250;s.behind=true;break;
  }
  return s;
}
const NS='http://www.w3.org/2000/svg';
function node(name,attrs={},text) { const e=document.createElementNS(NS,name);for(const [k,v] of Object.entries(attrs))e.setAttribute(k,String(v));if(text!==undefined)e.textContent=text;return e; }
const fills={G:'#29a66e',R:'#df625e'},strokes={G:'#126140',R:'#a42f37'};
function group(role) {return node('g',{fill:fills[role],stroke:strokes[role],'stroke-width':2,'stroke-dasharray':role==='R'?'7 3':'none'});}
function label(parent,role,x,y) {parent.append(node('text',{x,y,fill:'#fff',stroke:'none','font-size':14,'font-weight':800,'text-anchor':'middle','dominant-baseline':'middle'},role));}
function landmark(s) {
  const g=group(s.landmarkRole), k=s.kind;
  let lx=540,ly=140;
  const rect=(x,y,width,height,extra={})=>g.append(node('rect',{x,y,width,height,rx:5,...extra}));
  if(['enter','exit','inside','admit','block'].includes(k)) {
    g.append(node('path',{d:'M430 120H640V310H430V285H610V160H475V235H430Z','fill-rule':'evenodd'}));
    if(['admit','block'].includes(k)) rect(430,235,10,50*(1-s.gate),{rx:0});
  } else if(k==='through') {
    rect(430,165,210,60);rect(430,285,210,25);ly=193;
  } else if(['on','onto','off','above','overpass'].includes(k)) {
    rect(400,240,240,60);ly=275;
  } else if(['under','underpass'].includes(k)) {
    rect(420,175,220,45);ly=198;
  } else if(['front','behind','around'].includes(k)) {
    rect(470,190,100,120,{opacity:s.behind?.76:1});lx=520;ly=207;
  } else if(k==='along') {
    rect(310,260,350,35);ly=280;
  } else if(['among','into-mass','out-of-mass'].includes(k)) {
    rect(420,145,240,155,{'fill-opacity':.3});
    for(let y=165;y<291;y+=25) for(let x=440;x<650;x+=30)g.append(node('circle',{cx:x+(y%2)*5,cy:y,r:4,'stroke-width':0,'fill-opacity':.65}));
    ly=155;
  } else if(['between','through-gap'].includes(k)) {
    rect(460,135,170,40);rect(460,295,170,40);ly=156;
    label(g,s.landmarkRole,540,315);
  } else if(['push','pull'].includes(k)) {
    rect(s.landmark.x-35,s.landmark.y-35,70,70);lx=s.landmark.x;ly=s.landmark.y;
  } else if(k==='carry') {
    rect(310+s.landmark.x,248,160,25);lx=430+s.landmark.x;ly=261;
  } else if(['cover','uncover'].includes(k)) {
    rect(s.landmark.x,175,180,150,{opacity:.91});lx=s.landmark.x+90;ly=195;
  } else { rect(510,155,60,145);lx=540;ly=180; }
  label(g,s.landmarkRole,lx,ly);return g;
}
export function renderScene(svg,scene,progress) {
  const s=sampleScene(scene.kind,progress,scene.focusFigure);
  const fragment=document.createDocumentFragment();
  fragment.append(node('title',{},`Сцена ${scene.number}. Опишите зелёное тело G относительно красного R.`));
  fragment.append(node('line',{x1:40,y1:345,x2:720,y2:345,stroke:'#cbd6d7','stroke-width':2}));
  for(let x=60;x<=700;x+=40) fragment.append(node('line',{x1:x,y1:345,x2:x-7,y2:352,stroke:'#cbd6d7'}));
  if(['cover','uncover','between','through-gap'].includes(scene.kind))fragment.append(node('text',{x:30,y:35,fill:'#667779','font-size':12},'Вид сверху / составной ориентир'));
  if(s.kind==='pull')fragment.append(node('line',{x1:s.figure.x+18,y1:s.figure.y,x2:s.landmark.x-35,y2:s.landmark.y,stroke:'#53616b','stroke-width':3}));
  const ball=group(s.figureRole);ball.append(node('circle',{cx:s.figure.x,cy:s.figure.y,r:18}));label(ball,s.figureRole,s.figure.x,s.figure.y);
  const body=landmark(s);
  if(s.behind)fragment.append(ball,body);else fragment.append(body,ball);
  svg.replaceChildren(fragment);svg.dataset.kind=scene.kind;svg.dataset.progress=progress.toFixed(3);
  return s;
}
