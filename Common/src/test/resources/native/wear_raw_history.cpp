#include <algorithm>
#include <array>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <map>
#include <utility>
#include <iostream>
using jlong = long long;
using jfloat = float;
#define LOGGER(...)
using HistorySamples = std::map<jlong, std::pair<jlong,jlong>>;
struct ScanData {
    uint32_t t=0; int id=0; int32_t g=0; int tr=0; float ch=0;
    bool valid() const { return g >= 20 && g <= 600 && t>1598911200u; }
    int getmgdL() const { return g; }
};
struct RawData { uint16_t raw=0; };
struct Info { uint32_t starttime=1790000000; uint16_t nightiter=0; };
struct SensorGlucoseData {
    std::array<ScanData,16> polls{};
    std::array<RawData,16> raws{};
    std::array<uint16_t,16> temps{};
    Info info; int count=0;
    bool error()const{return false;}
    Info* getinfo(){return &info;}
    const ScanData* getPollsData()const{return polls.data();}
    const RawData* getRawPollsData()const{return raws.data();}
    size_t pollStorageCapacity()const{return polls.size();}
    bool validPollIndex(int id)const{return id>=0 && id<int(polls.size());}
    bool hasStreamID(int id)const{return validPollIndex(id)&&polls[id].id==id&&polls[id].g;}
    uint16_t getTempForPoll(int id)const{return temps[id];}
    uint16_t getRawForPoll(const ScanData* poll)const{return raws[poll-polls.data()].raw;}
    struct View {
        const ScanData* first; const ScanData* last;
        const ScanData* begin()const{return first;}
        const ScanData* end()const{return last;}
    };
    View getPolldata()const{return {polls.data(),polls.data()+count};}
    void backstream(int){} void backhistory(int){}
    template<int> bool savepollallIDs(uint32_t t,int id,int g,int tr,float ch,int raw,uint16_t temp) {
        polls[id]={t,id,g,tr,ch}; raws[id].raw=raw; temps[id]=temp;
        count=std::max(count,id+1); return true;
    }
    template<int seconds> bool savepollallIDsQuiet(uint32_t t,int id,int g,int tr,float ch,int raw,uint16_t temp) {
        return savepollallIDs<seconds>(t,id,g,tr,ch,raw,temp);
    }
};
struct Backup {};
Backup* backup=nullptr;
void syncListStarttime(SensorGlucoseData*,uint32_t){}
float derivechangeforsample(const ScanData*,int,int,int,uint32_t){return 1.0f;}
double calibrateNow(const SensorGlucoseData*,const ScanData&){return NAN;}
using std::isnan;
// PRODUCTION_STORAGE
// PRODUCTION_EXPORT
int main() {
    SensorGlucoseData hist;
    const jlong t=hist.info.starttime+120;
    assert(storeGlucoseStreamSample(&hist,"sensor",t,0,105,0,true,true,true,nullptr));
    assert(hist.polls[2].g==0 && std::isnan(hist.polls[2].ch));
    HistorySamples history;
    appendPollHistory(&hist,0,history);
    assert(history.at(t).first==0 && std::abs(history.at(t).second-1050)<=10);
    // Completing the same sample with Auto must preserve the earlier Raw lane.
    assert(storeGlucoseStreamSample(&hist,"sensor",t,11,0,0,true,true,true,nullptr));
    assert(hist.polls[2].g==110 && hist.raws[2].raw>0);
    // A Raw-only retry at the same instant cannot erase Auto.
    assert(storeGlucoseStreamSample(&hist,"sensor",t,0,106,0,true,true,true,nullptr));
    assert(hist.polls[2].g==110);
    history.clear(); appendPollHistory(&hist,0,history);
    assert(history.size()==1 && history.at(t).first==1100 && history.at(t).second>0);
    // A different timestamp in the same native slot cannot retime old Auto.
    const auto before=hist.polls[2]; const auto rawBefore=hist.raws[2].raw;
    assert(!storeGlucoseStreamSample(&hist,"sensor",t+20,0,120,0,true,true,true,nullptr));
    assert(hist.polls[2].t==before.t && hist.polls[2].g==before.g && hist.raws[2].raw==rawBefore);
    // Auto-only then Raw at the same timestamp completes both lanes.
    assert(storeGlucoseStreamSample(&hist,"sensor",t+60,12,0,0,true,true,true,nullptr));
    assert(storeGlucoseStreamSample(&hist,"sensor",t+60,0,115,0,true,true,true,nullptr));
    history.clear(); appendPollHistory(&hist,0,history);
    assert(history.at(t+60).first==1200 && history.at(t+60).second>0);
    std::cout << "PASS: actual native Raw-only store/export, both arrival orders, duplicate and Auto protection\n";
}
