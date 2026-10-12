const {chromium}=require('playwright'),fs=require('fs');
(async()=>{
 const b=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const checks=[];
 for(const width of [1180,900,600]){
  const p=await b.newPage({viewport:{width,height:780}});
  await p.goto('http://127.0.0.1:8765');await p.getByText('把好故事，留给大屏幕。').waitFor();
  async function shot(name){await p.waitForTimeout(350);await p.screenshot({path:'docs/screenshots/desktop-'+width+'-'+name+'.png',fullPage:false});const size=await p.evaluate(()=>({viewport:innerWidth,scroll:document.documentElement.scrollWidth,body:document.body.scrollWidth,dialogOverflow:document.querySelector('dialog[open]')?document.querySelector('dialog[open]').scrollWidth-document.querySelector('dialog[open]').clientWidth:0}));checks.push({width,page:name,...size,noHorizontalOverflow:size.scroll<=width&&size.body<=width&&size.dialogOverflow<=1});}
  await shot('home');
  await p.locator('[data-page="sources"]').click();await p.getByLabel('目录 JSON').fill(JSON.stringify({format:'nkg-catalog-v1',videos:[{id:'long',title:'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(9),description:'长描述 '.repeat(100),source:'SOURCE'.repeat(60),url:'https://example.org/a.mp4'}]}));await p.getByRole('button',{name:'验证并导入'}).click();
  await p.locator('[data-page="home"]').click();await shot('long-title');await p.locator('.card').nth(1).click();await shot('detail');
  await p.locator('#closeDetail').click();await p.locator('.hero .primary').click();await p.waitForFunction(()=>document.querySelector('video')?.currentTime>.2);await shot('player');
  await p.getByRole('button',{name:'← 返回影片详情',exact:true}).click();await p.locator('#detail[open]').waitFor();if(await p.evaluate(()=>window.scrollY)!==0)throw Error('navigation kept stale scroll');await p.locator('#closeDetail').click();await p.locator('[data-page="sources"]').click();await shot('sources');await p.close();
 }
 fs.writeFileSync('artifacts/desktop-layout.json',JSON.stringify({passed:checks.every(x=>x.noHorizontalOverflow),scope:'Linux Chromium rendering of desktop UI, not Windows screenshot or smoothness measurement',checks},null,2));await b.close();
 if(!checks.every(x=>x.noHorizontalOverflow))process.exitCode=1;
})().catch(e=>{console.error(e);process.exit(1)});
