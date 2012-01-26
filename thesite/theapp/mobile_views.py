""" mobile views.

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
 """
from django.views.decorators.csrf import csrf_protect, csrf_exempt
from django.http import HttpResponse
from thesite.theapp.views import get_basic_context, \
    add_latest_to_context, render_with_cookie, update_onepost_context
from django.shortcuts import render_to_response
from thesite.theapp.forms import PostForm
from django.http import HttpResponseRedirect
import urllib
import constants as c
import re
from util import get_current_rounding, get_current_radius


def mobile_map(request):
    ''' get the map page. note that all the smarts are in js '''
    return render_to_response('mobile/map.html', 
                              get_basic_context(request, True))


def mobile_select_address(request):
    ''' get the select-address page. note that all the smarts are in js '''
    ctx = get_basic_context(request, True)
    import_next_into_context(request, ctx)
    return render_to_response('mobile/selectaddress.html', ctx)

@csrf_exempt
def mobile_prefs(request):
    ''' get the prefs page. note that all the smarts are in js '''
    if request.POST.get("set", None) == "1":
        resp = HttpResponse(content="Preferences set! Redirecting...", 
                            status=303)
        resp["Location"] = "/m/" 
        #resp.set_cookie('round', get_current_rounding(request),
        #                max_age = c.OWNER_TOKEN_EXPIRES,
        #                expires = c.OWNER_TOKEN_EXPIRES)
        resp.set_cookie('radius', get_current_radius(request),
                        max_age = c.OWNER_TOKEN_EXPIRES,
                        expires = c.OWNER_TOKEN_EXPIRES)
        return resp

    ctx = get_basic_context(request, True)
    return render_to_response('mobile/prefs.html', ctx)

                              

def mobile_mainpage(request):
    ''' get the start page (if loc is unset) or the main page '''
    ctx = get_basic_context(request, True)

    ctx['postform'] = PostForm(request.POST, request.FILES)
    if request.GET.get('force_change', None) or not ctx.get('loc', None):
        # redirect to first.html if loc is unset
        return HttpResponseRedirect("/m/first")
        #return render_to_response('mobile/first.html', ctx)
    add_latest_to_context(request, ctx)
    #ctx['postboxtemplate'] = 'mobile/mobile_postbox.html'
    return render_with_cookie(request, ctx, 'mobile/main.html')

def import_next_into_context(request, ctx):
    """ pull the 'next' param into the context dict """
    ctx['next'] = request.GET.get('next', '')
    if not re.compile("^[a-z0-9/-]+$").match(ctx['next']):
        ctx['next'] = '' # prevent redirect-hijacking

def mobile_first(request):
    """ show the first page of the app """
    ctx = get_basic_context(request, True)
    import_next_into_context(request, ctx)
    return render_to_response('mobile/first.html', ctx)

def mobile_get_one_thread(request, postid):
    '''' get a single thread page '''
    ctx = get_basic_context(request, True)
    ctx['in_thread_view'] = True
    if not ctx.get('loc', None):
        return HttpResponseRedirect("/m/first?next=%s" % urllib.quote(request.path))
    update_onepost_context(ctx, postid)
    return render_with_cookie(request, ctx, 'mobile/main.html')

