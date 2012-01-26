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

import _root_.android.provider.Settings.Secure
import _root_.android.content.DialogInterface
import scala.collection.JavaConversions._
import _root_.android.app.Activity
import _root_.java.security.MessageDigest
import _root_.android.view.View
import _root_.java.lang.Math.{PI, asin, sqrt, cos, sin, pow, round, tan, max, floor}
import _root_.java.util.ArrayList
import _root_.java.util.UUID
import _root_.java.util.Random
import _root_.java.util.HashMap
import _root_.android.util.Log
import _root_.org.json.JSONException
import _root_.org.json.JSONObject
import _root_.org.json.JSONArray
import _root_.java.util.Collections
import _root_.java.util.Comparator
import _root_.java.lang.Thread
/*import _root_.android.util.Base64 // can't use this; it's only api >=8  */

import _root_.android.media.ExifInterface
import _root_.android.graphics.Matrix
import _root_.java.io.StringWriter
import _root_.java.io.PrintWriter
import _root_.android.app.AlertDialog
import _root_.android.app.Dialog
import _root_.android.content.Context
import _root_.java.io.File
import _root_.android.location.Location
import _root_.android.widget.LinearLayout
import _root_.android.graphics.BitmapFactory
import _root_.android.graphics.Bitmap

object StaticMethods {
  val uniqueIDLock = new Object()

// ***********************************************************************************
// copied from the Google examples
val TWO_MINUTES = 1000 * 60 * 2; 

/** Determines whether one Location reading is better than the current Location fix
  * @param location  The new Location that you want to evaluate
  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
  */
def isBetterLocation(location:Location, currentBestLocation:Location):Boolean = {
    if (currentBestLocation == null) {
      return true
    } 
      
    // Check whether the new location fix is newer or older
    val timeDelta = location.getTime() - currentBestLocation.getTime();
    val isSignificantlyNewer = timeDelta > TWO_MINUTES;
    val isSignificantlyOlder = timeDelta < -TWO_MINUTES;
    val isNewer = timeDelta > 0;

    if (isSignificantlyNewer) {
      Log.v(SQConstants.LOG_TAG, "isBetterLocation(): isSignificantlyNewer->ret true")
      return true
    }
    if (isSignificantlyOlder) {
      Log.v(SQConstants.LOG_TAG, "isBetterLocation(): isSignificantlyOlder->ret false")
      return false
    }
      val accuracyDelta = (location.getAccuracy() - currentBestLocation.getAccuracy()).asInstanceOf[Int];
      val isLessAccurate = accuracyDelta > 0;
      val isMoreAccurate = accuracyDelta < 0;
      val isSignificantlyLessAccurate = accuracyDelta > 200;
      
      val isFromSameProvider = isSameProvider(location.getProvider(),
					      currentBestLocation.getProvider());
//      Log.v(SQConstants.LOG_TAG, "isBetterLocation(): isMoreAccurate=" + isMoreAccurate + " isLessAccurate=" + isLessAccurate + "  isNewer=" + isNewer + " isSignificantlyLessAccurate:" + isSignificantlyLessAccurate + " isFromSameProvider:" + isFromSameProvider)
      // Determine location quality using a combination of timeliness and accuracy
      if (isMoreAccurate) {
	Log.v(SQConstants.LOG_TAG, "isBetterLocation(): ism")
        return true
      }
      if (isNewer && !isLessAccurate) {
	Log.v(SQConstants.LOG_TAG, "isBetterLocation(): isn && !isl")
        return true
      }
      if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	Log.v(SQConstants.LOG_TAG, "isBetterLocation(): isn && !isla && ifsp")
        return true
      }
      Log.v(SQConstants.LOG_TAG, "isBetterLocation(): no")
      return false
}


  def isSameProvider(provider1:String, provider2:String) = {
    if (provider1 == null) {
      provider2 == null;
      }
    provider1.equals(provider2);
  }
  // ***********************************************************************************



  def serializeStateToJson(app:SQApp, pla:PostListAdapter):String = {
    val meta = new JSONObject
    meta.put("current_zone", app.currentUtmZone)
    meta.put("current_x", app.currentX)
    meta.put("current_y", app.currentY)
    meta.put("current_time", System.currentTimeMillis - app.timeOffset)
    val top = new JSONObject
    top.put("meta", meta)
    val posts = serializePosts(pla)
    top.put("posts", posts)
    posts.length match {
      // only return json if there were any posts to save
      case 0 => null
      case _ => top.toString
    }

  }

  def serializePosts(pla:PostListAdapter):JSONArray = {
    val out = new JSONArray
    for (i <- 0 until pla.getCount) {
      val p = pla.getItem(i)
      if (!p.isPlaceholder)
	out.put(p.toJSON)
    }
    out
  }




  def inflatePosts(arr:JSONArray):ArrayList[Post] = {
    Log.v(SQConstants.LOG_TAG, " inflatePosts:" + arr.length)
    val out = new ArrayList[Post]()
    var i = 0
    for (i <- 0 until arr.length()) {
      var p = arr.optJSONObject(i);

      var np = new Post(p.getLong("id"), 
			p.optString("content", null), 
			p.getLong("gridx"), p.getLong("gridy"), p.getInt("utm_zone"), 
			p.getInt("rounding"), p.optString("picture_url", null), 
			p.getLong("created"), p.getLong("latest_update"),
			p.optInt("hidden", 0), p.optBoolean("deletable", false), 
			p.optBoolean("deleted", false))
      out.add(np)
      var childarr = p.optJSONArray("children")
      if (childarr != null && childarr.length > 0) {
	for (child <- inflatePosts(childarr)) {
	  np.children.add(child)
	  }
	  }
    }
    out
  }


  def locToString(loc:Location, forUrl:Boolean) = {
    "" + loc.getLatitude + (if (forUrl) {"%2C"} else {","}) + loc.getLongitude
  }


  def decodeJSON(app:SQApp, json:String):ArrayList[Post] = {
    val jobj = new JSONObject(json)
    Log.v(SQConstants.LOG_TAG, " decodeJSON: got meta " + jobj.getJSONObject("meta"))
    Log.v(SQConstants.LOG_TAG, " decodeJSON: setting current_zone to " + jobj.getJSONObject("meta").getLong("current_zone"))
    app.setOXYZ(System.currentTimeMillis - jobj.getJSONObject("meta").getLong("current_time"),
		jobj.getJSONObject("meta").getLong("current_x"),
		jobj.getJSONObject("meta").getLong("current_y"),
		jobj.getJSONObject("meta").getLong("current_zone"))
    Log.v(SQConstants.LOG_TAG, " decodeJSON new time offset " + app.timeOffset)
    StaticMethods.inflatePosts(jobj.getJSONArray("posts"))
  }

  def haversine_gcdist(loc1:Location, loc2:Location):Double = {
    haversine_gcdist(loc1.getLatitude * (PI/180), loc1.getLongitude * (PI/180), 
                     loc2.getLatitude * (PI/180), loc1.getLongitude * (PI/180))
  }
  
  def haversine_gcdist_deg(ps:Double, ls:Double, pf:Double, lf:Double) = 
    haversine_gcdist(ps*(PI/180), ls*(PI/180), pf*(PI/180), lf*(PI/180))


  def haversine_gcdist(ps:Double, ls:Double, pf:Double, lf:Double) = {
      val dsigma = 2 * asin(sqrt(pow((sin((pf-ps)/2)),2) +
				 cos(ps)*cos(pf)*pow((sin((lf-ls)/2)),2)))
    dsigma * SQConstants.ERAD
  }

  def exToText(e:Exception) = {
    // http://the-enginerd.blogspot.com/2005/04/java-convert-stacktrace-to-string.html

    val sw = new StringWriter()
    e.printStackTrace(new PrintWriter(sw))
    sw.toString()
  }

  def logCurrentStackTrace() {
    Log.e(SQConstants.LOG_TAG, "STACK TRACE")
    for (st <- Thread.currentThread().getStackTrace()) {
      Log.e(SQConstants.LOG_TAG, "     " + st.toString)
    }
  }


  def createSimpleMessage(ctx:Context, title:Int, msg:Int) = {
    new AlertDialog.Builder(ctx)
    .setTitle(ctx.getString(title))
    .setMessage(ctx.getString(msg))
    .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
      def onClick(dialog:DialogInterface , whichButton:Int) {
        //noop
      }})		       
    .create();
  }

  def removeExistingPosts(uniqueNewPosts:ArrayList[Post], postListAdapter:PostListAdapter) = {
    // remove the posts currently in postListAdapter from uniqueNewPosts
    Log.v(SQConstants.LOG_TAG, "removeExistingPosts() called with  " +  uniqueNewPosts.size + " new posts" + " and " + postListAdapter.getCount + " existing")
    val hm = new HashMap[Long, Post]()
    for (i <- 0 until postListAdapter.getCount ) {
      if (!postListAdapter.getItem(i).isPlaceholder) 
	hm.put(postListAdapter.getItem(i).id, postListAdapter.getItem(i))
    }
    val out = new ArrayList[Post]()
    for (p <- uniqueNewPosts if ((!hm.containsKey(p.id)) || hm.get(p.id).latest_update < p.latest_update)) {
      out.add(p)
    }
    Log.v(SQConstants.LOG_TAG, "removeExistingPosts() returning " + out.size + " posts")
    out
  }

  def uniquifyPostLists(pl1:ArrayList[Post], pl2:ArrayList[Post]) = {
    // take the latest item from each of these lists
    Log.v(SQConstants.LOG_TAG, " uniquifyPostLists() called")
    val hm = new HashMap[Long, Post]()
    for (p <- pl1) {
      hm.put(p.id, p)
    }
    Log.v(SQConstants.LOG_TAG, " uniquifyPostLists: hm has " + hm.size + " members after insterting newPostsAvailable")
    for (p <- pl2) {
      if (!hm.containsKey(p.id) || hm.get(p.id).latest_update < p.latest_update)
      hm.put(p.id, p)
    }
    new ArrayList[Post](hm.values)
  }


  def sortPosts(al:ArrayList[Post]) = {
    Collections.sort(al, new Comparator[Post]() {
      override def compare(p1:Post, p2:Post) = {
	p1.latest_update.compareTo(p2.latest_update)
      }
      })
    Collections.reverse(al)
    al
  }


  def euclideanDist(x1:Long, y1:Long, x2:Long, y2:Long) = {
    sqrt(pow((x2-x1),2) + pow((y1-y2),2))
  }

  def niceDist(rounding:Int, dist:Double):String = {
    niceDist(rounding, dist, false)
  }
  def niceDist(rounding:Int, dist:Double, justDistance:Boolean) = {

    round(dist) match {
      case x if rounding != 0 && rounding > x =>
	(if (justDistance) "" else "Within ") + rounding + (if (justDistance) " m" else " meters")
      case x if x < 10 =>
	(if (justDistance) "" else "Within ") + "10" + (if (justDistance) " m" else " meters")
      case x if x < 1000 =>
	x + (if (justDistance) " m" else " meters away")
      case x => 
	String.format("%1.1f", (x/1000.0).asInstanceOf[AnyRef]) + " km" + (if (justDistance) "" else " away")
    }
  }

  def getNiceTimeDiff(timeOffset:Long, tstamp:Long) = {
    val diff = round(((System.currentTimeMillis - timeOffset) - tstamp)/1000)
    diff match {
      case x if x < 61       => x + " sec. ago" 
      case x if x < 60*60    => round(x/60) + " min. ago"
      case x if x < 60*60*36 => round(x/(60*60)) + " hr. ago"
      case x                 => round(x/(60*60*24)) match {
	case 1 => "1 day ago"
	case y => y + " days ago"
      }
    }
  }

  def getDist(p:Post, app:SQApp) = {
    app.currentUtmZone == p.utmZone match {
      case true  => 
	euclideanDist(p.gridX, p.gridY, app.currentX, app.currentY)
      case false => {
	val (alat, alng) = convertUtmToLatLng(app.currentX, app.currentY, app.currentUtmZone)
	val (plat, plng) = convertUtmToLatLng(p.gridX, p.gridY, p.utmZone)
	haversine_gcdist_deg(alat, alng, plat, plng)
      }
    }
  }

  def getStatsText(p:Post, app:SQApp) = {
    // get the zones. if this is in a different zone, report no
    // distance. (we could send down lat&lng, but that would break security)
    // if they're in different zones, we just don't show that post (for now; fixme)    
    val timeString = getNiceTimeDiff(app.timeOffset, p.created)
    p.deleted match {
      case true => timeString
      case false => {Log.v(SQConstants.LOG_TAG, " getStatsText(): acz:" + app.currentUtmZone + " puz:" + p.utmZone)
		     niceDist(p.gridRounding, getDist(p, app)) + " - " + timeString
		   }
    }
  }


  def getOrientationTransformDegrees(fn:String) = {
    /// get the degrees needed to transform this to a normal orientation
    new ExifInterface(fn).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) match {
      case ExifInterface.ORIENTATION_ROTATE_180 => -180
      case ExifInterface.ORIENTATION_ROTATE_270 => -90
      case ExifInterface.ORIENTATION_ROTATE_90 => -270
      case x => {
	Log.e(SQConstants.LOG_TAG, "getOrientationTransformDegrees(): unknown orientation " + x)
	0
      }
    }
  }


  def getScaledBitmap(path:String, newmax:Int):Bitmap = {
    Log.e(SQConstants.LOG_TAG, "getScaledBitmap(): called on path " + path + " with newmax=" + newmax)
    Log.e(SQConstants.LOG_TAG, "getScaledBitmap(): path exists? " + (new File(path)).exists)
    val (w, h) = StaticMethods.getImageWidthHeight(path)
    Log.e(SQConstants.LOG_TAG, "getScaledBitmap(): w=" + w + " h=" + h)
    // This SHOULD cause an exception:
    // if (w == 0 || h == 0) {
    //   Log.e(SQConstants.LOG_TAG, "getScaledBitmap(): dimensions = 0! returning")
    //   return null
    // }
    if (w == -1 || h == -1) { 
      Log.e(SQConstants.LOG_TAG, "getScaledBitmap(): height/width were -1!!!!");
      return null;
      }
    val (neww, newh) = scaleProportionately(w, h, newmax)
    val bmp = (w <= neww && h <= newh) match {
      case true => BitmapFactory.decodeFile(path)
      case false => {
	val options = new BitmapFactory.Options()
	val iss = floor(max(w, h)/max(neww, newh))
	Log.v(SQConstants.LOG_TAG, "getScaledBitmap(): oldw=" + w + " oldh=" + h + " neww=" + neww + " newh=" + newh + " iss=" + iss)
	options.inSampleSize = iss.toInt // save memory by downsampling
	BitmapFactory.decodeFile(path, options)
      }
    }
    val matrix = new Matrix()
    val (scalew, scaleh) = (neww.asInstanceOf[Float]/bmp.getWidth, newh.asInstanceOf[Float]/bmp.getHeight)
    matrix.postScale(scalew, scaleh)
    val ori = getOrientationTransformDegrees(path)
    Log.v(SQConstants.LOG_TAG, "getScaledBitmap(): postScale(" + scalew + "," + scaleh+"). rotate=" + ori)
    if (ori != 0) {
      matrix.postRotate(ori)
    }
    Log.v(SQConstants.LOG_TAG, "getScaledBitmap(): createBitmap from range(" + bmp.getWidth+","+bmp.getHeight);
    Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth, bmp.getHeight, matrix, true)
  }

  def scaleProportionately(oldx:Int, oldy:Int, newMax:Int):(Int,Int) = {
    // return x, y scaled proportionately such that the larger of oldx, oldy is no bigger than newmax
    Log.v(SQConstants.LOG_TAG, "scaleProportionately(): called with oldx=" + oldx + " oldy=" + oldy + " newmax=" + newMax)
    val scale = oldx > oldy match {
      case true => oldx/newMax.asInstanceOf[Float]
      case false => oldy/newMax.asInstanceOf[Float]
    }
    (round(oldx/scale), round(oldy/scale))
  }

  def getImageWidthHeight(path:String):(Int,Int) = {
    val bounds = new BitmapFactory.Options()
    bounds.inJustDecodeBounds = true
    Log.v(SQConstants.LOG_TAG, "getImageWidthHeight: image path=" + path)
    BitmapFactory.decodeFile(path, bounds)
    (bounds.outWidth, bounds.outHeight)
  }

  

  /**
   * Converts a UTM (x,y), zone location into lat, lng in degrees
   *     using the method from
   * http://www.uwgb.edu/dutchs/usefuldata/utmformulas.htm
   * represented in
   * http://www.uwgb.edu/dutchs/usefuldata/UTMConversions1.xls    
   *
   * Note that the license on this website is NOT GPL-compatible.
   * Since this distribution is non-commerical (and this isn't using the code
   * -- just adapting the formulae), I think Squarechan is in the clear,
   * but if you plan to use this yourself, I'd recommend looking into this.
   * 
   */
  def convertUtmToLatLng(x:Long, y:Long, z:Long) = {
    val xfactor = 500000
    val K0 = 0.9996
    val ERAD = SQConstants.ERAD
    val PRAD = SQConstants.PRAD
    val ECC = sqrt(1-pow(PRAD,2)/pow(ERAD,2))

    val arc = y/K0
    val mu = arc/(ERAD*(1 - pow(ECC,2)/4 - 3*pow(ECC,4)/64 - 5*pow(ECC,6)/256))
    val e1 = (1 - pow(1-pow(ECC,2),.5))  /  (1+pow(1-pow(ECC,2),.5))
    val J1 = 3*e1/2 - 27*pow(e1,3)/32
    val J2 = 21*pow(e1,2)/16 - 55*pow(e1,4)/32
    val J3 = 151*pow(e1,3)/96
    val J4 = 1097*pow(e1,4)/512 
    
    val fp = mu + J1*sin(2*mu) + J2*sin(4*mu) + J3*sin(6*mu) + J4*sin(8*mu)
    

    val ep2 = pow(ECC,2)/(1-pow(ECC,2))
    val C1 = ep2 * pow(cos(fp),2)
    val T1 = pow(tan(fp),2)
    val R1 = (ERAD*(1-pow(ECC,2))) / pow( 1 - pow(ECC,2) * pow(sin(fp),2), 1.5)
    val N1 = ERAD/pow(1-pow(ECC,2)*pow(sin(fp),2),.5)
    val D =  (xfactor - x)/(N1*K0)

    val Q1 = N1 * tan(fp)/R1
    val Q2 = D*D / 2
    val Q3 = (5 + 3*T1 + 10*C1 - 4*pow(C1,2) - 9*ep2)*pow(D,4)/24
    val Q4 = (61 + 90*T1 + 298*C1 + 45*T1*T1 - 3*C1*C1 - 252*ep2)*pow(D,6)/720

    val lat = fp - Q1 * (Q2-Q3+Q4)
    val latdeg = lat * (180/PI)
					     
    val Q5 = D
    val Q6 = (1 + 2*T1 + C1)*pow(D,3)/6
    val Q7 = (5 * 2*C1 + 28*T1 - 3*pow(C1,2) + 8*ep2 + 24 * pow(T1,2))*pow(D,5)/120

    val lngprime = (Q5-Q6+Q7)/cos(fp)
    val lngdegprime = lngprime * (180/PI)
    val zonecm = if (z>0) 6*z-183 else 3
    val lngdeg = zonecm - lngdegprime

    (latdeg, lngdeg)
  }

  def makeRandomString(salt:String) = {
    val md = MessageDigest.getInstance("SHA-1")
    md.update(System.currentTimeMillis.toString.getBytes)
    md.update(UUID.randomUUID.toString.getBytes)
    if (salt != null) {
          md.update(salt.getBytes)
    }
    val rand = new Random()
    val barr = new Array[Byte](10)
    rand.nextBytes(barr)
    md.update(barr)
    md.update(rand.nextInt(99999999).toString.getBytes)
//    Base64.encodeToString(md.digest(), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING)
    Base64.encodeBase64URLSafeString(md.digest()).replace("=","")
  }

  def dismissDialogSafely(dialog:Dialog) {
    if (dialog != null) {
	try {
	  dialog.dismiss()
	} catch {
	  case iex:IllegalArgumentException => {
	    Log.e(SQConstants.LOG_TAG, "dismissDialogSafely.dialog dismiss threw iex:" + iex);
	    Log.e(SQConstants.LOG_TAG, StaticMethods.exToText(iex));
	  }
	}
    }
  }

  def getUniqueID(act:Activity):String = {
    uniqueIDLock.synchronized({
      val prefs = act.getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE)
      val suid = prefs.getString(SQConstants.SQ_UNIQUE_ID, null)
      if (suid != null) {
//	Log.v(SQConstants.LOG_TAG, "getUniqueID(): using old: " + suid)
	suid
      } else {
	Log.v(SQConstants.LOG_TAG, "getUniqueID(): making new")
	val aid = Secure.getString(act.getContentResolver(), Secure.ANDROID_ID)
//	Log.v(SQConstants.LOG_TAG, "getUniqueID(): System.ANDROID_ID=" + aid)
	val nuid = makeRandomString(aid)
	val editor = act.getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE).edit()
	editor.putString(SQConstants.SQ_UNIQUE_ID, nuid)
	editor.commit()
	nuid
      }
    })
  }


}




