/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      Fri Jan 27 12:37:55 CET 2023                                                 */


#include "SensorGlucoseData.hpp"
#include "jnihistory.h"
#include "nfcdata.hpp"
#include "datbackup.hpp"
extern bool saveSputnik_PG2(const jniHistory &hist,time_t nutime,int nuid,const nfcdata  *nfcptr, SensorGlucoseData &save) ;
/* An NFC scan carries the last 32 history records (8 hours at 15 minutes). Every
   one of them is walked, not just the records past the previous scan end: a BLE
   streaming outage leaves holes below that end, and only a later scan can still
   repair them. Ranking, oldest rule first:
     - a slot holding a valid BLE-streamed value is never touched (1-minute
       accurate, better than the 15-minute scan copy);
     - a slot below the previous scan end is filled only while it is still empty;
     - a slot at or past the previous scan end is refreshed as before.
   Records the algorithm did not vouch for are dropped instead of stored as 0 —
   the newest record of a scan is regularly still in progress and comes back as
   0, and writing that would erase whatever the slot already held. */
bool saveSputnik_PG2(const jniHistory &hist,time_t nutime,int nuid,const nfcdata  *nfcptr, SensorGlucoseData &save) {
     jint len=hist.size();
    LOGGER("saveSputnik_PG2 size=%d\n",len);
    if(len<=0)
    	return false;
const int mininterval=save.getmininterval();
if(mininterval<=0) {
	LOGGER("saveSputnik_PG2 mininterval=%d\n",mininterval);
	return false;
	}
decltype(auto)  first=hist.get(0);
    int fid=first.getId();
    int pos=int(round(fid/(double)mininterval));
/* Side effect: pulls starthistory down to the oldest scanned position. */
    const int prevend=save.getlastpos(pos);
    if(pos>=prevend&&save.getstarthistory()<=0) {
	save.setstarthistory(pos);
	}
const int uselen=std::min(history::num,len);
const int histmaxpos=save.maxpos();
int lastgood=std::max(prevend,pos)-1;
int firstchanged=-1;
int validCount=0;
int writtenCount=0;
int skippedQualityCount=0;
int skippedStreamedCount=0;
int skippedExistingCount=0;
int skippedRangeCount=0;
LOGGER("NFC_DIAG history-begin len=%d uselen=%d nuid=%d nutime=%lu prevend=%d firstpos=%d mininterval=%d\n",
    len, uselen, nuid, static_cast<unsigned long>(nutime), prevend, pos, mininterval);
LOGGER("saveSputnik_PG2 uselen=%d prevend=%d firstpos=%d\n",uselen,prevend,pos);
    for(int i=0;i<uselen;i++) {
	GlucoseValue gluv=hist.get(i);
	const int id=gluv.getId();
	const int topos=int(round(id/(double)mininterval));
	if(topos<0||topos>=histmaxpos) {
		++skippedRangeCount;
		LOGGER("GLU: savehistory pos %d out of 0..%d\n",topos,histmaxpos);
		continue;
		}
	if(!gluv.getQuality())
		lastgood=std::max(lastgood,topos);
	const int value=gluv.getValue();
	if(gluv.getQuality()||value<=38||value>=502) {
		++skippedQualityCount;
		LOGGER("GLU: savehistory skip %d quality=%d value=%d\n",topos,gluv.getQuality(),value);
		continue;
		}
	++validCount;
	Glucose *item=save.getglucose(topos);
	if(item->valid()) {
		if(item->isStreamed()) {
#ifndef NOLOG
			time_t tim=item->gettime();
			LOGGER("already streamed %d %.1f %s",item->getid(),(float)item->getmgdL()/convfactordL,ctime(&tim));
#endif
			++skippedStreamedCount;
			continue;
			}
		if(topos<prevend) { /*Already written by an earlier scan.*/
			++skippedExistingCount;
			continue;
			}
		}
	const uint16_t gv=10*value;//Same unit as raw
	const uint32_t was=nutime-(nuid-id)*60;
	const uint16_t rawel= nfcptr?nfcptr->gethistoryglucose(i):0;
	*item={.time=was,.id=(uint16_t)id};
	item->glu[0]=rawel;item->glu[1]=gv;
	item->glu[2]&=~0x4000;	/*Scan-sourced: keep the streamed flag truthful.*/
	if(firstchanged<0||topos<firstchanged)
		firstchanged=topos;
	++writtenCount;
#ifndef NOLOG
	time_t tim=item->gettime();
	LOGGER("add %d %.1f %s",item->getid(),(float) item->getmgdL()/convfactordL,ctime(&tim));
#endif
    	}
save.setendScanhistory(lastgood+1);
LOGGER("NFC_DIAG history-end valid=%d written=%d skipQuality=%d skipStreamed=%d skipExisting=%d skipRange=%d firstChanged=%d lastGood=%d endScanHistory=%d\n",
    validCount, writtenCount, skippedQualityCount, skippedStreamedCount,
    skippedExistingCount, skippedRangeCount, firstchanged, lastgood, lastgood+1);
if(save.getStreamendhistory()) {
	if(firstchanged!=-1) {
		int maxint=backup->getupdatedata()->sendnr;
		save.setstarthistback(maxint,firstchanged);
		const auto first=std::max((uint16_t)firstchanged,save.getinfo()->startedwithStreamhistory);
		if(save.getinfo()->libreviewnotsendHistory>first) {
			LOGGER("libreviewnotsendHistory=%d\n",first);
			save.getinfo()->libreviewnotsendHistory=first; 
			}
		}
	}

return true;
}

extern int saveScanTrend(const nfcdata *nfcptr,time_t nutime,int nuid, SensorGlucoseData &save) ;
/* Besides the 32 fifteen-minute history records, the patch carries trend[16]: the raw
   sensor value for each of the 16 minutes before the scan. Juggluco kept those only in
   trends.dat for the legacy trend thumbnail, so scanning during a BLE outage recovered
   nothing at one-minute resolution even though the tag was holding it. Append the minutes
   we do not have yet.

   These are uncompensated sensor values in the same unit as the history raw lane
   (mg/dL x 10), not algorithm output, so they go into the raw lane with no auto value.
   Only the Abbott library can turn a raw sample into a glucose reading, and it exposes
   that for the current minute alone — deriving the other fifteen would be inventing
   readings, which this app does not do.

   The newest entry is left out on purpose: the scan itself already records that minute
   with a real algorithm value, and a raw-only poll at the same second would displace it
   in getGlucoseHistory(). */
int saveScanTrend(const nfcdata *nfcptr,time_t nutime,int nuid, SensorGlucoseData &save) {
    if(!nfcptr) return 0;
    std::vector<NfcRawSample> rows;
    rows.reserve(trend::num-1);
    int invalid=0;
    const auto polls=save.getPolldata();
    for(int i=0;i<trend::num-1;i++) {
        const uint16_t raw=nfcptr->gettrendglucose(i);
        if(!raw) continue;
        const int minutesback=trend::num-1-i;
        const int64_t wideId=static_cast<int64_t>(nuid)-minutesback;
        if(wideId<0||wideId>=maxminutes) { ++invalid; continue; }
        const int id=static_cast<int>(wideId);
        int64_t timestamp=static_cast<int64_t>(nutime)-minutesback*60;
        // NFC scans and BLE receipt times can differ by most of a minute. A nearby
        // measured BLE sample anchors this sensor-life minute without moving its cursor.
        int closest=31;
        for(const auto &poll:polls) {
            if(!poll.valid(0)) continue;
            const int64_t distance=std::abs(static_cast<int64_t>(id)-poll.id);
            if(distance<closest) {
                closest=static_cast<int>(distance);
                timestamp=static_cast<int64_t>(poll.t)+(static_cast<int64_t>(id)-poll.id)*60;
            }
        }
        if(id<0||id>=maxminutes||timestamp<=1598911200LL||timestamp>=2145909600LL) {
            ++invalid;
            continue;
        }
        const NfcRawSample row{static_cast<uint32_t>(timestamp),id,raw};
        if(!NfcRawHistory::valid(row)) { ++invalid; continue; }
        rows.push_back(row);
    }
    // Do not use savepoll(): its duplicate guard protects the live BLE/parser cursor.
    // This separate file is keyed by sensor-life minute, not by the last BLE id.
    const auto result=save.saveNfcRawHistory(rows);
    LOGGER("NFC_DIAG trend-history added=%zu known=%zu invalid=%d persisted=%d\n",
           result.added,result.known,invalid,result.persisted?1:0);
    return static_cast<int>(result.added);
}

extern std::vector<int> usedsensors;
extern void setusedsensors();

void setstartedwithStreamhistory() {
	if(SensorGlucoseData *sens=sensors->getSensorData()) {
		if(sens->isLibre2()&&!sens->getinfo()->startedwithStreamhistory) {
			sens->getinfo()->startedwithStreamhistory=std::max(sens->getinfo()->endhistory,1);
			}
		}
	if(settings->data()->streamHistLib)  {
		return;
		}
	setusedsensors();
	for(int index:usedsensors) {
		  SensorGlucoseData *sens=sensors->getSensorData(index );
		  if(sens&&sens->isLibre2()) {
			sens->getinfo()->startedwithStreamhistory=std::max(sens->getinfo()->endhistory,1);
			}
		}
	settings->data()->streamHistLib=true;
	}
void setendedwithStreamhistory() {
	if(!settings->data()->streamHistLib) 
		return;
	setusedsensors();
	for(int index:usedsensors) {
		  SensorGlucoseData *sens=sensors->getSensorData(index );
		  if(sens&&sens->isLibre2()) {
			sens->getinfo()->startedwithStreamhistory=0;
			}
		}
	settings->data()->streamHistLib=false;
	}
extern bool addStreamHistory(const jniHistory &hist,time_t nutime,int nuid, SensorGlucoseData &save) ;
bool addStreamHistory(const jniHistory &hist,time_t nutime,int nuid, SensorGlucoseData &save) { 
	jint len=hist.size();
	LOGGER("addStreamHistory size=%d\n",len);
	if(len<2)  {
		return false;
		}
	int endhist=save.getAllendhistory();
	GlucoseValue   histvalue=hist.get(len-2);
	if(!histvalue.valid()) {
		LOGAR("not valid");
		return false;
		}
	int id=histvalue.getId();
	int pos=int(round(id/ save.getmininterval()));
	if(pos<endhist)  {
		LOGAR("old value");
		return false;
		}
	uint16_t gv=10*histvalue.getValue();//Same unit as raw
	time_t was=nutime-(nuid-id)*60;
	LOGGER("glucose id=%d %.1f (%d) %ld %s",id,gv/180.0f,gv,was,ctime(&was));
	save.saveel(pos,was,id, {0,gv,0x4000});
	save.setendStreamhistory(pos+1);
	if(!save.getstarthistory()) {
		save.setstarthistory(pos);
		}
	return true;
	}
