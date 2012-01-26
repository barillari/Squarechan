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

import _root_.android.content.DialogInterface
import _root_.android.os.Bundle
import _root_.android.content.IntentFilter
import _root_.android.content.BroadcastReceiver
import _root_.android.net.ConnectivityManager
import _root_.android.content.Context
import _root_.android.content.Intent
import _root_.android.util.Log
import _root_.android.location.LocationManager
import _root_.android.view.LayoutInflater
import _root_.android.location.LocationListener
import _root_.android.location.Location
import _root_.android.view.View
import _root_.android.widget.Toast
import _root_.android.app.AlertDialog
import _root_.android.os.Handler
import _root_.android.os.Message
import _root_.android.provider.Settings
import _root_.android.net.NetworkInfo
import _root_.android.net.wifi.WifiManager
import _root_.com.github.droidfu.activities.BetterDefaultActivity
import _root_.android.view.ContextThemeWrapper

import TypedResource._

trait LocationFindingTrait extends BetterDefaultActivity with TypedActivity {

  val SNAP_RADIUS = "snap_radius";
  val DEFAULT_SNAP_RADIUS = 100; // meters
  val NEVER_SHOW_WIFI = "sq_never_show_wifi"

  val MINIMUM_LOCATION_UPDATE_INTERVAL = "minimum_location_update_interval"
  val DEFAULT_MINIMUM_LOCATION_UPDATE_INTERVAL = 30 // in minutes
  val SQ_USER_PREFS_FILE = "sq_user_prefs_file"

  private var myLocationManager:LocationManager = null
  var locationListener:LocationListener = null
  val locationLock = new Object

  val mWifiStateChanged = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
  val mNetworkStateChanged = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)

  // Override this if necessary
  def startCheckingPosts(force:Boolean) {
    Log.v(SQConstants.LOG_TAG, "abstract startCheckingPosts called");
  }

  // Override this if necessary
  def updateCurrentPosts() {
    Log.v(SQConstants.LOG_TAG, "abstract updateCurrentPosts called");
  }

  def getLM():LocationManager = {
    locationLock.synchronized({      
      myLocationManager = myLocationManager match {
	case lm:LocationManager => lm
	case _ => getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
      }
    })
    myLocationManager
  }



  def getSnapRadius():Int = {
    val prefs = getSharedPreferences(SQ_USER_PREFS_FILE, Context.MODE_PRIVATE)
    prefs.getInt(SNAP_RADIUS, DEFAULT_SNAP_RADIUS)
  }

  def updateClickNetSettingsView() {
    findView(TR.turn_on_wifi_message) match {
      case v:View => v.setVisibility( if (netIsEnabled && (wifiIsConnected||neverShowWifi) && gpsIsEnabled) View.GONE else View.VISIBLE )
      case _ => null
    }
  }

  val receiverLock = new Object()
  var receiverIsRegistered = false
  val onWifiChanged = new BroadcastReceiver() {
    def onReceive(context:Context, intent:Intent) {
      val wic = wifiIsConnected()  
      Log.v(SQConstants.LOG_TAG, "onWifiChanged invoked: wic=" + wic)
      updateClickNetSettingsView()
      if (wic) {
	unregisterWifiReceiver()
      }
    }
  }


  def unregisterWifiReceiver() {
    receiverLock.synchronized({
      if (receiverIsRegistered) {
	receiverIsRegistered = false
	unregisterReceiver(onWifiChanged)
      }})
  }



  def onOverscrollUp() {
      Log.v(SQConstants.LOG_TAG, "onOverscrollUp() stub")
  }
  def onOverscrollDown() {
      Log.v(SQConstants.LOG_TAG, "onOverscrollDown() stub")
  }


  val handler = new Handler() {
    override def handleMessage(msg:Message) {
      Log.v(SQConstants.LOG_TAG, "handleMessage(): got a message " + msg.what + " " + msg)
      var skip = false;
      msg.what match {
//	case EventConstants.PERIODIC_CHECK => runPeriodicCheck() // FIXME

	case EventConstants.OVERSCROLL_UP => onOverscrollUp()
	case EventConstants.OVERSCROLL_DOWN => onOverscrollDown()

	case EventConstants.RUN_LOCATION_UPDATE => runLocationUpdateTask(getApplicationContext())
	case EventConstants.STOP_LOCATION_UPDATE => {
	  Log.v(SQConstants.LOG_TAG, "handleMessage(): got STOP_LOCATION_UPDATE")
	  stopCheckingLocation()
	}
	case _ => skip = true;
      }
      if (skip) {
	Log.v(SQConstants.LOG_TAG, "SKIPPED a message!")
      } else {
	Log.v(SQConstants.LOG_TAG, "DONE with msg")
      }
    }
  }



  def getApp() = {
    getApplication().asInstanceOf[SQApp]
  }


  def startCheckingLocation() {
    startCheckingLocation(false)
  }

  

  def startCheckingLocation(force : Boolean) {
    if (!(netIsEnabled||gpsIsEnabled)) {
      showEditLocSettingsDialog()
      return
    }
    val app = getApp
    Log.v(SQConstants.LOG_TAG, "startCheckingLocation(): force=" + force)
    if (force || app.currentLocation==null || locationUpdateNeeded()) {
      Log.v(SQConstants.LOG_TAG, "startCheckingLocation(): running current=" +  app.currentLocation + " updateNeeded=" + locationUpdateNeeded())
      val wic = wifiIsConnected()  
      Log.v(SQConstants.LOG_TAG, "startCheckingLocation: wifi on=" + wic)
      updateClickNetSettingsView()
      if (!wic) {
	// fixme: which one do we need?
	receiverLock.synchronized({
	  if (receiverIsRegistered == true) {
	    unregisterWifiReceiver()
	  }
	  registerReceiver(onWifiChanged, mWifiStateChanged)
	  registerReceiver(onWifiChanged, mNetworkStateChanged)
	  receiverIsRegistered = true
	})
      }
      findView(TR.seeking_location_bar).setVisibility(View.VISIBLE)       
      findView(TR.seeking_location_text).setText(getString(R.string.seeking_location))
      handler.removeMessages(EventConstants.STOP_LOCATION_UPDATE)      
      handler.sendMessageDelayed(Message.obtain(handler, EventConstants.RUN_LOCATION_UPDATE), 
				 SQConstants.LOCATION_LOOKUP_TIMEOUT)	
      if (app.currentLocation==null) {
	Log.v(SQConstants.LOG_TAG, "startCheckingLocation(): current==None ")
	val lastKnownGps = getLM().getLastKnownLocation(LocationManager.GPS_PROVIDER);
	val lastKnownNet = getLM().getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
	if (lastKnownGps != null && lastKnownNet != null) {
	  Log.v(SQConstants.LOG_TAG, "startCheckingLocation(): lkg:"+ lastKnownGps + " lkn:" + lastKnownNet + " ibl(lastKnownGps, lastKnownNet):" +  StaticMethods.isBetterLocation(lastKnownGps, lastKnownNet) )
	  app.currentLocation = StaticMethods.isBetterLocation(lastKnownGps, lastKnownNet) match {
	    case true => lastKnownGps
	    case false => lastKnownNet
	  }
	  app.locUpdates += 1 
	} else if (lastKnownGps != null) {
	    app.currentLocation = lastKnownGps
	  app.locUpdates += 1 
	} else if (lastKnownNet != null) {
	  app.currentLocation = lastKnownNet
	  app.locUpdates += 1 
	}
	if (app.currentLocation != null) {
	  updateAccuracyDisplay(app.currentLocation)
	}

      }
      handler.sendMessage(Message.obtain(handler, EventConstants.RUN_LOCATION_UPDATE))	
    }
  }



  def eraseCurrentListener() { 
    Log.v(SQConstants.LOG_TAG, "LMCTL eraseCurrentListener(): eraseCurrent called on:" + locationListener) ;
    locationListener match {
      case ll:LocationListener => { 
	Log.v(SQConstants.LOG_TAG, "LMCTL eraseCurrentListener(): eraseCurrent erasing...") 
	locationLock.synchronized({
	  getLM().removeUpdates(ll)
	  locationListener = null 
	})
      }
      case _ => 
	Log.v(SQConstants.LOG_TAG, "LMCTL eraseCurrentListener(): nothing to erase") 
      null
    }
  }

  def stopCheckingLocation() {
    Log.v(SQConstants.LOG_TAG, "LMCTL stopCheckingLocation(): called");
    eraseCurrentListener();
    findView(TR.seeking_location_bar).setVisibility(View.GONE)
  }

  def wifiIsConnected():Boolean = {
    /* see http://stackoverflow.com/questions/2973290/check-wifi-and-gps-isconnected-or-not-in-android */
    val connManager = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    val wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState()
    (wifi == NetworkInfo.State.CONNECTED || wifi == NetworkInfo.State.CONNECTING)
  }

  def gpsIsEnabled():Boolean = {
    /*http://stackoverflow.com/questions/843675/how-do-i-find-out-if-the-gps-of-an-android-device-is-enabled*/
    getLM.isProviderEnabled(LocationManager.GPS_PROVIDER)
  }

  def netIsEnabled():Boolean = {
    /*http://stackoverflow.com/questions/843675/how-do-i-find-out-if-the-gps-of-an-android-device-is-enabled*/
    getLM.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }




  def updateAccuracyDisplay(loc:Location) {
    val nd = StaticMethods.niceDist(1, loc.getAccuracy, true)
    findView(TR.seeking_location_text).setText(getString(R.string.seeking_location) + " (accuracy: " + nd + ")")    
  }

  def handleChangedLocation(loc:Location) {
    val app = getApp
    Log.v(SQConstants.LOG_TAG, " handleChangedLocation(): checking " + loc + " against=" + app.currentLocation);
    updateAccuracyDisplay(loc)
    var started = false
    if (app.currentLocation == null)  {
      app.currentLocation = loc
      app.locUpdates += 1 
      started = true
      startCheckingPosts(false) // we may not have the write loc, but load _something_
    } else {
      //val dist = haversine_gcdist(loc, app.currentLocation);
      if (StaticMethods.isBetterLocation(loc, app.currentLocation)) {
	Log.v(SQConstants.LOG_TAG, " handleChangedLocation(): isBetterLocation() was true: updating location to " + loc + " from old=" + app.currentLocation);
	app.currentLocation = loc;
	app.locUpdates += 1 
      } else {
	Log.v(SQConstants.LOG_TAG, " handleChangedLocation(): isBetterLocation was false")
	null
      }
    }
    // if we're within the radius, stop
    if (loc.getAccuracy <= getSnapRadius()) {
      stopCheckingLocation()
      if (!started) {
	updateCurrentPosts()
	startCheckingPosts(false)
      }
    }
  }


  def runLocationUpdateTask(context:Context) {
    Log.v(SQConstants.LOG_TAG, " runLocationUpdateTask: CALLED with ctx:" + context)
    eraseCurrentListener();
    Log.v(SQConstants.LOG_TAG, " runLocationUpdateTask: creating location listener")
    locationLock.synchronized({
	locationListener = new LocationListener() {
	  var gpsEnabled = true;
	  var netEnabled = true;
	  def onLocationChanged(loc:Location) {
	    Log.v(SQConstants.LOG_TAG, " onLocationChanged(loc="+loc+")");
	      // Called when a new location is found by the network location provider.
	      handleChangedLocation(loc);
	  }
	  
	  def onStatusChanged(provider:String, status:Int, extras:Bundle) {
	    Log.v(SQConstants.LOG_TAG, " onStatusChanged(provider=" + provider + " status=" + status);
	  }
	
	  def onProviderEnabled(provider:String) {
	    Log.v(SQConstants.LOG_TAG, " onProviderEnabled(provider:"+provider);
	    provider match {
		case LocationManager.GPS_PROVIDER => gpsEnabled = true
	      case LocationManager.NETWORK_PROVIDER => netEnabled = true
	    }
	  }
	
	  def onProviderDisabled(provider:String) {
	    Log.v(SQConstants.LOG_TAG, " onProviderDisabled:" + provider);
	      provider match {
		case LocationManager.GPS_PROVIDER => gpsEnabled = false
		case LocationManager.NETWORK_PROVIDER => netEnabled = false
	      }
	    if (!(gpsEnabled||netEnabled)) {
	      // stop this, show an alert, let the user resume it later. 
	      stopCheckingLocation();
	      Toast.makeText(context, context.getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()

	    }
	    
	  }
	}
    })
    
    Log.v(SQConstants.LOG_TAG, " isProviderEnabled gps: " + getLM().isProviderEnabled(LocationManager.GPS_PROVIDER));
    Log.v(SQConstants.LOG_TAG, " isProviderEnabled net: " + getLM().isProviderEnabled(LocationManager.NETWORK_PROVIDER));
      Log.v(SQConstants.LOG_TAG, " runLocationUpdateTask: ...done making ll:" + locationListener)
    try {
      getLM().requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener)
    } catch {
      case e:Exception => Log.e(SQConstants.LOG_TAG, " LMCTL CAUGHT EXCEPTION " + e + "string:'" + StaticMethods.exToText(e)+"'")
      case _ =>     Log.e(SQConstants.LOG_TAG, " LMCTL CAUGHT NON EXCEPTION")
    }
    Log.v(SQConstants.LOG_TAG, " LMCTL runLocationUpdateTask: ...added ll to network:" + locationListener)
    getLM().requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener)
    Log.v(SQConstants.LOG_TAG, " LMCTL runLocationUpdateTask: ...added ll to GPS:"+locationListener)


  }


  def locationUpdateNeeded():Boolean = {
    getApp.currentLocation match {
      case null => true
      case cl:Location => {
	val delta = System.currentTimeMillis - cl.getTime;
	val prefs = getSharedPreferences(SQ_USER_PREFS_FILE, Context.MODE_PRIVATE);
	val minivl = prefs.getInt(MINIMUM_LOCATION_UPDATE_INTERVAL, DEFAULT_MINIMUM_LOCATION_UPDATE_INTERVAL);
	delta > minivl * 60 * 1000;
      }
    }
  }


  def showEditLocSettingsDialog() {
    updateEditLocationSettingsPrompt()
    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val v:View = inflater.inflate(R.layout.location_settings_dialog, null)

    val (intent_code, request_code) = if (!netIsEnabled || !gpsIsEnabled) {
      v.findView(TR.location_settings_networks).setVisibility( if (netIsEnabled) View.GONE else View.VISIBLE )
      v.findView(TR.location_settings_gps).setVisibility( if (gpsIsEnabled) View.GONE else View.VISIBLE )
      v.findView(TR.never_wifi_checkbox).setVisibility(View.GONE)
      (Settings.ACTION_LOCATION_SOURCE_SETTINGS, SQConstants.ENABLE_GPS_OR_NET)
    } else if (!wifiIsConnected && !neverShowWifi) {
      // show the wifi stuff iff net/gps are already enabled
      val wifiVis = if (wifiIsConnected || neverShowWifi) View.GONE else View.VISIBLE 
      v.findView(TR.location_settings_wifi).setVisibility(wifiVis)
      v.findView(TR.never_wifi_checkbox).setVisibility(wifiVis)
      (Settings.ACTION_WIRELESS_SETTINGS, SQConstants.ENABLE_WIFI)
    } else {
      return
    }

    val cb = v.findView(TR.never_wifi_checkbox)
    cb.setChecked(neverShowWifi)

    val b = new AlertDialog.Builder(this)//new ContextThemeWrapper(this, R.style.Red))
    b.setView(v)
    b.setTitle(getString(R.string.location_settings_alert))
    b.setPositiveButton(getString(R.string.location_settings_edit_settings_button_text), 
				 new DialogInterface.OnClickListener() {
				   def onClick(dialog:DialogInterface , id:Int) {
				     setNeverShowWifi(cb.isChecked)
				     startActivityForResult(new Intent(intent_code), request_code)
				   }
				 })
    b.setNegativeButton(getString(R.string.location_settings_cancel_button_text), 
		       new DialogInterface.OnClickListener() {
			 def onClick(dialog:DialogInterface , id:Int) {
			   setNeverShowWifi(cb.isChecked)
			   dialog.cancel()
			 }
		       })
    val alert = b.create()
    alert.show()
  }

  def neverShowWifi() =
    getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE).getBoolean(NEVER_SHOW_WIFI, false)

  def setNeverShowWifi(state:Boolean) {
    val editor = getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE).edit()
    editor.putBoolean(NEVER_SHOW_WIFI, state)
    editor.commit()
  }

  def clickedSeekingLocationBar(v:View) {
    Log.v(SQConstants.LOG_TAG, " clickedSeekingLocationBar() clicked")
    
    if (!(netIsEnabled && (wifiIsConnected||neverShowWifi) && gpsIsEnabled)) {
      showEditLocSettingsDialog()
    }
    /*    findView(TR.update_post_list_bar) match {
     case null => null
     case v:View => v.setVisibility(View.GONE)
     }*/
  }

  def updateEditLocationSettingsPrompt() = {
    findView(TR.location_unavailable_bar) match {
      case v:View => v.setVisibility( if (netIsEnabled || gpsIsEnabled) View.GONE else View.VISIBLE )
      case _ => null
    }
    updateClickNetSettingsView()
  }

  def checkLocActivityResult(requestCode:Int) = requestCode match {
    case SQConstants.ENABLE_GPS_OR_NET | SQConstants.ENABLE_WIFI => {  
      updateEditLocationSettingsPrompt()
      if (netIsEnabled||gpsIsEnabled) {
	startCheckingLocation()
      }
      true
    }
    case _ => false
  }



}
