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
import _root_.android.app.Activity
import _root_.android.util.Log

trait ShowOneThreadTrait extends Activity {

  def showOneThread(postid:Long, scrollToReplyBox:Boolean, savedLocation:String) {
    val pname = classOf[OneThreadActivity].getPackage.getName
    val intent = new Intent(this, classOf[OneThreadActivity])
    Log.v(SQConstants.LOG_TAG, "showOneThread: postid=" + postid)
    intent.putExtra(pname +  "." + OTAConstants.POST_ID, new java.lang.Long(postid).longValue())
    if (savedLocation != null)  {
      intent.putExtra(pname +  "." + NPAConstants.SAVED_LOCATION, savedLocation)
    }
//    intent.putExtra(ename, postid)
    startActivity(intent)
  }

}
