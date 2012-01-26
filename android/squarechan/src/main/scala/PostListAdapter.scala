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

import _root_.android.content.Intent
import _root_.android.content.Context
import _root_.android.widget.BaseAdapter
import _root_.android.widget.ArrayAdapter
import _root_.android.view.View
import _root_.android.view.LayoutInflater
import _root_.android.util.Log
import _root_.java.lang.Math.{pow, sqrt}
import _root_.scala.math.{round}
import _root_.android.widget.Button
import _root_.android.view.ViewGroup
import _root_.android.widget.LinearLayout
import _root_.android.app.Activity
import _root_.java.util.ArrayList
import _root_.android.widget.TextView
import _root_.com.github.droidfu.widgets.WebImageView

import com.squarechan.android.TypedResource._  // this imports  the implicit conversions needed so that we can call view.findView

//import _root_.com.github.droidfu.imageloader.ImageLoader


// based fedorvlasov's LazyAdapter
// note that the 2nd arg to ArrayAdapter() is ignored; we bypass it with getView

class PostListAdapter(context:Context, sqapp:SQApp, singleThreadView:Boolean) extends ArrayAdapter[Post](context, 0) {
  val MAX_POST_LENGTH_IN_THREAD_VIEW = 250;
  val POST_LENGTH_SLOP = 50;

  val inflater = context.asInstanceOf[Activity].getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater];
//  val imageLoader = new ImageLoader(activity);

  // def getCount() = posts.size()
  
  // def getItem(position:Int) = {posts.get(position)}
  
  // def getItemId(position:Int) = {posts.get(position).id}


    def formattedContent(post:Post, singleThreadView:Boolean) = {
//      Log.v(SQConstants.LOG_TAG, " formattedContent called on " + post.content)
//      Log.v(SQConstants.LOG_TAG, " formattedContent is null? " + (post.content==null))
      post.content match {
	case null => ""
	case content => singleThreadView || content.length < (MAX_POST_LENGTH_IN_THREAD_VIEW+POST_LENGTH_SLOP) match {
	  case true => content
	  case false => content.substring(0, MAX_POST_LENGTH_IN_THREAD_VIEW) + context.getString(R.string.hidden_suffix)
	}
      }
    }

  override def getDropDownView(position:Int, convertView:View, parent:ViewGroup):View = {
    Log.v(SQConstants.LOG_TAG, " getDropDownView(): THIS SHOULD NEVER HAVE BEEN CALLED")
    return null;
  }

    def setupImage(post:Post, iv:WebImageView, postView:View) {
      if (post.deleted) {
	iv.setVisibility(View.GONE)
	// we could condition on findView(TR.deleted_icon) being null
	// but that should never happen, since if post.deleted is
	// true, this _must_ be an op, not a reply. {only OPs have
	// deleted_icon}
//	postView.findView(TR.deleted_icon).setVisibility(View.VISIBLE)
	return
//      } else {
//	postView.findView(TR.deleted_icon).setVisibility(View.GONE)
      } else {
	iv.setVisibility(View.VISIBLE)
      }
      if (post.imageURL == null) {
	Log.e(SQConstants.LOG_TAG, "setupImage() !!!!!!!!!!! called on post " + post.id + " content:" + post.content + 
	      " deleted" + post.deleted + " WITH NULL IMAGE!!! post:" + post);	
	null
      }
      val imgurl = post.imageURL.replace("*", "thumb")
      if (imgurl != null) {
	iv.setImageUrl(imgurl)
	iv.loadImage()
      } else {
	Log.v(SQConstants.LOG_TAG, " PLA.getView(): IMGURL IS NULL: item:" + post+ " origurl:" + post.imageURL);null
	}
    }

  def setupContent(pvi:PostViewInfo, postView:View) {
    if (pvi.post.deleted) {
      pvi.contentView.setVisibility(View.GONE)
      postView.findView(TR.content_deleted).setVisibility(View.VISIBLE)
    } else {
      pvi.contentView.setVisibility(View.VISIBLE)
      postView.findView(TR.content_deleted).setVisibility(View.GONE)
      pvi.contentView.setText(formattedContent(pvi.post, singleThreadView))
      if (SQConstants.DEBUG) {
	pvi.contentView.setText("post:" + pvi.post.id + " --- " +  pvi.contentView.getText)
      }
    }
  }

  def setupDeleteBar(pvi:PostViewInfo, postView:View) {
    Log.v(SQConstants.LOG_TAG, "setupDeleteBar(): post:" + pvi.post.id + " deletable:" + pvi.post.deletable + " stv:" + singleThreadView)
    if (pvi.post.deletable && singleThreadView) {
      postView.findView(TR.delete_button_bar).setVisibility(View.VISIBLE)
      postView.findView(TR.delete_post_button).setTag(pvi.post.id)
    } else {
      postView.findView(TR.delete_button_bar).setVisibility(View.GONE)
    }
  }

  override def getView(position:Int, inConvertView:View, parent:ViewGroup):View = {
    val ownerToken = StaticMethods.getUniqueID(context.asInstanceOf[Activity])
    if (getItem(position).isPlaceholder) {
      val tv = inflater.inflate(R.layout.loading_list_header, null)
//      tv.findView(TR.loading_header_footer_text).setText(position match {case 0 => "Loading top"; case _ => "Loading bot"})
      return tv
    }
    val convertView = if (inConvertView != null && inConvertView.findView(TR.op_content) == null) { null } else { inConvertView }

    val (view:View, pvi:PostViewInfo) = convertView match {
      case null  => { 
	val v:View = inflater.inflate(R.layout.single_op, null)

	val p:PostViewInfo = new PostViewInfo(v.findView(TR.op_thumbnail),
				 v.findView(TR.op_content),
				 v.findView(TR.op_replies),
				 v.findView(TR.op_replies_hidden),
				 v.findView(TR.op_see_all_replies),
				 v.findView(TR.op_stats),
				 getItem(position))
	v.setTag(p)
//	val reply_b = v.findViewById(R.id.op_reply).asInstanceOf[Button];
//	val see_all_b = v.findViewById(R.id.op_see_all_replies).asInstanceOf[Button];
	if (singleThreadView) {
	  v.findViewById(R.id.op_reply_bottom_rl).setVisibility(View.VISIBLE) // we use ById here b/c this is an <include>, which doesn't seem to work with typed resources
//	  v.findViewById(R.id.op_reply_top_rl).asInstanceOf[View].setVisibility(View.VISIBLE)
	  v.findView(TR.single_thread_return_top).setVisibility(View.VISIBLE)
	  v.findView(TR.single_thread_return_bottom).setVisibility(View.VISIBLE)
	}
	(v, p)
      }
      case _ => {
	convertView.getTag().asInstanceOf[PostViewInfo].post = getItem(position)
	(convertView, convertView.getTag())
      }
    }
    setupContent(pvi, view)
    setupDeleteBar(pvi, view)

//    if(SQConstants.DEBUG) Log.v(SQConstants.LOG_TAG, " formattedContent called;  content now=" + pvi.contentView.getText)
    if (SQConstants.DEBUG) {
      pvi.seeAllReplies.setText("See all " + pvi.post.id)
    }



    setupImage(pvi.post, pvi.imageView, view)

    Log.v(SQConstants.LOG_TAG, " PLA.getView(postid="+pvi.post.id+"):[getItem(position).id="+getItem(position).id + "  children.size(): " + pvi.post.children.size() + " children:" + pvi.post.children)
    pvi.repliesList.removeAllViews()
    if (pvi.post.children.size() == 0) {
      	pvi.repliesList.setVisibility(View.GONE)
    } else {
      pvi.repliesList.setVisibility(View.VISIBLE)
//	if(SQConstants.DEBUG) Log.v(SQConstants.LOG_TAG, " PLA.getView(): showing post " + getItem(position))
	for (n <- 0 until pvi.post.children.size) {
	  var childpost = pvi.post.children.get(n)
	  Log.v(SQConstants.LOG_TAG, " PLA.getView(postid="+pvi.post.id+"): showing child " + childpost)
	  var wiv:WebImageView = null;
	  var rv:View = null;
//	  Log.v(SQConstants.LOG_TAG, " PLA.getView(): the imageurl is '" + childpost.imageURL + "' ==null:" + (childpost.imageURL==null||childpost.imageURL.length==0))
	  if (childpost.imageURL==null || childpost.imageURL.length == 0) {
//	    Log.v(SQConstants.LOG_TAG, " using _nopic for content:" + childpost.content)
	    rv = inflater.inflate(R.layout.single_reply_nopic, null)
	  } else {
//	    Log.v(SQConstants.LOG_TAG, " using withpic for content:" + childpost.content)
	    rv = inflater.inflate(R.layout.single_reply, null)
	    wiv = rv.findView(TR.op_thumbnail)
	  }
	  var cpvi:PostViewInfo = new PostViewInfo(wiv,
						   rv.findView(TR.op_content),
						   null, null, null, 
						   rv.findView(TR.op_stats),
						   childpost)
	  rv.setTag(cpvi)

//	  Log.v(SQConstants.LOG_TAG, " PLA.getView(): child content was " + cpvi.contentView.getText)
//	  Log.v(SQConstants.LOG_TAG, " PLA.getView(): setting child content to " + formattedContent(childpost, singleThreadView))
	  setupContent(cpvi, rv)
//	  Log.v(SQConstants.LOG_TAG, " formattedContent called on childpost;  content now=" + cpvi.contentView.getText)
//	  Log.v(SQConstants.LOG_TAG, " PLA.getView(): child content now set to " + cpvi.contentView.getText)
	  setupDeleteBar(cpvi, rv)
	  if (wiv != null) {
	    setupImage(cpvi.post, cpvi.imageView, rv)
	  }
	  cpvi.updateStats(sqapp)
	  pvi.repliesList.addView(rv)
	}
//	Log.v(SQConstants.LOG_TAG, " PLA.getView(): rL vis was:" +pvi.repliesList.getVisibility)

//	Log.v(SQConstants.LOG_TAG, " PLA.getView(): rL vis now:" +pvi.repliesList.getVisibility)
//	Log.v(SQConstants.LOG_TAG, " PLA.getView(): note that visible is " + View.VISIBLE + " and gone is " + View.GONE)
    }

    pvi.updateStats(sqapp)
    val seeMoreWidgetsVisibility = singleThreadView match {
      case true  => View.GONE
      case false => pvi.post.hidden match {
	case 0 => View.GONE
	case x => {
	  pvi.repliesHiddenText.setText(x match {
		case 1 => "1 reply hidden ⇒"
	    case y => y + " replies hidden ⇒"
	  })
	  View.VISIBLE
	}
      }
    }
//    Log.v(SQConstants.LOG_TAG, " PLA.getView(): setting visibility to:" + seeMoreWidgetsVisibility)
//    Log.v(SQConstants.LOG_TAG, " PLA...          ...content=" + pvi.contentView.getText)
    pvi.repliesHiddenText.setVisibility(seeMoreWidgetsVisibility)
    pvi.seeAllReplies.setVisibility(seeMoreWidgetsVisibility)
    view
  }

}
