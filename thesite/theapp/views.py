""" views

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
import constants as c
import forms
import urlparse
import json
import S3
import re
import urllib
from thesite import settings
from django.core.mail import send_mail
import StringIO
from subprocess import Popen, PIPE
from hashlib import sha1
from base64 import urlsafe_b64encode
from util import default_int, string_to_location, make_random_token, \
    nicely_formatted_reldist, get_current_rounding, get_current_radius
from django.http import Http404, HttpResponse, HttpResponseRedirect
from models import Post, Picture, SiteComment
import time
from datetime import datetime
from django.shortcuts import render_to_response
#from django.template.context import RequestContext
from django.views.decorators.csrf import csrf_protect, csrf_exempt
from django.core.context_processors import csrf
from detect_mobile_browser import is_mobile_user_agent

WEB = 0
MOBILE_WEB = 1
ANDROID = 2

# N.B.: django_header = "HTTP_"+scala_header.upper().replace("-","_")
REPLY_TO_HTTP_HEADER = "HTTP_X_SQ_REPLYTO"
LOCATION_HTTP_HEADER = "HTTP_X_SQ_LOCATION"
HAS_IMAGE_HTTP_HEADER = "HTTP_X_SQ_HAS_IMAGE"
CONTENT_HTTP_HEADER = "HTTP_X_SQ_CONTENT"
OWNER_TOKEN_HTTP_HEADER = "HTTP_X_SQ_OWNER_TOKEN"
POST_DELETED = "[Post deleted by poster.]"
DELETED_POST_PIC_URL = "http://squarechan.com/static/deleted-thumb.jpg"
OWNER_TOKEN_COOKIE = "ot"
YES = "yes"



MOBILE_OVERRIDE = "mobile-override"
MOBILE = "mobile"
NON_MOBILE = "non-mobile"

THREADRE = re.compile("/(m/)?thread/([0-9]+)/?")

MAXFILESIZE = 1024 * 1024 * 16 # bytes. (this is absurd, but some
                               # people have ridiculous cameras...)\
MAX_POST_LEN = 1024



SUFFIX_TO_CTYPE = { 'jpeg': 'image/jpeg',
                    'png': 'image/png',
                    'gif': 'image/gif' }



SHOW_REPLY_COUNT = 3

DEFAULT_POST_COUNT = 25

JPEG_QUALITY = 50

PNG_QUALITY = 100

S3_TRIES = 3

PNG_HEADER = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A"
GIF89A_HEADER = "GIF89a"
GIF87A_HEADER = "GIF87a"
JPEG_HEADER = "\xff\xd8"

JPEG_SUFFIX = "jpeg"

THUMB_WIDTH = 100
THUMB_HEIGHT = 100

SLIDE_WIDTH = 640
SLIDE_HEIGHT = 480


@csrf_exempt
def post_comment(request):
    """ post a comment """
    comment = request.POST.get('comment', None)
    if not comment:
        return HttpResponse("fail")
    cobj = SiteComment(comment=comment)
    cobj.save()
    send_mail('Squarechan comment', comment, 'comments@squarechan.com',
              ['comments@squarechan.com'], fail_silently=False)
    return HttpResponse("ok")





def get_current_loc(request):
    """ get the current location object from the cookie or the
    request, latter overriding former """
    loc = string_to_location(request.COOKIES.get('loc', None))
    rloc = string_to_location(request.REQUEST.get('loc', None))
    if rloc: # request overrides cookie
        return rloc
    # header overrides cookie but not request
    hloc = string_to_location(request.META.get(LOCATION_HTTP_HEADER, None))
    if hloc:
        return hloc
    return loc

@csrf_protect
def conventional_post(request):
    """ handle a conventional post (used to handle a file upload)
        -> take the post and redirect the user to the main page
    """

    postform = forms.PostForm(request.POST, request.FILES)
    if not postform.is_valid():
        # FIXME: this is incredibly user-unfriendly, but all errors
        # should be caught by the javascript before we get
        # here. anything that gets this far is a serious problem with
        # the site, not a user-input error.
        return HttpResponse(content="Error: %s. Please click the back"
                            " button to correct it." % postform.errors)

    # this is also bad, but it's the consequence of the redirect to
    # /conventional-post. we could do a second redirect back to / and
    # save the form state so we could show the user an error message,
    # but, quite frankly, I think errors of this sort will be rare. 
    if request.FILES['picture_file'].size > MAXFILESIZE:
        return HttpResponse(content=("Error: file too large (%d bytes)."\
                                     " Maximum permitted is %d bytes. "\
                                     "Please click the back button "
                                     "and submit a smaller file.")\
                                % (request.FILES['picture_file'].size, 
                                   MAXFILESIZE))

    picture = save_image(request.FILES['picture_file'])
    if not picture:
        return HttpResponse(content=("Error: could not save picture. "
                                     "It might be invalid. "
                                     " Please click the back button and"
                                     " submit a different image."))
    

    owner_token = get_owner_token(request)
    # FIXME; we shouldn't _require_ this
    # if not owner_token:
    #     return HttpResponse(content=("Post error: owner_token missing."
    #                                  " Are cookies disabled? "
    #                                  "Try enabling cookies, reloading the page,"
    #                                  " and reposting."))
        


    reply_to = default_int(request.POST.get('reply_to', None), -1)
    reply_to = reply_to if reply_to and reply_to != -1 else None
    if reply_to != None:
        found = Post.objects.filter(id=reply_to, censored=False)
        if len(found) == 1:
            reply_to = found[0]
        else:
            # FIXME: log these
            return HttpResponse(content=("Internal error: invalid reply_to id."
                                         " Try pressing the back button,"
                                         " reloading the page, and reposting."))

    newpost = make_real_post(request, owner_token,
                   get_current_loc(request), 
                   request.POST.get('content'), 
                   get_current_rounding(request),
                   reply_to,
                   picture = picture, 
                   source=MOBILE_WEB if use_mobile(request) else WEB)
    response = HttpResponse(content="Post successful! Redirecting...", 
                            status=303)

    # note that in_thread_view means *single* thread view.
    in_thread_view = request.REQUEST.get('in_thread_view', None)=="1"

    response["Location"] = ("/m/" if use_mobile(request) else "/") \
        + (("thread/"+str(reply_to.id)) if in_thread_view else "") \
        + "#postchildbox_" + str(newpost.id)
    return response
    
def s3_put(key, data, ctype, is_public):
    """ upload something to s3 """
    tries = S3_TRIES
    headers = { 'x-amz-storage-class': 'REDUCED_REDUNDANCY', 
                'content-type': ctype,
                'cache-control': 'max-age=31536000' }
    if is_public:
        headers['x-amz-acl'] = 'public-read'
    while tries > 0:
        tries = tries - 1
        conn = S3.AWSAuthConnection(settings.AWS_ACCESS_KEY_ID, 
                                    settings.AWS_SECRET_KEY)
        # don't bother checking; it will never change.
        response = conn.put(\
            settings.BUCKETNAME, key, data,  
            headers = headers) 
        if response.http_response.status == 200:
            return True
        time.sleep(0.5)
    raise Exception("S3 upload FAIL: %d\n%s" \
                        % (response.http_response.status, 
                           response.read()))


def save_image(dfile):
    """ given a django file handle, save the image """
    data = dfile.read()
    name = urlsafe_b64encode(sha1(data).digest())
    suffix = None
    if data[0:8] == PNG_HEADER:
        suffix = 'png'
    elif data[0:2] == JPEG_HEADER:
        suffix = JPEG_SUFFIX
    elif data[0:6] == GIF89A_HEADER or data[0:6] == GIF87A_HEADER:
        suffix = 'gif'
    else:
        suffix = JPEG_SUFFIX # we'll just force-convert it to a jpeg
                        # (assuming imagemagick can read it)

    # fixme: in the future, thread this
    # keep everything in pipes to save disk hits
    s3_put("%s-%s.%s" % (name, 'original', suffix), data, 
           SUFFIX_TO_CTYPE[suffix], False)
    pipe = Popen(["convert", 
                  "-auto-orient",
                  "-quality",
                  str(JPEG_QUALITY if suffix==JPEG_SUFFIX else PNG_QUALITY), 
                  '-resize',
                  "%dx%d>" % (SLIDE_WIDTH, SLIDE_HEIGHT), "-", 
                  "%s:-" % suffix], 
                 stdout=PIPE, stdin=PIPE)
    slide = pipe.communicate(data)[0]
    if pipe.returncode != 0:
        return False
    s3_put("%s-%s.%s" % (name, 'slide', suffix), slide, 
           SUFFIX_TO_CTYPE[suffix], True)
    pipe = Popen(["convert", 
                  "-auto-orient",
                  "-quality", 
                  str(JPEG_QUALITY if suffix==JPEG_SUFFIX else PNG_QUALITY), 
                  '-resize',
                  "%dx%d>" % (THUMB_WIDTH, THUMB_HEIGHT), "-[0]", 
                  "%s:-" % suffix], 
               stdout=PIPE, stdin=PIPE)
    thumb = pipe.communicate(slide)[0]
    if pipe.returncode != 0:
        return False
    s3_put("%s-%s.%s" % (name, 'thumb', suffix), thumb, 
           SUFFIX_TO_CTYPE[suffix], True)
    
    pic = Picture(name=name, suffix=suffix)
    pic.save()
    return pic

def get_owner_token(request, cookie_only=False):
    """ get the owner_token as a req param, failing that, as a cookie """
    rot = request.REQUEST.get('owner_token', None)
    if not rot or cookie_only:
        rot = request.COOKIES.get('owner_token', None)
    return rot
    

def get_basic_context(request, is_mobile = False):
    """ get the context with various standard stuff preinitialized """
    ctx = {}
    ctx['REMOTE_ADDR'] = request.META['REMOTE_ADDR']
    ctx['remote_owner_token'] = get_owner_token(request)
    ctx['DEV_MODE'] = True
    next_after_accept = request.REQUEST.get('next_after_accept', None)
    if next_after_accept and re.match(r"/thread/[0-9]+", next_after_accept):
        ctx['next_after_accept'] = next_after_accept
    ctx['in_thread_view'] = request.REQUEST.get('in_thread_view', None)=="1"
    ctx['requestpath'] = request.path
    loc = get_current_loc(request)
    ctx['loc'] = loc
    ctx['is_mobile'] = is_mobile
    ctx['radius_options'] = c.RADIUS_MENU
    ctx['more_available'] = False
    if loc:
        ctx['loc_fixed'] = "%0.5f,%0.5f" % (loc.latitude, loc.longitude)
    ctx['MAX_POST_LEN'] = MAX_POST_LEN
    ctx['SHOW_REPLY_COUNT'] = SHOW_REPLY_COUNT
    ctx['rounding_val'] = get_current_rounding(request)
    ctx['radius'] = get_current_radius(request)
    ctx['nice_radius'] = c.RADII_TEXT[c.RADII.index(ctx['radius'])]
    ctx['round_options'] = c.ROUND_OPTIONS
    ctx['THUMB_WIDTH'] = THUMB_WIDTH
    ctx['THUMB_HEIGHT'] = THUMB_HEIGHT
    ctx['SLIDE_WIDTH '] = SLIDE_WIDTH 
    ctx['SLIDE_HEIGHT'] = SLIDE_HEIGHT
    ctx['sitename'] = settings.SITENAME
    ctx['tagline'] = 'Foursquare meets 4chan'
    ctx.update(csrf(request))
    return ctx

def update_onepost_context(ctx, postid, define_single_post = False, 
                           truncate = 0):
    """ update the ctx with the values needed to render a
    single-thread page """
    ctx['SHOW_REPLY_COUNT'] = 0
    found = Post.objects.filter(id=postid, censored=False)
    assert len(found) == 1
    ctx['onepostmode'] = True
    ctx['onepostmode_and_is_mobile'] = ctx['is_mobile']
    ctx['posts'] = found
    if define_single_post and found:
        ctx['threadmap'] = build_thread_map(ctx['posts'], truncate)
        ctx['post'] = found[0]
    else:
        ctx['threadmap'] = build_thread_map(ctx['posts'])

def get_one_thread(request, postid):
    """ get the page for just one thread """
    ctx = get_basic_context(request)
    ctx['in_thread_view'] = True
    if not ctx.get('loc', None):
        return HttpResponseRedirect('/?next_after_accept=/thread/'+postid)
    update_onepost_context(ctx, postid)
    if not ctx['posts']:
        return Http404("no such post")
    return render_with_cookie(request, ctx, 'latest.html')
    
def render_with_cookie(request, ctx, templatepath):
    """ render the response and set the location cookie from the
    POST/GET (if there was one), otherwise refresh the cookie we
    received, or, if we don't have a location, don't set the location cookie.
    also set the ot (owner_token) cookie if there isn't one
    """
    resp = render_to_response(templatepath, ctx)
    if ctx.get('loc', None): 
        resp.set_cookie('loc', str(ctx['loc']))
    
    if not get_owner_token(request, cookie_only=True):
        
        resp.set_cookie("owner_token",
                        value = make_random_token(ctx['REMOTE_ADDR']),
                        max_age = c.OWNER_TOKEN_EXPIRES,
                        expires = c.OWNER_TOKEN_EXPIRES)
    return resp

def validator_test_page(request):
    """ hack to make the validator see some real content """
    request.COOKIES['loc'] = '42.701188800,-73.150268400'
    return mainpage(request)

def add_latest_to_context(request, ctx):
    """ add the data needed to render the main page or mobile main
    page to the context """
    ctx['posts'] = get_latest(ctx['loc'], radius=get_current_radius(request))
    ctx['threadmap'] = build_thread_map(ctx['posts'], 
                                        truncate=SHOW_REPLY_COUNT)
    # fencepost error here: if we have exactly 50 posts,
    # this will say there are more, even though there aren't.
    ctx['more_available'] = len(ctx['posts']) >= DEFAULT_POST_COUNT


def mainpage(request):
    """
    top page of the application
    """
    ctx = get_basic_context(request)
    if use_mobile(request):
        return goto_mobile(request)
        

    postform = forms.PostForm(request.POST)
    ctx['postform'] = postform
    if ctx.get('loc', None):
        add_latest_to_context(request, ctx)
    return render_with_cookie(request, ctx, 'latest.html')

def get_latest(loc, radius=c.DEFAULT_RADIUS, hard_cutoff=False, 
               num=DEFAULT_POST_COUNT, skiplist=None, 
               includelist = None, 
               only_these_postids = False):
    """
    find the latest num posts within radius of location. 
    NOTE: radius is not a real radius; it's 1/2 the side of a square.
    """
    assert includelist or not only_these_postids
    out = []
    zone, locx, locy = loc.utm()
    includelist = includelist if includelist else []
    skiplist = skiplist if skiplist else []

    if only_these_postids:
        out = Post.objects.filter(censored=False, \
                                      pk__in = set(includelist)-set(skiplist))\
                                      .order_by('-latest_update')
    else:
        includesql = skipsql = ""
        if skiplist:
            skipsql = " AND id NOT IN (%s)" % ",".join(["%s"] * len(skiplist))
        if includelist:
            includesql = " OR id IN (%s)" % ",".join(["%s"] * len(includelist))
        out = [x for x in Post.objects.raw(\
            "SELECT * FROM theapp_post WHERE censored = 0 AND ((utm_zone = %s"
            " AND (utm_x_rounded BETWEEN %s AND %s) "
            " AND (utm_y_rounded BETWEEN %s AND %s) "
            " AND reply_to_id IS NULL) " + includesql +  ")" + skipsql + 
            " ORDER BY latest_update DESC LIMIT %s",
            [zone, locx-radius, locx+radius, locy-radius, 
                 locy+radius] + includelist + skiplist + [num])]
    return out

class PostThread(object):
    """ holds a thread """
    def __init__(self, root):
        self.root = root
        self.postlist = []
        self.count = 0
        self.hidden = 0

    def add_post(self, post):
        """ add a post to the list """
        self.postlist.append(post)
        self.count += 1

    def truncate(self, limit):
        """truncate the posts shown to limit & update the 'hidden'
        counter accordingly"""
        if self.count > limit:
            self.hidden = self.count - limit
            self.postlist = self.postlist[-limit:]
    

def build_thread_map(posts, truncate=0):
    """
    we could let Django handle this, but I would really rather cut
    down on the number of DB queries. This does the following:
    given a list of posts, fetch _all_ sub-posts
    - build a mapping of ids (from the original posts) to PostThread objects, which for now just contain:
    .postlist -> a list of posts in chronological order
    """
    threadmap = {}
    for post in posts:
        threadmap[post.id] = PostThread(post)
    children = Post.objects.filter(censored = False, \
        reply_to__in = [post.id for post in posts]).\
        order_by('created')
    for child in children:
        threadmap[child.reply_to.id].add_post(child)
    if truncate:
        for key in threadmap.keys():
            threadmap[key].truncate(truncate)
    return threadmap

def total_milliseconds(dto):
    """ datetime to milliseconds since epoch """
    return long(time.mktime(dto.utctimetuple()) * 1000L)

def dictify_post(post, remote_owner_token):
    """ turn a post into a dictionary suitable for json serialization
    """
    thedict =  {'id': post.id,
                'gridx': post.utm_x_rounded,
                'gridy': post.utm_y_rounded,
                'utm_zone': post.utm_zone,
                'rounding': post.rounding,
                'created': total_milliseconds(post.created),
                'latest_update': total_milliseconds(post.latest_update)}
    if post.picture:
        if post.deleted:
            thedict['picture_url'] = DELETED_POST_PIC_URL
        else:
            thedict['picture_url'] = post.picture.get_url('*')
    if post.content:
        thedict['content'] = post.content if not post.deleted else POST_DELETED
    if post.deleted:
        thedict['deleted'] = True
        thedict['utm_zone'] = thedict['gridy'] = thedict['gridx'] = 0
    else:
        if post.isDeletableBy(remote_owner_token):
            thedict['deletable'] = True
    return thedict

def serialize_posts_to_list(posts, threadmap, remote_owner_token = None):
    """ convert posts to json """
    out = []
    for post in posts:
        parent = dictify_post(post, remote_owner_token)
        parent['hidden'] = threadmap[post.id].hidden
        parent['children'] = []
        parent['hidden'] = threadmap[post.id].hidden
        for child in (threadmap[post.id].postlist \
                          if threadmap.has_key(post.id) else []):
            parent['children'].append(dictify_post(child, remote_owner_token))
        out.append(parent)
    return out

def get_latest_ajax(request):
    """ get the latest via ajax """
    ctx = get_basic_context(request)
    if str(request.REQUEST.get("is_mobile", "0")) == "1":
        ctx['is_mobile'] = True # force it to be true
    skip = [default_int(item, None) for item in \
                request.REQUEST.get("skip", "").split(",")]

    postids = [default_int(item, None) for item in \
                   request.REQUEST.get("postids", "").split(",")]

    use_only_these = not not request.REQUEST.get("only_these_postids", False)
                   
    skip = [item for item in skip if item]
    # fixme: check loc cookie!
    # FIXME: radius!!
    ctx['posts'] = get_latest(ctx['loc'], 
                              radius=get_current_radius(request),
                              skiplist = skip, includelist=postids, 
                              only_these_postids = use_only_these)

    if request.REQUEST.get('return_empty_if_none', False) and not ctx['posts']:
        return HttpResponse(status=204, content="")
    
    truncate_to = 0 if request.REQUEST.get('show_all_replies', False) \
        else SHOW_REPLY_COUNT 

    ctx['threadmap'] = build_thread_map(ctx['posts'], truncate=truncate_to)
    if request.REQUEST.get('json', False):
        # note that remote_owner_token is *ignored* in the multipost view
        remote_owner_token = get_owner_token(request)
        return HttpResponse(\
            wrap_json_posts(ctx['loc'], serialize_posts_to_list(\
                    ctx['posts'], ctx['threadmap'], remote_owner_token)))
    else:
        return render_to_response('posts_div_contents.html', ctx)

# b/c we get the ot from a POST param rather than a cookie, we need
# not worry about csrf.
@csrf_exempt 
def delete_post(request):
    """ delete a post """
    postid = request.REQUEST.get('postid', None)
    if not postid:
        return HttpResponse(content="No post id supplied.", status=400)
    found = Post.objects.get(id=postid, censored=False)
    if not found:
        # will throw a DoesNotExist, actually
        return Http404("No such post found.")
    remote_ot = request.POST.get('owner_token', None)
    if not remote_ot:
        return HttpResponse(content="No owner_token supplied.", status=400)
    if found.isDeletableBy(remote_ot):
        found.deleted = True
        found.latest_update = datetime.now()
        found.save()
        if (found.reply_to):
            found.reply_to.latest_update = found.latest_update
            found.reply_to.save()
        return HttpResponse("ok")
    else:
        return HttpResponse("Squarechan could not confirm that you created that post."
                            " (Perhaps your cookies were deleted.) Post not deleted.")


def wrap_json_posts(loc, postlist):
    """ dump a json object with the posts and metadata """
    utmz, utmx, utmy = loc.utm()
    return json.dumps({'posts': postlist,
                       'meta': {
                'current_x': float(utmx),
                'current_y': float(utmy),
                'current_zone': utmz,
                'current_time': total_milliseconds(datetime.now())}})


def get_single_post_ajax(request):
    """ get just the postbox for a single post """
    ctx = get_basic_context(request)
    assert ctx['loc']
    postid = request.REQUEST.get('postid', None)
    if str(request.REQUEST.get("is_mobile", "0")) == "1":
        ctx['is_mobile'] = True # force it to be true
    truncate = default_int(request.REQUEST.get('truncate', 0), 0)
    if not postid:
        return Http404("No post id supplied.")
#    update_onepost_context(ctx, postid, define_single_post = True, 
#                           truncate = truncate)
    found = Post.objects.filter(id=postid, censored=False)
    assert len(found) == 1
    ctx['posts'] = found
    ctx['onepostmode'] = True
    ctx['post'] = found[0]
    ctx['threadmap'] = build_thread_map(ctx['posts'], truncate)
    if ctx['posts']:
        if request.REQUEST.get('json', False):
            return HttpResponse(wrap_json_posts(ctx['loc'], \
            serialize_posts_to_list(ctx['posts'], ctx['threadmap'], 
                                    get_owner_token(request))))
        else:
            return render_to_response('postbox.html', ctx)
    else:
        return Http404("No post with that id.")

@csrf_exempt
def post_android(request):
    """
    post from an android phone. thanks to some omissions on the
    platform (like an easy way to serialize up a MIME document)*, we
    do this a little differently: the params are HTTP headers and the
    image (if there is one) is the request body.
    * there probably is one; I didn't look too hard
    """
    #ctx = get_basic_context(request, is_mobile = True)
    reply_to = default_int(request.META.get(REPLY_TO_HTTP_HEADER, None), -1)
    has_image = request.META.get(HAS_IMAGE_HTTP_HEADER, None) == YES
    raw_content = request.META.get(CONTENT_HTTP_HEADER, None)
    owner_token = request.META.get(OWNER_TOKEN_HTTP_HEADER, None)
    #assert owner_token # FIXME: don't require this ; some ppl have older clients
    content = urllib.unquote(raw_content.replace("+"," "))\
        .decode('utf8', 'replace') if raw_content else None
    # FIXME: in the dev version of django, we can use a file-like
    # iface and skip the stringio
    imagedata = request.raw_post_data
    image = None
    if imagedata:
        image = save_image(StringIO.StringIO(imagedata))
    assert image or not has_image
    reply_to_post = Post.objects.get(id=reply_to, censored=False) \
        if reply_to != -1 else None

    newpost = make_real_post(request, owner_token, 
                             get_current_loc(request), 
                             content,
                             get_current_rounding(request),
                             reply_to_post,
                             picture = image, source=ANDROID)
    return HttpResponse(content=str(newpost.id), status=200)


# def raw_extract(ustring):
#     """ this is a HACK to decode a unicode string containing
#     utf-8 to plain old unicode
#     FIXME FIXME FIXME: this is *clearly* not the right way to do this.
#     """
#     out = []
#     for byte in ustring:
#         out.append(chr(ord(byte)))
#     return ("".join(out)).decode('utf-8')

def post_ajax(request):
    """
    actually make a post via ajax. note that this kind of post
    can't have an image unless it's uploaded separately.
    """
    parsedpost = urlparse.parse_qs(str(request.POST['form']))
    # FIXME: is the csrf stuff needed?
    csrftoken = request.COOKIES.get('csrftoken', None)
    if not csrftoken:
        return HttpResponse(json.dumps(['fail', 'csrf token missing']))
    if csrftoken != parsedpost.get('csrfmiddlewaretoken', [None])[0]:
        return HttpResponse(json.dumps(\
                ['fail', 'csrf token mismatch %s %s' \
                     % (csrftoken, parsedpost['csrfmiddlewaretoken'])]))
    owner_token = parsedpost.get('owner_token', [None])[0]
    if not owner_token:
        owner_token = get_owner_token(request, cookie_only=True)
    #if not owner_token: # fixme: shouldn not require this...
    #    return HttpResponse(json.dumps(['fail', 'owner_token missing']))

    #rounding = default_int(parsedpost.get('round', [None])[0], c.DEFAULT_ROUNDING)
    # FIXME: if rounding-editing is re-enabled, we should use the above not this
    rounding = get_current_rounding(request)

    loc = string_to_location(parsedpost.get('loc', [None])[0])
    if not loc:
        return HttpResponse(json.dumps(['fail', 'location invalid']))
    content = parsedpost.get('content', [None])[0]
    if not content:
        return HttpResponse(json.dumps(['fail', 'post was blank']))
    if len(content) > MAX_POST_LEN:
        return HttpResponse(json.dumps(['fail', 
                   'post must be under %d characters' % MAX_POST_LEN]))
#    content = raw_extract(content)
    content = content.decode('utf-8', 'ignore')
    antidupetoken = parsedpost.get('antidupetoken', None)
    if not antidupetoken:
        return HttpResponse(json.dumps(['fail', 'antidupetoken missing']))
    if antidupetoken == request.session.get('antidupetoken',''):
        return HttpResponse(json.dumps(['fail', 'duplicate post ignored']))
    reply_to = default_int(parsedpost.get('reply_to', [None])[0], -1)
    reply_to = reply_to if reply_to and reply_to != -1 else None
    if reply_to == None:
        return HttpResponse(json.dumps(['fail', 'All thread-creating posts'
                                        ' must contain an image.']))
    else:
        found = Post.objects.filter(id=reply_to, censored=False)
        if len(found) == 1:
            reply_to = found[0]
        else:
            return HttpResponse(\
                json.dumps(['fail', 
                            'internal error: invalid reply_to id.']))
    make_real_post(request, owner_token, loc, content, 
                   rounding, reply_to, 
                   source=MOBILE_WEB if use_mobile(request) else WEB)
    request.session['antidupetoken'] = antidupetoken
    return HttpResponse(json.dumps(['ok', '']))


def make_real_post(request, owner_token, loc, content, rounding, 
                   reply_to, picture=None, source=None):
    """ actually create and save the post """
    assert reply_to or picture
    post = Post(latitude=loc.latitude, longitude=loc.longitude, 
                content = content, rounding = rounding, 
                reply_to = reply_to,
                picture = picture,
                ipaddr = request.META['REMOTE_ADDR'])
    if owner_token:
        post.owner_token = owner_token
    if source != None:
        post.source = source
    post.save()
    if reply_to:
        reply_to.latest_update = post.created
        reply_to.save()
    return post

def goto_mobile(request):
    """ set a cookie to force the mobile site """
    return override_mobile_guess(request, True)

def goto_non_mobile(request):
    """ set a cookie to force the non-mobile site """
    return override_mobile_guess(request, False)

def override_mobile_guess(request, mobile):
    """ set a cookie to override the mobile device detection """
    path = request.GET.get('path', '')
    resp = None
    # FIXME: HAAAACK
    if THREADRE.match(path):
        resp = HttpResponseRedirect(\
            ('/m' if mobile else '')+"/thread/"+THREADRE.match(path).group(2))
    else:
        resp = HttpResponseRedirect('/m/' if mobile else '/')
    resp.set_cookie(MOBILE_OVERRIDE, MOBILE if mobile else NON_MOBILE)
    return resp


def use_mobile(request):
    """ True if we should use the mobile site, False otherwise """
    mover = request.COOKIES.get(MOBILE_OVERRIDE, None)
    if mover == MOBILE:
        return True
    elif mover == NON_MOBILE:
        return False
    return is_mobile_user_agent(request.META.get('HTTP_USER_AGENT', ''))



def fail(request):
    assert 0
