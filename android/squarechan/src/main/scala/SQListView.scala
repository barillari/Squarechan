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

import _root_.android.util.Log
import _root_.android.content.Context
import _root_.android.widget.ListView
import _root_.android.util.AttributeSet
import _root_.android.view.MotionEvent

import _root_.android.os.Handler
import _root_.android.os.Message


class SQListView(context:Context, attrs:AttributeSet) extends ListView(context, attrs) {
  val TOP = 0
  val BOTTOM = 1
  val NEITHER = 2
  val NOY = -1.

  var mHandler:Handler = null

  var mLastTB = NEITHER
  var mLastY = NOY


  var THRESHHOLD = 50

  def setHandler(h:Handler) {
    mHandler = h
  }

  override def onTouchEvent(mevt:MotionEvent):Boolean = {

    val result = super.onTouchEvent(mevt)
    Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent me:" + mevt + " result:" + result + " sX:" + getScrollX  + " sY:" + getScrollY + " gMSA:" + getMaxScrollAmount + " cVSE:" + computeVerticalScrollExtent + " cVSO:" + computeVerticalScrollOffset + " cVSR:" + computeVerticalScrollRange + " ct:" + getCount + " glvf,l:" + getFirstVisiblePosition +","+ getLastVisiblePosition) 

    /*
     *    CANCEL -> clear any state and return
     *     if no state:
     *            UP -> ignore
     *            DOWN/MOVE -> if at top or bottom, record t/b & current Y. else do nothing

     *     if state:
     *            UP/MOVE -> if still at top or bottom from last time, check if current Y has incremented by at least THRESH pixels. if so, activate. else ignore.
     *                                if not at top or botom, erase state
     *            DOWN->clear state

       */
    if (mevt.getAction == MotionEvent.ACTION_CANCEL) {
      clearState()
    }
    
    if (mLastTB == NEITHER) { // no state
      mevt.getAction match {
	case x:Int if x == MotionEvent.ACTION_DOWN || x == MotionEvent.ACTION_MOVE => {
	  mLastTB = topBottomNeither
	  mLastY = mevt.getY
	  Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent started new state tbn:" + mLastTB  + " mevtY:" + mLastY)
	  null
	}
	case _ => null
      }
    } else { // state
      mevt.getAction match {
	case MotionEvent.ACTION_DOWN => clearState
	case x:Int if x == MotionEvent.ACTION_UP || x == MotionEvent.ACTION_MOVE => {
	  if (mLastTB == topBottomNeither) {
	    Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent checking state tbn:" + mLastTB  + " getY:" +  mevt.getY + " mevtY:" + mLastY)
	    if (mLastTB == TOP && (mevt.getY - mLastY) > THRESHHOLD) {
	      upAct()
	      clearState()
	    } else if  (mLastTB == BOTTOM && (mLastY - mevt.getY) > THRESHHOLD) {
	      downAct()
	      clearState()
	    }
	  } else {
	    clearState()
	  }
	}
        case _ => null
      }
    }


    // if we scroll up while already at the top or scroll down while already at the bottom, 


    return result
  }

  def clearState() {
    mLastTB = NEITHER
    mLastY = NOY
  }

  def upAct() {
    Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent: overscroll up")
    if (mHandler != null) {
      mHandler.sendMessage(Message.obtain(mHandler, EventConstants.OVERSCROLL_UP))
    }
  }

  def downAct() {
    Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent: overscroll down")
    if (mHandler != null) {
      mHandler.sendMessage(Message.obtain(mHandler, EventConstants.OVERSCROLL_DOWN))
    }
  }


  def topBottomNeither():Int = {
    if (computeVerticalScrollOffset+computeVerticalScrollExtent == computeVerticalScrollRange) {
//      Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent: at bottom")
      BOTTOM
    } else if (computeVerticalScrollOffset == 0) {
//      Log.v(SQConstants.LOG_TAG, "SQLV.onTouchEvent: at top")
      TOP
    } else {
      NEITHER
    }
  }


}


//removeFooterView
