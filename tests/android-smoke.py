import subprocess,time,xml.etree.ElementTree as ET,re,json
from pathlib import Path
ADB='/workspace/toolchains/android/platform-tools/adb'
checks=[]
def adb(*args):return subprocess.check_output([ADB,*args],timeout=25).decode(errors='replace')
def tree():
 adb('shell','uiautomator','dump','/sdcard/nkg.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/nkg.xml'))
def click(text,partial=False):
 for node in tree().iter('node'):
  label=node.get('text','')
  if (text in label if partial else label==text):
   x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
 raise AssertionError('missing '+text)
def expect(text):
 assert any(text in n.get('text','') for n in tree().iter('node')),text
 checks.append(text);print('PASS',text,flush=True)
adb('shell','am','force-stop','studio.nkg.tv');adb('shell','am','start','-n','studio.nkg.tv/.MainActivity');time.sleep(2)
expect('你的私人放映室');click('查看详情');expect('开始播放');click('收藏影片');expect('取消收藏');click('开始播放');time.sleep(3);expect('原片播放');adb('shell','input','keyevent','4');expect('取消收藏');click('历史');expect('继续观看');click('收藏');expect('我的收藏');click('来源');expect('内容来源');click('保存并刷新');time.sleep(12);expect('刷新失败');adb('shell','input','keyevent','4');expect('你的私人放映室')
Path('artifacts/android-ui-smoke.json').write_text(json.dumps({'checks':checks,'device':'Android 10 x86_64 software emulator','limitations':'No real device, frame smoothness or sustained AI performance measured'},ensure_ascii=False,indent=2))
