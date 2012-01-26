# quick sanity check: see how the lat, lng great-circle distance differs from the x-y euclidian distance

"""
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

# (cd squarechan; env DJANGO_SETTINGS_MODULE=settings PYTHONPATH=.:thesite:thesite/theapp python -i thesite/theapp/test.py )

#from django.db import connection
from theapp.models import Post
import util, decimal

def test():
    # cambridge, ma
    myloc_lat = decimal.Decimal('40.77131')
    myloc_lng = decimal.Decimal('-73.95676')

    myz, myx, myy = util.get_utm(myloc_lat, myloc_lng)

    for post in Post.objects.all():
        havdist = util.haversine_gcdist_deg(myloc_lat, myloc_lng, post.latitude, post.longitude)
        z, x, y = util.get_utm(post.latitude, post.longitude)
        if myz != z:
            print "skipping %d; zones differ (%d!=%d)" % (post.id, myz, z)
        else:
            euclidist = util.euclidean_dist(x, y, myx, myy)
            print "%d: %f %f DIFFERENCE FRACTION %f" % (post.id, havdist, euclidist, (abs(havdist-euclidist)/havdist))
    
        post.save() # hack:update the x,y,z!

test()
