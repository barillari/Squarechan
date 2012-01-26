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

//import collection.jcl.BufferWrapper

import scala.collection.JavaConversions._
import _root_.android.widget.LinearLayout
import _root_.com.github.droidfu.widgets.WebImageView
import _root_.android.widget.Button
import _root_.android.widget.Spinner
import _root_.android.view.LayoutInflater
import _root_.android.content.Intent
import _root_.android.app.Activity
import _root_.android.net.NetworkInfo
import _root_.android.net.wifi.WifiManager
import _root_.android.widget.ArrayAdapter
import _root_.android.app.AlertDialog
import _root_.android.net.ConnectivityManager
import _root_.android.provider.Settings
import _root_.android.location.LocationManager
import _root_.android.location.LocationListener
import _root_.android.location.Location
import _root_.android.media.MediaPlayer
import _root_.android.provider.Settings.Secure
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.view.MotionEvent
import _root_.android.view.View
import _root_.android.widget.AdapterView.OnItemClickListener
import _root_.android.os.AsyncTask
import _root_.android.view.Menu
import _root_.android.content.BroadcastReceiver
import _root_.android.content.DialogInterface
import _root_.android.view.MenuInflater
import _root_.android.widget.AdapterView
import _root_.android.view.MenuItem
import _root_.java.util.concurrent.TimeUnit.SECONDS
import _root_.java.util.HashMap
import _root_.java.util.concurrent.ScheduledExecutorService
import _root_.android.widget.TextView
import TypedResource._
import _root_.android.net.Uri
import _root_.android.util.Log
import _root_.android.os.Handler
import _root_.android.os.Message

import _root_.android.content.DialogInterface

import _root_.java.util.ArrayList
import _root_.org.apache.http.entity.StringEntity
import _root_.org.json.JSONException
import _root_.org.json.JSONObject
import _root_.org.json.JSONArray
import _root_.com.github.droidfu.concurrent.BetterAsyncTask
import _root_.com.github.droidfu.activities.BetterDefaultActivity




object EventConstants {
  val PERIODIC_CHECK = 0
  val RUN_LOCATION_UPDATE = 1
  val STOP_LOCATION_UPDATE = 2
  val OVERSCROLL_UP = 3
  val OVERSCROLL_DOWN = 4
}

object FTTConstants {
  val UNDEFINED = -1
  val JUST_FETCH = 0
  val FETCH_NEWER_AND_PREPEND_NOW = 1
  val FETCH_OLDER_AND_APPEND_NOW = 2
  val FETCH_AND_ADD_IMMEDIATELY = 3
}


trait SQActivityHelperFunctions extends Activity {

  def goHome() {
    val i = new Intent(this, classOf[ThreadListActivity])
    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    startActivity(i)
  }


  def clickShowLargerImage(v:View) {
    // FIXME: use a webview??? (that way we don't break the back btn)
    val url = v.asInstanceOf[WebImageView].getImageUrl().replace("thumb", "slide");
    val bi = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//    val bl = new Bundle
//    bl.putBoolean("new_window", true)
//    bi.putExtra(bl)
    bi.putExtra("new_window", true)
    startActivity(bi);
  }
}



class ThreadListActivity extends BetterDefaultActivity with SQActivityHelperFunctions with SQPostViewHelper  with ShowOneThreadTrait  with LocationFindingTrait with TypedActivity {
  val ABOUT_DIALOG = 1001
  val UNKNOWN_MENU_OPTION = 1002
  val RANGE_WHATS_THIS_DIALOG = 1003

  val MINIMUM_POSTS_UPDATE_INTERVAL = "minimum_posts_update_interval"
  val DEFAULT_MINIMUM_POSTS_UPDATE_INTERVAL = 10 // in minutes
  val SPLASH_REQUEST = 0
  var setRadiusDialog:AlertDialog = null

// http://grahamhackingscala.blogspot.com/2010/02/how-to-convert-java-list-to-scala-list.html

//  implicit def javaList2Seq[T](javaList: java.util.List[T]) : Seq[T] = new BufferWrapper[T]() { def underlying = javaList }


  override def onCreateOptionsMenu(menu:Menu) = {
    super.onCreateOptionsMenu(menu);
    val inflater = getMenuInflater();
    inflater.inflate(R.menu.thread_list_activity_menu, menu);
    true;
  }

  override def onSaveInstanceState(outState:Bundle) {
    super.onSaveInstanceState(outState);
    Log.v(SQConstants.LOG_TAG, "onSaveInstanceState called! bundle=" + outState)
    val state = StaticMethods.serializeStateToJson(getApp(), postListAdapter)
    if (state != null) {
    Log.v(SQConstants.LOG_TAG, "onSaveInstanceState: bundling state=" + state)
      outState.putString("json", state)
    }
  }


  def onAboutClick(v:View) {
    showDialog(ABOUT_DIALOG)
  }

    override def onOptionsItemSelected(item:MenuItem) = {
      item.getItemId() match {
//	case R.id.settings => 
//	case R.id.newpost => createNewPost()
//	case R.id.crash_now => {         	  val foo:View = null; foo.getTag()	}
//	case R.id.about => showDialog(ABOUT_DIALOG)
//	case R.id.stop_checking_location => stopCheckingLocation()
	case R.id.force_relocate => startCheckingLocation(true)
	case R.id.force_reload_posts => {
	  newPostsAvailable.clear()
	  updatePostListBar.setVisibility(View.GONE)
	  //postListAdapter.clear()
	  startCheckingPosts(true)
	}
	case _ => showDialog(UNKNOWN_MENU_OPTION)
      }
      super.onOptionsItemSelected(item)
    }

  override def onCreateDialog(id:Int) = {
    Log.v(SQConstants.LOG_TAG, "Activity.onCreateDialog() id=" + id)

    id match {
      case RANGE_WHATS_THIS_DIALOG => 
	StaticMethods.createSimpleMessage(this, R.string.information, R.string.range_whats_this_explanation_text)
      case SQConstants.FETCH_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.threads_fetch_error)
      case ABOUT_DIALOG => {
	StaticMethods.createSimpleMessage(this, R.string.about_title, R.string.about_message);	
      }
      case UNKNOWN_MENU_OPTION => {
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.no_such_menu_item);	
      }
      case 0 =>  {
	Log.e(SQConstants.LOG_TAG, "oCD(): got bogus id=0 (probably from BetterAsyncTask)")
	this.setProgressDialogTitleId(R.string.loading)
	this.setProgressDialogMsgId(R.string.checking_posts_dotdotdot)
	this.createProgressDialogWrapper()
      }
      case SQConstants.NEED_LOCATION_BEFORE_POSTING =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.need_loc_before_posting)
      case x => 
	Log.e(SQConstants.LOG_TAG, "oCD(): got bogus id=" + x)
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.no_such_message);	
    }
  }

  // FIXME: this should be in the *application*, right?
  def setupPeriodicCheck() {
    val app = getApp()
    if (app.periodicTaskHandle == null || app.periodicTaskHandle.isCancelled() || app.periodicTaskHandle.isDone()) {
      Log.v(SQConstants.LOG_TAG, "setupPeriodicCheck(): pth" + app.periodicTaskHandle);
      if (app.periodicTaskHandle != null) {
	Log.v(SQConstants.LOG_TAG, "                    ... cancelled:" +  
	      app.periodicTaskHandle.isCancelled() + " done:" + app.periodicTaskHandle.isDone());
	// make sure it's cancelled
	if (!app.periodicTaskHandle.isCancelled()) {
	  app.periodicTaskHandle.cancel(true);
	}
      }
      val thetask = new Runnable() {
        def run() {   
	  Log.v(SQConstants.LOG_TAG, ">>>sending periodic check message<<<")
          handler.sendMessage(Message.obtain(handler, EventConstants.PERIODIC_CHECK))
	};
      }
      app.periodicTaskHandle = app.scheduler.scheduleAtFixedRate(thetask, 0, 10*60, SECONDS);
    }
  }
  							 
  override def mergePostsIntoPLA(pl:ArrayList[Post]) {
    postListAdapter.synchronized({
      // interleave the lists, replacing any duplicate(=>updated)
      // posts we could do this in linear time, but I'm too lazy to
      // look for the inevitable off-by-one errors

      Log.v(SQConstants.LOG_TAG, "uPLCH: pl.size:" + pl.size + " pLA.gC:"+postListAdapter.getCount);

      val hm = new HashMap[Long, Post]()
      for (i <- 0 until postListAdapter.getCount) {
	val opost = postListAdapter.getItem(i)
	if (!opost.isPlaceholder) {
	  hm.put(opost.id, opost)
	  Log.v(SQConstants.LOG_TAG, "inserted id:" + opost.id + " post:" + opost)
	  null
	}
      }
      // first, pick the latest version of every post
      for (i <- 0 until pl.size) {
	val npost = pl.get(i)
	if (!hm.containsKey(npost.id) || hm.get(npost.id).latest_update < npost.latest_update) {
	  Log.v(SQConstants.LOG_TAG, "   inserted new post " + npost.id + " post:" + npost)
	  hm.put(npost.id, npost)
	} else {
	  Log.v(SQConstants.LOG_TAG, "skipping not-updated new post " + npost.id)
	  null
	}
      }

      pl.clear()

      Log.v(SQConstants.LOG_TAG, "uPLCH: total posts after merge:" + hm.size)

      // now, sort them by latest_update
      var sposts = StaticMethods.sortPosts(new ArrayList[Post](hm.values))

      // chop it to the limit
      var size = sposts.size
      while (size > SQConstants.MAX_THREADS_TO_DISPLAY) {
	sposts.remove(SQConstants.MAX_THREADS_TO_DISPLAY)
        size -= 1
      }

      Log.v(SQConstants.LOG_TAG, "uPLCH: total posts after chop:" + sposts)

      postListAdapter.clear()
      for (p <- sposts) postListAdapter.add(p)
      Log.v(SQConstants.LOG_TAG, "uPLCH: after all that, pLA.gC:"+postListAdapter.getCount)
      null
    })}    
  

  def clickRangeWhatsThis(v:View) {
    showDialog(RANGE_WHATS_THIS_DIALOG)
  }
  

  def updatePostListClickHandler(target:View) {
    Log.v(SQConstants.LOG_TAG, "updatePostListClickHandler: called!")
    updatePostListBar.setVisibility(View.GONE)
    mergePostsIntoPLA(newPostsAvailable)
  }
				 

  def overscroll(isUp:Boolean) {
    /* * this function is indempotent over short time periods: if you
     call it a zillion times in a few seconds (which pressing and
     dragging slowly will do), it will only invoke the overscroll-up action once*/
    var doPlay = false
    postListAdapter.synchronized({
      val ct = postListAdapter.getCount
      if (ct == 0) { return }
      if (isUp)  {
	if (postListAdapter.getItem(0).isPlaceholder) { return }
	postListAdapter.insert(new Post(true), 0)
	startCheckingPostsInner(true, FTTConstants.FETCH_NEWER_AND_PREPEND_NOW)
	doPlay = true
      }
      if (!isUp)  {
	if (postListAdapter.getItem(ct - 1).isPlaceholder) { return }
	postListAdapter.add(new Post(false))
	startCheckingPostsInner(true, FTTConstants.FETCH_OLDER_AND_APPEND_NOW)
	doPlay = true
      }
    })

    if (doPlay) {
      Log.v(SQConstants.LOG_TAG, "Click: playing sound")
      val mp = MediaPlayer.create(this, R.raw.tsk)
      mp.start()
    }

  }

  override def onOverscrollUp() {
    Log.v(SQConstants.LOG_TAG, "onOverscrollUp() impl")
    overscroll(true) 
  }
  override def onOverscrollDown() {
      Log.v(SQConstants.LOG_TAG, "onOverscrollDown() impl")
    overscroll(false)
  }




  def clickThreadSeeAllButton(v:View) {
    val pvi = v.getParent.getParent.getParent.asInstanceOf[View].getTag
//    Log.v(SQConstants.LOG_TAG, "seeall clixz0r: pvi.post " + pvi.asInstanceOf[PostViewInfo].post)
    showOneThread(pvi.asInstanceOf[PostViewInfo].post.id, false, StaticMethods.locToString(getApp().currentLocation, false))
  }


  override def onActivityResult(requestCode:Int, resultCode:Int, data:Intent){
    if (checkLocActivityResult(requestCode)) return
    requestCode match {
      case SPLASH_REQUEST => resultCode match {
	case Activity.RESULT_FIRST_USER =>  {
	  val editor = getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE).edit()
	  editor.putBoolean(SQConstants.SAW_SPLASH_SCREEN, true)
	  editor.commit()
	}
	case Activity.RESULT_CANCELED => finish()
	case _ => { Log.v(SQConstants.LOG_TAG, "unknown result " + resultCode); finish(); }
      }
      case _ => { Log.v(SQConstants.LOG_TAG, "unknown request " + requestCode); finish(); }
    }
  }

  def runPeriodicCheck() {
    // first, see if the location is stale. if so, update it and download the posts
    // if not, see if the posts are stale. if so, update them
      locationUpdateNeeded() match {
      case true => startCheckingLocation(true);
      case false => if (postsUpdateNeeded) {startCheckingPosts(true)};
    }
  }

  def updateTitleBarRadius() {
    findView(TR.btn_title_radius).setText(SQConstants.RADII.indexOf(getRadius) match {
      case -1 => {
	Log.e(SQConstants.LOG_TAG, "updateTitleBarRadius: got unmatchable radius " + getRadius)
	getString(R.string.error)
      }
      case x:Int => SQConstants.RADII_TEXT(x)
    })}



  def dropOutOfRangePosts() {
    // remove anything out of range from the list
    val rad = getRadius
    val app = getApp
    postListAdapter.synchronized({
      for (i <- 0 until postListAdapter.getCount reverse) {
	val p = postListAdapter.getItem(i)
	Log.v(SQConstants.LOG_TAG, "dropOutOfRangePosts:  post.id=" + p.id + " i=" + i)
	  if (!p.isPlaceholder) {
	    val dist = StaticMethods.getDist(p, app)
	      if (dist > rad) {
		Log.i(SQConstants.LOG_TAG, "dropOutOfRangePosts: dropping post.id=" + p.id + " dist=" + dist + " > rad=" + rad)
		postListAdapter.remove(p)
	      } else {
		Log.v(SQConstants.LOG_TAG, "dropOutOfRangePosts: keeping post.id=" + p.id + " dist=" + dist + " !> rad=" + rad)
	      }
	  }
      }
      })
  }

  def clickChangeRadius(clicked:View) {
    val builder = new AlertDialog.Builder(this)
    builder.setTitle(R.string.set_range)
    var alert:AlertDialog = null

    val sel = SQConstants.RADII.indexOf(getRadius) match { 
      case -1 => 0
      case x:Int => x
    }
    
    builder.setSingleChoiceItems(SQConstants.RADII_TEXT.toArray.asInstanceOf[Array[CharSequence]], sel, new DialogInterface.OnClickListener() {
      def onClick(dialog:DialogInterface, item:Int) {
	val editor = getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE).edit()
	editor.putInt(SQConstants.RADIUS, SQConstants.RADII(item))
	editor.commit()
	updateTitleBarRadius()
	dropOutOfRangePosts()
	startCheckingPosts(true)
	alert.dismiss()
      }
    })
    alert = builder.create()
    alert.show()

//			    setRadiusDialog = null
//    setRadiusDialog = b.create()
//    setRadiusDialog.show()
  }


  override def onCreate(saved: Bundle) {
    super.onCreate(saved)
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onCreate called; app =  " + getApp())
    // fixme: make sure we won't crash if rotated before prefs set
    val prefs = getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE);
    if (!prefs.getBoolean(SQConstants.SAW_SPLASH_SCREEN, false)) {
      val intent = new Intent(this, classOf[SplashScreen]);
      startActivityForResult(intent, SPLASH_REQUEST);
    }

    StaticMethods.getUniqueID(this)


    setContentView(R.layout.mymainscreen)
    setTitle(getString(R.string.viewing_all_threads_title))
    updateTitleBarRadius()
    findView(TR.btn_title_radius_separator).setVisibility(View.VISIBLE)
    findView(TR.btn_title_radius).setVisibility(View.VISIBLE)

    postListView = findView(TR.thread_list)
    Log.v(SQConstants.LOG_TAG, "plv =  " + postListView)
    postListView.asInstanceOf[SQListView].setHandler(handler)
//    postListView = findViewById(R.id.thread_list).asInstanceOf[SQListView]
    postListView.setDividerHeight(SQConstants.LIST_DIVIDER_HEIGHT)

    postListView.setOnItemClickListener(new OnItemClickListener() {
      def onItemClick(parent:AdapterView[_] , view:View, position:Int , id:Long ) {
	Log.v(SQConstants.LOG_TAG, "plv:onItemClick: parent:" + parent + " view:" + view + " pos:" + position + " id:" + id)
	view match {
	  case ll:LinearLayout => ll.getTag match {
	    case pvi:PostViewInfo => {
	      Log.v(SQConstants.LOG_TAG, "plv:onItemClick: clicked post object=" + pvi.post)
	      showOneThread(pvi.post.id, false, StaticMethods.locToString(getApp().currentLocation, false))
	    }
	    case x => Log.e(SQConstants.LOG_TAG, "plv:onItemClick: non-PVI-tag: " + x)
	  }
	  case _ => Log.e(SQConstants.LOG_TAG, "plv:onItemClick: non-LinearLayout: " + view)
	}

      }
    })


    updatePostListBar = findView(TR.update_post_list_bar)
    postListAdapter = new PostListAdapter(this, getApp(), false)
    postListView.setAdapter(postListAdapter)

    if (saved != null && saved.containsKey("json")) {
      postListView.setVisibility(View.VISIBLE)
      updateThreadViewWithPosts(this, StaticMethods.decodeJSON(getApp(), saved.getString("json")), FTTConstants.UNDEFINED)
    }
    setupPeriodicCheck()
  }


  def postsUpdateNeeded():Boolean = {
    val delta = System.currentTimeMillis - getApp().lastPostsUpdateTime;
    val prefs = getSharedPreferences(SQ_USER_PREFS_FILE, Context.MODE_PRIVATE);
    val minivl = prefs.getInt(MINIMUM_POSTS_UPDATE_INTERVAL, DEFAULT_MINIMUM_POSTS_UPDATE_INTERVAL);
    Log.v(SQConstants.LOG_TAG, "postsUpdateNeeded(): delta=" + delta + " minlvl=" + (minivl * 60 * 1000) + " result="+(delta > minivl * 60 * 1000))
    delta > minivl * 60 * 1000;
  }


  override def updateCurrentPosts() {
    for (i <- 0 until postListView.getChildCount) {
      var pvi = postListView.getChildAt(i).getTag.asInstanceOf[PostViewInfo]
      if (pvi != null && pvi.post != null && !pvi.post.isPlaceholder) { // e.g., it's not a placeholder
	pvi.updateStats(getApp())
	for (j <- 0 until pvi.repliesList.getChildCount) {
	  var cpvi = pvi.repliesList.getChildAt(j).getTag.asInstanceOf[PostViewInfo]
	  cpvi.updateStats(getApp())
	}
      }
    }
  }

  def clickForceCheckPosts(v:View) {
    startCheckingPostsInner(true, FTTConstants.FETCH_AND_ADD_IMMEDIATELY)
  }


  override def startCheckingPosts(force:Boolean) {
    startCheckingPostsInner(force, FTTConstants.JUST_FETCH)
  }
    
  def startCheckingPostsInner(force:Boolean, action:Int) {
    // this is for fetching main-page posts only. there's a different function for the single-thread pages
    Log.v(SQConstants.LOG_TAG, "startCheckingPosts(): called")
    if (!(force||postsUpdateNeeded)) {
      Log.v(SQConstants.LOG_TAG, "startCheckingPosts(): update not needed and force not set; skipping.");
      return;
    }
    if (getApp().currentLocation==null) {
      Log.v(SQConstants.LOG_TAG, "startCheckingPosts(): no location. QUITTING!")
      return;
    }
    val posts = new ArrayList[Long]()
    postListAdapter.synchronized({
      for (i <- 0 until postListAdapter.getCount) {
	if (!postListAdapter.getItem(i).isPlaceholder) 
	  posts.add(postListAdapter.getItem(i).id)
      }
    })

    val params = new PostParams(StaticMethods.locToString(getApp().currentLocation, true), posts, action, null)
    startFTT(this, params)
  }




  def needsUpdate():Boolean = {
    val lupt = getApp.latestPostSeenLUTime
    if (lupt == 0) { return false }
    postListAdapter.synchronized({
      for (i <- 0 until postListAdapter.getCount) {
	if (postListAdapter.getItem(i).latest_update >= lupt) {
	  return false
	}
      }
    })
    return true
  }	   

  override def onResume() {
    super.onResume()
    Log.v(SQConstants.LOG_TAG, "TLA.onResume: called")
    if (needsUpdate() == true) {
      Log.v(SQConstants.LOG_TAG, "TLA:onResume: lastUpdatedPostTime calls for update")
      getApp.latestPostSeenLUTime = 0      
      startCheckingPosts(true)
    } else {
      startCheckingLocation()
      startCheckingPosts(false)
    }
  }

  override def onDestroy() {
    super.onDestroy()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onDestroy called")
  }

  override def onStop() {
    super.onStop()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onStop called")
  }

  override def onRestart() {
    super.onRestart()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onRestart called")
  }

  override def onStart() {
    super.onStart()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onStart called")
  }


  override def onPause() {
    super.onPause()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: onPause called")
    stopCheckingLocation()    
    unregisterWifiReceiver()
    Log.v(SQConstants.LOG_TAG, "TLA lifecycle: setRadiusDialog=" +setRadiusDialog) 
    if (setRadiusDialog != null) {
      Log.v(SQConstants.LOG_TAG, "TLA lifecycle: closing set-radius dialog")
      setRadiusDialog.cancel()
      setRadiusDialog = null
    }

  }


}
