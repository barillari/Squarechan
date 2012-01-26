""" admin views.

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
import json
from thesite import settings
from django.core.paginator import Paginator, InvalidPage, EmptyPage
from django.shortcuts import render_to_response
from django.http import HttpResponse
import urllib
import re
from models import Post, Picture, SiteComment

def modlist(request): 
    " list posts for mod "
    assert (settings.DEBUG and request.META['REMOTE_ADDR'] == "127.0.0.1") or request.META['REMOTE_USER'] == "admin"
    posts = Post.objects.all().order_by('-created')
    paginator = Paginator(posts, 100)

    try:
        page = int(request.GET.get('page', '1'))
    except ValueError:
        page = 1

    try:
        plist = paginator.page(page)
    except (EmptyPage, InvalidPage):
        plist = paginator.page(paginator.num_pages)

    return render_to_response('modlist.html', {"plist": plist})


def censor(request):
    assert (settings.DEBUG and request.META['REMOTE_ADDR'] == "127.0.0.1") or request.META['REMOTE_USER'] == "admin"
    pid = request.POST['pid']
    post = Post.objects.get(id=pid)
    post.censored = True
    post.save()
    return HttpResponse(json.dumps(["ok", str(post.id)]))
    
