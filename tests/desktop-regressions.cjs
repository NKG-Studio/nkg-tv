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
 await p.getByRole('button',{name:'保存并刷新'}).click();await p.waitForFunction(()=>!!window.__resolveCatalog);await p.locator('[data-page="home"]').click();await p.evaluate(()=>window.__resolveCatalog({format:'nkg-catalog-v1',videos:[{id:'stale-navigation',title:'不应出现',url:'https://example.org/stale.mp4'}]}));await p.waitForTimeout(200);if((await p.evaluate(()=>window.__saved.videos[0].id))!=='new')throw Error('navigation response replaced state');checks.push('late response after navigation is discarded');
 await p.locator('[data-page="sources"]').click();
 for(const [title,ok] of [['中'.repeat(256),true],['a'.repeat(257),false],['😀'.repeat(129),false]]){
  await p.getByLabel('目录 JSON').fill(JSON.stringify({format:'nkg-catalog-v1',videos:[{id:'title',title,url:'https://example.org/title.mp4'}]}));await p.getByRole('button',{name:'验证并导入'}).click();
  if(ok){await p.getByText('已导入 1 部影片',{exact:true}).waitFor();if((await p.evaluate(()=>window.__saved.videos[0].title)).length!==256)throw Error('valid title not persisted');}
  else await p.getByText('导入失败：影片字段无效',{exact:false}).waitFor();
 }
 checks.push('JS title UTF16 limits match native Go: 256 accepted, 257 or 129 emoji rejected');
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
