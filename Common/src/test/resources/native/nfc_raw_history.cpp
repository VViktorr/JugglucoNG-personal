#include "NfcRawHistory.hpp"
#include <cassert>
#include <cmath>
#include <fstream>
#include <thread>

using jlong = int64_t;
using std::isnan;
using HistorySamples = std::map<jlong, std::pair<jlong, jlong>>;
constexpr int maxminutes = 100000;
namespace trend { constexpr int num = 16; }

struct ScanData {
    uint32_t t;
    int32_t id;
    int32_t g;
    uint16_t raw;
    bool valid(int = 0) const { return g > 0 && t > 1598911200u; }
};
struct nfcdata {
    uint16_t raw[16] {};
    uint16_t gettrendglucose(int i) const { return raw[i]; }
};
struct SensorGlucoseData {
    std::string path;
    std::vector<ScanData> polls;
    int cursorWrites = 0;
    double pollinterval = 60.220;
    bool libre2 = true;
    struct Info { int startedwithStreamhistory = 0; int endhistory = 0; } info {};
    Info *getinfo() { return &info; }
    bool isLibre2() const { return libre2; }
    const std::vector<ScanData> &getPolldata() const { return polls; }
    uint16_t getRawForPoll(const ScanData *poll) const { return poll->raw; }
    NfcRawHistory::Result saveNfcRawHistory(const std::vector<NfcRawSample> &rows) const {
        return NfcRawHistory(path).append(rows);
    }
    std::vector<NfcRawSample> getNfcRawHistory() const {
        return libre2 ? NfcRawHistory(path).snapshot() : std::vector<NfcRawSample>{};
    }
    bool savepoll(time_t, int, int, int, float, int) { ++cursorWrites; return false; }
};
static double calibrateNow(const SensorGlucoseData *, const ScanData &) { return NAN; }
#define LOGGER(...) ((void)0)

struct Sensors {
    std::vector<SensorGlucoseData *> active;
    SensorGlucoseData *getSensorData() { return active.empty() ? nullptr : active.front(); }
    SensorGlucoseData *getSensorData(int index) { return active.at(index); }
};
struct Settings {
    struct Data { bool streamHistLib = false; } value;
    Data *data() { return &value; }
};
static Sensors *sensors;
static Settings *settings;

// PRODUCTION_NFC_SAVE
// PRODUCTION_EXPORTERS

// Definitions follow the production consumers: their extern declarations must exist.
std::vector<int> usedsensors;
void setusedsensors() {
    usedsensors.clear();
    for (size_t i = 0; i < sensors->active.size(); ++i) usedsensors.push_back(i);
}

int main() {
    char directory[] = "juggluco-nfc-test-XXXXXX";
    assert(mkdtemp(directory));
    const std::string path = std::string(directory) + "/nfc-raw-history.dat";
    // Reproduce the supplied log: minutes 9980..9982 missing, newest BLE id 9988.
    constexpr uint32_t latest = 1791875309;
    SensorGlucoseData sensor {path, {
        {1791874772, 9979, 0, 56},
        {1791875010, 9983, 112, 0},
        {1791875070, 9984, 113, 63},
        {1791875128, 9985, 113, 0},
        {1791875188, 9986, 113, 0},
        {1791875249, 9987, 112, 0},
        {latest, 9988, 112, 0}
    }};
    const auto before = sensor.polls;
    SensorGlucoseData lifecycleSecond {std::string(directory) + "/lifecycle.dat", {}};
    sensor.info.endhistory = 7;
    lifecycleSecond.info.endhistory = 11;
    Sensors lifecycleSensors {{&sensor, &lifecycleSecond}};
    Settings lifecycleSettings;
    sensors = &lifecycleSensors;
    settings = &lifecycleSettings;
    setstartedwithStreamhistory();
    assert(settings->data()->streamHistLib);
    assert(sensor.info.startedwithStreamhistory == 7 && lifecycleSecond.info.startedwithStreamhistory == 11);
    setendedwithStreamhistory();
    assert(!settings->data()->streamHistLib);
    assert(sensor.info.startedwithStreamhistory == 0 && lifecycleSecond.info.startedwithStreamhistory == 0);
    nfcdata scan;
    std::fill(std::begin(scan.raw), std::end(scan.raw), 1000);
    assert(saveScanTrend(&scan, 1791875359, 9988, sensor) == 15);
    assert(sensor.cursorWrites == 0 && sensor.pollinterval == 60.220);
    assert(sensor.polls.size() == before.size());
    for (size_t i = 0; i < before.size(); ++i) {
        assert(sensor.polls[i].id == before[i].id && sensor.polls[i].t == before[i].t);
        assert(sensor.polls[i].g == before[i].g && sensor.polls[i].raw == before[i].raw);
    }
    const auto rows = sensor.getNfcRawHistory();
    assert(rows.size() == 15 && rows.front().id == 9973 && rows.back().id == 9987);
    for (int id : {9980, 9981, 9982}) {
        const auto found = std::find_if(rows.begin(), rows.end(), [id](const auto &row) { return row.id == id; });
        assert(found != rows.end() && found->rawMgdlTimes10 == 1000);
    }
    HistorySamples samples;
    appendPollHistory(&sensor, 0, samples);
    const auto bleAuto = samples.at(1791875010).first;
    const auto bleRaw = samples.at(1791875070).second;
    appendNfcRawHistory(&sensor, 0, samples);
    assert(samples.at(1791875010).first == bleAuto && bleAuto == 1120);
    assert(samples.at(1791875010).second == 1000); // Fill absent Raw; preserve Auto.
    assert(samples.at(1791875070).second == bleRaw && bleRaw != 1000); // BLE Raw wins.
    for (const auto &row : rows) {
        if (row.id >= 9980 && row.id <= 9982) {
            assert(samples.at(row.time).first == 0); // Never turn Raw into Auto.
            assert(samples.at(row.time).second == 1000);
        }
    }
    // Repeated scans with shifted clocks/values neither duplicate nor replace a minute.
    std::fill(std::begin(scan.raw), std::end(scan.raw), 1100);
    assert(saveScanTrend(&scan, 1791875479, 9988, sensor) == 0);
    assert(sensor.getNfcRawHistory().front().rawMgdlTimes10 == 1000);
    assert(sensor.getNfcRawHistory().front().time == rows.front().time);
    // Persisted independently of SensorGlucoseData lifetime or a BLE cursor.
    SensorGlucoseData reopened {path, {}};
    assert(reopened.getNfcRawHistory().size() == 15);
    SensorGlucoseData other {std::string(directory) + "/other.dat", {}};
    assert(other.getNfcRawHistory().empty());
    assert(saveScanTrend(&scan, 1791875359, 9988, other) == 15);
    assert(other.polls.empty() && other.cursorWrites == 0);
    samples.clear();
    appendNfcRawHistory(&reopened, rows.back().time, samples);
    assert(samples.empty()); // Inclusive start boundary is not re-exported.
    // Separate instances must merge concurrent additions without losing either set.
    NfcRawHistory one(std::string(directory) + "/concurrent.dat");
    NfcRawHistory two(std::string(directory) + "/concurrent.dat");
    std::thread first([&] { assert(one.append({{latest, 1, 1000}}).persisted); });
    std::thread second([&] { assert(two.append({{latest + 60, 2, 1100}}).persisted); });
    first.join(); second.join();
    assert(one.snapshot().size() == 2);
    // A truncated file is preserved; a failed append must never touch BLE state.
    const std::string broken = std::string(directory) + "/broken.dat";
    { std::ofstream stream(broken); stream << "broken"; }
    assert(!NfcRawHistory(broken).append({{latest, 1, 1000}}).persisted);
    struct stat info {}; assert(stat(broken.c_str(), &info) == 0 && info.st_size == 6);
    assert(!NfcRawHistory(std::string(directory) + "/missing-parent/file.dat")
        .append({{latest, 1, 1000}}).persisted);
    // Invalid NFC samples are not stored; file contents remain unchanged.
    assert(one.append({{latest, -1, 1000}, {latest, 3, 0}, {0, 4, 1000}}).added == 0);
    assert(one.snapshot().size() == 2);
    for (const auto &name : {"nfc-raw-history.dat", "other.dat", "concurrent.dat", "broken.dat"})
        unlink((std::string(directory) + "/" + name).c_str());
    assert(rmdir(directory) == 0);
}
