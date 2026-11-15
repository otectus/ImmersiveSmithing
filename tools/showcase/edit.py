#!/usr/bin/env python3
"""Edit marked, real gameplay into a short captioned MP4 without covering the game interface."""
import argparse,concurrent.futures,json,math,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
FONT='/usr/share/fonts/noto/NotoSans-Regular.ttf'
TITLE='/usr/share/fonts/noto/NotoSerif-Bold.ttf'
def main():
    p=argparse.ArgumentParser();p.add_argument('--take',required=True);a=p.parse_args()
    take=ROOT/'build/showcase'/a.take
    result=json.loads((take/'result.json').read_text());assert result['pass']
    start=int((take/'recording-start-ms').read_text())
    m={x['name']:(x['wall_ms']-start)/1000 for x in result['markers']}
    scenes=[]
    def add(name,begin,duration,caption,card=False):
        scenes.append(dict(name=name,start=max(0,begin),duration=round(duration*30)/30,caption=caption,card=card))
    def span(a,b,cap):
        # From marker a until marker b (or cap seconds), when both moments happened in this take.
        return None if a not in m else min(cap,(m[b]-m[a]-.2) if b in m else cap)
    def scene(name,marker,offset,duration,caption,card=False):
        if marker in m and duration and duration>.6:add(name,m[marker]+offset,duration,caption,card)
    scene('intro','workshop',.7,3.5,'A hands-on smithing mod for Minecraft',True)
    scene('heat','heat',.5,span('heat','hands_on',4),'MELT  /  Fuel the forge. Bring your metal to life.')
    scene('choose','work_pattern_book_live',0,span('work_pattern_book_live','work_job_committed',2.6),'CHOOSE  /  Pick a pattern and cast a billet.')
    scene('forge','work_job_committed',.3,span('work_job_committed','work_billet_ready',6),'HEAT  /  Work the bellows and turn the billet until it glows evenly.')
    scene('draw','work_billet_ready',0,span('work_billet_ready','work_anvil_first_blows',2.4),'DRAW  /  Take it from the fire at the ideal heat.')
    scene('anvil','work_anvil_first_blows',-.3,span('work_anvil_first_blows','work_reheat_on_rest',4.5),'SHAPE  /  Strike where the metal needs it. Every blow moves it.')
    scene('reheat','work_reheat_on_rest',-.2,span('work_reheat_on_rest','work_anvil_half_shaped',2.6),'REHEAT  /  Back on the forge\'s rest. Nothing is lost.')
    scene('anvil_finish','work_shaped',-4.2,4.6,'EVERY BLOW COUNTS  /  Shaping sets efficacy.')
    scene('quench','work_quench_lowered',-.3,span('work_quench_lowered','work_fitting',3.4),'QUENCH  /  Hold it under until the hiss fades.')
    scene('fit','work_fitting',-.2,span('work_fitting','work_finished',2.2),'FIT  /  Add the grip at the anvil.')
    scene('refine','work_grind_contact',-.4,span('work_grind_contact','quality',2.9),'REFINE  /  Hold the edge to the wheel.')
    scene('quality','quality',.2,2.8,'MADE BY YOU  /  Your work determines the finished quality.')
    scene('guide','guide',.2,3.6,'LEARN THE TRADE  /  A readable, searchable Smithing Guide.')
    scene('outro','outro',.5,3.2,'Minecraft Java Edition  /  Forge 1.20.1',True)
    out=ROOT/'output/sneak-peek';out.mkdir(parents=True,exist_ok=True)
    work=take/'edit';work.mkdir(exist_ok=True)
    def render(args):
        i,s=args
        caption=work/f'{i:02d}.txt';caption.write_text(s['caption'])
        vf=['scale=1184:666:flags=lanczos','pad=1280:720:48:0:color=0x131719',
            'drawbox=x=48:y=665:w=1184:h=2:color=0xba8d56:t=fill',
            f'drawtext=fontfile={FONT}:textfile={caption}:fontcolor=0xf1e5cf:fontsize=23:x=64:y=683']
        if s['card']:
            name=work/f'{i:02d}-title.txt';name.write_text('IMMERSIVE SMITHING')
            subtitle=work/f'{i:02d}-subtitle.txt';subtitle.write_text('Gameplay sneak peek' if i==0 else 'Every strike leaves its mark.')
            vf+=['drawbox=x=48:y=0:w=1184:h=666:color=black@0.34:t=fill',
                f'drawtext=fontfile={TITLE}:textfile={name}:fontcolor=0xffe8bb:fontsize=52:x=(w-tw)/2:y=244:shadowcolor=black@0.6:shadowx=2:shadowy=2',
                'drawbox=x=500:y=329:w=280:h=2:color=0xba8d56:t=fill',
                f'drawtext=fontfile={FONT}:textfile={subtitle}:fontcolor=white:fontsize=29:x=(w-tw)/2:y=352']
        if i==0:vf+=['fade=t=in:st=0:d=0.4']
        if i==len(scenes)-1:vf+=[f"fade=t=out:st={s['duration']-.5}:d=0.5"]
        target=work/f'{i:02d}.mp4'
        cmd=['ffmpeg','-hide_banner','-loglevel','error','-y','-ss',str(s['start']),'-t',str(s['duration']),'-i',str(take/'raw.mp4'),
             '-vf',','.join(vf),'-af',f"volume=1.4,alimiter=limit=0.94,afade=t=in:st=0:d=0.035,afade=t=out:st={s['duration']-.05}:d=0.05",
             '-c:v','libx264','-preset','medium','-crf','18','-pix_fmt','yuv420p','-r','30','-threads','3','-c:a','aac','-ar','48000','-ac','2','-b:a','160k',str(target)]
        subprocess.run(cmd,check=True)
        return target
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:clips=list(pool.map(render,enumerate(scenes)))
    listing=work/'concat.txt';listing.write_text(''.join(f"file '{x}'\n" for x in clips))
    final=out/'immersive-smithing-sneak-peek.mp4'
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(listing),'-c','copy','-movflags','+faststart',str(final)],check=True)
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-ss','1.5','-i',str(final),'-frames:v','1',str(out/'preview.png')],check=True)
    (out/'edit.json').write_text(json.dumps({'footage':'Actual Minecraft client; scripted player inputs on the focused work views, every action validated and scored by the server.','take':a.take,'scenes':scenes,'total_seconds':sum(s['duration'] for s in scenes)},indent=2)+'\n')
    print(final)
    print('Duration:',round(sum(s['duration'] for s in scenes),2))
if __name__=='__main__':main()
