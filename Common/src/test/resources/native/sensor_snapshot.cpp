#include <vector>
#include <mutex>
#include <atomic>
#include <thread>
#include <cstdint>
#include <ctime>
#include <utility>
#include <cassert>
#include <iostream>
constexpr uint32_t daysecs=86400;
std::atomic<int> stage{0};
struct ScanData { uint32_t t=1790000000; };
struct SensorGlucoseData {
    ScanData poll;
    int streaminterval()const{return 1;}
    const ScanData* lastValidStream()const{return &poll;}
};
struct Info { int current=0; };
struct Sensoren {
    Info info; SensorGlucoseData hist;
    std::vector<int> nextActive{0,1};
    Info* infoblockptr(){return &info;}
    std::vector<int> bluetoothactive(uint32_t,uint32_t){return nextActive;}
    const SensorGlucoseData* getSensorData(int) {
        if(stage.load()==0) {
            stage.store(1);
            while(stage.load()!=2) std::this_thread::yield();
        }
        return &hist;
    }
};
Sensoren state; Sensoren* sensors=&state;
void keepnet(uint32_t,uint32_t,int){}
// PRODUCTION_SNAPSHOT
// PRODUCTION_READER
int main() {
    setusedsensors();
    std::thread update([] {
        while(stage.load()!=1) std::this_thread::yield();
        // Exactly the production update path, while the reader is mid-iteration.
        {std::lock_guard<std::mutex> lock(usedsensorsmutex); sensors->nextActive={0};}
        setusedsensors();
        stage.store(2);
    });
    const auto [hist,index]=getlaststream(1790000001);
    update.join();
    assert(hist==&state.hist && index==1);
    assert(usedsensorssnapshot().size()==1);
    std::cout << "PASS: actual sensor snapshot/reader while active roster shrinks from two sensors to one\n";
}
