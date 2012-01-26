""" utility functions

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
#ERAD = 6371010 # earth radius (m)
ERAD = 6378137 # earth radius (m)
from decimal import Decimal, ROUND_CEILING, ROUND_FLOOR, Context
from decimal import InvalidOperation
import time
import datetime
import random
import math
from hashlib import sha1
from uuid import uuid1, uuid4
from pyproj import Proj, transform
from urllib2 import unquote
from base64 import urlsafe_b64encode
import constants as c

PI = Decimal("3.1415926535897932384626433832795028841971693993751")
from math import sqrt, cos, sin, asin, acos


POINTS_IN_ENGLISH = {'N': "North",
                     'NE': "Northeast",
                     'E': "East",
                     'SE': "Southeast",
                     'S': "South",
                     'SW': "Southwest",
                     'W': "West",
                     'NW': "Northwest"}

class Location(object):
    """ convenience object for holding a location """
    def __init__(self, latitude, longitude):
        self.latitude = latitude
        self.longitude = longitude
        self.__utm = None
    def latrad(self):
        " get lat in rads "
        return (PI/180) * self.latitude
    def longrad(self):
        " get long in rads "
        return (PI/180) * self.longitude
    def utm(self):
        " get get utm z, x, y "
        if not self.__utm: 
            self.__utm = get_utm(self.latitude, self.longitude)
        return self.__utm
    def __str__(self):
        return "%s,%s" % (str(self.latitude), str(self.longitude))
        


def haversine_gcdist_deg(phis, lambdas, phif, lambdaf):
    """ convert args to rads and call haversine_gcdist """
    return haversine_gcdist(phis*(PI/180), lambdas*(PI/180), 
                            phif*(PI/180), lambdaf*(PI/180))

def haversine_gcdist(ps, ls, pf, lf):
    """ given lat, long pairs (ps,ls) and (pf, lf) in rads, compute gc
    dist in meters """
    dsigma = 2 * asin(sqrt((sin((pf-ps)/2))**2 + \
                               cos(ps)*cos(pf)*(sin((lf-ls)/2))**2))
    return dsigma * ERAD

def default_int(string, default):
    """ convert string to int. if it fails, return default """
    try:
        return int(string)
    except:
        return default

def nicely_formatted_reldist(rounding, distm):
    """ return a nicely formated relative distance """
    if rounding and rounding >= distm:
        return "within %d meters" % rounding
    if distm < 10:
        return "within 10 meters" 
    if distm < 1000:
        return "%d meters away" % distm
    return "%0.1f km away" % (distm / 1000)


def relative_angle(post, loc):
    """ get the angle in degrees from loc to post as if loc were the
    origin """
    locz, locx, locy = loc.utm()
    if post.utm_zone != locz:
        return None # can't figure angle
    deltay = post.utm_y_rounded-locy
    deltax = post.utm_x_rounded-locx
    if deltax == 0:
        return 0
    return acos(deltay/deltax) * (180/PI)

def text_relative_angle(angle):
    """ given an angle in degrees (0-360), return N, NW, W... """
    points = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']
    
    #fixme

def relative_distance_rounded(post, loc):
    """ return the relative distance from loc to post, rounded """
    # if they're in different zones, return the euclidean distance
    locz, locx, locy = loc.utm()
    if post.utm_zone != locz:
        # this shouldn't happen in general, because we're only going
        # to look for results in the same zone.
        return relative_distance_gc(post, loc)
    distm = euclidean_dist(locx, locy, post.utm_x_rounded, post.utm_y_rounded)
    return nicely_formatted_reldist(post.rounding, distm)

def relative_distance_gc(post, loc):
    """ return the relative distance from loc to post using the great
    circle method """
    distm = int(haversine_gcdist_deg(loc.latitude, loc.longitude, 
                                     post.latitude, post.longitude))
    return nicely_formatted_reldist(post.rounding, distm)

def relative_date(thedate):
    """ return relative date as a string """
    delta = datetime.datetime.now() - thedate
    secs = delta.days*24*60*60 + delta.seconds
    if secs < 61:
        return "%d second%s ago" % (int(secs), 's' if int(secs) != 1 else "")
    if secs < 60*60:
        return "%d minute%s ago" % (int(secs/60), 
                                    "s" if int(secs/60) != 1 else "")
                                    
    if secs < 60*60*36:
        return "%d hour%s ago" % (int(secs/(60*60)), 
                                  "s" if int(secs/(60*60)) != 1 else "")
                                  
    if secs < 60*60*24*31:
        return "%d day%s ago" % (int(secs/(60*60*24)), 
                                 "s" if int(secs/60*60*24) != 1 else "")
                                 
    return thedate.strftime("on %d %B %Y")


def get_mercator(lat_deg, lng_deg): 
    """ given latitude and longitude (lat, lng) in degrees, get a
    mercator projection (x,y) in meters"""
    lng_0 = -PI # center of the map
    lat = lat_deg * (PI/180)
    lng = lng_deg * (PI/180)
    xco = lng - lng_0
    yco = math.log(math.tan(lat) + (1/math.cos(lat)))
    return xco * ERAD, yco * ERAD

def get_utm(lat, lng):
    """ given a lat,lng pair, returnn a z,x,y (zone,easting,northing) triple """
    # alternative: Proj({'proj':'utm','zone':19})(lng, lat)
    zone = get_utm_zone(lng)
    xco, yco = transform(Proj(proj='latlong', ellps='WGS84'), 
                     Proj(proj='utm', zone=zone, ellps='WGS84'), lng, lat)
    return zone, Decimal(str(xco)), Decimal(str(yco))
    
def get_utm_zone(lng):
    """ get the UTM zone for a given longitude """
    # http://www.uwgb.edu/dutchs/FieldMethods/UTMSystem.htm
    return int(math.ceil((lng+180)/6))

def euclidean_dist(xx1, yy1, xx2, yy2):
    """ get the euclidian distance.  """
    # args doubled to satisfy pylint
    return math.sqrt((xx2-xx1)**2 + (yy1-yy2)**2)



def round_to_nearest(num, roundto):
    """ rounds num to the closest multiple of roundto. e.g., 
    round_to_nearest(8,5) => 10
    round_to_nearest(6,5) => 5
    int -> int -> int
    """
    assert num > 0 and roundto > 0 # for now
    ceil_ctx = Context()
    ceil_ctx.rounding = ROUND_CEILING
    
    num = Decimal(num)
    roundto = Decimal(roundto)
    modval = num % roundto
    if modval > roundto / 2:
        return (num/roundto).quantize(1, rounding=ROUND_CEILING) * roundto
    else:
        return (num/roundto).quantize(1, rounding=ROUND_FLOOR) * roundto


def string_to_location(strval):
    """ make a loc object from a lat,lng string """
    if not strval:
        return None
    try: 
        pair = unquote(strval.strip()).split(",")
        if len(pair) != 2: 
            return None
        latitude = Decimal(pair[0].strip())
        if latitude > 90 or latitude < -90:
            return None
        longitude = Decimal(pair[1].strip())
        if latitude > 180 or latitude < -180:
            return None
        return Location(latitude, longitude)
    except InvalidOperation:
        return None

def make_random_token(saltstr):
    """ make a random string for a token """
    digest = sha1(str(saltstr) + str(uuid1().bytes) + \
                      str(uuid4().bytes) + str(time.time()) + \
                      str(random.random()))
    # strip specials so we don't screw up regex-based splitting in js
    return urlsafe_b64encode(digest.digest()).replace("=","A").\
        replace("-","A").replace("_","A")


def get_current_rounding(request):
    """ get the current rounding/blur radius from the cookie or the
    request, latter overriding formula """
    # rounding = request.COOKIES.get('round', None)
    # rrdg = request.REQUEST.get('round', None)
    # if rrdg:
    #     return default_int(rrdg, c.DEFAULT_ROUNDING)
    # return default_int(rounding, c.DEFAULT_ROUNDING)
    # FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME FIXME 
    # disabling for now to get cleaner UI.
    return c.DEFAULT_ROUNDING
    

def get_current_radius(request):
    """ snaps the radius to the list of acceptable radii """
    rad = get_current_radius_inner(request)
    return rad if rad in c.RADII else c.DEFAULT_RADIUS


def get_current_radius_inner(request):
    """ get the current search radius from the cookie or the
    request, latter overriding formula """
    radiusc = request.COOKIES.get('radius', None)
    radiusr = request.REQUEST.get('radius', None)
    if radiusr:
        return default_int(radiusr, c.DEFAULT_RADIUS)
    return default_int(radiusc, c.DEFAULT_RADIUS)
    
