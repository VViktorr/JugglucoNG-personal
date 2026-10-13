"""Focused regression checks; run with Python 3, C++17 and JDK 17 installed.

Usage: python tools/tests/history_recovery_regression.py [repository-root]
Compiles extracted production methods against small recording fixtures.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def run(command, cwd):
    subprocess.run(command, cwd=cwd, check=True, timeout=30)


def main(root):
    native = (root / 'Common/src/main/cpp/g.cpp').read_text()
    bluetooth = (root / 'Common/src/main/java/tk/glucodata/SensorBluetooth.java').read_text()
    export = method(native, 'static void appendStoredHistory(')
    # Match either the old synchronized declaration or the fixed declaration.
    removal_start = bluetooth.rfind('    private ', 0, bluetooth.index('void removeDevice(String'))
    removal = method(bluetooth[removal_start:], bluetooth[removal_start:bluetooth.index('{', removal_start)])
    remove_all = method(bluetooth, 'private void removeDevices()')
    free = method(bluetooth, 'private static void freeRetiredCallbacks(')
    cpp = r'''
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <iostream>
#include <map>
#include <utility>
#include <vector>
using jlong = long long;
using HistorySamples = std::map<jlong, std::pair<jlong,jlong>>;
struct Glucose {
    jlong time; uint16_t autoValue, raw; bool good = true;
    bool valid() const { return good; }
    jlong gettime() const { return time; }
    uint16_t getsputnik() const { return autoValue; }
    uint16_t getraw() const { return raw; }
};
struct SensorGlucoseData {
    std::vector<Glucose> rows; bool dexcom = false;
    bool isDexcom() const { return dexcom; }
    int getstarthistory() const { return 0; }
    int getAllendhistory() const { return rows.size(); }
    int maxpos() const { return rows.size(); }
    const Glucose *getglucose(int index) const { return &rows[index]; }
};
// EXPORT
int main() {
    SensorGlucoseData sensor{{{100,1100,950}, {101,1110,0}, {102,1120,65535},
                             {103,1130,379}, {104,1140,5021}, {105,1150,380},
                             {106,1160,5020}, {107,1170,1000,false}}};
    HistorySamples result;
    appendStoredHistory(&sensor, 0, result);
    assert(result.at(100).first == 1100 && result.at(100).second == 950);
    for (int t=101; t<=104; ++t) assert(result.at(t).second == 0);
    assert(result.at(105).second == 380 && result.at(106).second == 5020);
    assert(result.count(107) == 0);
    result.clear();
    appendStoredHistory(&sensor, 105, result);
    assert(result.size() == 1 && result.count(106) == 1);
    result.clear(); sensor.dexcom = true;
    appendStoredHistory(&sensor, 0, result);
    assert(result.empty());
    std::cout << "PASS: stored NFC Auto/Raw, invalid Raw, start time and Dexcom exclusion\n";
}
'''.replace('// EXPORT', export)
    java = r'''
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class RemovalRegression {
    static final boolean doLog = false;
    static final String LOG_ID = "test";
    static final ArrayList<SuperGattCallback> gattcallbacks = new ArrayList<>();
    static final Set<String> removed = new HashSet<>();
    static String current;
    static RemovalRegression owner;
    static class Log {
        static void i(String tag, String msg) {}
        static void stack(String tag, String msg, Throwable t) { throw new AssertionError(t); }
    }
    static class Natives { static int count; static void setmaxsensors(int n) { count=n; } }
    static class SuperGattCallback {
        final String SerialNumber;
        volatile boolean retired, stopHealth=true;
        int frees;
        final CountDownLatch attemptingFree = new CountDownLatch(1);
        final AtomicBoolean unsafeFree = new AtomicBoolean();
        SuperGattCallback(String serial) { SerialNumber=serial; }
        void retire() { retired=true; }
        void free() {
            // Observe the caller before acquiring the same monitor as production free().
            unsafeFree.set(Thread.holdsLock(owner) || Thread.holdsLock(gattcallbacks));
            attemptingFree.countDown();
            synchronized(this) {
                if (!retired || gattcallbacks.contains(this)) unsafeFree.set(true);
                frees++;
            }
        }
    }
    static boolean callbackMatchesSensorId(SuperGattCallback gatt, String serial) {
        return gatt.SerialNumber.equals(serial) || ("alias-"+gatt.SerialNumber).equals(serial);
    }
    static void removePersistedManagedSensor(String serial) { removed.add(serial); }
    static void rehomeCurrentSensorAfterRemoval(String serial) {
        if (serial.equals(current)) current=gattcallbacks.isEmpty()?"":gattcallbacks.get(0).SerialNumber;
    }
    // METHODS
    static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }
    public static void main(String[] args) throws Exception {
        owner=new RemovalRegression();
        SuperGattCallback a=new SuperGattCallback("A"), b=new SuperGattCallback("B");
        gattcallbacks.add(a); gattcallbacks.add(b); current="A";
        CountDownLatch callbackHeld=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread reconnect=new Thread(() -> {
            try {
                synchronized(a) {
                    callbackHeld.countDown();
                    check(a.attemptingFree.await(3,TimeUnit.SECONDS), "Removal did not reach free");
                    synchronized(owner) {
                        synchronized(gattcallbacks) { check(!gattcallbacks.contains(a), "Still attached"); }
                    }
                }
            } catch(Throwable t) { failure.set(t); }
        });
        Thread removal=new Thread(() -> {
            try { owner.removeDevice("alias-A"); } catch(Throwable t) { failure.set(t); }
        });
        reconnect.setDaemon(true); removal.setDaemon(true);
        reconnect.start(); check(callbackHeld.await(3,TimeUnit.SECONDS), "No callback");
        removal.start(); removal.join(4000); reconnect.join(1000);
        check(!removal.isAlive() && !reconnect.isAlive(), "Deadlock during removal/reconnect");
        check(failure.get()==null, "Worker failure: "+failure.get());
        check(!a.unsafeFree.get() && a.frees==1, "Unsafe free or double free");
        check(gattcallbacks.size()==1 && gattcallbacks.get(0)==b, "Second sensor lost");
        check(!b.stopHealth && current.equals("B") && Natives.count==1, "Selection/health/count");
        check(removed.contains("A") && removed.contains("alias-A"), "Alias persistence");
        owner.removeDevice("missing");
        check(a.frees==1 && b.frees==0, "Unknown sensor affected live callback");
        owner.removeDevices();
        check(b.retired && b.frees==1 && !b.unsafeFree.get(), "Unsafe bulk removal");
        check(gattcallbacks.isEmpty() && Natives.count==0, "Bulk roster/count");
        System.out.println("PASS: concurrent reconnect/removal, aliases, second sensor and bulk cleanup");
    }
}
'''.replace('// METHODS', '\n'.join((removal, remove_all, free)))
    with tempfile.TemporaryDirectory(prefix='history-recovery-') as directory:
        temporary=Path(directory)
        (temporary/'history.cpp').write_text(cpp)
        (temporary/'RemovalRegression.java').write_text(java)
        run([os.environ.get('CXX','c++'), '-std=c++17', '-Wall', '-Wextra', '-Werror',
             'history.cpp', '-o', 'history'], directory)
        run([str(temporary/'history')], directory)
        run([os.environ.get('JAVAC','javac'), 'RemovalRegression.java'], directory)
        run([os.environ.get('JAVA','java'), '-cp', directory, 'RemovalRegression'], directory)


if __name__ == '__main__':
    main(Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2])
