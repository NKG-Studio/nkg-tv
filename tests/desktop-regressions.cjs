const {chromium}=require('playwright'),fs=require('fs');
(async()=>{
 const b=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const p=await b.newPage({ignoreHTTPSErrors:true});const checks=[];
 const movie={id:'kept',title:'已收藏',description:'',source:'test',url:'demo.mp4'};
 await p.addInitScript(({movie})=>{
  window.__saved={videos:[{id:'bad',title:'x'.repeat(300),url:'https://example.org/a'}],favorites:{kept:movie},history:{kept:{video:movie,time:1,position:1}}};
  window.nativeLoad=async()=>JSON.stringify(window.__saved);
  window.nativeSave=async raw=>{window.__saved=JSON.parse(raw)};
  window.nativeCatalog=()=>new Promise(resolve=>window.__resolveCatalog=resolve);
 },{movie});
 await p.goto('http://127.0.0.1:8765');
 await p.locator('[data-page="favorites"]').click();await p.getByRole('heading',{name:'已收藏',exact:true}).waitFor();checks.push('bad catalog does not discard favorites');
 await p.locator('[data-page="history"]').click();await p.getByRole('heading',{name:'已收藏',exact:true}).waitFor();checks.push('bad catalog does not discard history');
 await p.locator('[data-page="sources"]').click();await p.getByRole('button',{name:'保存并刷新'}).click();
 await p.waitForFunction(()=>!!window.__resolveCatalog);
 await p.getByLabel('目录 JSON').fill(JSON.stringify({format:'nkg-catalog-v1',videos:[{id:'new',title:'新导入',url:'https://example.org/new.mp4'}]}));
 await p.getByRole('button',{name:'验证并导入'}).click();
 await p.evaluate(()=>window.__resolveCatalog({format:'nkg-catalog-v1',videos:[{id:'old',title:'旧响应',url:'https://example.org/old.mp4'}]}));
 await p.waitForTimeout(200);if((await p.evaluate(()=>window.__saved.videos[0].id))!=='new')throw Error('stale response replaced import');checks.push('late response cannot replace imported source');
 for(const [path,wantsAI] of [['cors.mp4',true],['no-cors.mp4',false]]){
  await p.getByLabel('目录 JSON').fill(JSON.stringify({format:'nkg-catalog-v1',videos:[{id:'remote',title:'跨域测试',url:'https://127.0.0.1:8443/'+path}]}));await p.getByRole('button',{name:'验证并导入'}).click();
  await p.locator('[data-page="home"]').click();await p.getByRole('heading',{name:'跨域测试',exact:true}).click();await p.locator('#detail .primary').click();
  await p.waitForFunction(()=>document.querySelector('video')?.currentTime>.2&&!document.querySelector('video').paused&&document.querySelector('video').readyState>=2,{timeout:15000});
  if(wantsAI){await p.locator('#ai').check();await p.waitForFunction(()=>document.querySelector('#enhanced').style.display==='block',{timeout:15000});checks.push('CORS-enabled remote media permits Rust frame inference');}
  else {if(!await p.locator('#ai').isDisabled())throw Error('no-CORS AI not disabled');checks.push('no-CORS media falls back to original playback');}
  await p.locator('[data-page="sources"]').click();
 }
 fs.writeFileSync('artifacts/desktop-regressions.json',JSON.stringify({passed:true,checks,scope:'Linux Chromium + mocked native bridge; Go parser/persistence separately tested'},null,2));
 await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
