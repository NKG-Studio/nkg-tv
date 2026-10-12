#!/usr/bin/env python3
"""Independent FFprobe/FFmpeg audit, outside Android's extractor timestamp model."""
import hashlib,json,subprocess,sys
from pathlib import Path
original,output=map(Path,sys.argv[1:3])
def probe(p):return json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_packets','-of','json',str(p)]))
def audio(p,r):
 idx=next(s['index'] for s in r['streams'] if s['codec_type']=='audio');packets=[x for x in r['packets'] if x['stream_index']==idx];data=p.read_bytes();h=hashlib.sha256()
 for x in packets:h.update(data[int(x['pos']):int(x['pos'])+int(x['size'])])
 pcm=subprocess.check_output(['ffmpeg','-v','error','-i',str(p),'-map','0:a:0','-f','s16le','-'])
 stream=next(s for s in r['streams'] if s['index']==idx)
 return dict(packets=len(packets),pts=[x['pts_time'] for x in packets],payload_sha256=h.hexdigest(),pcm_sha256=hashlib.sha256(pcm).hexdigest(),pcm_bytes=len(pcm),start=stream['start_time'],duration=stream['duration'],first_packet_side_data=packets[0].get('side_data_list',[]))
a,b=probe(original),probe(output);before,after=audio(original,a),audio(output,b)
assert before==after, 'Audio packets, PTS, edit/priming, stream duration or decoded PCM changed'
video=lambda r:[x['pts_time'] for x in r['packets'] if x['codec_type']=='video']
assert video(a)==video(b), 'video PTS changed'
print(json.dumps(dict(passed=True,scope='Independent FFprobe packet timeline and FFmpeg decoded audio; not listening test',original=str(original),output=str(output),video_frames=len(video(b)),audio=after),indent=2))
