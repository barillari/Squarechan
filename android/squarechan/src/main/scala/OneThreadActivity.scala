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
import _root_.android.widget.LinearLayout
import _root_.android.view.Window
import _root_.android.view.View
import _root_.android.app.ProgressDialog
import _root_.android.view.Menu
import _root_.android.view.MenuInflater
import _root_.android.view.MenuItem
import _root_.java.util.ArrayList
import _root_.android.app.AlertDialog
import _root_.android.content.DialogInterface
import _root_.android.content.DialogInterface.OnClickListener
import _root_.android.os.Message
import _root_.android.os.Handler
import _root_.android.util.Log
import _root_.android.content.Intent
import _root_.android.os.Bundle
import _root_.org.apache.http.message.BasicNameValuePair
import _root_.org.apache.http.NameValuePair
import _root_.org.apache.http.client.entity.UrlEncodedFormEntity
import _root_.android.app.Activity
import _root_.com.github.droidfu.concurrent.BetterAsyncTask
import _root_.com.github.droidfu.activities.BetterDefaultActivity

import _root_.org.apache.http.HttpEntity
import _root_.org.apache.http.entity.ByteArrayEntity
import _root_.org.apache.http.HttpResponse
import _root_.org.apache.http.client.ClientProtocolException
import _root_.org.apache.http.client.HttpClient
import _root_.org.apache.http.client.methods.HttpPost
import _root_.org.apache.http.impl.client.DefaultHttpClient
import _root_.org.apache.http.params.BasicHttpParams
import _root_.org.apache.http.params.HttpConnectionParams
import _root_.org.apache.http.params.HttpParams
import _root_.org.apache.http.params.HttpProtocolParams
import _root_.org.apache.http.util.EntityUtils



// FIXME/TODO: auto-reload with new replies (<=> set up a socket to
// the server? poll? long-poll?) I like twitter's "scroll up to force
// reload feature


object OTAConstants {
  val POST_ID = "PostId"  
  val REPLY_TO_ID = "ReplyToId"  
  val OK = "ok"
  val FINISH_MSG = 1
  val DELETE_MSG = 2
}

class OneThreadActivity extends BetterDefaultActivity with SQActivityHelperFunctions with SQPostViewHelper with TypedActivity {
  var postid:Long = -1
  var deletePostTask:DeletePostTask = null
  var deleteProgressDialog:ProgressDialog = null

  class DeletePostParams(val postId:Long, val ownerToken:String) {}

  val act = this
  private val mHandler = new Handler() {
    override def handleMessage(msg:Message) {
      msg.what match {
	case OTAConstants.FINISH_MSG => finish
	case OTAConstants.DELETE_MSG => {
	  val dpt = new DeletePostTask(act)
	  dpt.disableDialog()
	  dpt.execute(new DeletePostParams(msg.obj.asInstanceOf[Long], StaticMethods.getUniqueID(act)))
	}
      }
    }
  }

  def pollThread() {
    val al = new ArrayList[Long]()
    al.add(postid)
    Log.v(SQConstants.LOG_TAG, "pollThread(): savedLocation=" + savedLocation)
    val params = new PostParams(savedLocation.replace(",","%2C"), al, FTTConstants.UNDEFINED, StaticMethods.getUniqueID(this))
    Log.v(SQConstants.LOG_TAG, "pollThread(): startftt(" + this + "," + params)
    startFTT(this, params)
  }

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    singlePostViewMode = true
    requestWindowFeature(Window.FEATURE_PROGRESS)
    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate() called")
    setContentView(R.layout.mymainscreen)
    findView(TR.window_title_text).setText(R.string.one_thread)
    findView(TR.window_title_text).setVisibility(View.VISIBLE)
    findView(TR.window_title_button).setVisibility(View.GONE)
    findView(TR.btn_title_home).setVisibility(View.VISIBLE)
    findView(TR.btn_title_home_separator).setVisibility(View.VISIBLE)
    
    postListView = findView(TR.thread_list)
//    postListView = findViewById(R.id.thread_list).asInstanceOf[SQListView]
    postListView.setDividerHeight(0) // because there's only one item
    postListAdapter = new PostListAdapter(this, getApplication().asInstanceOf[SQApp], singlePostViewMode)
    postListView.setAdapter(postListAdapter)
    updatePostListBar = findView(TR.update_post_list_bar)

    setTitle(getString(R.string.viewing_one_thread))
//    postid = getIntent().getLongExtra(OTAConstants.POST_ID, -1);
    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: all extras:" + getIntent().getExtras())
//    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: extra:"
    // + getIntent().getExtras().get("com.squarechan.android.PostId"))

    // FIXME: something screwy about scala boxing/unboxing [i assume]
    // prevents putExtra from storing this as a primitave long, so getLongExtra fails.
    val pname = classOf[OneThreadActivity].getPackage.getName
    postid = getIntent().getExtras().get(pname + "." + OTAConstants.POST_ID).asInstanceOf[Long]
    if (SQConstants.DEBUG) {
      setTitle(getTitle + "  postid:" + postid)
    }
    savedLocation = getIntent().getExtras().get(pname + "." + NPAConstants.SAVED_LOCATION) match {
      case null => null
      case sl:String => sl
      case _ => null
    }



    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: extra class:" + getIntent().getExtras().get("com.squarechan.android.PostId").getClass().getName())
    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: all extras isempty:" + getIntent().getExtras.isEmpty)
    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: postid=" + postid)
    if (postid == -1) {
      // "Answer" callback.
      val failMsg:Message = Message.obtain(mHandler, OTAConstants.FINISH_MSG, 0, 0)
      new AlertDialog.Builder(this)
      .setCancelable(false)
      .setMessage("Error! No postid.")
      .setPositiveButton("OK", new OnClickListener() {
        def onClick(dialog:DialogInterface, which:Int) {
          failMsg.sendToTarget()
        }}).show()
    }
    pollThread()
    Log.v(SQConstants.LOG_TAG, "OneThreadActivity.onCreate: done")
  }


  override def onCreateDialog(id:Int) = {
    Log.v(SQConstants.LOG_TAG, "OTA.onCreateDialog() id=" + id)
    id match {
      case SQConstants.NEED_LOCATION_BEFORE_POSTING =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.need_loc_before_posting)
      case SQConstants.DELETE_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.delete_error)
      case SQConstants.FETCH_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.one_thread_fetch_error)
      case _ => {
	// fixme: where is the 0 coming from? could it have something to do with the progressdialog?
	Log.v(SQConstants.LOG_TAG, "Unexpected id in onCreateDialog; ignoring=" + id)
	//StaticMethods.createSimpleMessage(this, R.string.error, R.string.no_such_message);
	null
      }
    }
  }

  override def onCreateOptionsMenu(menu:Menu) = {
    super.onCreateOptionsMenu(menu);
    val inflater = getMenuInflater();
    inflater.inflate(R.menu.single_thread_menu, menu);
    true
  }

  override def onOptionsItemSelected(item:MenuItem) = {
    item.getItemId() match {
      case R.id.see_all_threads => finish()
      case R.id.force_reload_posts => pollThread()
    }
    false
  }

  def onHomeClick(v:View) { goHome() }
  
  def clickSingleThreadReply(v:View) {
    clickReply(postid)
  }

  def clickForceCheckPosts(v:View) {
    pollThread()
  }

  def clickPostDeleteButton(v:View) {
    val delMsg = Message.obtain(mHandler, OTAConstants.DELETE_MSG, v.getTag().asInstanceOf[Long])
    new AlertDialog.Builder(this)
    .setCancelable(true)
    .setMessage(R.string.really_delete_post)
	.setPositiveButton(R.string.do_delete, new OnClickListener() {
	  def onClick(dialog:DialogInterface, which:Int) {
	    delMsg.sendToTarget()
	  }})
	.setNegativeButton(R.string.cancel, new OnClickListener() {
	  def onClick(dialog:DialogInterface, which:Int) {
	    // do nothing
	    }})
      .show()
  }

  class DeletePostTask(context:Context) extends BetterAsyncTask[DeletePostParams, Int, Boolean](context) {
    override def doCheckedInBackgroundSingleton(context:Context, params:DeletePostParams):Boolean = {
      Log.v(SQConstants.LOG_TAG, " DPT.doCheckedInBackground() called");
      Log.v(SQConstants.LOG_TAG, " DPT.doCheckedInBackground() ...... on ctx:" + context + " with params " + params);
      deletePostViaHttp(params.postId, params.ownerToken)
    }

    override def handleError(context:Context, exception:Exception) {
      Log.e(SQConstants.LOG_TAG, "DPT:delete error: " + exception);
      Log.e(SQConstants.LOG_TAG, "DPT:delete error trace: " + StaticMethods.exToText(exception));
      resetUI()
      showDialog(SQConstants.DELETE_ERROR)
    }

    override def before(context:Context) {
      Log.v(SQConstants.LOG_TAG, "DPT: Delete.before() called on ctx:" + context);
      context.asInstanceOf[Activity].setProgressBarVisibility(true)
      val dil = new DialogInterface.OnCancelListener() {
	override def onCancel(dialog:DialogInterface) {
	  if (deletePostTask != null) {
 	    deletePostTask.cancel(true)
	  }
	}
      }
      deleteProgressDialog = ProgressDialog.show(context, null, getString(R.string.delete_progress), true, true, dil)
    }

    def resetUI() {
      context.asInstanceOf[Activity].setProgressBarVisibility(false)
      if (deleteProgressDialog != null) {
	StaticMethods.dismissDialogSafely(deleteProgressDialog)
	deleteProgressDialog = null
      }
    }

    override def after(context:Context, result:Boolean) {
      if (result != true) {
	throw new Exception("Error: got negative result from DPT, which is never supposed to return a negative result.") 
      }
      resetUI()
      pollThread()
    }
  }


  def deletePostViaHttp(postId:Long, ownerToken:String):Boolean = {
    val request = new HttpPost(SQConstants.DELETE_POST_URI)
    val hparams = new BasicHttpParams()
    HttpConnectionParams.setConnectionTimeout(hparams, SQConstants.POST_HTTP_TIMEOUT)
    HttpConnectionParams.setSoTimeout(hparams, SQConstants.POST_HTTP_TIMEOUT)
    request.setParams(hparams)
    val nvp = new ArrayList[NameValuePair]()
    nvp.add(new BasicNameValuePair(SQConstants.OWNER_TOKEN_POST_FIELD_NAME, ownerToken))
    nvp.add(new BasicNameValuePair(SQConstants.POST_ID_POST_FIELD_NAME, postId.toString))  
    request.setEntity(new UrlEncodedFormEntity(nvp))
    val client = new DefaultHttpClient()
    val response = client.execute(request)
    response.getStatusLine.getStatusCode match {
      case 200 => { 
	Log.v(SQConstants.LOG_TAG, "deleteViaHTTP() got 200 OK")
	val responseString = EntityUtils.toString(response.getEntity()).trim
	responseString match {
	  case OTAConstants.OK => true
	  case x => throw new Exception("Error deleting post: " + x) 
	}
      }
      case other  => {  
	Log.e(SQConstants.LOG_TAG, "delViaHTTP: FAILED code=" + other)
	throw new Exception("DPT: Error: " + response.getStatusLine.getStatusCode) 
      }
    }
  }












}
