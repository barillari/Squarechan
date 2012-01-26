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

var LOC=null;
var YES='y';
var NO='n';
var geocoder=null;
var map=null;
var GEO_AVAILABLE=null;
var MARKER = null; 
var CANDIDATE_LOC = null;
var FILE_FIELD_ISOPEN = {};
var CANDIDATE_SHORTLOC = null; // has truncated numbers
var UNLOADED = true;
var LATEST_RESULTS = null;
var BOTTOM_ZONE_HEIGHT = 25;
var IN_BOTTOM_ZONE = false;
var LOAD_MORE_RUNNING = false;
var MOBILE="mobile";
var DESKTOP="desktop";
var GEO_INSTRUCTION_BAR = null;
var GEO_HELP_BANNER_DELAY = 500;
var RADIUS = null;
var ROUND = null;

function mobile_prefs_update() {
    //    if (parseInt($("#radiusselect").val()) < parseInt($("#roundselect").val())) {
    //	alert('The "Show posts within nearest" distance must be greater than or equal to the "Round location to nearest" distance. Please raise the former or reduce the latter.');
    //	return false;
    //    }
    $.cookie('radius', $("#radiusselect").val());
    //    $.cookie('round', $("#roundselect").val());
    document.location = '/m';
}

function setup_commment_form() {
    $("#commentformdiv").show();
    $("#postcommentform").submit(sendcomment);
    $("#commentlink").click(function () {

	    $("#commentformdivinner").toggle();
	    if (IS_MOBILE) { 
		$(document).scrollTop($(document).height()); // make sure we can see it
	    }});
}

function handle_comment_submit(data, status, xhr) {
	if (data == "ok") {
	    $("#commentthanks").show(250);
	    $("#commentfield").val("");
	    setTimeout(function() {$("#commentthanks").hide(250); $("#commentformdivinner").hide(250);}, 2500);
	} else {
	    alert("Posting comment failed. Please try again or contact support.");
	}
}

function sendcomment() {
    var value = $("#commentfield").val();
    if (value == null || value.length == 0) {
	alert("Please enter some text in the comment.");
    }
    else {
	$.post("/post-comment", {'comment': value}, handle_comment_submit, "text");
    }
    return false;
}

function getInstructionBarHTML() {
   if (navigator.userAgent.indexOf("Firefox") > -1) {
       return '<table><tr><td><p>To see posts (and to post) near your location, please...</p><ol><li>Check "Remember for this site"</li><li>Click "Share location"</li></ol></td><td><img id="wuparrow" src="/static/whiteuparrow.gif" alt="up arrow"></td></tr></table>';
   }
   if (navigator.userAgent.indexOf("Chrome") > -1) {
       return '<table><tr><td><p>Please click "Allow" so we can show you posts (and you can post) near your location.</p></td><td><img id="wuparrow"  src="/static/whiteuparrow.gif" alt="up arrow"></td></tr></table>';
   }
   // with googlebar.
   if (navigator.userAgent.indexOf("MSIE") > -1) {
       return '<table><tr><td><p>To see posts (and to post) near your location, please...</p><ol><li>Check "Remember for this site"</li><li>Click "Share my location"</li></ol></td><td><img id="wuparrow" src="/static/whiteuparrow.gif" alt="up arrow"></td></tr></table>';
   }
}

function showGeoInstructionBar() {
    if ($("#geo_instruction_bar").length > 0) {
	$("#geo_instruction_bar").show(250);
	return;
    }
    var html = getInstructionBarHTML();
    if (html == null) {
	return;
    }
    var div = document.createElement("div");
    $(div).attr("id", "geo_instruction_bar");
    $(div).html(html);
    $(document.body).append(div);
    $(div).show(250);
}



function get_mobile_postformsubmithook(pid) {
    // should refactor to remove duplicate code with on_post_form_submit
    return function() {
    var len = $("#id_content_"+pid).val().length;
    if ($("#upload_input_"+pid).val()==="")  {
	if ($("#mobile_reply_to").val()==="") {
	    alert("Please an attach an image. (All new-thread-starting posts must include an image.)");
	    return false;
	}
	if (len===0) {
	    alert("Please type some text or attach a picture before posting.");
	    return false;
	}
    }
    if (len > MAX_POST_LEN) {
	alert("Post too long. Posts cannot exceed " + MAX_POST_LEN + " characters. This post is " + len + " characters.");
	return false;
    }
    if ($("#upload_input_"+pid).val()==="") {
	submit_ajax_post(pid);
    } else {
	$("#postform_"+pid).submit();
    }
    return false;
}
}

function mobile_go_to_newpost() {
    $("#mobile-newpost").show();
    //    $("#mobile-newpost-top").hide();
    document.location.hash = '#mobile-newpost';
}

function mobile_acceptandcontinue() {
    var next = get_qs_hash_value("next");
    if (!(next && next.match(/[0-9a-z/-]+/))) {
	    next = '/m/';
    }
    document.location = next + '?loc=' + encodeURIComponent(CANDIDATE_LOC);
}

function mobile_select_address_update(use_form) {
    var formaddress = $("#address").val();
    var urladdress = get_qs_hash_value("address");
    var address = use_form ? formaddress : urladdress;
    $("#searchedfor").text(address);
    if (use_form) {
	document.location.hash = "#address=" + encodeURIComponent(formaddress) + (NEXT ?  "&next=" + encodeURIComponent(NEXT) : "");
    }
    else {
	$("#address").val(urladdress);
    }
    if (!address) {
	$("#noaddress").show(250);
	return;
    }
    if (address != ADDRESS) {
	lookup_address_matches_mobile(address);
	ADDRESS=address;
    }

}

function qs_lookup(qs, name) {
    // lookup name in qs, which is a query string
    var pairs = qs.split("&");
    for (var i = 0; i < pairs.length; i++) {
	var pair = pairs[i].split("=");
	if (pair.length != 2) {
	    continue;
	}
	//if (decodeURIComponent(pair[0])==name) { // we're going to save some computation and assume keys have no special chars
	if (pair[0]===name) { 
	    return decodeURIComponent(pair[1].replace(/[+]/g," ")); 
	}
    }
    return null;
}

function get_qs_hash_value(name) {
    // lookup name in the query string. also look it up in the #hash,
    // treating #hash as if it were formatted like a query
    // string. return the #hash value if exists, then the qs value if
    // exists, then null
    var hashl =  qs_lookup(window.location.hash.substring(1), name);
    if (hashl !== null) {
	return hashl;
    }
    return qs_lookup(window.location.search.substring(1), name);
}

function document_scroll_hook(event) {
    // if the page is at least two whole viewports' (screenfuls')
    // worth of content, then scrolling to the bottom will auto-click the "moar" button (if it is available)
    if (!MORE_AVAILABLE) {
	return;
    }
    // http://updatepanel.net/2009/02/20/getting-the-page-and-viewport-dimensions-using-jquery/
    var viewportHeight = window.innerHeight ? window.innerHeight : $(window).height();
    var pageHeight = $(document).height();
    if (viewportHeight+$(document).scrollTop() 
	     >= pageHeight - BOTTOM_ZONE_HEIGHT) {
	// within BOTTOM_ZONE_HEIGHT px of the bottom
	if (!IN_BOTTOM_ZONE) { // don't call again if we're already in the zone
	    IN_BOTTOM_ZONE = true;
	    load_more();
	}
    }
    else {
	IN_BOTTOM_ZONE = false;
    }
}

function do_mobile_location_lookup() {
    $("#lookinguplocation").show();
    $("#lookupfailed").hide();
   geo_position_js.getCurrentPosition(mobile_locate_success, mobile_locate_error,  {enableHighAccuracy:true, maximumAge:10*60*1000, timeout:30*1000});
}


function mobile_locate_error(err) {
    $("#lookinguplocation").hide();
    $("#lookupfailed").show(250);
}

function mobile_locate_success(loc) { 
    $("#lookinguplocation-success").show();
    var pos = ""+loc.coords.latitude+","+loc.coords.longitude;
    var next = get_qs_hash_value("next");
    window.location = "/m/map#loc=" + encodeURIComponent(pos) + (next ? ("&next=" + encodeURIComponent(NEXT)) : "");
}




function getCheckToHideGeobarFunction(oldloc) {
    return function() {
	if ($(window).height() == oldloc) {
	    $("#geo_instruction_bar").hide()
	}
    };

}

function lookup_location_latest() {
    var oldloc = $(window).height();
    geo_position_js.getCurrentPosition(show_map_latest, show_map_error_latest,  {enableHighAccuracy:true, maximumAge:10*60*1000, timeout:30*1000 });
    setTimeout(function () {
	    if ($(window).height() < oldloc) {
		showGeoInstructionBar(); 
		setInterval(getCheckToHideGeobarFunction(oldloc),  100);
	    }}, GEO_HELP_BANNER_DELAY);
}

function show_map_latest(loc) {
    var latlng = new google.maps.LatLng(loc.coords.latitude, loc.coords.longitude);
    set_candidate_loc(loc.coords.latitude, loc.coords.longitude);
    map.setCenter(latlng);
    add_marker(latlng);
}

function show_map_lat_lng(lat, lng) {
    var latlng = new google.maps.LatLng(lat, lng);
    set_candidate_loc(lat, lng);
    map.setCenter(latlng);
    add_marker(latlng);

}

function show_map_error_latest(loc) {
    if (LOC==null){
	alert("Auto-location failed. Please enter your location manually.");
    }
}

function add_marker(latlng) {
    if (MARKER != null) {
	MARKER.setMap(null);
	MARKER = null;
    }
    var marker = new google.maps.Marker({position: latlng,  map: map,  title:"Your location"});   
    MARKER=marker;
    return marker;
}


function init_map(latlng) {
    var myOptions = {
      zoom: 15,
      center: latlng,
      mapTypeId: google.maps.MapTypeId.ROADMAP
    }
    map = new google.maps.Map(document.getElementById("map_canvas"), myOptions);
    $("#mobile-mapinstructions").show();
    google.maps.event.addListener(map, 'click', function(event) {
	    add_marker(event.latLng);
	    set_candidate_loc(event.latLng.lat(), event.latLng.lng());
	});
    var marker = add_marker(latlng);
  }

function is_android() {
    return (navigator != null && navigator.userAgent != null && navigator.userAgent.indexOf("Android") > -1);
}

function maybe_show_android_overlay() {
    if (is_android()) {
	if ($.cookie('sawdroid') != YES) {
	    $.cookie('sawdroid', YES);
	    $("#nothanksdroid").click(function(){$("#android-shutter").hide();$("#everything-else").show()});
	    $("#android-shutter").show();
	    $("#everything-else").hide();
	}
    }
}

function supports(bool, suffix) {var s = "Your browser ";if (bool) {s += "supports " + suffix + ".";} else {s += "does not support " + suffix + ". :(";}return s;}

function get_handle_post_submit(pid) {
    return function (data, status, xhr) {
	if (data[0] == "ok") {
	    $("#post_successful_"+pid).show(250);
	    $("#file_upload_div_"+pid).hide();
	    setTimeout(function () {$("#post_successful_"+pid).hide(250);   }, 2000);
	    //	    setTimeout(function () {close_reply_box(pid)   }, 2250);

	    $("#id_content_"+pid).val("");
	    randomize_antidupetoken(pid); // run _after_ clearing the box
	    if (""+pid == "-1") {
		reload_latest();
	    } else {
		reload_post(pid);
	    }

	}
	else {
	    alert("Post failed: " + data[1]);
	}
    }
}


function on_post_form_submit(event) {
    // should refactor to remove duplicate code with mobile_postformsubmithook
    var pid = get_postid_from_elem(event.target);
    var len = $("#id_content_"+pid).val().length;

    if (!FILE_FIELD_ISOPEN[pid]) { 
	
	if (""+pid === "-1") {
	    alert("Please an attach an image. (All new-thread-starting posts must include an image.)");
	    return false;
	}
	if (len == 0) {
	    alert("Post is empty. Please add some text or a picture.");
	    return false;
	}
    }

    if (len > MAX_POST_LEN) {
	alert("Post too long. Posts cannot exceed " + MAX_POST_LEN + " characters. This post is " + len + " characters.");
	return false;
    }


    if (FILE_FIELD_ISOPEN[pid]) { // FIXME: can we just say $("#filefieldid").val()==="" ??? 
	//	debugger; //we do a real post in this case, redirecting to a transition page to prevent f5 from reposting.
	// n.b. in the future, we should add a flash upload for
	// browsers that support it.
	$("#postform_"+pid).submit(); // do we have to cancel the onsubmit hook first?
	// FIXME: why not just return true?
    }
    submit_ajax_post(pid);
    return false;
}

function submit_ajax_post(pid) {
    var params = {form: $("#postform_"+pid).serialize()};
    $.post("/post-ajax", params, get_handle_post_submit(pid), "json");
    
}

function randomize_antidupetoken(pid) {
    // used to prevent inadvertent duplicates
    $("#antidupetoken_"+pid).attr('value', Math.random() + '-' + (new Date().getTime()) + '-' + Math.random());
}



function change_click() {
    if (INITIAL_LOCATION_NEEDED != true) {$("#closechangebox").show();}
    $("#changelocbox").toggle();
    if( $('#changelocbox').is(':visible') ) {
	$("#changeloc").hide();
    } else {
	$("#changeloc").show();
    }
    if (UNLOADED) {
	geocoder = new google.maps.Geocoder();
	if (INITIAL_LOCATION_NEEDED != true) {
	    $("#click2change").attr('style','');
	    var pair = $('input[name="loc"]').val().split(",");
	    var latlng = new google.maps.LatLng(parseFloat(pair[0]),parseFloat(pair[1]));
	    init_map(latlng);
	}
    }
}




function lookup_address() {
    gcodeAddress();
}

function seemore_func(event) {
    $(event.target.parentNode.parentNode).children('.content_trunc').hide();
    $(event.target.parentNode.parentNode).children('.content_full').show(250);
}

function seeless_func(event) {
    $(event.target.parentNode.parentNode).children('.content_full').hide();
    $(event.target.parentNode.parentNode).children('.content_trunc').show(250);

}

function close_reply_box(pid) {
    $("#replybox_"+pid).hide(250);
}

function toggle_reply_box(event) {
    var pid = get_postid_from_elem(event.target);
    if (!pid) {alert("post id missing!"); return;}
    $("#replybox_"+pid).toggle(250);
}

function reset_file_field_wrapper(event) {
    return reset_file_field(get_postid_from_elem(event.target));
}

function reveal_file_field_wrapper(event) {
    return reveal_file_field(get_postid_from_elem(event.target));
}

function delete_func(event) {
    var pid = get_postid_from_elem(event.target);
    if (!pid) {alert("Can't happen: post id missing!"); return;}
    var ot = $.cookie('owner_token');
    if (!pid) {alert("Owner token missing! If you cleared your cookies, you will not be able to delete this post."); return;}
    if (confirm("Really delete this post? This action cannot be undone.")===true) {
	$.post("/delete-post", {'postid': pid, 'owner_token': ot, 'return_json': 1}, get_handle_delete_post(pid), "text");
    }
}


function get_handle_delete_post(pid) {
    // close over pid.
    return function (data, status, xhr) {
	if ("success"!==status) {
	    alert("Deletion failed.");
	    return;
	}
	if (data != "ok") {
	    alert("Deletion failed: " + data);
	    return;
	}
	var cnc = ['', 'child']; 
	// we don't bother remembering if it's a childpost or not. we
	// just handle both possibilities, since only one will be true.
	for (var i = 0; i < cnc.length; i++) {
	    $("#post"+cnc[i]+"box_"+pid+" > .postinfo > .reldistance").remove();
	    $("#post"+cnc[i]+"box_"+pid+" > .postinfo > .deletespan").remove();
	    $("#post"+cnc[i]+"box_"+pid+" > .postthumbdiv").remove();
	    $("#post"+cnc[i]+"box_"+pid+" > .content_full").remove();
	    $("#post"+cnc[i]+"box_"+pid+" > .content_trunc").html('<span class="deletemsg">[Post deleted by author.]</span>').show();
	}
    }
}


function run_after_loading_posts(pid) {
    $(".mobile-postform").submit(get_mobile_postformsubmithook(MOBILE_POST_FORM_PID));
    $(".seemore").click(seemore_func);
    $(".seeless").click(seeless_func);
    $(".deletespan").click(delete_func);

    if (!IS_MOBILE) {
	// mobile screens are too small to bother with this
    $(".lightbox").lightbox({
	    fitToScreen: true,
		overlayOpacity : 0.5,
		navBarSlideSpeed: 100,
		resizeSpeed : 100,
		imageClickClose: true
		});
    }

    $(".reply").click(toggle_reply_box);
    $(".add_a_picture_link").click(reveal_file_field_wrapper);
    $(".upload_input").change(reveal_file_field_wrapper);
    $(".file_cancel_img").click(reset_file_field_wrapper);
    $(".click_to_change_text").click(change_click);
    var pelems = $(".newpostform");
    if (pid) {
	// if pid is defined, we only run the for loop on one post
	// (saves some time--or does it?)
	pelems = $("#postform_" + pid); 
    }
    for (var i = 0; i < pelems.length; i++) {
	var pid = get_postid_from_elem(pelems[i]);
	// we don't want to reset the file field if the user has
	// already edited it, so so set a tag to ensure this function
	// is run only once
	if (!$("#postform_"+pid).is(".postinitialized")) {
		// make sure there are no lingering files in the
		// fields when the page loads
	    if (!IS_MOBILE) { 
		reset_file_field(pid);	
	    }
	    randomize_antidupetoken(pid);
	    $("#postform_"+pid).addClass("postinitialized");
	}
	if (!IS_MOBILE) { 
	    $("#postform_"+pid).submit(on_post_form_submit);
	    $("#id_content_"+pid).blur(show_hide_extra_post_stuff);
	    $("#id_content_"+pid).focus(show_hide_extra_post_stuff);
	    // prevent the "attach" link from vanishing if the user tabs into the box
	    $("#upload_input_wrapper_"+pid).scroll( function (evt){var uiw = $(evt.target); if (uiw.scrollLeft()!=0) {uiw.scrollLeft(0);} }  );
	}
    }
    if (!IN_SINGLE_THREAD_VIEW) { $("#replybox_"+pid).hide(); }
}

function accept_new_location() {
    RADIUS = $("#radiusselect").val();
    //    ROUND = $("#roundselect").val();
    if (CANDIDATE_LOC != null) {
	LOC = CANDIDATE_LOC;
	CANDIDATE_LOC = null;
    }
    $.cookie('loc', LOC);
    $.cookie('radius', RADIUS);
    //    $.cookie('round', ROUND);
    if (NEXT_AFTER_ACCEPT != null) {
	window.location.href = NEXT_AFTER_ACCEPT;
	return; // is it location or location.href??
    }
    MORE_AVAILABLE=true; 
    $("#changelocbox").hide(250);
    $("#changeloc").show();
    //    $(".roundfield").val($("#roundselect").val());
    $(".radiusfield").val($("#radiusselect").val());

    $('input[name="loc"]').val(LOC);
    $('#query').val('');
    $('#matches').html('');
    $("#matches").hide();
    $('#currentloc').text(CANDIDATE_SHORTLOC);
    $('#current_radius_nice').text($('#radiusselect :selected').text());
    $('#currentlocdiv').show();
    $("#acceptnew").hide();
    $("#acceptnew").attr('disabled','true');
    $("#acceptnew").hide();
    $("#noposts").hide(); // we don't know if it's true yet
    $("#loadingposts").show();
    $("#loadingposts").css('display','block');
    $("#postsholder").show()
    $("#set_loc_infobox").hide(100);
    $("#new_coords").text("");
    $("#closechangebox").show();
    reload_latest();
}

function get_postid_from_elem(elem) {
    var elemid = $(elem).attr('id');
    if (!elemid) {return null;}
    var thesplit = elemid.split("_");
    if (thesplit.length < 2) { 
	return null;
    }
    return thesplit[thesplit.length-1];
}

function reload_latest() {
    /// fixme: don't do this the dumb way
    $("#posts").load('/'+(IS_MOBILE ? 'm/' : '')+'latest-ajax', {loc: LOC}, run_after_loading_posts);
}

function mobile_load_more() {
    load_more(MOBILE)
}

function load_more(mode) {

    if (LOAD_MORE_RUNNING) {return;} // not reenterant (prevent dupes)
    var skip = [];
    $("#no_more_posts").hide();
    $.each($(".postbox"), 
	   function (i,elem) { skip.push(get_postid_from_elem(elem))});
    $("#loadmorespinner").show();
    LOAD_MORE_RUNNING = true; // prevent reentrance
    // we prove a complete callback so it executes _after_ the success callback
    // (and the new html is therefore already integrated)
    var postdata = {loc: LOC, skip: skip.join(","), return_empty_if_none: 1};
    if (mode == MOBILE) {
	postdata.is_mobile = 1;
    }
    $.ajax({
	url: '/latest-ajax',
	dataType: 'html',
	data: postdata,
	complete: function() {$("#loadmorespinner").hide();
		              LOAD_MORE_RUNNING = false;},
	success: append_more_posts
       });
}



function erase_duplicates() {
    // we shouldn't have any duplicate posts, but if we do, dump them.
    // (we can get dupes if we call load_more() twice in a row
    // [although the code should prevent that]

    // FIXME: we aren't using this code just yet. hopefully
    // load_more() should be able to prevent reentrant calls
    var seen = {};
    $.each($(".postbox"), function (i, elem) {
	    var classes = $(elem).attr('class');
	    var pid = classes.replace(/.*postboxid_([0-9]+).*/,"$1");
	    if (pid===classes) {alert("FIXME:"+classes); return;}
	    var found = $(".postboxid_" + pid);
	    if (found.length > 1) { 
		// remove the *older* (earlier) versions
		for (var j = 0; j > found.length - 2; j++) {
		    $(found[j]).remove();
		}
	    }
	    if (seen[pid] !== undefined) {
		// remove the old one
		seen[pid].parentNode.removeChild(seen[pid]);
	    }
		
    if (!elemid) {return null;}
    var thesplit = elemid.split("_");
    if (thesplit.length < 2) { 
	return null;
    }
    return thesplit[thesplit.length-1];
	    });
}

function append_more_posts(data, status, xhr) {
    if ("success"===status) {
	if (data.length > 0) {
	    $("#posts").append(data);
	    //	    erase_duplicates(); // see comments in erase_duplicates()
	    run_after_loading_posts();
	}
	else {
	    $("#no_more_posts").show(250);
	    $("#MOAR").hide(250); // this is only visible in mobile mode
	    setTimeout(function(){$("#no_more_posts").hide(250);}, 5000);
	}
    } else {
    // fixme: else?//	alert("Request failed.");
    }
    

}


function get_single_post_rlap(pid) {
    return function () {run_after_loading_posts(pid);} //close over pid
}

function reload_post(pid) {
    $("#postbox_"+pid).load('/single-post-ajax', {loc: LOC, postid: pid, in_thread_view: IN_SINGLE_THREAD_VIEW ? "1" : "0", truncate: TRUNCATE_REPLIES_TO, is_mobile: IS_MOBILE ? "1":"0"}, get_single_post_rlap(pid));
}

function hide_aorc_for_pid(pid) {
    return function () {$("#adjust_or_conceal_"+pid).hide(250);}
}

function show_hide_extra_post_stuff(event) {
    var pid = get_postid_from_elem(event.target);
    if (!pid) {alert("post id missing!"); return;}
    if (event.type=="blur" && $("#id_content_"+pid).val()=="") {
	// we have to delay this slightly because otherwise IE will hide it before we can register the click
	setTimeout(hide_aorc_for_pid(pid), 250);
    } else {
	$("#adjust_or_conceal_"+pid).show(250);
    }
}


function register_simple_toggler(cid) {
    // register a simple toggler. $cid is the button that toggles the box.
    // $cid-div is the box itself, and $cid-close is the button that closes the box.
    $("#"+cid).click(function () { $("#"+cid+"-div").toggle(250); });
    $("#"+cid+"-close").click(function () { $("#"+cid+"-div").hide(250); });
}

function handle_new_radius_selected(evt) {
    if (LOC == null) { return; } // do nothing 
    if (CANDIDATE_LOC != null && LOC != CANDIDATE_LOC) {return;} // do nothing; the button is already showing
    if (""+RADIUS != ""+$("#radiusselect").val()) {
	$("#acceptnew").attr('disabled','');
	$("#acceptnew").show(250);	
    } else {
	$("#acceptnew").attr('disabled','true');
	$("#acceptnew").hide(250);	
    }
}

function init_latest() {
    //    register_simple_toggler("whats-this-round");
    var a = document.createElement('u');
    $("#closechangebox").click(change_click);
    //    $("#toggleoptionsbox").click(function () {$("#optionsbox").toggle(250); $("#optionslarr").toggle(); $("#optionsdarr").toggle(); });
    $(a).click(change_click);
    $("#autofindlink").click(lookup_location_latest);
    $("#acceptnew").click(accept_new_location);
    $("#radiusselect").change(handle_new_radius_selected);
    $("#lookupaddressbutton").click(lookup_address);
    a.appendChild(document.createTextNode("change"));
    $("#changeloc").append(document.createTextNode("["));
    $("#changeloc").append(a);
    $("#changeloc").append(document.createTextNode("]"));
}

/*
function startpage_draglistener(marker) {
    $("#coords").text("Position: " + marker.getPosition().toString());
    // why is this loc and not candidate loc? why isn't it set_candidate_loc??? oh wait it's old code. junk it.
    LOC=""+marker.getPosition().lat()+","+marker.getPosition().lng();
    }*/

function set_fake_location() {
    set_candidate_loc(42.701188800, -73.150268400);
}

function set_candidate_loc(lat, lng) {
    if (map===null && typeof google != 'undefined') {
	init_map(new google.maps.LatLng(lat, lng))
    }
    $("#click2change").show();
    CANDIDATE_LOC=""+lat+","+lng;
    CANDIDATE_SHORTLOC = lat.toFixed(5)+","+lng.toFixed(5);
    $("#new_coords").html('<span class="b">New location:</span> ' + CANDIDATE_SHORTLOC);
    $("#acceptnew").attr('disabled','');
    $("#acceptnew").show(250);
}

function go_to_result(i) {
    set_candidate_loc(LATEST_RESULTS[i].geometry.location.lat(), LATEST_RESULTS[i].geometry.location.lng());
    map.setCenter(LATEST_RESULTS[i].geometry.location);
    add_marker(LATEST_RESULTS[i].geometry.location);
}

function lookup_address_matches_mobile(address) {
    /* also copied from the google maps api examples */
    $("#mobile-matches").hide();
    $("#searching").hide();
    $("#noresults").hide();
    $("#noaddress").hide();

    if (geocoder===null) {geocoder = new google.maps.Geocoder();}
    geocoder.geocode( { 'address': address}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
	  $("#mobile-matches").html('');
	  if (results.length > 0) {
	      var newmatches = '<div class="bold">Found '+results.length+' possible match'+(results.length==1?'':'es')+'. Tap an address to continue:</div><div class="mobile-matchlist">';
	      for (var i = 0; i < results.length; i++) {
		  var mloc = encodeURIComponent("" + results[i].geometry.location.lat()+","+results[i].geometry.location.lng());
		  var nextblk = '';
		  if (NEXT) {
		      nextblk = "&next=" + encodeURIComponent(NEXT);
		  }
		  newmatches += '<div class="mobile-match"><a href="/m/map/#loc='+mloc+nextblk+'" class="mobile-match">' + results[i].formatted_address + '</a></div>';
	      }
	      newmatches += '</div>';
	      $("#mobile-matches").html(newmatches);
	      $("#mobile-matches").show();
	  }
	  else {
	      $("#noresults").show();
	  }
      } else {
	  alert("Geocode was not successful for the following reason: " + status);
      }
    });
    
}

function gcodeAddress() {
      /** 
	  this function was copied from the google maps api docs examples
      */
    var address = document.getElementById("query").value;
    geocoder.geocode( { 'address': address}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
	  LATEST_RESULTS = results;
	  go_to_result(0);
	  $("#matches").html('');
	  if (results.length > 0) {
	      var newmatches = '<div class="bold">Found '+results.length+' possible matches:</div><ol class="matchlist">';
	      for (var i = 0; i < results.length; i++) {
		  newmatches += '<li class="matchlistitem">[<span class="u" onclick="go_to_result('+i+')">go to</span>] ' + results[i].formatted_address + '</li>';
	      }
	      newmatches += '</ol>';
	      $("#matches").html(newmatches);
	      $("#matches").show();
	  }
      } else {
	  alert("Geocode was not successful for the following reason: " + status);
      }
    });
}



function reset_file_field(pid) {
   /// you can't actually change the contents of a file field, but you can erase it entirely...
   $("#filefieldspan_"+pid).html('');
   // note that this html should match exactly the same line in latest.html
   $("#filefieldspan_"+pid).html('<input type="file" accept="image/jpeg,image/jpg,image/png,image/gif" id="upload_input_'+pid+'" name="picture_file" class="hiddenupload" onchange="reveal_file_field('+pid+')"></input>');
   $("#imagerequired").show();
   $("#upload_input_"+pid).addClass("hiddenupload");
   $("#upload_input_wrapper_"+pid).addClass("hiddenuploadwrapper");
   $("#add_a_picture_link_"+pid).show();
   FILE_FIELD_ISOPEN[pid] = false;
}

function reveal_file_field(pid) {
    $("#upload_input_"+pid).removeClass("hiddenupload");
    $("#upload_input_wrapper_"+pid).removeClass("hiddenuploadwrapper");
    $("#add_a_picture_link_"+pid).hide();
    $("#adjust_or_conceal_"+pid).show(250);
    $("#imagerequired").hide();
    FILE_FIELD_ISOPEN[pid] = true;
}



























/**
 * jQuery Lightbox
 * Version 0.5 - 11/29/2007
 * @author Warren Krewenki
 *
 * This package is distributed under the BSD license.
 * For full license information, see LICENSE.TXT
 *
 * Based on Lightbox 2 by Lokesh Dhakar (http://www.huddletogether.com/projects/lightbox2/)
 * Originally written to make use of the Prototype framework, and Script.acalo.us, now altered to use jQuery.
 *
 *
 **/

(function($){

	$.fn.lightbox = function(options){
		// build main options
		var opts = $.extend({}, $.fn.lightbox.defaults, options);
        
		return this.each(function(){
			$(this).click(function(){
    		    // initalize the lightbox
    		    initialize();
				start(this);
				return false;
			});
		});
		
	    /**
	     * initalize()
	     *
	     * @return void
	     * @author Warren Krewenki
	     */
	     
	    function initialize() {
		    $('#overlay').remove();
		    $('#lightbox').remove();
		    opts.inprogress = false;
		    
		    // if jsonData, build the imageArray from data provided in JSON format
            if(opts.jsonData && opts.jsonData.length > 0) {
                var parser = opts.jsonDataParser ? opts.jsonDataParser : $.fn.lightbox.parseJsonData;                
                opts.imageArray = [];
                opts.imageArray = parser(opts.jsonData);
	        }
		    
		    var outerImage = '<div id="outerImageContainer"><div id="imageContainer"><iframe id="lightboxIframe" /><img id="lightboxImage"><div id="hoverNav"><a href="javascript://" title="' + opts.strings.prevLinkTitle + '" id="prevLink"></a><a href="javascript://" id="nextLink" title="' + opts.strings.nextLinkTitle + '"></a></div><div id="loading"><a href="javascript://" id="loadingLink"><img src="'+opts.fileLoadingImage+'"></a></div></div></div>';
		    var imageData = '<div id="imageDataContainer" class="clearfix"><div id="imageData"><div id="imageDetails"><span id="caption"></span><span id="numberDisplay"></span></div><div id="bottomNav">';

		    if (opts.displayHelp)
			    imageData += '<span id="helpDisplay">' + opts.strings.help + '</span>';

		    imageData += '<a href="javascript://" id="bottomNavClose" title="' + opts.strings.closeTitle + '"><img src="'+opts.fileBottomNavCloseImage+'"></a></div></div></div>';

		    var string;

		    if (opts.navbarOnTop) {
		      string = '<div id="overlay"></div><div id="lightbox">' + imageData + outerImage + '</div>';
		      $("body").append(string);
		      $("#imageDataContainer").addClass('ontop');
		    } else {
		      string = '<div id="overlay"></div><div id="lightbox">' + outerImage + imageData + '</div>';
		      $("body").append(string);
		    }

		    $("#overlay").click(function(){ end(); }).hide();
		    $("#lightbox").click(function(){ end();}).hide();
		    $("#loadingLink").click(function(){ end(); return false;});
		    $("#bottomNavClose").click(function(){ end(); return false; });
		    $('#outerImageContainer').width(opts.widthCurrent).height(opts.heightCurrent);
		    $('#imageDataContainer').width(opts.widthCurrent);
		
		    if (!opts.imageClickClose) {
        		$("#lightboxImage").click(function(){ return false; });
        		$("#hoverNav").click(function(){ return false; });
		    }
	    };
	    
	    function getPageSize() {
		    var jqueryPageSize = new Array($(document).width(),$(document).height(), $(window).width(), $(window).height());
		    return jqueryPageSize;
	    };
	    
	    function getPageScroll() {
		    var xScroll, yScroll;

		    if (self.pageYOffset) {
			    yScroll = self.pageYOffset;
			    xScroll = self.pageXOffset;
		    } else if (document.documentElement && document.documentElement.scrollTop){  // Explorer 6 Strict
			    yScroll = document.documentElement.scrollTop;
			    xScroll = document.documentElement.scrollLeft;
		    } else if (document.body) {// all other Explorers
			    yScroll = document.body.scrollTop;
			    xScroll = document.body.scrollLeft;
		    }

		    var arrayPageScroll = new Array(xScroll,yScroll);
		    return arrayPageScroll;
	    };
	    
	    function pause(ms) {
		    var date = new Date();
		    var curDate = null;
		    do{curDate = new Date();}
		    while(curDate - date < ms);
	    };
	    
	    function start(imageLink) {
		    $("select, embed, object").hide();
		    var arrayPageSize = getPageSize();
		    $("#overlay").hide().css({width: '100%', height: arrayPageSize[1]+'px', opacity : opts.overlayOpacity}).fadeIn("fast");
		    imageNum = 0;

		    // if data is not provided by jsonData parameter
            if(!opts.jsonData) {
                opts.imageArray = [];
		        // if image is NOT part of a set..
		        if(!imageLink.rel || (imageLink.rel == '')){
			        // add single image to Lightbox.imageArray
			        opts.imageArray.push(new Array(imageLink.href, opts.displayTitle ? imageLink.title : ''));
		        } else {
		        // if image is part of a set..
			        $("a").each(function(){
				        if(this.href && (this.rel == imageLink.rel)){
					        opts.imageArray.push(new Array(this.href, opts.displayTitle ? this.title : ''));
				        }
			        });
		        }
		    }
		
		    if(opts.imageArray.length > 1) {
		        for(i = 0; i < opts.imageArray.length; i++){
				    for(j = opts.imageArray.length-1; j>i; j--){
					    if(opts.imageArray[i][0] == opts.imageArray[j][0]){
						    opts.imageArray.splice(j,1);
					    }
				    }
			    }
			    while(opts.imageArray[imageNum][0] != imageLink.href) { imageNum++;}
		    }

		    // calculate top and left offset for the lightbox
		    var arrayPageScroll = getPageScroll();
		    var lightboxTop = arrayPageScroll[1] + (arrayPageSize[3] / 10);
		    var lightboxLeft = arrayPageScroll[0];
		    $('#lightbox').css({top: lightboxTop+'px', left: lightboxLeft+'px'}).show();


		    if (!opts.slideNavBar)
			    $('#imageData').hide();

		    changeImage(imageNum);
	    };
	    
	    function changeImage(imageNum) {
		    if(opts.inprogress == false){
			    opts.inprogress = true;
			    opts.activeImage = imageNum;	// update global var

			    // hide elements during transition
			    $('#loading').show();
			    $('#lightboxImage').hide();
			    $('#hoverNav').hide();
			    $('#prevLink').hide();
			    $('#nextLink').hide();

			    if (opts.slideNavBar) { // delay preloading image until navbar will slide up
				    // $('#imageDataContainer').slideUp(opts.navBarSlideSpeed, $.fn.doChangeImage);
				    $('#imageDataContainer').hide();
				    $('#imageData').hide();
				    doChangeImage();
			    } else {
			        doChangeImage();
			    }
		    }
	    };
	    
	    function doChangeImage() {

		    imgPreloader = new Image();

		    // once image is preloaded, resize image container
		    imgPreloader.onload=function(){
		        var newWidth = imgPreloader.width;
		        var newHeight = imgPreloader.height;


			    if (opts.fitToScreen) {
		            var arrayPageSize = getPageSize();
				    var ratio;
				    var initialPageWidth = arrayPageSize[2] - 2 * opts.borderSize;
				    var initialPageHeight = arrayPageSize[3] - 200;

				    if (imgPreloader.height > initialPageHeight)
				    {
					    newWidth = parseInt((initialPageHeight/imgPreloader.height) * imgPreloader.width);
					    newHeight = initialPageHeight;
				    }
				    else if (imgPreloader.width > initialPageWidth)
				    {
					    newHeight = parseInt((initialPageWidth/imgPreloader.width) * imgPreloader.height);
					    newWidth = initialPageWidth;
				    }
			    }

			    $('#lightboxImage').attr('src', opts.imageArray[opts.activeImage][0])
							       .width(newWidth).height(newHeight);
			    resizeImageContainer(newWidth, newHeight);
		    };

		    imgPreloader.src = opts.imageArray[opts.activeImage][0];
	    };
	    
	    function end() {
		    disableKeyboardNav();
		    $('#lightbox').hide();
		    $('#overlay').fadeOut("fast");
		    $('select, object, embed').show();
	    };
	    
	    function preloadNeighborImages(){
		    if(opts.loopImages && opts.imageArray.length > 1) {
	            preloadNextImage = new Image();
	            preloadNextImage.src = opts.imageArray[(opts.activeImage == (opts.imageArray.length - 1)) ? 0 : opts.activeImage + 1][0]
	            
	            preloadPrevImage = new Image();
	            preloadPrevImage.src = opts.imageArray[(opts.activeImage == 0) ? (opts.imageArray.length - 1) : opts.activeImage - 1][0]
	        } else {
		        if((opts.imageArray.length - 1) > opts.activeImage){
			        preloadNextImage = new Image();
			        preloadNextImage.src = opts.imageArray[opts.activeImage + 1][0];
		        }
		        if(opts.activeImage > 0){
			        preloadPrevImage = new Image();
			        preloadPrevImage.src = opts.imageArray[opts.activeImage - 1][0];
		        }
	        }
	    };
	    
	    function resizeImageContainer(imgWidth, imgHeight) {
		    // get current width and height
		    opts.widthCurrent = $("#outerImageContainer").outerWidth();
		    opts.heightCurrent = $("#outerImageContainer").outerHeight();
            
		    // get new width and height
		    var widthNew = (imgWidth  + (opts.borderSize * 2));
		    var heightNew = (imgHeight  + (opts.borderSize * 2));

		    // scalars based on change from old to new
		    opts.xScale = ( widthNew / opts.widthCurrent) * 100;
		    opts.yScale = ( heightNew / opts.heightCurrent) * 100;

		    // calculate size difference between new and old image, and resize if necessary
		    wDiff = opts.widthCurrent - widthNew;
		    hDiff = opts.heightCurrent - heightNew;

		    $('#imageDataContainer').animate({width: widthNew},opts.resizeSpeed,'linear');
		    $('#outerImageContainer').animate({width: widthNew},opts.resizeSpeed,'linear',function(){
			    $('#outerImageContainer').animate({height: heightNew},opts.resizeSpeed,'linear',function(){
				    showImage();
			    });
		    });

		    // if new and old image are same size and no scaling transition is necessary,
		    // do a quick pause to prevent image flicker.
		    if((hDiff == 0) && (wDiff == 0)){
			    if (jQuery.browser.msie){ pause(250); } else { pause(100);}
		    }

		    $('#prevLink').height(imgHeight);
		    $('#nextLink').height(imgHeight);
	    };
	    
	    function showImage() {
		    $('#loading').hide();
		    $('#lightboxImage').fadeIn("fast");
		    updateDetails();
		    preloadNeighborImages();

		    opts.inprogress = false;
	    };
	    
	    function updateDetails() {

		    $('#numberDisplay').html('');

		    if(opts.imageArray[opts.activeImage][1]){
			    $('#caption').html(opts.imageArray[opts.activeImage][1]).show();
		    }

		    // if image is part of set display 'Image x of x'
		    if(opts.imageArray.length > 1){
			    var nav_html;

			    nav_html = opts.strings.image + (opts.activeImage + 1) + opts.strings.of + opts.imageArray.length;

			    if (!opts.disableNavbarLinks) {
                    // display previous / next text links
                    if ((opts.activeImage) > 0 || opts.loopImages) {
                      nav_html = '<a title="' + opts.strings.prevLinkTitle + '" href="#" id="prevLinkText">' + opts.strings.prevLinkText + "</a>" + nav_html;
                    }

                    if (((opts.activeImage + 1) < opts.imageArray.length) || opts.loopImages) {
                      nav_html += '<a title="' + opts.strings.nextLinkTitle + '" href="#" id="nextLinkText">' + opts.strings.nextLinkText + "</a>";
                    }
                }

			    $('#numberDisplay').html(nav_html).show();
		    }

		    if (opts.slideNavBar) {
		        $("#imageData").slideDown(opts.navBarSlideSpeed);
		    } else {
			    $("#imageData").show();
		    }

		    var arrayPageSize = getPageSize();
		    $('#overlay').height(arrayPageSize[1]);
		    updateNav();
	    };
	    
	    function updateNav() {
		    if(opts.imageArray.length > 1){
			    $('#hoverNav').show();
                
                // if loopImages is true, always show next and prev image buttons 
                if(opts.loopImages) {
		            $('#prevLink,#prevLinkText').show().click(function(){
			            changeImage((opts.activeImage == 0) ? (opts.imageArray.length - 1) : opts.activeImage - 1); return false;
		            });
		            
		            $('#nextLink,#nextLinkText').show().click(function(){
			            changeImage((opts.activeImage == (opts.imageArray.length - 1)) ? 0 : opts.activeImage + 1); return false;
		            });
		        
		        } else {
			        // if not first image in set, display prev image button
			        if(opts.activeImage != 0){
				        $('#prevLink,#prevLinkText').show().click(function(){
					        changeImage(opts.activeImage - 1); return false;
				        });
			        }

			        // if not last image in set, display next image button
			        if(opts.activeImage != (opts.imageArray.length - 1)){
				        $('#nextLink,#nextLinkText').show().click(function(){

					        changeImage(opts.activeImage +1); return false;
				        });
			        }
                }
                
			    enableKeyboardNav();
		    }
	    };
	    
	    function keyboardAction(e) {
            var o = e.data.opts
		    var keycode = e.keyCode;
		    var escapeKey = 27;
            
		    var key = String.fromCharCode(keycode).toLowerCase();
            
		    if((key == 'x') || (key == 'o') || (key == 'c') || (keycode == escapeKey)){ // close lightbox
			    end();
		    } else if((key == 'p') || (keycode == 37)){ // display previous image
		        if(o.loopImages) {
		            disableKeyboardNav();
		            changeImage((o.activeImage == 0) ? (o.imageArray.length - 1) : o.activeImage - 1);
		        } 
		        else if(o.activeImage != 0){
				    disableKeyboardNav();
				    changeImage(o.activeImage - 1);
			    }
		    } else if((key == 'n') || (keycode == 39)){ // display next image
		        if (opts.loopImages) {
		            disableKeyboardNav();
		            changeImage((o.activeImage == (o.imageArray.length - 1)) ? 0 : o.activeImage + 1);
		        }
			    else if(o.activeImage != (o.imageArray.length - 1)){
				    disableKeyboardNav();
				    changeImage(o.activeImage + 1);
			    }
		    }
	    };
	    
	    function enableKeyboardNav() {
		    $(document).bind('keydown', {opts: opts}, keyboardAction);
	    };

	    function disableKeyboardNav() {
		    $(document).unbind('keydown');
	    };
	    
	};
    
    $.fn.lightbox.parseJsonData = function(data) {
        var imageArray = [];
        
        $.each(data, function(){
            imageArray.push(new Array(this.url, this.title));
        });
        
        return imageArray;
    };

	$.fn.lightbox.defaults = {
		fileLoadingImage : '/static/loading.gif',
		fileBottomNavCloseImage : '/static/closelabel.gif',
		overlayOpacity : 0.8,
		borderSize : 10,
		imageArray : new Array,
		activeImage : null,
		inprogress : false,
		resizeSpeed : 350,
		widthCurrent: 250,
		heightCurrent: 250,
		xScale : 1,
		yScale : 1,
		displayTitle: true,
		navbarOnTop: false,
		slideNavBar: false, // slide nav bar up/down between image resizing transitions
		navBarSlideSpeed: 350,
		displayHelp: false,
		strings : {
			help: ' \u2190 / P - previous image\u00a0\u00a0\u00a0\u00a0\u2192 / N - next image\u00a0\u00a0\u00a0\u00a0ESC / X - close image gallery',
			prevLinkTitle: 'previous image',
			nextLinkTitle: 'next image',
			prevLinkText:  '&laquo; Previous',
			nextLinkText:  'Next &raquo;',
			closeTitle: 'close image gallery',
			image: 'Image ',
			of: ' of '
		},
		fitToScreen: false,		// resize images if they are bigger than window
        disableNavbarLinks: false,
        loopImages: false,
        imageClickClose: true,
        jsonData: null,
        jsonDataParser: null
	};
	
})(jQuery);
/** END jquery lightbox */

