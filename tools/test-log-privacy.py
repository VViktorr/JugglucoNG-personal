#!/usr/bin/env python3
"""Compile production Java/native log redactors and exercise pre-write privacy boundaries."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = root / 'Common/src/main/java/tk/glucodata'
cpp = root / 'Common/src/main/cpp'
with tempfile.TemporaryDirectory(prefix='log-privacy-') as folder:
    tmp = Path(folder)
    sources = {
        'tk/glucodata/Applic.java': 'package tk.glucodata; class Applic { static boolean Nativesloaded=false; static java.util.Locale usedlocale=java.util.Locale.US; }',
        'tk/glucodata/BuildConfig.java': 'package tk.glucodata; class BuildConfig { static final int doLog=1; }',
        'tk/glucodata/Natives.java': 'package tk.glucodata; class Natives { static boolean islogging(){return true;} static void log(String s){android.util.Log.lines.add(s);} }',
        'android/os/Process.java': 'package android.os; public class Process {public static int myPid(){return 1;}}',
        'android/util/Log.java': '''package android.util; public class Log {
            public static final java.util.List<String> lines=new java.util.ArrayList<>();
            public static int e(String a,String b){lines.add(a+" "+b);return 0;}
            public static int d(String a,String b){return e(a,b);} public static int i(String a,String b){return e(a,b);}
            public static int w(String a,String b){return e(a,b);} public static int v(String a,String b){return e(a,b);}
            public static String getStackTraceString(Throwable e){return e.toString();}
        }''',
        'tk/glucodata/PrivacyTest.java': r'''package tk.glucodata;
        public class PrivacyTest {
            static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
            public static void main(String[] args){
                String serial="0M000DV8V60", mac="AA:BB:CC:DD:EE:FF", key="deadbeefcafebabe0011223344556677";
                String alias=LogPrivacy.sensorAlias(serial);
                LogPrivacy.registerAddress(serial,mac);
                check(alias.equals(LogPrivacy.sensorAlias(serial)),"alias changed");
                check(!alias.equals(LogPrivacy.sensorAlias("0M000DV8V61")),"sensors share alias");
                check(LogPrivacy.sanitize(serial+" "+mac).equals(alias+" "+alias),"identity correlation lost");
                for(String raw:new String[]{"passcode: "+key,"Authenticationdata: "+key,"UID=0011223344556677",
                    "access_token=credential", "dataptr=12345", "challenge="+key, "getStreamingUnlockPayload("+key+")"}){
                    check(LogPrivacy.sanitize(raw).contains("sensitive log omitted"),"secret label leaked: "+raw);
                }
                String raw=serial+" "+mac+" 0x7abcde1234 "+key+" 01 02 03 04 05 06 07 08 glucose=123 status=133 rssi=-70";
                String safe=LogPrivacy.sanitize(raw);
                check(safe.contains(alias)&&safe.contains("glucose=123")&&safe.contains("status=133"),"useful diagnostic lost");
                for(boolean loaded:new boolean[]{false,true}){
                    Applic.Nativesloaded=loaded;
                    Log.d("test",raw);Log.i("test",raw);Log.w("test",raw);Log.v("test",raw);Log.e("test",raw);
                    Log.info(raw);Log.format("%s",raw);Log.showbytes("challenge",new byte[]{1,2,3});
                    Log.stack("test",serial,new RuntimeException("unlabelled-sensitive-value"));
                    Log.doLog=false;Log.e("test",raw);Log.stack("test",serial,new RuntimeException("unlabelled-sensitive-value"));
                    Log.doLog=true;
                }
                SensorTraceRing.add('I',serial,raw);
                String all=String.join("\n",android.util.Log.lines)+String.join("\n",SensorTraceRing.snapshot());
                for(String secret:new String[]{serial,mac,key,"0x7abcde1234","01 02 03 04 05 06 07 08","unlabelled-sensitive-value"})
                    check(!all.contains(secret),"sink leaked "+secret);
                System.out.println("PASS: Java aliases, pre-ring/pre-write redaction, native-not-loaded fallback and exception privacy");
            }
        }'''
    }
    for pkg, name in [('android.bluetooth.le','ScanFilter'),('android.bluetooth.le','ScanSettings'),('android.bluetooth','BluetoothDevice'),('dalvik.system','BaseDexClassLoader')]:
        sources[pkg.replace('.','/')+'/'+name+'.java'] = f'package {pkg}; public class {name} {{}}'
    sources['androidx/annotation/Keep.java']='package androidx.annotation; public @interface Keep {}'
    for filename, content in sources.items():
        path=tmp/filename;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
    subprocess.run(['javac','-d',str(tmp),*[str(p) for p in tmp.rglob('*.java')],
                    *[str(java/name) for name in ['LogPrivacy.java','Log.java','SensorTraceRing.java']]],check=True)
    subprocess.run(['java','-cp',str(tmp),'tk.glucodata.PrivacyTest'],check=True)
    harness=tmp/'privacy.cpp'
    harness.write_text(r'''
#include "logprivacy.hpp"
#ifndef NOLOG
#include "logs.hpp"
#endif
#include <cassert>
#include <cstring>
#include <string>
#include <iostream>
int main(){
    privacy_log_identity("0M000DV8V60","sensor-1");
    privacy_log_identity("AA:BB:CC:DD:EE:FF","sensor-1");
    std::string raw="0M000DV8V60 AA:BB:CC:DD:EE:FF 0x7abcde1234 deadbeefcafebabe0011223344556677 01 02 03 04 05 06 07 08 glucose=123 status=133";
    std::string safe=privacy_log_message(raw.data(),raw.size());
    for(const auto *secret:{"0M000DV8V60","AA:BB:CC:DD:EE:FF","0x7abcde1234","deadbeefcafebabe0011223344556677","01 02 03 04 05 06 07 08"}) assert(safe.find(secret)==std::string::npos);
    assert(safe.find("sensor-1")!=std::string::npos&&safe.find("glucose=123")!=std::string::npos);
    for(const auto *secret:{"passcode: abcd", "Authenticationdata: abcd", "getStreamingUnlockPayload(abc)", "token=abc", "dataptr=123"})
        assert(std::strstr(privacy_log_message(secret,std::strlen(secret)),"sensitive log omitted"));
    #ifndef NOLOG
    logprint("%s\n",raw.c_str());
    LOGGERN(raw.data(),raw.size());
    logprint("passcode: do-not-write-this\n");
#endif
    std::cout<<"PASS: native log aliases and credential/payload/pointer redaction\n";
}
''')
    subprocess.run(['g++','-std=c++17','-DNOLOG','-DNOTAPP','-I'+str(cpp),'-I'+str(cpp/'share'),
                    str(cpp/'share/logs.cpp'),str(harness),'-o',str(tmp/'privacy')],check=True)
    subprocess.run([str(tmp/'privacy')],check=True)
    subprocess.run(['g++','-std=c++20','-DNOTAPP','-I'+str(cpp),'-I'+str(cpp/'share'),
                    str(cpp/'share/logs.cpp'),str(harness),'-o',str(tmp/'privacy-writer')],check=True)
    result=subprocess.run([str(tmp/'privacy-writer')],check=True,capture_output=True,text=True)
    assert 'sensor-1' in result.stderr and 'glucose=123' in result.stderr
    for secret in ['0M000DV8V60','AA:BB:CC:DD:EE:FF','0x7abcde1234','deadbeefcafebabe0011223344556677','do-not-write-this']:
        assert secret not in result.stderr, secret
    print('PASS: native formatted and vectored writers redact before writing')

    android=tmp/'android/log.h';android.parent.mkdir(exist_ok=True)
    android.write_text("""#pragma once
#include <stdio.h>
#include <stdarg.h>
#define ANDROID_LOG_INFO 4
static inline int __android_log_print(int level,const char *tag,const char *fmt,...) {
    va_list args;va_start(args,fmt);int n=vfprintf(stderr,fmt,args);va_end(args);return n;
}
static inline int __android_log_write(int level,const char *tag,const char *msg) {return fputs(msg,stderr);}
""")
    subprocess.run(['g++','-std=c++20','-DLOGCAT','-D__ANDROID__','-DNOTAPP','-I'+str(tmp),
                    '-I'+str(cpp),'-I'+str(cpp/'share'),str(cpp/'share/logs.cpp'),str(harness),
                    '-o',str(tmp/'privacy-logcat')],check=True)
    result=subprocess.run([str(tmp/'privacy-logcat')],check=True,capture_output=True,text=True)
    for secret in ['0M000DV8V60','AA:BB:CC:DD:EE:FF','0x7abcde1234','deadbeefcafebabe0011223344556677','do-not-write-this']:
        assert secret not in result.stderr, secret
    assert 'sensor-1' in result.stderr
    print('PASS: native LOGCAT pre-write redaction')
