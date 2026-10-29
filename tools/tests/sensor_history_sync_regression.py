"""Run focused native sensor-list/Wear-history regressions with C++17.

Usage: python tools/tests/sensor_history_sync_regression.py [repository-root]
Uses the production readers, storage and exporters with recording fixtures.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    end = opening + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def main(root):
    def read(path):
        return (root / path).read_text(encoding='utf-8')

    native = read('Common/src/main/cpp/g.cpp')
    watch = read('Common/src/main/cpp/watch/watchvalue.cpp')
    glucose = read('Common/src/main/cpp/glucose.cpp')
    fixtures = 'Common/src/test/resources/native/'
    storage = method(native, 'static int compactRawMgdl(') + '\n' + method(native, 'static bool storeGlucoseStreamSample(')
    exporter = method(native, 'static bool rawOnlyPollValid(') + '\n' + method(native, 'static void appendPollHistory(')
    raw = read(fixtures + 'wear_raw_history.cpp').replace('// PRODUCTION_STORAGE', storage).replace('// PRODUCTION_EXPORT', exporter)
    first = glucose.index('std::vector<int> usedsensors;\nstatic std::mutex usedsensorsmutex;')
    last = glucose.index('\nextern std::vector<int> usedsensors;', first)
    snapshot = glucose[first:last]
    reader = method(watch, 'std::pair<const SensorGlucoseData *,int> getlaststream(const uint32_t nu) {')
    sensors = read(fixtures + 'sensor_snapshot.cpp').replace('// PRODUCTION_SNAPSHOT', snapshot).replace('// PRODUCTION_READER', reader)
    measured = read(fixtures + 'measured_history.cpp').replace('// PRODUCTION_EXPORTER', method(native, 'static void appendStoredHistory('))
    with tempfile.TemporaryDirectory(prefix='sensor-sync-test-') as directory:
        temporary = Path(directory)
        for name, source in [('raw', raw), ('sensors', sensors), ('measured', measured)]:
            if os.environ.get('SENSOR_SYNC_ASAN') == '1':
                source = 'extern "C" const char* __asan_default_options() { return "detect_leaks=0"; }\n' + source
            (temporary / (name + '.cpp')).write_text(source, encoding='utf-8')
            command = [os.environ.get('CXX', 'c++'), '-std=c++17', '-pthread', '-Wall', '-Wextra',
                       '-Werror', '-Wno-unused-parameter', '-g', '-O1', name + '.cpp', '-o', name]
            # Optional memory instrumentation for the concurrent reader test.
            if os.environ.get('SENSOR_SYNC_ASAN') == '1':
                command.insert(1, '-fsanitize=address')
            subprocess.run(command, cwd=directory, check=True, timeout=30)
            subprocess.run([str(temporary / name)], cwd=directory, check=True, timeout=15)
    # Source-wiring checks complement the native tests; they are not Room/JVM execution.
    repository = read('Common/src/mobile/java/tk/glucodata/data/HistoryRepository.kt')
    single = method(repository, 'suspend fun storeReading(')
    assert 'storeReadingsReplacingSensorBuckets(' in single
    assert 'deleteSensorRowsInBucketRanges(' not in single
    current = read('Common/src/main/java/tk/glucodata/CurrentGlucoseSource.kt')
    local = method(current, 'private fun getFromLocalOrNative(')
    assert 'getFromNative(now, maxAgeMillis, targetSensor)' in local
    wear = read('Common/src/main/java/tk/glucodata/WearSync2.kt')
    assert 'WearHistoryLanes.hasValue(it.value, it.rawValue)' in wear
    assert 'WearHistoryLanes.validMgdl(auto10 / 10f)' in wear
    assert 'WearHistoryLanes.validMgdl(raw10 / 10f)' in wear
    assert 'auto10 <= 0' not in wear
    print('PASS: single-reading protected route, sensor-specific native route and Wear lane wiring')


if __name__ == '__main__':
    main(Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
