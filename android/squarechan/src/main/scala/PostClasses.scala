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
import _root_.android.view.View
import _root_.android.location.Location
import _root_.java.util.ArrayList
import _root_.android.util.Log
import _root_.org.json.JSONException
import _root_.org.json.JSONObject
import _root_.org.json.JSONArray
import _root_.com.github.droidfu.widgets.WebImageView
import _root_.android.widget.TextView
import _root_.android.widget.LinearLayout
import _root_.android.widget.Button

// meant to avoid unneeded findViewById calls
class PostViewInfo(val imageView:WebImageView, val contentView:TextView, 
		   val repliesList:LinearLayout,
		   val repliesHiddenText:TextView,
		   val seeAllReplies:Button,
		   val statsText:TextView,
		   var post:Post) {
   def updateStats(app:SQApp) {
     val txt = StaticMethods.getStatsText(post, app)
     Log.v(SQConstants.LOG_TAG, "PostViewInfo.updateStats: id:" + post.id + " txt:" + txt)
     statsText.setText(txt)      
   }
}


class PostParams(val locString:String, val postIds:ArrayList[Long], val action:Int, val owner_token:String) { }

class Post(val id:Long, val content:String, val gridX:Long, val gridY:Long, 
	   val utmZone:Int, 
	   val gridRounding:Int, val imageURL:String, val created:Long, 
	   val latest_update:Long, val hidden:Int, val deletable:Boolean, 
	   val deleted:Boolean) {
  var isPlaceholder = false

  def this(isTopPostition:Boolean) = {this(0,"",0,0,0,0,"",0,0,0,false,false); isPlaceholder=true}

  var view = null:View;
  val children = new ArrayList[Post];
  def toJSON():JSONObject = {
    if (isPlaceholder) {
      throw new Exception("You can't JSONize a placeholder. Don't try.")
    }
    val j = new JSONObject
    j.put("id", id)
    j.put("content", content)
    j.put("gridx", gridX)
    j.put("gridy", gridY)
    j.put("utm_zone", utmZone)
    j.put("rounding", gridRounding)
    if (imageURL != null) {
      j.put("picture_url", imageURL)
    }
    j.put("created", created)
    j.put("latest_update", latest_update)
    j.put("hidden", hidden)
    j.put("deletable", deletable)
    j.put("deleted", deleted)
    val jc = new JSONArray
    for (child <- children) {
      jc.put(child.toJSON)
    }
    j.put("children", jc)
    j
  }

}
