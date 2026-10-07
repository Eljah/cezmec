/** Native decoded video from actual Blender meshes. No generated UI images. */
export class Player3D {
  constructor(video, notify) {
    this.video=video;this.notify=notify;this.ready=false;this.wanted=false;this.pending=0;this.scene=null;
    this.video.muted=true;this.video.playsInline=true;this.video.preload='auto';
    video.addEventListener('loadeddata',()=>{
      if(!this.matches())return;
      this.ready=true;video.dataset.ready='true';
      this.seek(this.pending??0);
      if(this.wanted)this.play();
    });
    video.addEventListener('error',()=>{
      if(!this.scene)return;
      this.ready=false;this.wanted=false;video.dataset.ready='error';
      notify('Не удалось загрузить 3D-ролик. Проверьте файл MP4 этой сцены; ответ пока не отправлен.',true);
    });
    video.addEventListener('seeked',()=>{if(this.matches()){this.pending=null;this.mark();}});
    video.addEventListener('timeupdate',()=>this.mark());
    video.addEventListener('ended',()=>{if(!video.loop)this.wanted=false;});
  }
  matches(){return this.scene&&this.video.currentSrc.endsWith(this.url);}
  setScene(scene,playing=false){
    if(!/^[a-z]+(?:-[a-z]+)*-[gr]$/.test(scene.id)||String(scene.version)!=='2')throw new Error('Неизвестная версия 3D-стимула');
    this.video.pause();this.scene=scene;this.ready=false;this.pending=0;this.wanted=playing;
    this.url=`/media/v2/${scene.id}/animation.mp4`;
    this.video.dataset.kind=scene.kind;this.video.dataset.scene=scene.id;this.video.dataset.ready='false';
    this.video.dataset.renderer='blender-video';this.video.dataset.progress='0.000';
    this.video.dataset.figureRole=scene.focusFigure?'G':'R';
    this.video.poster=`/media/v2/${scene.id}/start.png`;
    this.video.src=this.url;this.video.load();
  }
  get lastTime(){return Math.max(0,(Number.isFinite(this.video.duration)?this.video.duration:4)-1/24);}
  get progress(){return this.pending??Math.min(1,Math.max(0,this.video.currentTime/(this.lastTime||1)));}
  get playing(){return this.wanted&&(this.ready?!this.video.paused:true);}
  mark(){if(!this.matches())return;this.video.dataset.progress=this.progress.toFixed(3);this.video.dataset.frame=String(Math.min(95,Math.floor(this.video.currentTime*24)));}
  seek(p){
    if(!Number.isFinite(p))return;
    this.pending=Math.max(0,Math.min(1,p));
    if(this.ready&&this.matches()){
      const time=this.pending*this.lastTime;
      if(!this.video.seeking&&Math.abs(this.video.currentTime-time)<.0001)this.pending=null;
      else this.video.currentTime=time;
    }
    this.mark();
  }
  async play(){
    this.wanted=true;
    if(!this.ready)return;
    if(this.progress>=.999)this.seek(0);
    try{await this.video.play();}catch(error){
      if(error.name==='AbortError')return;
      this.wanted=false;
      this.notify('Браузер остановил автоматическое воспроизведение. Нажмите «Воспроизвести».');
    }
  }
  pause(){this.wanted=false;this.video.pause();}
  configure(rate,loop){this.video.playbackRate=Number(rate);this.video.loop=Boolean(loop);}
}
