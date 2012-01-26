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
import _root_.android.content.Intent
import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.view.View
import _root_.android.view.View.OnClickListener
import _root_.android.widget.TextView
import _root_.android.text.method.LinkMovementMethod
import _root_.android.util.Log

class SplashScreen extends Activity with OnClickListener with TypedActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.splashscreen)
    findView(TR.splash_agreement_textview).setMovementMethod(LinkMovementMethod.getInstance());
    findView(TR.splash_agree_and_continue).setOnClickListener(this);
    findView(TR.splash_cancel).setOnClickListener(this);
    }

  override def onClick(v: View) { 
    Log.v("sq", "ss clicked:"+v);
    v.getId() match {
      case R.id.splash_cancel => { 
	setResult(Activity.RESULT_CANCELED); 
	finish() 
	}
      case R.id.splash_agree_and_continue => {
	setResult(Activity.RESULT_FIRST_USER)
	finish();
      }
	case _ => Log.e("sq", "unknown splash screen click: someone clicked " + v)
    }
  }
}
				   
 
