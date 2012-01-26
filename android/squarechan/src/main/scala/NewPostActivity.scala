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
import _root_.android.graphics.Bitmap
import _root_.android.location.Location
import _root_.android.widget.EditText
import _root_.android.widget.TextView
import _root_.android.net.Uri
import _root_.android.widget.Button
import _root_.android.widget.ImageView
import _root_.android.provider.MediaStore
import _root_.android.provider.MediaStore.Images
import _root_.android.provider.MediaStore.Images.Media
import _root_.android.view.Window
import _root_.java.io.FileOutputStream
import _root_.android.view.View
import _root_.android.view.Menu
import _root_.android.view.MenuInflater
import _root_.android.view.MenuItem
import _root_.android.text.Editable
import _root_.android.app.ProgressDialog
import _root_.android.graphics.BitmapFactory
import _root_.android.os.Environment
import _root_.java.util.ArrayList
import _root_.java.io.File

import _root_.android.app.AlertDialog
import _root_.android.provider.Settings
import _root_.android.app.Dialog
import _root_.android.content.DialogInterface
import _root_.android.content.DialogInterface.OnClickListener
import _root_.android.os.Message
import _root_.android.os.Handler
import _root_.android.util.Log
import _root_.java.io.ByteArrayOutputStream
import _root_.android.content.Intent
import _root_.java.net.URLEncoder
import _root_.android.os.Bundle
import _root_.android.app.Activity
import _root_.com.github.droidfu.activities.BetterDefaultActivity
import _root_.com.github.droidfu.concurrent.BetterAsyncTask

import _root_.org.apache.http.HttpEntity
import _root_.org.apache.http.entity.ByteArrayEntity
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



// FIXME/TODO: auto-reload with new replies (<=> set up a socket to
// the server? poll? long-poll?) I like twitter's "scroll up to force
// reload feature


class NewPostParams(val owner_token:String, val replyTo:Long, var location:String, val content:String, val imagePath:String) {}

object NPAConstants {
  val NOT_A_REPLY = (-1).asInstanceOf[Long]
  val CANCEL_POST = 1
  val DO_POST = 2


  val TAKE_PHOTO = 3
  val DROP_PHOTO = 4
  val PIC_OR_TEXT_REQUIRED_DIALOG = 5
  val PIC_REQUIRED_DIALOG = 6
  val UNEXPECTED_POST_ERROR = 7
  val TEMP_PHOTO_FN = "squarechan-temp-photo.jpeg"
  val PICK_FROM_GALLERY  = 8
  val UNEXPECTED_INTENT_ERROR = 9
  val POST_TOO_LONG = 9


  val POST_ERROR = 10
  val ERROR_PROCESSING_IMAGE = 11
  val SAVED_LOCATION = "saved_location"
  val NO_MEDIA_ERROR = 12


  val POST_TOO_LONG_WITHOUT_ARG = 13
  val LOCATION_UNAVAILABLE = 14
  val LOCATION_UNAVAILABLE_NO_WIFI = 15

  val POST_LOCATION_TIMEOUT_MILLIS = 30 * 1000
  val POST_LOCATION_POLL_MILLIS = 1000

  val INVALID_PICTURE = 16
  val CANCEL_POST_AND_GO_HOME = 17
  val COULD_NOT_FETCH_IMAGE_ERROR = 18

}


class NewPostActivity extends BetterDefaultActivity with SQActivityHelperFunctions with SQPostViewHelper with ShowOneThreadTrait with LocationFindingTrait {
  var replyToId = NPAConstants.NOT_A_REPLY
  val POST_LENGTH_LIMIT = 7000
  val POST_QUALITY = 50
  val THUMB_SIZE = 100
  val MAX_POST_IMG_SIDE_SIZE = 1024

  var postTask:NewPostTask = null

  val CURRENT_SIZE = "current_size"
  val THUMBNAIL = "thumbnail"
  val REPLY_TO_ID = "reply_to_id"
  val POST_TEXT = "post_text"
  val IMAGE_PATH = "image_path"

  val SAVED_LOCATION = "saved_location"
  var postText:EditText = null
  var thumbnailImageView:ImageView = null
  var savedThumbJpegArray:Array[Byte] = null
  val savedThumbnailLock = new Object()
  var postButton:Button = null
  var imagePath:String = null
  var progressDialog:ProgressDialog = null
  var thumbProgressDialog:ProgressDialog = null
  
  private val mHandler  = new Handler() {
    override def handleMessage(msg:Message) {
      msg.what match {
	case NPAConstants.CANCEL_POST | NPAConstants.CANCEL_POST_AND_GO_HOME => {
	  Log.v(SQConstants.LOG_TAG, " CANCEL_POST[and_go_home?]() : erasing picture")
	  getCameraTempFile(true) 
	  if (msg.what == NPAConstants.CANCEL_POST_AND_GO_HOME) goHome()
	  finish
	}
      }
    }
  }

  def updateThumbnail() {
    imagePath == null match {
      case true => null // do *not* clickDetachImage(null)
      case false => {
	savedThumbnailLock.synchronized({
	  if (savedThumbJpegArray != null) {
	    Log.v(SQConstants.LOG_TAG, "PostActivity.updateThumbnail(): saved thumbnail exists, loading...")
	    val result = BitmapFactory.decodeByteArray(savedThumbJpegArray, 0, savedThumbJpegArray.length)
	    if (result != null) {
	      Log.v(SQConstants.LOG_TAG, "PostActivity.updateThumbnail(): ...succeeded in loading thumb")
	      thumbnailImageView.setImageBitmap(result)
		showImageThumbnailBar()
	      return
	    }
	      Log.v(SQConstants.LOG_TAG, "PostActivity.updateThumbnail(): loading saved thumb failed, running TT.")
	      null
	  }
	})
//	Log.v(SQConstants.LOG_TAG, "PostActivity.updateThumbnail(): running ThumbnailTask")
	val tt = new ThumbnailTask(this)
	tt.disableDialog()
	tt.execute(imagePath)
///	Log.v(SQConstants.LOG_TAG, "PostActivity.updateThumbnail(): ...executed")
	null
      }
    }
  }
  
  override def onSaveInstanceState(outState:Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(POST_TEXT, postText.getText.toString)
    outState.putString(IMAGE_PATH, imagePath)
    outState.putByteArray(THUMBNAIL, savedThumbJpegArray)
    if (savedLocation != null) {
      outState.putString(SAVED_LOCATION, savedLocation)
    }
  }


  override def onCreate(bundle:Bundle) {
    super.onCreate(bundle)
//    Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate() called")
//    requestWindowFeature(Window.FEATURE_PROGRESS)
    setContentView(R.layout.post)
    findView(TR.btn_title_post).setVisibility(View.GONE)
    findView(TR.btn_title_post_separator).setVisibility(View.GONE)
    findView(TR.btn_title_refresh).setVisibility(View.GONE)
    findView(TR.btn_title_refresh_separator).setVisibility(View.GONE)


    findView(TR.btn_title_home).setVisibility(View.VISIBLE)
    findView(TR.btn_title_home_separator).setVisibility(View.VISIBLE)


    postText = findView(TR.post_text)
    postButton = findView(TR.post_button)
    thumbnailImageView = findView(TR.thumbnail)

    // FIXME: for some reason we temporarily(?) lose this after the
    // camera snap. might as well just save the loc when we start
    // posting.

    // val cl = getApplicationContext().asInstanceOf[SQApp].currentLocation
    // Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate() checking lu=" + getApplicationContext().asInstanceOf[SQApp].locUpdates + "  currentloc="+cl)
    // if (cl==null) {
    //   failBecauseOfLocation()
    //   return
    // }


    // FIXME: something screwy about scala boxing/unboxing [i assume]
    // prevents putExtra from storing this as a primitave long, so getLongExtra fails.
    val pname = classOf[OneThreadActivity].getPackage.getName
    replyToId = getIntent().getExtras() match {
      case null => NPAConstants.NOT_A_REPLY
      case y => y.get(pname + "." + OTAConstants.REPLY_TO_ID) match {
	case null => NPAConstants.NOT_A_REPLY
	case x => x.asInstanceOf[Long]
      }
    }

    findView(TR.window_title_text).setText(if (replyToId==NPAConstants.NOT_A_REPLY) R.string.new_post_header else R.string.single_thread_reply_string)
    findView(TR.window_title_text).setVisibility(View.VISIBLE)
    findView(TR.window_title_button).setVisibility(View.GONE)

    savedLocation = getIntent().getExtras().get(pname + "." + NPAConstants.SAVED_LOCATION).asInstanceOf[String]
//    Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): loaded location from Intent: " + savedLocation)

//    Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate() replyToId=" + replyToId + " bundle=" + bundle)
/////    Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate() xxx")
    if (bundle != null) {
      if (savedLocation == null) {
	// this saves us from running the locationfinder over and over if we change orientation.
	if (bundle.containsKey(SAVED_LOCATION)) {
	  savedLocation = bundle.getString(SAVED_LOCATION)
//	  Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): loaded location from Bundle: " + savedLocation)
	  null
	}
      }

//      Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate():: bundle was not null")
      if (bundle.containsKey(POST_TEXT)) {
//	Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate():: loaded post text")
	  postText.setText(bundle.getString(POST_TEXT))
      }
	savedThumbnailLock.synchronized({
	  savedThumbJpegArray = bundle.containsKey(THUMBNAIL) match {
	    case true => bundle.getByteArray(THUMBNAIL)
	    case false => null
	    }
	})

      if (bundle.containsKey(IMAGE_PATH)) {
	imagePath = bundle.getString(IMAGE_PATH)
//	Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): loaded image path " + imagePath)
	updateThumbnail()
      }

      // if (bundle.containsKey(SAVED_LOCATION)) {
      // 	savedLocation = bundle.getString(SAVED_LOCATION)
      // 	Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): loaded location " + savedLocation)
      // }
    }

    if (imagePath == null) { 
      val es = getIntent().getExtras().get(Intent.EXTRA_STREAM) match {
	case uri:Uri => {
	  copyToTempFile(uri)
	}
	case _ => null
      }
      
//      Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): got EXTRA_STREAM " + es)
      null
    }





    if (savedLocation == null) {
      startCheckingLocation()
    }

    setTitle(getString(replyToId match {
      case NPAConstants.NOT_A_REPLY =>  R.string.new_post_title
      case _ =>  R.string.reply_title
    }))
/*
    findViewById(R.id.post_activity_header).asInstanceOf[TextView].setText(replyToId match {
      case NPAConstants.NOT_A_REPLY =>  R.string.new_post_header
      case _ =>  R.string.reply_header
    })
*/
  }
						    
  def copyToTempFile(uri:Uri) {
    val cr = getContentResolver()
    val is = cr.openInputStream(uri)
    var fp = getCameraTempFile(true)
    val os = new FileOutputStream(fp)
    val block = 128000
    val b = new Array[Byte](block)
    var o = 0
    // FIXME: this should go in an AsyncTask...
    var count = 0
    do {
      count = is.read(b, o, block)
      if (count != -1) {os.write(b, 0, count)}
    } while (count != -1) 
    os.close()
    Log.v(SQConstants.LOG_TAG, "PostActivity.onCreate(): EXTRA_STREAM file length (bytes)=" + fp.length)
    if (fp.length > 0) {
      imagePath = fp.getPath
      updateThumbnail()
    } else {
      showDialog(NPAConstants.INVALID_PICTURE)
    }
  }
    

  def getCameraTempFile(delete:Boolean) = {
//    val fp = new File(getCacheDir(), NPAConstants.TEMP_PHOTO_FN)

    // FIXME: if the sdcard is not available, we should just turn off EXTRA_OUTPUT
    // (but does the camera even work w/o the sdcard? no, right?)
    // val extdir = getExternalFilesDir(null) <- api level >=8 only
    // see http://developer.android.com/guide/topics/data/data-storage.html

    val basepath = Environment.getExternalStorageDirectory + "/Android/data/"+(classOf[NewPostActivity].getPackage.getName)+"/files/"
    val extdir = new File(basepath)
    Log.v(SQConstants.LOG_TAG, "NPA.getCameraTempFile: basepath: " + basepath);
    val mkdirsresult = extdir.mkdirs()
    Log.v(SQConstants.LOG_TAG, "NPA.getCameraTempFile: mkdirs result: "+ mkdirsresult + " on " + extdir.getPath() + " fp:" + extdir)
    val nomediafp = new File(basepath + ".nomedia")
    if (nomediafp.exists) {
      Log.v(SQConstants.LOG_TAG, "NPA.getCameraTempFile: .nomedia file exists")
      null
    } else {
      val nmfpresult = nomediafp.createNewFile()
      Log.v(SQConstants.LOG_TAG, "NPA.getCameraTempFile: created .nomedia file; result="+nmfpresult)
      null
    }

    val fp = new File(extdir, NPAConstants.TEMP_PHOTO_FN)
    Log.v(SQConstants.LOG_TAG, "NPA.getCameraTempFile: " + fp.getPath() + " name:" + fp.getName())
    if (delete) {
      savedThumbnailLock.synchronized({savedThumbJpegArray = null}) // fixme: do we have a race here somewhere?
      if (fp.exists) {
	Log.v(SQConstants.LOG_TAG, "getCameraTempFile: delete file of length=" + fp.length)
	//      StaticMethods.logCurrentStackTrace()
	  fp.delete()
      }
    }
    fp
  }

  def clickTakePicture(v:View) {
    // if we someday support 1.5, note that there's a huge steaming pile of fail in some implementations:
    // http://stackoverflow.com/questions/1910608/android-action-image-capture-intent

    //fixme: could we use internal storage if we used FLAG_GRANT_WRITE_URI_PERMISSION?

    val state = Environment.getExternalStorageState()
    if (!Environment.MEDIA_MOUNTED.equals(state)) {
      showDialog(NPAConstants.NO_MEDIA_ERROR)
      return
    }
    val i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // if the user dbl-taps, this will prevent the confusing "camera runs twice" situation
    Log.v(SQConstants.LOG_TAG, "clickTakePicture: erasing picture...")
    var fp = getCameraTempFile(true)
    i.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(fp)) 
    Log.v(SQConstants.LOG_TAG, "clickTakePicture: taking picture...")
    startActivityForResult(i, NPAConstants.TAKE_PHOTO)
  }

  def clickGetFromGallery(v:View) {
    val i = new Intent(Intent.ACTION_GET_CONTENT)
    i.setType("image/*")
    val c = Intent.createChooser(i, getString(R.string.select_picture))
    startActivityForResult(c, NPAConstants.PICK_FROM_GALLERY)

  }

  def clickDetachImage(v:View) {
    findView(TR.image_preview_bar).setVisibility(View.GONE)
    findView(TR.pick_image_buttons_bar).setVisibility(View.VISIBLE)
    imagePath = null
    Log.v(SQConstants.LOG_TAG, "clickDetachImage: erasing picture...")
    getCameraTempFile(true)
  }
  
  def showImageThumbnailBar() {
    findView(TR.image_preview_bar).setVisibility(View.VISIBLE)
    findView(TR.pick_image_buttons_bar).setVisibility(View.GONE)
    
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, i:Intent){
    Log.v(SQConstants.LOG_TAG, "NPA.onActivityResult called: req:" + requestCode + " res:" + resultCode + " data:" + i)
    if (checkLocActivityResult(requestCode)) return
    resultCode match {
      case Activity.RESULT_CANCELED => return
      case Activity.RESULT_OK => requestCode match { 
	case NPAConstants.TAKE_PHOTO => {
	  imagePath = getCameraTempFile(false).getPath
	  Log.v(SQConstants.LOG_TAG, "onActivityResult called: TAKE_PHOTO: path=" + imagePath)
//	  Log.v(SQConstants.LOG_TAG, "onActivityResult called: TAKE_PHOTO: path exists=" + (new File(imagePath)).exists)
	  // val bmp:Bitmap = i.getExtras().get("data").asInstanceOf[Bitmap]
	  // val faos = new FileOutputStream(imagePath)
	  // bmp.compress(Bitmap.CompressFormat.JPEG, POST_QUALITY, faos)
	  updateThumbnail()
	  
	}
	case NPAConstants.PICK_FROM_GALLERY => {
	  Log.v(SQConstants.LOG_TAG, "onActivityResult called: PICK_FROM_GALLERY: data=" + i.getData())
	  val filePath = i.getData().getPath()
	  Log.v(SQConstants.LOG_TAG, "onActivityResult called: PICK_FROM_GALLERY: datapath=" + filePath)
	  val fetchedPath = StaticM.getPath(this, i.getData())
	  imagePath = fetchedPath match {
	    case null => filePath
	    case _ => fetchedPath
	  }
	  Log.v(SQConstants.LOG_TAG, "onActivityResult called: PICK_FROM_GALLERY: final path=" + imagePath)
	  if (imagePath == null) {
	    showDialog(NPAConstants.COULD_NOT_FETCH_IMAGE_ERROR)
	  } else {
	    updateThumbnail()
	  }
	  
	}
      }
      case _ => showDialog(NPAConstants.UNEXPECTED_INTENT_ERROR)
    }
  }


  // def getPath(uri:Uri) = {
  // 	/* copied from
  // 	 http://stackoverflow.com/questions/2169649/open-an-image-in-androids-built-in-gallery-app-programmatically
  // 	 */

  //   val projection = Array(MediaStore.Images.Media.DATA)
  //   val cursor = managedQuery(uri, projection, null, null, null)
  //   val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
  //   cursor.moveToFirst()
  //   cursor.getString(column_index)
  // }

  def clickPostButton(v:View) {
    val elen = URLEncoder.encode(postText.getText.toString, "UTF-8").length
    if (elen > POST_LENGTH_LIMIT) {
      // this is imposed by the standard apache HTTP header length
      // limit (with a wide safety margin) and I'm too lazy to fix it.
      val b = new Bundle
      b.putInt(CURRENT_SIZE, elen)
//      showDialog(NPAConstants.POST_TOO_LONG, b) // FIXME: only works in android 8

      showDialog(NPAConstants.POST_TOO_LONG_WITHOUT_ARG)
      return
    }
    if (replyToId == NPAConstants.NOT_A_REPLY) { 
      if (imagePath != null) {
	runPostTask
      } else {
	  showDialog(NPAConstants.PIC_REQUIRED_DIALOG)
      }
    } else {
      if (imagePath != null || postText.getText.length > 0) {
	runPostTask
      } else {
	showDialog(NPAConstants.PIC_OR_TEXT_REQUIRED_DIALOG)
      }
    }
  }
  

  def runPostTask() {
    if (postTask != null) {
      postTask.cancel(true)
    }
    postTask = new NewPostTask(this)
    postTask.disableDialog()
    postTask.execute(new NewPostParams(StaticMethods.getUniqueID(this), 
				       replyToId, savedLocation, 
				       postText.getText.toString, imagePath))
  }


  def failBecauseOfLocation() {
    val cancelMsg:Message = Message.obtain(mHandler, NPAConstants.CANCEL_POST, 0, 0)
    new AlertDialog.Builder(this)
    .setCancelable(true)
    .setMessage(R.string.no_location_available)
    .setPositiveButton(R.string.ok, new OnClickListener() {
      def onClick(dialog:DialogInterface, which:Int) {
          cancelMsg.sendToTarget()
      }})
    .show()
  }


  def onHomeClick(v:View) { 
    handleExitRequest(v, Message.obtain(mHandler, NPAConstants.CANCEL_POST_AND_GO_HOME, 0, 0))
  }

  def clickCancel(v:View) {
    handleExitRequest(v, Message.obtain(mHandler, NPAConstants.CANCEL_POST, 0, 0))
  }

  def handleExitRequest(v:View, msg:Message) {
    if (!(imagePath != null || postText.getText.length > 0)) {
      finish
    } else  {
      new AlertDialog.Builder(this)
	.setCancelable(true)
      .setMessage(R.string.really_discard_post)
	.setPositiveButton(R.string.yes, new OnClickListener() {
	  def onClick(dialog:DialogInterface, which:Int) {
              msg.sendToTarget()
	  }})
	.setNegativeButton(R.string.no, new OnClickListener() {
	  def onClick(dialog:DialogInterface, which:Int) {
	    // do nothing
	    }})
      .show()
      }
  }

/* only used in android >7
 * override def onPrepareDialog(id:Int, dialog:Dialog, args:Bundle) {
    id match {
      case NPAConstants.POST_TOO_LONG => {
	val sf = String.format(getString(R.string.post_too_long), 
			       args.getInt(CURRENT_SIZE).asInstanceOf[AnyRef], 
			       POST_LENGTH_LIMIT.asInstanceOf[AnyRef])
	dialog.asInstanceOf[AlertDialog].setMessage(sf)
      }
      case _ => dialog
    }
  }*/

						      /* fixme we should just use the R.string ids for these; no need to create another set of constants for them. */
  override def onCreateDialog(id:Int) = {
//    Log.v(SQConstants.LOG_TAG, "NewPostActivity.onCreateDialog() id=" + id)
    id match {
      case SQConstants.NEED_LOCATION_BEFORE_POSTING =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.need_loc_before_posting)
      case SQConstants.LOCATION_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.location_undefined_error)
      case NPAConstants.LOCATION_UNAVAILABLE => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.location_unavailable_wifi)
      case NPAConstants.LOCATION_UNAVAILABLE_NO_WIFI => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.location_unavailable_no_wifi)
      case NPAConstants.INVALID_PICTURE => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.invalid_picture)
      case NPAConstants.POST_ERROR =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.post_error)
      case NPAConstants.UNEXPECTED_INTENT_ERROR =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.unexpected_intent_error)
      case NPAConstants.POST_TOO_LONG =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.post_too_long)
      case NPAConstants.POST_TOO_LONG_WITHOUT_ARG =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.post_too_long_without_arg)
      case NPAConstants.PIC_REQUIRED_DIALOG => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.pic_required_message)
      case NPAConstants.PIC_OR_TEXT_REQUIRED_DIALOG => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.pic_or_text_required_message)
      case NPAConstants.UNEXPECTED_POST_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.unexpected_post_error)
      case NPAConstants.NO_MEDIA_ERROR => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.error_no_media)
      case NPAConstants.ERROR_PROCESSING_IMAGE =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.error_processing_image)
      case NPAConstants.COULD_NOT_FETCH_IMAGE_ERROR =>
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.error_fetching_image)
      case _ => 
	StaticMethods.createSimpleMessage(this, R.string.error, R.string.no_such_message)
    }
  }
  

  class NewPostTask(context:Context) extends BetterAsyncTask[NewPostParams, Int, Long](context) {
    var isReply:Boolean = false
    val STARTED = 0
    val READY_FOR_UPLOAD = 1
    val DETERMINING_LOCATION = 2

    override def doCheckedInBackgroundSingleton(context:Context, npp:NewPostParams):Long = {
      publishProgress(STARTED)

      if (npp.location == null) {
	publishProgress(DETERMINING_LOCATION)
	val start = System.currentTimeMillis
	while (npp.location == null && System.currentTimeMillis - start < NPAConstants.POST_LOCATION_TIMEOUT_MILLIS) {
	  npp.location = getApp.currentLocation match {
	    case cl:Location => StaticMethods.locToString(cl, false)
	    case _ => null
	  }
	  Thread.sleep(NPAConstants.POST_LOCATION_POLL_MILLIS)
	}
	if (npp.location == null) {
	  cancel(true)
	  context.asInstanceOf[BetterDefaultActivity].showDialog(wifiIsConnected() match {
	    case true => NPAConstants.LOCATION_UNAVAILABLE
	    case false => NPAConstants.LOCATION_UNAVAILABLE_NO_WIFI
	  })
	}
	savedLocation = npp.location

      }

      isReply = npp.replyTo == NPAConstants.NOT_A_REPLY
      Log.v(SQConstants.LOG_TAG, " NPT.doCheckedInBackground() called. isReply=" + isReply + " replytoid=" + npp.replyTo)
      Log.v(SQConstants.LOG_TAG, " NPT.doCheckedInBackground() ...... on ctx:" + context + " with params " + npp)
      publishProgress(READY_FOR_UPLOAD)
      uploadPost(npp)

    }
      
    override def handleError(context:Context, exception:Exception) {
      Log.e(SQConstants.LOG_TAG, " NPT: handleError: " + exception);
      Log.e(SQConstants.LOG_TAG, " NPT: handleError: " + StaticMethods.exToText(exception));
     
      Log.v(SQConstants.LOG_TAG, " NPT.handleError() progressdialog is " + progressDialog)
      if (progressDialog != null) {
	StaticMethods.dismissDialogSafely(progressDialog) 
	progressDialog = null
      }
      context.asInstanceOf[Activity].showDialog(NPAConstants.POST_ERROR)
    }
    

     override def onProgressUpdateSingleton(progress:Int) {
       progress match {
	 case DETERMINING_LOCATION => progressDialog.setMessage(getString(R.string.determining_location))
	 case READY_FOR_UPLOAD => progressDialog.setMessage(getString(R.string.posting_progress))
	 case _ => progressDialog.setMessage(getString(R.string.posting_progress))
       }
     }


    override def before(context:Context) {
      disableDialog()
//      Log.v(SQConstants.LOG_TAG, " NPT.before() called on ctx:" + context);
//      context.asInstanceOf[Activity].setProgressBarVisibility(true)

      val dil = new DialogInterface.OnCancelListener() {
	override def onCancel(dialog:DialogInterface) {
	  if (postTask != null) {
 	    postTask.cancel(true)
	  }
	}
      }
      progressDialog = ProgressDialog.show(context, null, getString(R.string.posting_progress), true, true, dil)

    }
    
    override def after(context:Context, result:Long) {
      // if it was a reply, send us to the (bottom) of the OneThread intent
      // otherwise, just send us to the OneThread intent for the new post.
      
//      Log.v(SQConstants.LOG_TAG, " NPT.after() called")

//      context.asInstanceOf[Activity].setProgressBarVisibility(false)
//      Log.v(SQConstants.LOG_TAG, " NPT.after() progressdialog is " + progressDialog)
      if (progressDialog != null) {
	StaticMethods.dismissDialogSafely(progressDialog)
	progressDialog = null
      }

      if (result <= 0) {
	showDialog(NPAConstants.UNEXPECTED_POST_ERROR)
      } else {
	// we add savedLocation b/c android might have killed and
	// restarted squarechan (=>lost the location attached to
	// SQApp) while the camera activity was running)
	showOneThread(replyToId match { case NPAConstants.NOT_A_REPLY => result
				        case x => x }, false, savedLocation)
	finish()
      }
    }
  }

def uploadPost(npp:NewPostParams):Long = {
  // based on
  // http://code.google.com/p/android-json-rpc/source/browse/trunk/android-json-rpc/src/org/alexd/jsonrpc/JSONRPCHttpClient.java

    val request = new HttpPost(SQConstants.CREATE_POST_URI);
    request.addHeader(SQConstants.REPLY_TO_HTTP_HEADER, npp.replyTo.toString)
    Log.v(SQConstants.LOG_TAG, "uploadPost() o_t is:" + npp.owner_token)
    request.addHeader(SQConstants.OWNER_TOKEN_HTTP_HEADER, npp.owner_token)
    Log.v(SQConstants.LOG_TAG, "uploadPost() location is:" + npp.location)
    request.addHeader(SQConstants.LOCATION_HTTP_HEADER, npp.location)
    request.addHeader(SQConstants.HAS_IMAGE_HTTP_HEADER, npp.imagePath match {
      case null => "no"
      case _ => "yes"
    })
    request.addHeader(SQConstants.CONTENT_HTTP_HEADER, URLEncoder.encode(npp.content, "UTF-8"))
    
    val hparams = new BasicHttpParams()
    HttpConnectionParams.setConnectionTimeout(hparams, SQConstants.POST_HTTP_TIMEOUT)
    HttpConnectionParams.setSoTimeout(hparams, SQConstants.POST_HTTP_TIMEOUT)
    request.setParams(hparams)
    val client = new DefaultHttpClient()
    if (npp.imagePath != null) {
      val bmp = StaticMethods.getScaledBitmap(npp.imagePath, MAX_POST_IMG_SIDE_SIZE)
      val baos = new ByteArrayOutputStream()
      bmp.compress(Bitmap.CompressFormat.JPEG, POST_QUALITY, baos)
      baos.flush()
      Log.v(SQConstants.LOG_TAG, "uploadPost() image bytes:" + baos.size)
      val entity = new ByteArrayEntity(baos.toByteArray)
      entity.setContentType("image/jpeg")
      request.setEntity(entity)
    }
    val response = client.execute(request)
    response.getStatusLine.getStatusCode match {
      case 200 => { 
	Log.v(SQConstants.LOG_TAG, " uploadpost() got 200 OK")
	val responseString = EntityUtils.toString(response.getEntity()).trim
	Log.v(SQConstants.LOG_TAG, " uploadpost() got response " + responseString)
	Log.v(SQConstants.LOG_TAG, " uploadpost() : erasing picture")
	getCameraTempFile(true) // wipe it out if it's there
	java.lang.Long.parseLong(responseString)
      }
      case other   => {  
	Log.e(SQConstants.LOG_TAG, " uploadpost() FAILED code + " + other)
	throw new Exception("Error: " + response.getStatusLine.getStatusCode) 
      }
    }
}

  override def onDestroy() {
    super.onDestroy()
    //    getCameraTempFile(true) // can't do this here, otherwise, we'll
    //    lose it when we rotate!
  }




  class ThumbnailTask(context:Context) extends BetterAsyncTask[String, Int, Bitmap](context) {
    val thumbTask = this
    override def doCheckedInBackgroundSingleton(context:Context, path:String):Bitmap = {

      Log.v(SQConstants.LOG_TAG, " TT.doCheckedInBackground() called");
//      Log.v(SQConstants.LOG_TAG, " TT.doCheckedInBackground() ...... on ctx:" + context + " with path " + path);
      val bmp = StaticMethods.getScaledBitmap(path, THUMB_SIZE)
      savedThumbnailLock.synchronized({
	val baos = new ByteArrayOutputStream()
	bmp.compress(Bitmap.CompressFormat.JPEG, POST_QUALITY, baos)
	baos.flush()
	Log.v(SQConstants.LOG_TAG, "TT: dcibs: compressed & saved thumbnail; size in bytes=" + baos.size)
	savedThumbJpegArray = baos.toByteArray()
      })
      bmp
    }
      
    override def handleError(context:Context, exception:Exception) {
      Log.e(SQConstants.LOG_TAG, " TT: handleError: " + exception)
      Log.e(SQConstants.LOG_TAG, " TT: handleError: " + StaticMethods.exToText(exception));
      if (thumbProgressDialog != null) {
	StaticMethods.dismissDialogSafely(thumbProgressDialog)
	Log.v(SQConstants.LOG_TAG, " TT.dialog dismissed. isShowing=" + thumbProgressDialog.isShowing);
	thumbProgressDialog = null
      }
      showDialog(NPAConstants.ERROR_PROCESSING_IMAGE)
    }
    
    override def before(context:Context) {
//      Log.v(SQConstants.LOG_TAG, " TT.before() called on ctx:" + context);

      val dil = new DialogInterface.OnCancelListener() {
	override def onCancel(dialog:DialogInterface) {
	  if (thumbTask != null) {

 	    thumbTask.cancel(true)
	  }
	}
      }
      if (thumbProgressDialog != null) {
//	Log.v(SQConstants.LOG_TAG, "TT.before(): dismissing zombie thumbProgressDialog:" + thumbProgressDialog);
	StaticMethods.dismissDialogSafely(thumbProgressDialog)
	thumbProgressDialog = null
      }
      thumbProgressDialog = ProgressDialog.show(context, null, getString(R.string.thumbnail_progress), true, true, dil)
    }
    
    override def after(context:Context, result:Bitmap) {
//      Log.v(SQConstants.LOG_TAG, " TT.after() called; result=" + result)
      if (result != null) {
	thumbnailImageView.setImageBitmap(result)
	showImageThumbnailBar()	
      }
      
      if (thumbProgressDialog != null) {
	StaticMethods.dismissDialogSafely(thumbProgressDialog)
//	Log.v(SQConstants.LOG_TAG, " TT.dialog dismissed. isShowing=" + thumbProgressDialog.isShowing);
	thumbProgressDialog = null
      }

    }
  }


}
