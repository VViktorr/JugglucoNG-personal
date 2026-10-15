"""Focused JVM regression tests for the imported sound and speech code.

Run with Python 3 and JDK 17: python tools/tests/audio_port_regression.py [repo-root]
Android services use recording stubs; this does not replace testing on a phone.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


FILES = {
    'android/util/Log.java': '''package android.util;
public class Log { public static int w(String a,String b,Throwable c){return 0;} }
''',
    'android/media/audiofx/AudioEffect.java': '''package android.media.audiofx;
public class AudioEffect { public static final int SUCCESS=0; }
''',
    'android/media/audiofx/LoudnessEnhancer.java': '''package android.media.audiofx;
public class LoudnessEnhancer {
 public LoudnessEnhancer(int id){} public void setTargetGain(int n){}
 public int setEnabled(boolean b){return 0;} public void release(){}
}
''',
    'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION {public static int SDK_INT=34;}
 public static class VERSION_CODES {public static final int P=28;} }
''',
    'android/media/Ringtone.java': '''package android.media;
public class Ringtone {public void setVolume(float n){}public void play(){}public void stop(){}}
''',
    'android/media/MediaPlayer.java': '''package android.media;
public class MediaPlayer {
 public interface OnErrorListener {boolean onError(MediaPlayer player,int what,int extra);}
 public OnErrorListener error;public float volume;public int writes,releases;public boolean playing;
 public void setOnErrorListener(OnErrorListener e){error=e;}
 public void setVolume(float a,float b){if(releases>0)throw new AssertionError("write after release");volume=a;writes++;}
 public int getAudioSessionId(){return 7;}public void seekTo(int n){}public void start(){playing=true;}
 public boolean isPlaying(){return playing;}public void stop(){playing=false;}public void reset(){}
 public void release(){releases++;}
}
''',
    'android/speech/tts/TextToSpeech.java': '''package android.speech.tts;
import java.util.*;
public class TextToSpeech {
 public static final int SUCCESS=0, ERROR=-1, QUEUE_FLUSH=0, QUEUE_ADD=1;
 public final List<String> events=new ArrayList<>();
 public boolean rejectEarcons, rejectWords;
 public Runnable beforeCall=()->{};
 public int playEarcon(String text,int mode,Object params,String id) {
  beforeCall.run();events.add("earcon:"+mode+":"+text);
  return rejectEarcons?ERROR:SUCCESS;
 }
 public int speak(String text,int mode,Object params,String id) {
  beforeCall.run();events.add("words:"+mode+":"+text);
  return rejectWords?ERROR:SUCCESS;
 }
}
''',
    'tk/glucodata/alerts/SoundRegression.java': '''package tk.glucodata.alerts;
public class SoundRegression {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Fake implements AlertSoundBoost.Effect {
  int gain,releases;boolean enabled,fail;
  public void targetGain(int n){gain=n;} public void enable(){if(fail)throw new IllegalStateException();enabled=true;}
  public void release(){releases++;}
 }
 public static void main(String[] args) {
  check(AlertSoundVolume.sanitize(-1)==30 && AlertSoundVolume.sanitize(110)==100);
  check(AlertSoundVolume.DEFAULT_PERCENT==80);
  check(AlertSoundVolume.boostMillibels(80)==0 && AlertSoundVolume.boostMillibels(100)==600);
  check(AlertSoundVolume.preferenceKey(0).equals("alert_0_soundVolumePercent"));
  check(!AlertSoundVolume.preferenceKey(0).equals(AlertSoundVolume.preferenceKey(1)));
  for(int percent=30;percent<=100;percent++) {
   for(String profile:new String[]{"SOFT","STEADY","STRONG","ESCALATING"}) {
    float previous=0;
    for(int step=0;step<=8;step++) {
     float gain=AlertSoundVolume.fadeGain(percent,profile,step/8f);
     check(gain>0 && gain>=previous && gain<=1);previous=gain;
    }
    check(Math.abs(previous-AlertSoundVolume.gain(percent,profile))<0.00001f);
   }
  }
  check(AlertSoundBoost.attach(0,100,id->{throw new AssertionError("shared mix");})==null);
  check(AlertSoundBoost.attach(2,80,id->{throw new AssertionError("default boost");})==null);
  Fake effect=new Fake();AlertSoundBoost boost=AlertSoundBoost.attach(2,100,id->effect);
  check(boost!=null && effect.enabled && effect.gain==600);
  boost.close();boost.close();check(effect.releases==1);
  Fake failing=new Fake();failing.fail=true;
  check(AlertSoundBoost.attach(2,100,id->failing)==null && failing.releases==1);
  System.out.println("PASS: volume bounds, profiles, fade, per-alert keys and session-local boost cleanup");
 }
}
''',
    'tk/glucodata/SpeechRegression.java': '''package tk.glucodata;
import java.util.*;
import android.speech.tts.TextToSpeech;
public class SpeechRegression {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Audio implements SpeechFocus.Audio {
  boolean held;int releases;
  public void request(){held=true;} public void abandon(){held=false;releases++;}
 }
 static class Engine implements LeadIn.Engine {
  List<String> calls=new ArrayList<>();boolean rejectSilence,rejectChime,rejectWords;
  public int playEarcon(String text,int mode,String id) {
   if((text.equals(LeadIn.SILENCE_EARCON)&&rejectSilence)||(text.equals(LeadIn.CHIME_EARCON)&&rejectChime))return -1;
   calls.add("earcon:"+mode+":"+text);return 0;
  }
  public int speak(String text,int mode,String id){calls.add("words:"+mode+":"+text);return rejectWords?-1:0;}
 }
 public static void main(String[] args) {
  Engine e=new Engine();check(LeadIn.queue(e,"5.8","a",true)==0);
  check(e.calls.equals(Arrays.asList("earcon:0:[lead_in]","earcon:1:[glucose_chime]","words:1:5.8")));
  e=new Engine();e.rejectSilence=true;e.rejectChime=true;
  check(LeadIn.queue(e,"5.8","a",true)==0 && e.calls.equals(Arrays.asList("words:0:5.8")));
  e=new Engine();e.rejectWords=true;check(LeadIn.queue(e,"5.8","a",false)==-1);
  Audio a=new Audio();SpeechFocus f=new SpeechFocus(a);
  f.claim("a");f.claim("b");f.release("a");check(a.held && a.releases==0);
  f.release(null);check(a.held);f.release("b");check(!a.held && a.releases==1);
  f.release("b");check(a.releases==1);
  check(LeadIn.isLeadIn(LeadIn.ID) && !LeadIn.isLeadIn("voice_preview"));
  System.out.println("PASS: lead-in/chime order, refusal fallback and stale speech-focus callbacks");
 }
}
''',
    'tk/glucodata/TalkerRegression.java': '''package tk.glucodata;
import android.speech.tts.TextToSpeech;
import java.util.concurrent.atomic.AtomicLong;
public class TalkerRegression {
 static boolean DontTalk=false,doLog=false,notifyfocus=false,engineReady=true;
 TextToSpeech engine=new TextToSpeech();
 final Object queueLock=new Object();
 static final AtomicLong utteranceSeq=new AtomicLong();
 String lastWordsId;
 static final SpeechRegression.Audio audio=new SpeechRegression.Audio();
 static final SpeechFocus focus=new SpeechFocus(audio);
 final Health health=new Health();static final String LOG_ID="test";
 static class Health {void record(boolean a,boolean b){}int consecutiveFailures(){return 0;}}
 static class Log {static void i(String a,String b){}static void e(String a,String b){}static void stack(String a,String b,Throwable c){}}
 static void ensureMinStreamVolume(){}
 // PRODUCTION_SPEAK
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args) {
  TalkerRegression t=new TalkerRegression();
  t.engine.beforeCall=()->check(audio.held);
  check(t.speak("5.8",true));
  check(t.engine.events.equals(java.util.Arrays.asList("earcon:0:[lead_in]","earcon:1:[glucose_chime]","words:1:5.8")));
  String first=t.lastWordsId;check(t.speak("5.9",false));
  focus.release(first);check(audio.held);
  focus.release(t.lastWordsId);check(!audio.held);
  t.engine.rejectWords=true;check(!t.speak("6.0",false));check(!audio.held);
  t.engine=null;check(!t.speak("6.1",false));check(!audio.held);
  System.out.println("PASS: actual Talker speak takes focus before audio and releases it on queue failure");
 }
}
''',
    'tk/glucodata/PlaybackRegression.java': '''package tk.glucodata;
import android.media.*;import android.os.Build;import java.util.*;import java.util.concurrent.TimeUnit;
public class PlaybackRegression {
 static final String LOG_ID="test";
 static class Log {static void w(String a,String b){}static void stack(String a,String b,Throwable t){}}
 static class Scheduler {
  final List<Runnable> tasks=new ArrayList<>();
  void schedule(Runnable task,long millis,TimeUnit unit){tasks.add(task);}
  void drain(){List<Runnable> copy=new ArrayList<>(tasks);tasks.clear();copy.forEach(Runnable::run);}
 }
 static class Applic {static final Scheduler scheduler=new Scheduler();}
 // PRODUCTION_PLAYER
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args) {
  MediaPlayer p=new MediaPlayer();AlertSoundHandle h=new AlertSoundHandle(null,p,"test");
  h.configureVolume(55,"STRONG");h.play();Applic.scheduler.drain();check(Math.abs(p.volume-0.55f)<0.001f);
  h.play();h.stop();int before=p.writes;Applic.scheduler.drain();check(p.writes==before && p.releases==1);
  h.stop();check(p.releases==1);
  p=new MediaPlayer();h=new AlertSoundHandle(null,p,"test");h.configureVolume(100,"STRONG");h.play();
  p.error.onError(p,1,1);before=p.writes;Applic.scheduler.drain();check(p.writes==before && p.releases==1);
  System.out.println("PASS: actual alert player volume, cancellation of stale fade tasks and error cleanup");
 }
}
''',
}


def main(root):
    sources = root / 'Common/src/main/java'
    files = dict(FILES)
    talker = (sources / 'tk/glucodata/Talker.java').read_text()
    files['tk/glucodata/TalkerRegression.java'] = files['tk/glucodata/TalkerRegression.java'].replace(
        '// PRODUCTION_SPEAK', method(talker, 'boolean speak(String message,boolean chime)'))
    notify = (sources / 'tk/glucodata/Notify.java').read_text()
    files['tk/glucodata/PlaybackRegression.java'] = files['tk/glucodata/PlaybackRegression.java'].replace(
        '// PRODUCTION_PLAYER', method(notify, 'private static final class AlertSoundHandle'))
    with tempfile.TemporaryDirectory(prefix='audio-port-test-') as directory:
        temp = Path(directory)
        for path in ('tk/glucodata/LeadIn.java', 'tk/glucodata/SpeechFocus.java',
                     'tk/glucodata/alerts/AlertSoundVolume.java', 'tk/glucodata/alerts/AlertSoundBoost.java'):
            files[path] = (sources / path).read_text()
        for path, content in files.items():
            target = temp / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        command = [os.environ.get('JAVAC', 'javac'), '-d', str(temp / 'classes')]
        command += [str(temp / path) for path in files]
        subprocess.run(command, check=True, timeout=30)
        for name in ('tk.glucodata.alerts.SoundRegression', 'tk.glucodata.SpeechRegression', 'tk.glucodata.TalkerRegression', 'tk.glucodata.PlaybackRegression'):
            subprocess.run([os.environ.get('JAVA', 'java'), '-cp', str(temp / 'classes'), name], check=True, timeout=15)
    locales = sorted((root / 'Common/src/main/res').glob('values*/strings.xml'))
    for path in locales:
        doc = ET.parse(path).getroot()
        names = [x.get('name') for x in doc if x.tag == 'string']
        for key in ('alert_sound_volume', 'voice_chime_title', 'voice_chime_desc'):
            assert names.count(key) == 1, (path, key)
    print('PASS: new strings occur exactly once in each locale')


if __name__ == '__main__':
    main(Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
