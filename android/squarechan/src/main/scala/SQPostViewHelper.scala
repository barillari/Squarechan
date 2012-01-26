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

import scala.collection.JavaConversions._
import _root_.com.github.droidfu.concurrent.BetterAsyncTask
import _root_.android.content.Context
import _root_.android.widget.TextView
import _root_.java.util.ArrayList
import _root_.android.widget.LinearLayout
import _root_.android.widget.ListView
import _root_.android.app.Activity
import _root_.android.util.Log
import _root_.android.widget.Toast
import _root_.android.view.View
import _root_.android.view.Window
import _root_.android.content.Intent
import _root_.android.media.MediaPlayer
import _root_.org.apache.http.HttpEntity
import _root_.org.apache.http.HttpResponse
import _root_.org.apache.http.client.ClientProtocolException
import _root_.org.apache.http.client.HttpClient
import _root_.org.apache.http.client.methods.HttpPost
import _root_.org.apache.http.client.methods.HttpGet
import _root_.org.apache.http.impl.client.DefaultHttpClient
import _root_.org.apache.http.params.BasicHttpParams
import _root_.org.apache.http.params.HttpConnectionParams
import _root_.org.apache.http.params.HttpParams
import _root_.org.apache.http.params.HttpProtocolParams
import _root_.org.apache.http.util.EntityUtils
import _root_.com.github.droidfu.activities.BetterDefaultActivity


trait SQPostViewHelper extends BetterDefaultActivity with TypedActivity {
  var singlePostViewMode:Boolean = false
  var postListView:ListView = null
  var updatePostListBar:LinearLayout = null
  var postListAdapter:PostListAdapter = null
  val newPostsAvailable = new ArrayList[Post]()
  var savedLocation:String = null
  
  def clickCreateNewPost(v:View) { createNewPost() }

  def createNewPost() {
    if (getApplication.asInstanceOf[SQApp].currentLocation==null) {
      showDialog(SQConstants.NEED_LOCATION_BEFORE_POSTING)
      return
    }
    val intent = new Intent(this, classOf[NewPostActivity])
    val pname = classOf[NewPostActivity].getPackage.getName
    intent.putExtra(pname + "." + NPAConstants.SAVED_LOCATION, StaticMethods.locToString(getApplication.asInstanceOf[SQApp].currentLocation, false))
    startActivity(intent)
  }

  def clickThreadReplyButton(v:View) {
    Log.v(SQConstants.LOG_TAG, "reply clixz0r")
    val pvi = v.getParent.getParent.getParent.asInstanceOf[View].getTag
    clickReply(pvi.asInstanceOf[PostViewInfo].post.id)
  }

  def clickReply(postid:Long) {
    Log.v(SQConstants.LOG_TAG, "clickThreadReplyButton: clicked reply for id=" + postid)
    val intent = new Intent(this, classOf[NewPostActivity])
    val pname = classOf[NewPostActivity].getPackage.getName
    val ename = pname +  "." + OTAConstants.REPLY_TO_ID
//    Log.v(SQConstants.LOG_TAG, "clickReply: bundling under ename=" + ename)
    intent.putExtra(ename, new java.lang.Long(postid).longValue())
    val cloc = getApplication.asInstanceOf[SQApp].currentLocation match {
      case null => savedLocation
      case acl => StaticMethods.locToString(acl, false)
    }
    if (cloc==null) {
      showDialog(SQConstants.LOCATION_ERROR)
      return
    }

    intent.putExtra(pname + "." + NPAConstants.SAVED_LOCATION, cloc)
    startActivity(intent)
  }

  def startFTT(context:Context, params:PostParams) = {
    val ftt = new FetchThreadsTask(this)
    ftt.disableDialog()
    ftt.execute(params)
    ftt
  }

  class FetchThreadsTask(context:Context) extends BetterAsyncTask[PostParams, Int, ArrayList[Post]](context) {
    var action = FTTConstants.UNDEFINED
    override def doCheckedInBackgroundSingleton(context:Context, params:PostParams):ArrayList[Post] = {
      Log.v(SQConstants.LOG_TAG, " FTT.doCheckedInBackground() called");
      Log.v(SQConstants.LOG_TAG, " FTT.doCheckedInBackground() ...... on ctx:" + context + " with params " + params);
      action = params.action
      getPostsViaHttp(context.asInstanceOf[Activity].getApplication.asInstanceOf[SQApp], params, singlePostViewMode)
    }

    override def handleError(context:Context, exception:Exception) {
      Log.e(SQConstants.LOG_TAG, " fetch error: " + exception);
      Log.e(SQConstants.LOG_TAG, " FTT: handleError: " + StaticMethods.exToText(exception));
      showDialog(SQConstants.FETCH_ERROR)

      resetUI()
    }

    override def before(context:Context) {
      Log.v(SQConstants.LOG_TAG, " FTT.before() called on ctx:" + context);
      context.asInstanceOf[Activity].setProgressBarVisibility(true)
      findView(TR.title_refresh_progress) match {
	case v:View => v.setVisibility(View.VISIBLE)
	case _ => null
      }
      // findView(TR.title_activity_indicator) match {
      // 	case v:TextView => {
      // 	  v.setText(getString(R.string.checking))
      // 	  v.setVisibility(View.VISIBLE)
      // 	}
      // 	case _ => null
      // }

      findView(TR.btn_title_refresh) match {
	case v:View => v.setVisibility(View.GONE)
	case _ => null
      }

//      if (postListAdapter.getCount == 0) {	findView(TR.loading_posts_bar).setVisibility(View.VISIBLE)      }
    }

    def resetUI() {
      context.asInstanceOf[Activity].setProgressBarVisibility(false)
//      findView(TR.loading_posts_bar).setVisibility(View.GONE)
      if (postListView.getVisibility==View.GONE) {
	Log.v(SQConstants.LOG_TAG, " FTT.after() making postListView visible!")
	postListView.setVisibility(View.VISIBLE)
      }
      findView(TR.btn_title_refresh) match {
	case v:View => v.setVisibility(View.VISIBLE)
	case _ => null
      }

      findView(TR.title_refresh_progress) match {
	case v:View => v.setVisibility(View.GONE)
	case _ => null
      }
      // findView(TR.title_activity_indicator) match {
      // 	case v:TextView => v.setVisibility(View.GONE)
      // 	case _ => null
      // }
    }

    override def after(context:Context, result:ArrayList[Post]) {
      val app = context.asInstanceOf[Activity].getApplication.asInstanceOf[SQApp]
      if (singlePostViewMode) 
	result.foreach({ p:Post => {if(app.latestPostSeenLUTime < p.latest_update) { app.latestPostSeenLUTime = p.latest_update}}})

      Log.v(SQConstants.LOG_TAG, " FTT.after() plv=" + postListView)
      Log.v(SQConstants.LOG_TAG, " FTT.after() plv.visibility:" + postListView.getVisibility)
      updateThreadViewWithPosts(context, result, action)
      resetUI()
    }
  }

  def getRadius() = {
    val prefs = getSharedPreferences(SQConstants.SQ_PREFS_FILE, Context.MODE_PRIVATE)
    prefs.getInt(SQConstants.RADIUS, SQConstants.DEFAULT_RADIUS)
  }

  def getPostsViaHttp(app:SQApp, pobj:PostParams, singlePostViewMode:Boolean):ArrayList[Post] = {
      // based on
      // http://code.google.com/p/android-json-rpc/source/browse/trunk/android-json-rpc/src/org/alexd/jsonrpc/JSONRPCHttpClient.java

    var uri = SQConstants.FETCH_THREADS_URI + "&loc=" + pobj.locString + "&postids=" + pobj.postIds.mkString(",")
    
    if (singlePostViewMode) {
      uri += "&only_these_postids=1&show_all_replies=1&owner_token=" + pobj.owner_token
    } else {
      uri += "&radius=" + getRadius
    }

    if (pobj.action == FTTConstants.FETCH_NEWER_AND_PREPEND_NOW) {
      uri += "&only_newer=1"
    }

    if (pobj.action == FTTConstants.FETCH_OLDER_AND_APPEND_NOW) {
      uri += "&only_older=1"
    }


      Log.v(SQConstants.LOG_TAG, " FetchThreadsTask.doCheckedInBackground() building request uri:" + uri)

      val request = new HttpGet(uri);
      val hparams = new BasicHttpParams();
      HttpConnectionParams.setConnectionTimeout(hparams, SQConstants.FETCH_THREAD_HTTP_TIMEOUT);
      HttpConnectionParams.setSoTimeout(hparams, SQConstants.FETCH_THREAD_HTTP_TIMEOUT);
      request.setParams(hparams);
//      val entity = new StringEntity(jsonObj.toString)
//      entity.setContentType("application/json")
      val client = new DefaultHttpClient()
//      request.setEntity(entity)
      Log.v(SQConstants.LOG_TAG, " FetchThreadsTask.doCheckedInBackground(): " + pobj.postIds.size + " executing request...")
      val response = client.execute(request)
      Log.v(SQConstants.LOG_TAG, " FetchThreadsTask.doCheckedInBackground(): response returned. checking status code")
      response.getStatusLine.getStatusCode match {
	case 200 => { 
	  Log.v(SQConstants.LOG_TAG, " FetchThreadsTask.doCheckedInBackground() got 200 OK")
	  val responseString = EntityUtils.toString(response.getEntity()).trim
	  StaticMethods.decodeJSON(app, responseString)
	}
	case other   => {  
	  Log.e(SQConstants.LOG_TAG, " FetchThreadsTask.doCheckedInBackground() FAILED code + " + other)
	  throw new Exception("Error: " + response.getStatusLine.getStatusCode) 
	}
      }
  }

  def clickNoPostsYet(v:View) {
    Log.v(SQConstants.LOG_TAG, "clickNoPostsYet " + v)

    createNewPost()
  }



  def updateThreadViewWithPosts(context:Context, result:ArrayList[Post], action:Int) {
	// the task itself sends the current loc and shown posts. we get
	// back a list of posts. stuff them into a table and ask the user
	// if we want to show them (if there's already stuff on the screen) 
	// if the table is nonempty, make sure we don't create dupes (toss older versions of items)
	// walk the list of shown posts. 
    var doMerge = false
    postListAdapter.synchronized({
      if (singlePostViewMode) {
	postListAdapter.clear()
      }
	
      if (postListAdapter.getCount == 0) {
	for (p <- result) postListAdapter.add(p)
      } else {
	  newPostsAvailable.synchronized({
	    // no need to sort this...it will be sorted when we fold it into the arrayadapter
	    val uniqueNewPosts = StaticMethods.uniquifyPostLists(newPostsAvailable, result) 
	    val nl = StaticMethods.removeExistingPosts(uniqueNewPosts, postListAdapter) 
	    if (nl.size > 0) {
	      newPostsAvailable.clear()
	      newPostsAvailable.addAll(nl)
		val t = "" + newPostsAvailable.size + " " + context.getString(R.string.update_post_list_text)
	      context.asInstanceOf[TypedActivity].findView(TR.update_post_list_text_view).setText(t)
	      if (action != FTTConstants.FETCH_NEWER_AND_PREPEND_NOW && action != FTTConstants.FETCH_OLDER_AND_APPEND_NOW)
		updatePostListBar.setVisibility(View.VISIBLE)
	    }
	    })
	action match {
	  case FTTConstants.FETCH_NEWER_AND_PREPEND_NOW => {
	    val top = postListAdapter.getItem(0)
	    if (top.isPlaceholder == true) {
	      postListAdapter.remove(top)
	    }
	    if (newPostsAvailable.length > 0) {
	      doMerge = true
	    } else {
	      Toast.makeText(getApplicationContext(), R.string.no_newer_posts_in_area, Toast.LENGTH_SHORT).show();
	    }
	  }
	  case FTTConstants.FETCH_OLDER_AND_APPEND_NOW => {
	    val bot = postListAdapter.getItem(postListAdapter.getCount-1)
	    if (bot.isPlaceholder == true) {
	      postListAdapter.remove(bot)
	    }
	    if (newPostsAvailable.length > 0) {
	      doMerge = true
	    } else {
	      Toast.makeText(getApplicationContext(), R.string.no_older_posts_in_area, Toast.LENGTH_SHORT).show();
	    }
	  }
	  case _ => null
	  }
	
      }

      // fixme: there might be a race cond here. should rethink the way we lock stuff.
      if (doMerge || activity == FTTConstants.FETCH_AND_ADD_IMMEDIATELY) {
	mergePostsIntoPLA(newPostsAvailable)
	if (doMerge) {
	  val mp = MediaPlayer.create(this, R.raw.fasterpop)
	  mp.start()
	}

      }

      findView(TR.no_posts_yet_bar).setVisibility(postListAdapter.getCount match {
	case 0 => View.VISIBLE
	case _ => View.GONE })
    })


  }

  def mergePostsIntoPLA(pl:ArrayList[Post]) {
    Log.v(SQConstants.LOG_TAG, "mergePostsIntoPLA STUB")
  }

}
