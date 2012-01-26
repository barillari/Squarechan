/*
 *  squarechan, a toy mobile photo-sharing app
 *     Copyright (C) 2012  Joseph Barillari
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License version 3
 *     as published by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.squarechan.android

import _root_.android.content.Context
import _root_.android.app.Application
import _root_.android.content.res.Configuration
import _root_.java.util.Arrays
import _root_.android.util.Log
import _root_.android.location.Location
import _root_.java.util.concurrent.Executors
import _root_.java.util.concurrent.ScheduledFuture
import _root_.com.github.droidfu.DroidFuApplication

import _root_.org.acra.ACRA
import _root_.org.acra.ReportingInteractionMode
import _root_.org.acra.annotation.ReportsCrashes


final object SQConstants extends SQDebugState {
  val LIST_DIVIDER_HEIGHT = 5
  val FETCH_ERROR = 9009
  val LOCATION_ERROR = 9010
//  val ERAD = 6371010 // # earth radius (m)
  val ERAD = 6378137 // # earth radius (m)
  val PRAD = 6356752.3142
  val LOG_TAG = "sq"

  val OWNER_TOKEN_POST_FIELD_NAME = "owner_token"
  val POST_ID_POST_FIELD_NAME = "postid"


  val RADIUS = "radius"
  // FIXME: should have a Makefile-run script write constants.py to a trait & extend it
  val RADII = Arrays.asList(       250,     500,     1000,   5000,  10000,    50000,   100000,   500000)
  val RADII_TEXT = Arrays.asList( "250 m", "500 m", "1 km", "5 km", "10 km", "50 km", "100 km", "500 km")
  val DEFAULT_RADIUS = 10000

  val FETCH_THREADS_URI = "http://"+HOSTNAME+"/latest-ajax?json=1"
  val CREATE_POST_URI = "http://"+HOSTNAME+"/post-android"
  val DELETE_POST_URI = "http://"+HOSTNAME+"/delete-post"
  val FETCH_THREAD_HTTP_TIMEOUT = 120 * 1000
  val POST_HTTP_TIMEOUT = 300 * 1000 // probably too long
  val FETCH_PIC_HTTP_TIMEOUT = 10 * 1000
  val LOCATION_LOOKUP_TIMEOUT = 45 * 1000
  val MAX_THREADS_TO_DISPLAY = 250

  val NEED_LOCATION_BEFORE_POSTING = 10091

  val REPLY_TO_HTTP_HEADER = "X-SQ-ReplyTo"
  val LOCATION_HTTP_HEADER = "X-SQ-Location"

  val HAS_IMAGE_HTTP_HEADER = "X-SQ-Has-Image"
  val CONTENT_HTTP_HEADER = "X-SQ-Content"
  val OWNER_TOKEN_HTTP_HEADER = "X-SQ-Owner-Token"

  val ENABLE_GPS_OR_NET = 9009
  val ENABLE_WIFI = 8008
  val DELETE_ERROR = 90210
  val SQ_PREFS_FILE = "sq_prefs_file"
  val SQ_UNIQUE_ID = "sq_unique_id"
  val SAW_SPLASH_SCREEN = "saw_splash_screen"

}




// sadly the compiler doesn't recognize that r.s.ctt is a constant
//		    mode = ReportingInteractionMode.TOAST,
//                    resToastText = R.string.crash_toast_text)

@ReportsCrashes(formKey = "dGNBMFNxR3RDRlVfSXdJT3djcGtORkE6MQ")
class SQApp extends DroidFuApplication {
  // holds global state
  val LOG_TAG = SQConstants.LOG_TAG
  val CACHED_LOC_EXPIRES_MINUTES = 15
  var timeOffset = 0L
  var currentUtmZone = 0L
  var currentX = 0L
  var currentY = 0L
  var locLoc = new Object()
  var lastPostsUpdateTime = 0L
  var currentLocation:Location = null
  var locUpdates = 0
  // latestUpdatedPostTime holds the last-updated time of the latest
  // post we've seen in OneThreadActivity. When we switch back to
  // ThreadListActivity, we check to see if this is newer than the
  // latest in ThreadListActivity and, if so, call for an update.
  // (there are hypothetical race conds where this doesn't work, but
  // none of them seem likely to arise in practice.)

  // FIXME: it would be better to avoid the second roundtrip, since
  // TLA could just chop out the middle posts (if any) from the
  // postlist fetched by OneThreadView. but this is definitely easier,
  // since we don't have to reimplement the truncation code that's on
  // the server side.
  var latestPostSeenLUTime = 0L
  val scheduler = Executors.newScheduledThreadPool(1)
  var periodicTaskHandle = null:ScheduledFuture[_]

// FIXME: do this
//  override def onLowMemory() {
    // call ImageLoader.clearCache()
//  }
  val X = "x"
  val Y = "y"
  val Z = "z"
  val OFFSET = "offset"
  val SAVED_TIMESTAMP = "saved_timestamp"
  val CACHED_SQ_APP_SETTINGS = "cached_squarechan_app_settings"


  def setOXYZ(o:Long, x:Long, y:Long, z:Long) {
    timeOffset = o
    currentX = x
    currentY = y
    currentUtmZone = z
    val editor = getSharedPreferences(CACHED_SQ_APP_SETTINGS, Context.MODE_PRIVATE).edit()
    editor.putLong(X, x)
    editor.putLong(Y, y)
    editor.putLong(Z, z)
    editor.putLong(OFFSET, o)
    editor.putLong(SAVED_TIMESTAMP, System.currentTimeMillis)
    editor.commit()

  }

  override def onCreate() {
    ACRA.init(this)
    super.onCreate()
    Log.v(SQConstants.LOG_TAG, "SQAPP: onCreate()")

    // fill in offset,x,y,z from the prefs so we have _something_
    // there if the app was reset this is suboptimal but easy enough
    // to implement. FIXME: shld probably actually run a loc check and
    // use the location manager's cached loc.
    if (timeOffset == 0L && currentX == 0L && currentY == 0L && currentUtmZone == 0L) {
      val prefs = getSharedPreferences(CACHED_SQ_APP_SETTINGS, Context.MODE_PRIVATE)
      val ts = prefs.getLong(SAVED_TIMESTAMP, 0L)
      if (ts == 0 || (System.currentTimeMillis-ts) < CACHED_LOC_EXPIRES_MINUTES*60*1000) {
	Log.v(SQConstants.LOG_TAG, "SQAPP: loading cached x/y/z/o")
	currentX = prefs.getLong(X, 0L)
	currentY = prefs.getLong(Y, 0L)
	currentUtmZone = prefs.getLong(Z, 0L)
	timeOffset = prefs.getLong(OFFSET, 0L)
      } else {
	Log.v(SQConstants.LOG_TAG, "SQAPP: NOT loading cached x/y/z/o: too old (ts=" + ts);null
      }
      
    }

  }

  override def onLowMemory() {
    super.onLowMemory()
    Log.v(SQConstants.LOG_TAG, "SQAPP: onLowMemory()")
  }


  override def onConfigurationChanged(newConfig:Configuration) {
    super.onConfigurationChanged(newConfig)
    Log.v(SQConstants.LOG_TAG, "SQAPP: onConfigurationChanged(" + newConfig + ")")
  }


}

