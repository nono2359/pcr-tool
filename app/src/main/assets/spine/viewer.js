(function () {
  "use strict";
  const params = new URLSearchParams(location.search);
  const unitId = params.get("unitId"), enemyId = params.get("enemyId"), rawType = params.get("type"), motionId=params.get("motionId")??"COMMON";
  const typeLabels = { "1": "バトル", "2": "ギルドハウス" };
  const details = document.querySelector("#request-details"), status = document.querySelector("#status"), message = document.querySelector("#stage-message");
  const motionGroup=document.querySelector("#motion-group"),motionGroupLabel=document.querySelector("#motion-group-label"),animationSelect = document.querySelector("#animation"), playbackButton = document.querySelector("#toggle-playback"), speedSelect = document.querySelector("#speed"), gifButton = document.querySelector("#save-gif");
  const addDetail = (label, value) => { const dt=document.createElement("dt"),dd=document.createElement("dd");dt.textContent=label;dd.textContent=value;details.append(dt,dd);return dd; };
  const validId = value => value !== null && /^[1-9]\d*$/.test(value);
  const errors=[];
  if(unitId!==null&&enemyId!==null) errors.push("unitIdとenemyIdは同時に指定できません。");
  if(unitId!==null&&!validId(unitId)) errors.push("unitIdは正の整数で指定してください。");
  if(enemyId!==null&&!validId(enemyId)) errors.push("enemyIdは正の整数で指定してください。");
  if(rawType!==null&&!(rawType in typeLabels)) errors.push("typeは1または2で指定してください。");
  if(rawType==="2"&&!/^(?:COMMON|\d{6})$/.test(motionId)) errors.push("motionIdはCOMMONまたは6桁で指定してください。");
  const hasTarget=unitId!==null||enemyId!==null;
  addDetail("対象",unitId!==null?"キャラクター":enemyId!==null?"エネミー":"未選択");
  addDetail("ID",unitId??enemyId??"—"); addDetail("種類",rawType===null?"Viewer既定値":typeLabels[rawType]??rawType);
  const modelDetail=addDetail("モデル","検索中…");
  const fail=text=>{status.className="error";status.textContent=text;message.hidden=false;message.textContent="素材を読み込めませんでした。再読み込みしてください。";};
  const selectorFail=text=>{status.className="error";status.textContent=`モデル一覧の取得に失敗しました: ${text}`;};
  if(errors.length){status.className="error";status.textContent=errors.join(" ");message.textContent="リクエストを修正して再読み込みしてください。";return;}
  if(!window.spine?.webgl){fail("Spine 3.6ランタイムを読み込めませんでした。");return;}
  const canvas=document.querySelector("#canvas"),stage=document.querySelector("#stage"),viewerFooter=document.querySelector(".viewer-footer"),gl=canvas.getContext("webgl",{alpha:true})||canvas.getContext("experimental-webgl",{alpha:true});
  function fitStageHeight(){
    const viewportHeight=window.visualViewport?.height??window.innerHeight;
    const footerHeight=viewerFooter?.offsetHeight??0,statusHeight=status?.offsetHeight??0;
    const controlsHeight=document.querySelector(".viewer-heading")?.getBoundingClientRect().height??0;
    const available=Math.max(300,Math.floor(viewportHeight-stage.getBoundingClientRect().top-controlsHeight-footerHeight-statusHeight-30));
    stage.style.height=`${available}px`;canvas.style.height=`${available}px`;
  }
  window.addEventListener("resize",fitStageHeight);
  window.visualViewport?.addEventListener("resize",fitStageHeight);
  const stageResizeObserver=new ResizeObserver(()=>requestAnimationFrame(fitStageHeight));
  stageResizeObserver.observe(document.querySelector(".viewer-heading"));stageResizeObserver.observe(status);
  requestAnimationFrame(fitStageHeight);
  if(!gl){fail("この環境ではWebGLを利用できません。");return;}
  const shader=spine.webgl.Shader.newTwoColoredTextured(gl),batcher=new spine.webgl.PolygonBatcher(gl),renderer=new spine.webgl.SkeletonRenderer(gl),mvp=new spine.webgl.Matrix4();
  let skeleton,skeletonData,state,bounds,paused=false,lastFrame=performance.now()/1000,model,assets,binaryData=null,binaryDone=false,binaryError=null,captureSession=null;
  const sample={name:"Spine公式サンプル（描画確認用）",format:"json",skeleton:"assets/sample/spineboy-ess.json",atlas:"assets/sample/spineboy.atlas",animation:"run",premultipliedAlpha:true};
  async function chooseModel(){
    if(!hasTarget) return sample;
    const kind=unitId!==null?"unit":"enemy",id=unitId??enemyId,type=rawType??"1",key=`${kind}:${id}:${type}`;
    try{
      const manifestResponse=await fetch("assets/local/manifest.json",{cache:"no-store"});
      if(manifestResponse.ok&&type!=="2"){const cached=(await manifestResponse.json()).models?.[key];if(cached)return cached;}
    }catch(error){console.warn("Local manifest lookup failed:",error);}
    if((kind==="unit"&&(type==="1"||type==="2"))||(kind==="enemy"&&type==="1")){
      status.className="notice";status.textContent="モデルを初回取得しています…";
      const motionQuery=kind==="unit"&&type==="2"?`&motionId=${encodeURIComponent(motionId)}`:"";
      const targetQuery=kind==="unit"?`unitId=${encodeURIComponent(id)}`:`enemyId=${encodeURIComponent(id)}`;
      const ensureResponse=await fetch(`/api/models/ensure?${targetQuery}&type=${type}${motionQuery}`,{cache:"no-store"});
      if(ensureResponse.ok)return (await ensureResponse.json()).entry;
      const failure=await ensureResponse.json().catch(()=>({}));
      throw new Error(`モデルを取得できませんでした（${id}, type=${type}）: ${failure.error??ensureResponse.status}`);
    }
    throw new Error(`対応していないモデル指定です（${key}）`);
  }
  async function setupRoomMotionGroups(){
    if(rawType!=="2")return;
    motionGroupLabel.hidden=false;
    const response=await fetch("/api/room-motions",{cache:"no-store"});
    if(!response.ok)throw new Error("モーションID一覧を取得できませんでした。");
    const motions=(await response.json()).motions;
    motionGroup.replaceChildren(...motions.map(motion=>{const option=document.createElement("option");option.value=motion.id;option.textContent=motion.name?`${motion.id}　${motion.name}`:motion.id;return option;}));
    motionGroup.value=motionId;motionGroup.disabled=false;
  }
  motionGroup.addEventListener("change",()=>{const next=new URL(location.href);next.searchParams.set("motionId",motionGroup.value);location.href=next.href;});
  function beginLoad(selected){
    model=selected;modelDetail.textContent=model.name;
    const local=model!==sample;
    status.className=local?"success":hasTarget?"notice":"success";
    status.textContent=local?"ローカル収録モデルを読み込みました。":hasTarget?"指定モデルはローカル未収録です。描画確認用サンプルを表示します。":"描画確認用サンプルを表示します。";
    assets=new spine.webgl.AssetManager(gl);assets.loadTextureAtlas(model.atlas);
    if(model.format==="binary"){
      spine.webgl.AssetManager.downloadBinary(model.skeleton,data=>{binaryData=data;binaryDone=true;},()=>{binaryError=`${model.skeleton}を読み込めませんでした。`;binaryDone=true;});
    }else{assets.loadText(model.skeleton);binaryDone=true;}
    requestAnimationFrame(waitForAssets);
  }
  function waitForAssets(){
    if(assets.isLoadingComplete()&&binaryDone){
      if(assets.hasErrors()){fail(`素材を読み込めませんでした: ${Object.values(assets.getErrors()).join(" / ")}`);return;}
      if(binaryError){fail(binaryError);return;}
      try{setup();requestAnimationFrame(render);}catch(error){fail(`モデルの初期化に失敗しました: ${error.message}`);} return;
    }
    requestAnimationFrame(waitForAssets);
  }
  function setup(){
    const loader=new spine.AtlasAttachmentLoader(assets.get(model.atlas));
    const newRegionAttachment=loader.newRegionAttachment.bind(loader),newMeshAttachment=loader.newMeshAttachment.bind(loader);
    loader.newRegionAttachment=(skin,name,path)=>newRegionAttachment(skin,name,path.trimEnd());
    loader.newMeshAttachment=(skin,name,path)=>newMeshAttachment(skin,name,path.trimEnd());
    const data=model.format==="binary"?new spine.SkeletonBinary(loader).readSkeletonData(binaryData):new spine.SkeletonJson(loader).readSkeletonData(assets.get(model.skeleton));skeletonData=data;
    skeleton=new spine.Skeleton(data);skeleton.setSkinByName("default");skeleton.setToSetupPose();skeleton.updateWorldTransform();
    const offset=new spine.Vector2(),size=new spine.Vector2();skeleton.getBounds(offset,size,[]);bounds={offset,size};
    state=new spine.AnimationState(new spine.AnimationStateData(data));animationSelect.replaceChildren();
    const visibleAnimations=model===sample?data.animations:rawType==="2"?data.animations.filter(animation=>motionId==="COMMON"?animation.name.startsWith("cmn_"):animation.name.startsWith(`itm_${motionId}_`)):data.animations;
    visibleAnimations.forEach(animation=>{const option=document.createElement("option");option.value=animation.name;option.textContent=animation.name;animationSelect.append(option);});
    if(!visibleAnimations.length)throw new Error(`モーションID ${motionId} に対応するアニメーションがありません。`);
    const initial=visibleAnimations.some(animation=>animation.name===model.animation)?model.animation:visibleAnimations[0].name;animationSelect.value=initial;state.setAnimation(0,initial,true);updateAnimationBounds(initial);
    animationSelect.disabled=false;playbackButton.disabled=false;gifButton.disabled=false;message.hidden=true;
  }
  function updateAnimationBounds(animationName){
    const animation=skeletonData.findAnimation(animationName);if(!animation)return;
    const probe=new spine.Skeleton(skeletonData),probeState=new spine.AnimationState(new spine.AnimationStateData(skeletonData)),entry=probeState.setAnimation(0,animationName,false);
    const offset=new spine.Vector2(),size=new spine.Vector2();let left=Infinity,bottom=Infinity,right=-Infinity,top=-Infinity;
    const samples=Math.max(2,Math.min(600,Math.ceil(animation.duration*30)));
    for(let index=0;index<=samples;index++){entry.trackTime=animation.duration*index/samples;probe.setToSetupPose();probeState.apply(probe);probe.updateWorldTransform();probe.getBounds(offset,size,[]);if(size.x>0&&size.y>0){left=Math.min(left,offset.x);bottom=Math.min(bottom,offset.y);right=Math.max(right,offset.x+size.x);top=Math.max(top,offset.y+size.y);}}
    if(Number.isFinite(left))bounds={offset:new spine.Vector2(left,bottom),size:new spine.Vector2(right-left,top-bottom)};
  }
  animationSelect.addEventListener("change",()=>{skeleton.setToSetupPose();state.setAnimation(0,animationSelect.value,true);updateAnimationBounds(animationSelect.value);});
  playbackButton.addEventListener("click",()=>{paused=!paused;playbackButton.textContent=paused?"再生":"一時停止";});
  function resize(){
    const ratio=Math.min(devicePixelRatio||1,2),width=Math.max(1,Math.floor(canvas.clientWidth*ratio)),height=Math.max(1,Math.floor(canvas.clientHeight*ratio));
    if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;}
    const cx=bounds.offset.x+bounds.size.x/2,cy=bounds.offset.y+bounds.size.y/2,scale=Math.max(bounds.size.x/width,bounds.size.y/height)*1.18,vw=width*scale,vh=height*scale;
    mvp.ortho2d(cx-vw/2,cy-vh/2,vw,vh);gl.viewport(0,0,width,height);
  }
  function render(){
    const now=performance.now()/1000,delta=Math.min(now-lastFrame,.1);lastFrame=now;
    if(captureSession){if(captureSession.index>0)state.update(captureSession.step*captureSession.speed);}else if(!paused)state.update(delta*Number(speedSelect.value));
    state.apply(skeleton);skeleton.updateWorldTransform();
    resize();gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);shader.bind();shader.setUniformi(spine.webgl.Shader.SAMPLER,0);shader.setUniform4x4f(spine.webgl.Shader.MVP_MATRIX,mvp.values);
    batcher.begin(shader);renderer.premultipliedAlpha=model.premultipliedAlpha!==false;renderer.draw(batcher,skeleton);batcher.end();shader.unbind();
    if(captureSession)captureGifFrame();requestAnimationFrame(render);
  }
  function gifFileName(){
    const ids=model.name.match(/\d{6}/g),modelId=ids?.[ids.length-1]??unitId??"model";
    const action=animationSelect.value.replace(/^\d+_/,"").replace(/[^0-9A-Za-z_-]+/g,"_");
    return `${modelId}_${action}.gif`;
  }
  async function selectGifTarget(name){
    if(!window.showSaveFilePicker)return null;
    try{return await window.showSaveFilePicker({suggestedName:name,types:[{description:"Animated GIF",accept:{"image/gif":[".gif"]}}]});}
    catch(error){if(error.name==="AbortError")throw error;console.warn("Save picker unavailable:",error);return null;}
  }
  gifButton.addEventListener("click",async()=>{
    if(captureSession||!skeleton)return;
    const name=gifFileName();let handle;
    try{handle=await selectGifTarget(name);}catch{return;}
    const animation=skeleton.data.findAnimation(animationSelect.value),fps=30,speed=Number(speedSelect.value);
    if(!animation)return fail("選択したアニメーションが見つかりません。");
    const frameCount=Math.max(2,Math.min(600,Math.ceil(animation.duration*fps/speed)));
    const track=state.tracks[0],restore={animation:track?.animation?.name??animationSelect.value,time:track?.trackTime??0,paused};
    paused=true;state.setAnimation(0,animationSelect.value,true);skeleton.setToSetupPose();
    animationSelect.disabled=true;playbackButton.disabled=true;speedSelect.disabled=true;gifButton.disabled=true;
    status.className="notice";status.textContent=`GIFフレームを生成中… 0/${frameCount}`;
    captureSession={name,handle,frameCount,index:0,step:1/fps,speed,frames:[],box:{left:Infinity,top:Infinity,right:-1,bottom:-1},restore};
  });
  function captureGifFrame(){
    const session=captureSession,source=document.createElement("canvas");source.width=canvas.width;source.height=canvas.height;
    const context=source.getContext("2d",{willReadFrequently:true});context.drawImage(canvas,0,0);
    const pixels=context.getImageData(0,0,source.width,source.height),data=pixels.data;
    let left=source.width,top=source.height,right=-1,bottom=-1;
    for(let y=0;y<source.height;y++)for(let x=0;x<source.width;x++)if(data[(y*source.width+x)*4+3]>8){if(x<left)left=x;if(x>right)right=x;if(y<top)top=y;if(y>bottom)bottom=y;}
    if(right>=left){
      const width=right-left+1,height=bottom-top+1;
      session.frames.push({image:context.getImageData(left,top,width,height),left,top,width,height});
      session.box.left=Math.min(session.box.left,left);session.box.top=Math.min(session.box.top,top);session.box.right=Math.max(session.box.right,right);session.box.bottom=Math.max(session.box.bottom,bottom);
    }else session.frames.push(null);
    session.index++;status.textContent=`GIFフレームを生成中… ${session.index}/${session.frameCount}`;
    if(session.index>=session.frameCount){captureSession=null;restoreAfterCapture(session);encodeGif(session);}
  }
  function restoreAfterCapture(session){
    const entry=state.setAnimation(0,session.restore.animation,true);entry.trackTime=session.restore.time;paused=session.restore.paused;
    animationSelect.disabled=false;playbackButton.disabled=false;speedSelect.disabled=false;gifButton.disabled=false;
  }
  function encodeGif(session){
    if(session.box.right<session.box.left)return fail("描画されたフレームが見つかりませんでした。");
    status.textContent="GIFをエンコード中…";
    const unionWidth=session.box.right-session.box.left+1,unionHeight=session.box.bottom-session.box.top+1;
    const staging=document.createElement("canvas");staging.width=unionWidth;staging.height=unionHeight;const stagingContext=staging.getContext("2d");
    const output=document.createElement("canvas");output.width=128;output.height=128;const outputContext=output.getContext("2d");
    const scale=Math.min(124/unionWidth,124/unionHeight),drawWidth=Math.max(1,Math.round(unionWidth*scale)),drawHeight=Math.max(1,Math.round(unionHeight*scale)),dx=Math.floor((128-drawWidth)/2),dy=Math.floor((128-drawHeight)/2);
    const encoder=new GIF({workers:2,quality:10,width:128,height:128,transparent:0xff00ff,workerScript:"./vendor/gif.worker.js",repeat:0});
    session.frames.forEach(frame=>{
      stagingContext.clearRect(0,0,unionWidth,unionHeight);if(frame)stagingContext.putImageData(frame.image,frame.left-session.box.left,frame.top-session.box.top);
      outputContext.clearRect(0,0,128,128);outputContext.drawImage(staging,0,0,unionWidth,unionHeight,dx,dy,drawWidth,drawHeight);
      const rgba=outputContext.getImageData(0,0,128,128),pixels=rgba.data;
      for(let index=0;index<pixels.length;index+=4){
        if(pixels[index+3]<96){pixels[index]=255;pixels[index+1]=0;pixels[index+2]=255;}pixels[index+3]=255;
      }
      outputContext.putImageData(rgba,0,0);encoder.addFrame(outputContext,{copy:true,delay:Math.round(1000/30)});
    });
    encoder.on("progress",value=>status.textContent=`GIFをエンコード中… ${Math.round(value*100)}%`);
    encoder.on("finished",blob=>saveGifBlob(blob,session));encoder.render();
  }
  async function saveGifBlob(blob,session){
    try{
      if(window.PcrToolAndroid?.saveGif){const dataUrl=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(reader.error);reader.readAsDataURL(blob);});window.PcrToolAndroid.saveGif(dataUrl,session.name);}
      else if(session.handle){const writable=await session.handle.createWritable();await writable.write(blob);await writable.close();}
      else{const url=URL.createObjectURL(blob),link=document.createElement("a");link.href=url;link.download=session.name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
      status.className="success";status.textContent=`${session.name}を保存しました。`;
    }catch(error){fail(`GIFの保存に失敗しました: ${error.message}`);}
  }  const characterDialog=document.querySelector("#character-dialog"),characterList=document.querySelector("#character-list"),characterSearch=document.querySelector("#character-search");
  const rarity6=document.querySelector("#rarity-6"),selectorNote=document.querySelector("#selector-note"),rarityOptions=document.querySelector("#rarity-options"),previewOptions=document.querySelector("#preview-options");
  let selectableUnits=null,selectableEnemies=null,selectableClanBosses=null,selectableClanBattle=null;
  const currentUnitBase=unitId===null?null:Number(unitId)-Number(unitId)%100+1,currentEnemy=enemyId===null?null:Number(enemyId);
  const selectorTarget=()=>document.querySelector('input[name="selector-target"]:checked').value;
  const selectedModelId=()=>Number(characterList.dataset.value)||0;
  function selectModelOption(id){characterList.dataset.value=String(id);characterList.querySelectorAll(".model-option").forEach(option=>option.setAttribute("aria-selected",String(Number(option.dataset.value)===id)));if(selectorTarget()==="unit")updateRarityChoice();}
  async function loadTargetList(){
    const target=selectorTarget();
    if(target==="unit"&&!selectableUnits){const response=await fetch("/api/units",{cache:"no-store"});if(!response.ok)throw new Error("キャラクター一覧を取得できませんでした。");selectableUnits=(await response.json()).units;}
    if(target==="enemy"&&!selectableEnemies){const response=await fetch("/api/enemies",{cache:"no-store"});if(!response.ok)throw new Error("エネミー一覧を取得できませんでした。");selectableEnemies=(await response.json()).enemies;}
    if(target==="clan"&&!selectableClanBosses){const response=await fetch("/api/clan-battle-bosses",{cache:"no-store"});if(!response.ok)throw new Error("クランバトルボス一覧を取得できませんでした。");selectableClanBattle=await response.json();selectableClanBosses=selectableClanBattle.bosses;}
    renderUnitList();
  }
  function renderUnitList(){
    const target=selectorTarget(),source=target==="unit"?selectableUnits:target==="enemy"?selectableEnemies:selectableClanBosses;if(!source)return;
    const query=characterSearch.value.trim().toLocaleLowerCase("ja"),sort=document.querySelector('input[name="character-sort"]:checked').value;
    const preferred=target==="unit"?currentUnitBase:currentEnemy,selected=selectedModelId()||preferred;
    const items=source.filter(item=>!query||String(item.id).includes(query)||item.name.toLocaleLowerCase("ja").includes(query));
    items.sort(sort==="name"?(a,b)=>a.name.localeCompare(b.name,"ja")||a.id-b.id:(a,b)=>a.id-b.id);
    characterList.replaceChildren(...items.map(item=>{const option=document.createElement("button");option.type="button";option.className="model-option";option.dataset.value=item.id;option.setAttribute("role","option");option.textContent=`${item.id}  ${item.name}${target==="unit"&&item.hasRarity6?"  ★6":""}`;option.addEventListener("click",()=>selectModelOption(item.id));option.addEventListener("dblclick",()=>document.querySelector("#show-character").click());return option;}));
    const next=items.some(item=>item.id===selected)?selected:items[0]?.id;if(next)selectModelOption(next);
    configureSelectorOptions();selectorNote.textContent=target==="clan"?`クランバトル ${selectableClanBattle.clanBattleId}（第${selectableClanBattle.phase}段階） / ${items.length}件`:`${items.length}件`;
  }
  function configureSelectorOptions(){
    const enemy=selectorTarget()!=="unit";rarityOptions.hidden=enemy;
    const roomRadio=document.querySelector('input[name="preview-type"][value="2"]');roomRadio.disabled=enemy;
    if(enemy)document.querySelector('input[name="preview-type"][value="1"]').checked=true;
    previewOptions.querySelector("legend").textContent=enemy?"プレビュー（バトルのみ）":"プレビュー";
    if(!enemy)updateRarityChoice();
  }
  function updateRarityChoice(){
    const unit=selectableUnits?.find(item=>item.id===selectedModelId());rarity6.disabled=!unit?.hasRarity6;
    if(rarity6.disabled&&rarity6.checked)document.querySelector('input[name="model-rarity"][value="default"]').checked=true;
  }
  async function openCharacterDialog(){
    try{
      const initialTarget=enemyId!==null?(params.get("source")==="clan"?"clan":"enemy"):"unit",targetRadio=document.querySelector(`input[name="selector-target"][value="${initialTarget}"]`);targetRadio.checked=true;await loadTargetList();
      if(unitId!==null){const suffix=Number(unitId)%100,rarity=suffix===31?"3":suffix===61?"6":"default",radio=document.querySelector(`input[name="model-rarity"][value="${rarity}"]`);if(radio&&!radio.disabled)radio.checked=true;}
      const previewRadio=document.querySelector(`input[name="preview-type"][value="${rawType??"1"}"]`);if(previewRadio&&!previewRadio.disabled)previewRadio.checked=true;
      configureSelectorOptions();characterDialog.showModal();characterSearch.focus();
    }catch(error){selectorFail(error.message);}
  }
  document.querySelector("#open-selector").addEventListener("click",openCharacterDialog);
  characterSearch.addEventListener("input",renderUnitList);
  document.querySelectorAll('input[name="character-sort"]').forEach(radio=>radio.addEventListener("change",renderUnitList));
  document.querySelectorAll('input[name="selector-target"]').forEach(radio=>radio.addEventListener("change",()=>loadTargetList().catch(error=>selectorFail(error.message))));
  document.querySelector("#show-character").addEventListener("click",()=>{
    const base=selectedModelId();if(!base)return;
    const theme=encodeURIComponent(params.get("theme")??document.documentElement.dataset.theme??"system");
    if(selectorTarget()!=="unit"){const source=selectorTarget()==="clan"?"&source=clan":"";location.href=`${location.pathname}?enemyId=${base}&type=1${source}&theme=${theme}`;return;}
    const rarity=document.querySelector('input[name="model-rarity"]:checked').value,target=rarity==="3"?base+30:rarity==="6"?base+60:base;
    const preview=document.querySelector('input[name="preview-type"]:checked').value;location.href=`${location.pathname}?unitId=${target}&type=${preview}&theme=${theme}`;
  });
  if(!hasTarget) openCharacterDialog();
  setupRoomMotionGroups().catch(error=>{if(location.protocol==="file:"){console.warn("Room motion index unavailable in WebView:",error);motionGroupLabel.hidden=true;}else fail(error.message);});
  chooseModel().then(beginLoad).catch(error=>{if(location.protocol==="file:"){console.warn("Model API unavailable in WebView; using sample:",error);beginLoad(sample);}else fail(`モデル索引の読み込みに失敗しました: ${error.message}`);});
})();
