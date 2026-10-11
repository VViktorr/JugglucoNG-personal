#!/usr/bin/env python3
"""Replay production LibreView unit/region, sensor registration and auth policies.
No Android runtime, server account or real sensor is required.
"""
from pathlib import Path
import subprocess
import tempfile
import re
root=Path(__file__).resolve().parents[1]
java=(root/'Common/src/mobile/java/tk/glucodata/Libreview.java').read_text()
native=(root/'Common/src/main/cpp/net/libreview/libreview.cpp').read_text()
settings=(root/'Common/src/main/cpp/settings/settings.hpp').read_text()
def method(source,signature):
 start=source.index(signature);brace=source.index('{',start);end=brace+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
cpp=r"""
#include <string>
#include <string_view>
#include <vector>
#include <array>
#include <cstring>
#include <ctime>
#include <cassert>
using std::string_view;
#define LOGGER(...) ((void)0)
#define LOGAR(...) ((void)0)
#define JNIEXPORT
#define JNICALL
#define fromjava(x) x
struct JNIEnv {};
using jclass=void*;
struct Info { bool putsensor=false; int cursor=123; };
struct SensorGlucoseData {
 Info info; bool libre2=true; std::string name="NEW_SENSOR";
 Info* getinfo(){return &info;}
 bool isLibre2(){return libre2;}
 time_t officialendtime(){return time(nullptr)+3600;}
 string_view showsensorname(){return name;}
};
struct Data { int unit=2,country=3; bool libreinit=true; int getLibreCountry(){return country;} };
struct Settings {
 Data values;
 Data* data() {return &values;}
 const std::array<string_view,3> unitlabels{"","mmol/L","mg/dL"};
 UNITLABEL
} setting;
Settings* settings=&setting;
struct Sensors {
 std::vector<SensorGlucoseData*> items;
 template<class F> void onallsensors(F f) { for(auto* sensor:items) f(sensor); }
} sensorStore;
Sensors* sensors=&sensorStore;
namespace Backup {constexpr int wakeall=1;}
struct Condition {int wakes=0;void wakebackup(int){++wakes;}} librecondition;
bool alwaysnewstatus=false;int threadStarts=0;
void startlibrethread(){++threadStarts;}
int registrations=0;bool registerResult=true;
static bool putsensor(bool,const char*){++registrations;return registerResult;}
REGISTER
RESET
void addstrview(char*&out,string_view s){memcpy(out,s.data(),s.size());out+=s.size();}
template<size_t N>void addar(char*&out,const char (&s)[N]){memcpy(out,s,N-1);out+=N-1;}
CONSTANTS
std::string payload(){
 char uitbuf[2048];char*uitptr=uitbuf;
 UOM
 MODEL
 return std::string(uitbuf,uitptr);
}
int main(){
 assert(payload().find("mg/dL")!=std::string::npos);
 assert(payload().find("com.freestylelibre.app.pl")!=std::string::npos);
 setting.values.unit=1;setting.values.country=0;
 assert(payload().find("mmol/L")!=std::string::npos);
 assert(payload().find("com.freestylelibre.app.gb")!=std::string::npos);
 setting.values.unit=2;setting.values.country=3;
 assert(payload().find("mg/dL")!=std::string::npos);
 SensorGlucoseData fresh,other;other.libre2=false;other.info.putsensor=true;
 sensorStore.items={&fresh,&other};
 registerResult=false;assert(!putwhenneeded(false,&fresh));assert(!fresh.info.putsensor);
 registerResult=true;assert(putwhenneeded(false,&fresh));assert(fresh.info.putsensor);
 int before=registrations;assert(putwhenneeded(false,&fresh));assert(registrations==before);
 resetLibre2ViewAuth(nullptr,nullptr);
 assert(!fresh.info.putsensor && other.info.putsensor);
 assert(fresh.info.cursor==123 && !setting.values.libreinit && alwaysnewstatus);
 assert(librecondition.wakes==1 && threadStarts==1);
 assert(putwhenneeded(false,&fresh));assert(registrations==before+1);
}
"""
label=method(settings,'  string_view getunitlabel() const')
# Harness data access retains const correctness.
cpp=cpp.replace('Data* data() {return &values;}','const Data* data() const{return &values;} Data* data(){return &values;}')
cpp=cpp.replace('UNITLABEL',label).replace('REGISTER',method(native,'bool putwhenneeded('))
cpp=cpp.replace('RESET',method(native,'extern "C" JNIEXPORT void JNICALL fromjava(resetLibre2ViewAuth)'))
constants=[]
for name in ['datastart','afterunit','appstart','countries']:
 match=re.search(r'^[ \t]*(?:constexpr|const constexpr)[^\n]*\b'+name+r'\b[^;]*;',native,re.M)
 if match is None:
  # Find a declaration on its line then consume the raw-string terminator.
  start=native.rfind('\n',0,native.index(name))+1
  match=native[start:native.index(';',native.index(name))+1]
 else: match=match.group()
 constants.append(match)
cpp=cpp.replace('CONSTANTS','\n'.join(constants))
a=native.index('  const int country = settings->data()->getLibreCountry();')
b=native.index('  SensorGlucoseData *currentsensor',a)
uom=native[a:b].replace('  char *uitptr = uitbuf;','')
cpp=cpp.replace('\n UOM\n','\n'+uom+'\n')
cpp=cpp.replace('MODEL','  addstrview(uitptr, appstart);\n  addar(uitptr, countries[country]);')
methods='\n'.join(method(java,sig) for sig in [
 'private static boolean authCooldownActive(', 'private static void rememberAuthLockout(',
 'private static HttpURLConnection openLibreViewConnection(', 'private static void closeLibreViewConnection(',
 'private static int rateLimitSeconds(', 'static boolean postgetauth(',
 'private static void applyLibreCountryFromAccountOnce(', 'private static boolean forceLibre2DeviceRegistrationRequested(',
 'private static void clearForcedLibre2DeviceRegistration(', 'private static byte[] withCurrentLibreToken(',
 'static boolean putsensor(',
])
j=r"""
import java.net.*;import java.io.*;import java.util.*;
class Test {
 static final String LOG_ID="test",LIBRE_COUNTRY_PREFS="state",LIBRE_COUNTRY_ACCOUNT_KEY="country",
 LIBRE_FORCE_LIBRE2_DEVICE_KEY="force",LIBRE_AUTH_LOCKOUT_UNTIL="until",LIBRE_AUTH_LOCKOUT_LOGIN="login";
 static boolean doLog=false;static String librestatus="",termsofuseversionurl=null;
 static final String nothing="nothing",success="success";
 static String datestr(long t){return "now";}static boolean libreconfig(boolean b,boolean r){return true;}
 static class JSONException extends Exception {}
 static final int HTTP_OK=200;
 static String getlibregateway(boolean b){return "gateway";}
 static String getlibreemail(){return "login";}
 static String getlibrepass(){return "pass";}
 static String getlibrebaseurl(boolean b){return "https://fake";}
 static String getnewYuApiKey(boolean b){return "key";}
 static void setlibreAccountID(String s){}
 static void savelibrerubbish(String a,String b,int c,String d,String e){}
 static boolean gettermversion(String s){return true;}
 static String stackline(Throwable t){return t.toString();}
 static JSONObject readJSONObject(HttpURLConnection c){return ((Conn)c).body;}
 static class Context {static final int MODE_PRIVATE=0;Prefs prefs=new Prefs();Prefs getSharedPreferences(String s,int i){return prefs;}}
 static class Applic{static Context app=new Context();}
 static class Prefs{
 Map<String,Object>m=new HashMap<>();
 String getString(String k,String d){return (String)m.getOrDefault(k,d);}
 long getLong(String k,long d){return ((Number)m.getOrDefault(k,d)).longValue();}
 boolean getBoolean(String k,boolean d){return (Boolean)m.getOrDefault(k,d);}
 Prefs edit(){return this;}Prefs putString(String k,String v){m.put(k,v);return this;}
 Prefs putLong(String k,long v){m.put(k,v);return this;}Prefs remove(String k){m.remove(k);return this;}
 void apply(){}
 }
 static class Natives{
 static int country=-1;static void initLibreCountryFromLocale(){}
 static int libreCountryIndex(String s){return "PL".equals(s)?3:-1;}
 static void setLibreCountry(int i){country=i;}
 static String getlibreDeviceID(boolean b){return "device";}
 static String token="old-token";static String getlibreUserToken(boolean b){return token;}
 static void setlibreUserToken(boolean b,String s){token=s;}
 }
 static class Build{static class VERSION{static String RELEASE="test";}}
 static class Log {static void i(String a,String b){}static void e(String a,String b){}}
 static class JSONObject {
 Map<String,Object>m=new HashMap<>();
 JSONObject(){}
 JSONObject(String text){m.put("UserToken",text.contains("old-token")?"old-token":"new-token");}
 public String toString(){return "{\"UserToken\":\""+m.get("UserToken")+"\"}";}
 JSONObject put(String k,Object v){m.put(k,v);return this;}
 int getInt(String k){return ((Number)m.get(k)).intValue();}
 int optInt(String k,int d){return ((Number)m.getOrDefault(k,d)).intValue();}
 String getString(String k){return (String)m.get(k);}
 String optString(String k,String d){return (String)m.getOrDefault(k,d);}
 JSONObject getJSONObject(String k){return (JSONObject)m.get(k);}
 JSONObject optJSONObject(String k){return (JSONObject)m.get(k);}
 }
 static class Conn extends HttpURLConnection{
 JSONObject body;int code;boolean closed;ByteArrayOutputStream out=new ByteArrayOutputStream();
 Conn(URL u,JSONObject b,int c){super(u);body=b;code=c;}
 public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
 public OutputStream getOutputStream(){return out;}
 public int getResponseCode(){return code;}
 public String getHeaderField(String k){return "2";}
 }
 static Queue<Conn> responses=new ArrayDeque<>();static java.util.List<Conn> opened=new ArrayList<>();
 static JSONObject error(){return new JSONObject().put("status",20).put("reason","wrongDeviceForUser");}
 static void enqueue(JSONObject b,int code)throws Exception{responses.add(new Conn(new URL("https://fake"),b,code));}
 METHODS
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args)throws Exception{
 URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
 protected URLConnection openConnection(URL u){
 Conn c=responses.remove();opened.add(c);return c;
 }}:null);
 enqueue(error(),200);enqueue(error(),200);
 check(!postgetauth(false));check(opened.size()==2);
 check(opened.get(0).out.toString().contains("\"SetDevice\":false"));
 check(opened.get(1).out.toString().contains("\"SetDevice\":true"));
 for(Conn c:opened)check(c.closed&&c.getConnectTimeout()==10000&&c.getReadTimeout()==60000);
 enqueue(new JSONObject().put("status",429).put("data",new JSONObject().put("data",new JSONObject().put("lockout",2))),200);
 check(!postgetauth(false));int count=opened.size();
 check(!postgetauth(false));check(opened.size()==count);
 Applic.app.prefs.putLong(LIBRE_AUTH_LOCKOUT_UNTIL,0L);
 enqueue(new JSONObject(),429);check(!postgetauth(false));count=opened.size();
 check(!postgetauth(false));check(opened.size()==count);
 Applic.app.prefs.putLong(LIBRE_AUTH_LOCKOUT_UNTIL,0L);
 JSONObject result=new JSONObject().put("UserToken","new-token").put("AccountId","account").put("Country","PL");
 enqueue(new JSONObject().put("status",0).put("result",result),200);enqueue(new JSONObject(),200);
 check(postgetauth(false));check(Natives.country==3);
 Natives.country=0;applyLibreCountryFromAccountOnce(result,"account");check(Natives.country==0);
 Natives.token="old-token";
 enqueue(new JSONObject().put("status",20).put("reason","wrongDeviceInToken"),200);
 enqueue(new JSONObject().put("status",0).put("result",result),200);enqueue(new JSONObject(),200);
 enqueue(new JSONObject().put("status",0),200);
 count=opened.size();
 check(putsensor(false,"{\"UserToken\":\"old-token\"}".getBytes()));
 check(opened.get(count).out.toString().contains("old-token"));
 check(opened.get(opened.size()-1).out.toString().contains("new-token"));
 for(Conn c:opened)check(c.closed);
 }
}
"""
j=j.replace('METHODS',methods)
with tempfile.TemporaryDirectory() as tmp:
 d=Path(tmp);(d/'test.cpp').write_text(cpp);(d/'Test.java').write_text(j)
 subprocess.run(['g++','-std=c++20','-o',str(d/'native'),str(d/'test.cpp')],check=True)
 subprocess.run([str(d/'native')],check=True)
 subprocess.run(['javac','-d',str(d),str(d/'Test.java')],check=True)
 subprocess.run(['java','-cp',str(d),'Test'],check=True)
print('PASS: mg/dL/mmol and region changes; new-sensor registration retry; reregistration preserves history; finite auth/device retry; JSON/HTTP 429 cooldown; timeouts and cleanup')
