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
/*      Sun Mar 10 11:40:55 CET 2024                                                 */


package tk.glucodata;

import static tk.glucodata.Log.doLog;
import static tk.glucodata.Notify.glucosetimeout;
import static tk.glucodata.Notify.penmutable;
import static tk.glucodata.Notify.timef;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class GlucoseWidget extends AppWidgetProvider {
    private static final String ACTION_GLUCOSE_UPDATE = "tk.glucodata.action.GLUCOSE_UPDATE";
    private static final Object widgetLock = new Object();
    private static final long MIN_UPDATE_INTERVAL_MS = 1500L;
    private static final int LEGACY_WIDGET_DIM_COLOR = Color.argb(128, 0, 0, 0);
    private static long lastUpdateElapsedRealtime = 0L;
    static RemoteGlucose remote = null;
    static int width=0;
    static int height=0;
    private static final int DEFAULT_WIDGET_WIDTH_DP = 200;
    private static final int DEFAULT_WIDGET_HEIGHT_DP = 50;

private static void setSize(int widthdip, int heightdip) {
    synchronized (widgetLock) {
        widthdip = Math.max(1, widthdip);
        heightdip = Math.max(1, heightdip);
        var density = GlucoseCurve.getDensity();
        if (density <= 0.0f) {
            density = 1.0f;
        }
        final float widthpx = widthdip * density;
        final float heightpx = heightdip * density;
        // Size against both axes. The old widget only followed width, which made
        // a resized 1x1 widget render a short bitmap inside a much taller host.
        final float widthDrivenSize = widthpx * (Applic.unit == 1 ? 0.35f : 0.4f);
        final float heightDrivenSize = heightpx * 0.72f;
        final float fontsize = Math.max(1.0f, Math.min(widthDrivenSize, heightDrivenSize));
        remote = new RemoteGlucose(fontsize, widthpx, heightpx,
                Applic.unit == 1 ? 0.30f : 0.32f, 2, true);
        width = widthdip;
        height = heightdip;
    }
}
@Override
public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle widgetInfo) {
   var widthdip=widgetInfo.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
   var heightdip=widgetInfo.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
   {if(doLog) {Log.i(LOG_ID,"onAppWidgetOptionsChanged widthdip="+widthdip+" heightdip="+heightdip);};};
   used=true;
   if(widthdip>0 && heightdip>0 && (widthdip!=width || heightdip!=height)) {
       setSize(widthdip, heightdip);
       updateAppWidget(context, appWidgetManager, appWidgetId);
       }
   } 

@Override
public void onReceive(Context context, Intent intent) {
   super.onReceive(context, intent);
   if (intent != null && ACTION_GLUCOSE_UPDATE.equals(intent.getAction())) {
      update();
   }
}

static private RemoteViews remoteMessage(String message) {
    RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.text);
    remoteViews.setTextColor(R.id.content, getLegacyWidgetForegroundColor());
    remoteViews.setInt(R.id.content, "setBackgroundColor", LEGACY_WIDGET_DIM_COLOR);
    remoteViews.setTextViewText(R.id.content, message);
    return remoteViews;
    }
static private int getLegacyWidgetForegroundColor() {
    synchronized (widgetLock) {
        return remote != null ? remote.getBaseForegroundColor() : Color.WHITE;
    }
}
static private long oldage=glucosetimeout;
static private void showviews(RemoteViews views,int rId,AppWidgetManager appWidgetManager, int appWidgetId) {
    Intent intent = new Intent(Applic.app, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(Applic.app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT|penmutable);
    try {
        views.setOnClickPendingIntent(rId, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    } catch (Throwable th) {
        Log.stack(LOG_ID, "showviews(" + appWidgetId + ")", th);
    }
   }
static private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
   try {
   var widgetInfo=appWidgetManager.getAppWidgetOptions(appWidgetId);
   for(var key : widgetInfo.keySet()) {
        {if(doLog) {Log.d(LOG_ID, key + " = " + widgetInfo.get(key));};};
        }
   var widthdip=widgetInfo.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
   var heightdip=widgetInfo.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
   if(widthdip<=0)
      widthdip=(remote==null || width<=0) ? DEFAULT_WIDGET_WIDTH_DP : width;
   if(heightdip<=0)
      heightdip=(remote==null || height<=0) ? DEFAULT_WIDGET_HEIGHT_DP : height;
   if(widthdip!=width || heightdip!=height) {
      setSize(widthdip, heightdip);
      }
   RemoteViews  views;
   int id= R.id.arrowandvalue;
   synchronized (widgetLock) {
      if (remote == null) {
         setSize(widthdip, heightdip);
      }
	      final var current = WidgetDisplaySource.resolveWidgetSnapshot(glucosetimeout);
      if (current != null) {
         final var now = System.currentTimeMillis();
         final var time = current.getTimeMillis();
         if ((now - time) > oldage) {
            final String tformat = timef.format(time);
            String message = context.getString(R.string.nonewvalue) + tformat;
            views = remoteMessage(message);
            id = R.id.content;
         } else {
            views = remote.widgetRemote(current);
         }
      } else {
         views = remoteMessage(context.getString(R.string.novalue));
         id = R.id.content;
      }
   }

   showviews(views,id,  appWidgetManager, appWidgetId);
   } catch (Throwable th) {
      Log.stack(LOG_ID, "updateAppWidget(" + appWidgetId + ")", th);
   }
    }

static private void updateall(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
   if(appWidgetIds==null|| appWidgetIds.length == 0) {
      {if(doLog) {Log.i(LOG_ID,"updateall zero");};};
      return;
      }
   used=true;
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
   }

static boolean used=true;
final    private static String LOG_ID="GlucoseWidget";
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        {if(doLog) {Log.i(LOG_ID,"onUpdate");};};
       updateall(context, appWidgetManager,  appWidgetIds);
    }

    @Override
    public void onEnabled(Context context) {
   used=true;
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
   used=false;
    }
public static void oldvalue(long time) {
   final var cl= GlucoseWidget.class;
   final var manage= AppWidgetManager.getInstance(Applic.app);
   int ids[] = manage.getAppWidgetIds(new ComponentName(Applic.app, cl));
   if(ids.length>0) {
      {if(doLog) {Log.i(LOG_ID,"oldvalue widgets");};};
      final String tformat= timef.format(time);
      String message = Applic.getContext().getString(R.string.nonewvalue) + tformat;
      var views=remoteMessage(message);
      for(var id:ids) {
         showviews(views,R.id.content,manage,id);
         }
      }
   else {
      {if(doLog) {Log.i(LOG_ID,"oldvalue no widgets");};};
      }

   }
 public static void update() {
    if (Applic.isWearable) {
      // The watch has no home-screen widget. This class is compiled into both
      // apps so main/ can call it; there is nothing to update here.
      return;
    }
    try {
    if(used) {
      final long now = SystemClock.elapsedRealtime();
      synchronized (widgetLock) {
         if ((now - lastUpdateElapsedRealtime) < MIN_UPDATE_INTERVAL_MS) {
            return;
         }
         lastUpdateElapsedRealtime = now;
      }
      final var cl= GlucoseWidget.class;
      final var manage= AppWidgetManager.getInstance(Applic.app);
      int ids[] = manage.getAppWidgetIds(new ComponentName(Applic.app, cl));
      if(ids.length>0) {
         updateall(Applic.app, manage, ids);
         }
      else
         used=false;
      }
    } catch (Throwable th) {
      Log.stack(LOG_ID, "update()", th);
    }
    }
}
