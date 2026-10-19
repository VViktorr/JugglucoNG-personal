#!/usr/bin/env python3
"""Replay production retirement, Libre 3 guards/wake locks and native sensor selection.
Android/JNI dependencies are fakes; the methods under test are read from source.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    end, depth = brace + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

roster = (root / 'Common/src/main/java/tk/glucodata/SensorBluetooth.java').read_text()
base = (root / 'Common/src/main/java/tk/glucodata/SuperGattCallback.java').read_text()
libre = (root / 'Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java').read_text()
watch = (root / 'Common/src/main/cpp/watch/watchvalue.cpp').read_text()

roster_methods = '\n'.join(method(roster, sig) for sig in [
    'public static ArrayList<SuperGattCallback> mygatts(',
    'private static void freeRetiredCallbacks(',
    'public static void retireCloneSensor(',
    'public static void blockLocalCloneConnection(',
    'boolean updateDevicers()',
])
libre_methods = '\n'.join(method(libre, sig) for sig in [
    'private boolean acceptGattCallback(',
    'public synchronized void onCharacteristicChanged(',
    'public synchronized void onReadRemoteRssi(',
    'public synchronized void onDescriptorWrite(',
    'private void onCharacteristicChanged33(',
])
java = '''
import java.util.*;
import java.util.concurrent.*;
class SensorBluetooth {
 static final ArrayList<SuperGattCallback> gattcallbacks=new ArrayList<>();
 static final String LOG_ID="test";
 static boolean doLog=false;
 static SensorBluetooth blueone;
 Object mBluetoothManager=new Object(),mBluetoothAdapter=new Object();
 int increasedwait;static final int startincreasedwait=1;
 void destruct(){}boolean initializeBluetooth(){return true;}void addReceivers(){}boolean connectDevices(long n){return true;}
 void stopScan(boolean b){LifecycleReplay.check(!Thread.holdsLock(gattcallbacks),"scan stop outside roster monitor");}
 static String[] filterActiveSensorNames(String[] s){return s;}
 static boolean isValidShortSensorName(String s){return s!=null&&!s.isEmpty();}
 static ArrayList<String> distinctRuntimeSensorIds(ArrayList<String>s){return s;}
 static int consumeMatchingDeviceIds(String[] names,SuperGattCallback c){int n=0;for(int i=0;i<names.length;i++)if(Objects.equals(names[i],c.SerialNumber)){names[i]=null;n++;}return n;}
 static boolean hasPersistedManagedRecord(String s){return false;}
 static boolean shouldSuppressGenericManagedShell(String s){return false;}
 static long resolvePersistedManagedDataptr(String s){return 0;}
 static SuperGattCallback getGattCallback(String s,long p){return new SuperGattCallback(s);}
 static void adoptCurrentSensorIfBlank(String s){}
 void rehomeCurrentSensorAfterRemoval(String s,List<String>c){if(Objects.equals(SensorIdentity.main,s))SensorIdentity.main=c.isEmpty()?"":c.get(0);}
 static String resolveReplacementSensorSerial(String s){return gattcallbacks.isEmpty()?null:gattcallbacks.get(0).SerialNumber;}
 static void setCurrentSensorSelection(String s){SensorIdentity.main=s;}
 ROSTER_METHODS
}
class SuperGattCallback {
 volatile boolean stop, retired;
 String SerialNumber;
 boolean freed,stopHealth;
 SuperGattCallback(String s){SerialNumber=s;}
 RETIRE_METHOD
 synchronized void free(){LifecycleReplay.check(!Thread.holdsLock(SensorBluetooth.gattcallbacks),"free outside roster monitor");retire();freed=true;}
 synchronized void setPause(boolean pause){if(!retired)stop=pause;}
 synchronized void closeGattTransport(){}
}
class SensorIdentity {
 static String main="main";
 static boolean matches(String a,String b){return Objects.equals(a,b);}
 static String resolveMainSensor(){return main;}
}
class ManagedSensorIdentityRegistry {
 static final ManagedSensorIdentityRegistry INSTANCE=new ManagedSensorIdentityRegistry();
 List<String> persistedSensorIds(App a){return List.of();}
 SuperGattCallback createManagedCallback(App a,String s,long p){return new SuperGattCallback(s);}
}
class Natives {
 static void setmaxsensors(int n){}static boolean getusebluetooth(){return true;}
 static String[] activeSensors(){return new String[]{"secondary"};}static long getdataptr(String s){return 1;}
}
class Log {static void i(String t,String m){} static void d(String t,String m){} static void w(String t,String m){} static void e(String t,String m){} static void stack(String t,String m,Throwable e){throw new AssertionError(e);}}
class BluetoothGatt {}
class BluetoothGattCharacteristic {
 UUID uuid; BluetoothGattCharacteristic(UUID u){uuid=u;}
 UUID getUuid(){return uuid;} byte[] getValue(){return new byte[0];}
}
class BluetoothGattDescriptor {BluetoothGattCharacteristic getCharacteristic(){return new BluetoothGattCharacteristic(Libre3GattCallback.LIBRE3_CHAR_GLUCOSE_DATA);}}
class PowerManager {
 static final int PARTIAL_WAKE_LOCK=1;
 static class WakeLock {boolean held;long timeout;void acquire(long t){held=true;timeout=t;}}
 WakeLock last;WakeLock newWakeLock(int type,String tag){return last=new WakeLock();}
}
class Applic {
 static final App app=new App();
 static boolean usewakelock=true;static final long GLUCOSE_WAKELOCK_TIMEOUT_MS=15000;
 static void releasewakelock(PowerManager.WakeLock w){if(w!=null)w.held=false;}
}
class App {PowerManager manager=new PowerManager(); Object getSystemService(String key){return manager;}}
class Libre3GattCallback {
 static boolean doLog=false;
 static final int GATT_SUCCESS=0;
 static final String LOG_ID="test",POWER_SERVICE="power";
 static final App app=new App();
 CONST_UUIDS
 String SerialNumber="main"; long dataptr=1;
 boolean retired,stop,shouldenablegattCharCommandResponse,lastphase5,fail;
 int readrssi=17,packets,enabled,descriptors;
 BluetoothGatt mBluetoothGatt=new BluetoothGatt();
 void glucose_data(byte[] v){packets++;if(fail)throw new IllegalStateException("packet failed");}
 void logcharacter(UUID u,String s,byte[] v){}
 void receivedpatchstatus(byte[] v){}void save_history(byte[] v){}void access1100(byte[] v){}
 int getsecdata(byte[] v){return 0;}void receivedCERT_DATA(){}void receivedCHALLENGE_DATA(){}
 void preparedata(byte[] v){}void logevent(byte[] v){}void fast_data(byte[] v){}
 void dodisconnect(BluetoothGatt g){}void disconnected(int i){}
 void enablegattCharCommandResponse(){enabled++;}
 void handleonDescriptorWrite(BluetoothGattCharacteristic c){descriptors++;}
 static final String charglucosedata="glucose";
 LIBRE_METHODS
}
public class LifecycleReplay {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  SuperGattCallback main=new SuperGattCallback("main"),secondary=new SuperGattCallback("secondary");
  SensorBluetooth.gattcallbacks.add(main);SensorBluetooth.gattcallbacks.add(secondary);
  CountDownLatch callbackLocked=new CountDownLatch(1);
  java.util.concurrent.atomic.AtomicReference<Throwable> readerFailure=new java.util.concurrent.atomic.AtomicReference<>();
  Thread reader=new Thread(()->{try{synchronized(main){
   callbackLocked.countDown();
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
   while(!main.retired && System.nanoTime()<deadline)Thread.yield();
   check(main.retired,"retirement published before waiting for callback");
   check(!SensorBluetooth.mygatts().contains(main),"roster readable while free waits for callback");
  }}catch(Throwable t){readerFailure.set(t);}});
  reader.start();check(callbackLocked.await(2,TimeUnit.SECONDS),"callback started");
  Thread remover=new Thread(()->new SensorBluetooth().updateDevicers());remover.start();
  reader.join(4000);remover.join(4000);
  check(!reader.isAlive()&&!remover.isAlive(),"retirement deadlock");
  if(readerFailure.get()!=null)throw new AssertionError(readerFailure.get());
  check(main.freed && secondary==SensorBluetooth.mygatts().get(0),"only target retired");
  check(SensorIdentity.main.equals("secondary"),"automatic main sensor replacement preserved");
  main.setPause(false);check(main.stop,"retirement cannot be undone by resume");
  SensorBluetooth.blockLocalCloneConnection("secondary");check(secondary.stop,"clone pause preserved");
  SensorBluetooth.retireCloneSensor("secondary");check(secondary.freed&&SensorBluetooth.mygatts().isEmpty(),"clone removal detached and freed");
  Libre3GattCallback l=new Libre3GattCallback();BluetoothGatt old=new BluetoothGatt();
  BluetoothGattCharacteristic ch=new BluetoothGattCharacteristic(Libre3GattCallback.LIBRE3_CHAR_GLUCOSE_DATA);
  l.shouldenablegattCharCommandResponse=true;
  l.onCharacteristicChanged(old,ch);l.onReadRemoteRssi(old,-80,0);l.onDescriptorWrite(old,new BluetoothGattDescriptor(),0);
  check(l.packets==0&&l.enabled==0&&l.descriptors==0&&l.readrssi==17,"stale callbacks cannot mutate stream/auth/RSSI");
  l.onReadRemoteRssi(l.mBluetoothGatt,-70,0);l.onDescriptorWrite(l.mBluetoothGatt,new BluetoothGattDescriptor(),0);
  l.onCharacteristicChanged(l.mBluetoothGatt,ch);
  check(l.packets==1&&l.enabled==1&&l.descriptors==1,"active callbacks still process");
  check(!l.app.manager.last.held && l.app.manager.last.timeout==15000,"bounded wake lock released normally");
  l.fail=true;
  try{l.onCharacteristicChanged(l.mBluetoothGatt,ch);throw new AssertionError("expected packet failure");}catch(IllegalStateException expected){}
  check(!l.app.manager.last.held,"wake lock released after exception");
  l.retired=true;l.stop=false;l.onCharacteristicChanged(l.mBluetoothGatt,ch);
  check(l.packets==2,"retired callback remains inert even if pause flag changed");
  System.out.println("PASS: concurrent retirement/main handover, Libre 3 stale callbacks and bounded exception-safe wake lock");
 }
}
'''
uuids = sorted(set(re.findall(r'LIBRE3_(?:SEC_)?CHAR_[A-Z_]+', libre_methods)))
java = java.replace('ROSTER_METHODS', roster_methods).replace('RETIRE_METHOD', method(base,'final void retire(')).replace('LIBRE_METHODS',libre_methods)
java = java.replace('CONST_UUIDS', '\n'.join(f'static final UUID {n}=new UUID(0,{i+1});' for i,n in enumerate(uuids)))
selector = method(watch, 'std::pair<const SensorGlucoseData *,int> getlaststream(const uint32_t nu) {')
jni_time = method(watch, 'extern "C" JNIEXPORT jlong  JNICALL   fromjava(lastglucosetime)')
jni_value = method(watch, 'extern "C" JNIEXPORT jobject JNICALL fromjava(lastglucose)')
cpp = '''
#include <vector>
#include <utility>
#include <cstdint>
#include <cassert>
#include <cstdio>
using jlong=long long;using jobject=const void*;using jclass=void*;struct JNIEnv{};
#define JNIEXPORT
#define JNICALL
#define fromjava(x) x
struct ScanData {uint32_t t;};
struct SensorGlucoseData {ScanData poll;int streaminterval()const{return 1;}const ScanData*lastValidStream()const{return &poll;}};
struct Info {int current=0;};
struct Sensoren {Info info;SensorGlucoseData hist[2];Info*infoblockptr(){return &info;}SensorGlucoseData*getSensorData(int i){return &hist[i];}};
Sensoren store;Sensoren*sensors=&store;std::vector<int>usedsensors{0,1};
std::vector<int> usedsensorssnapshot(){return usedsensors;}
void keepnet(uint32_t t,uint32_t now,int mins){}
uint32_t fakeNow=1800000000;
#define time(x) fakeNow
const int maxwatchage=330;
jobject lastGlucoseForHistory(JNIEnv*,const SensorGlucoseData*h,int){return h;}
SELECTOR
JNI_TIME
JNI_VALUE
int main(){
 store.hist[0].poll.t=fakeNow-3600;store.hist[1].poll.t=fakeNow-60;
 assert(lastglucosetime(nullptr,nullptr)==(fakeNow-60)*1000LL);
 assert(lastglucose(nullptr,nullptr)==&store.hist[1]);
 store.hist[0].poll.t=fakeNow-120;
 assert(lastglucose(nullptr,nullptr)==&store.hist[0]);
 assert(lastglucosetime(nullptr,nullptr)==(fakeNow-120)*1000LL);
 puts("PASS: stale main falls back to recent secondary; fresh main remains preferred; JNI returns milliseconds");
}
'''.replace('SELECTOR',selector).replace('JNI_TIME',jni_time).replace('JNI_VALUE',jni_value)
with tempfile.TemporaryDirectory() as d:
    tmp=Path(d)
    (tmp/'LifecycleReplay.java').write_text(java)
    subprocess.run(['javac',str(tmp/'LifecycleReplay.java')],check=True)
    subprocess.run(['java','-cp',d,'LifecycleReplay'],check=True,timeout=12)
    (tmp/'selection.cpp').write_text(cpp)
    subprocess.run(['g++','-std=c++17',str(tmp/'selection.cpp'),'-o',str(tmp/'selection')],check=True)
    subprocess.run([str(tmp/'selection')],check=True,timeout=5)
