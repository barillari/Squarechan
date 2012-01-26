'''models

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

'''
from django.db import models
import util
from thesite.settings import BUCKETNAME

#class RegisteredUser(models.Model):
#    pass


class SiteComment(models.Model):
    comment = models.TextField()

class Picture(models.Model):
    ''' holds data about an uploaded image '''
    name = models.TextField() # used to prevent iteration attacks
    suffix = models.TextField()
    # thumb_width = models.IntegerField()
    # thumb_height = models.IntegerField()

    # slide_width = models.IntegerField()
    # slide_height = models.IntegerField()

    # orig_width = models.IntegerField()
    # orig_height = models.IntegerField()

    def get_thumb_url(self):
        return self.get_url('thumb')

    def get_slide_url(self):
        return self.get_url('slide')

    def get_url(self, utype):
        return "http://s3.amazonaws.com/%s/%s-%s.%s" \
            % (BUCKETNAME, self.name, utype, self.suffix)


class Post(models.Model):
    content = models.TextField(blank=True, null=True)
    is_anonymous = models.BooleanField(default=True)
#    creator = models.ForeignKey(RegisteredUser, blank=True, null=True)
    latitude = models.DecimalField(max_digits=12, decimal_places=9)
    longitude = models.DecimalField(max_digits=12, decimal_places=9)
    rounding = models.IntegerField(blank=True, null=True)
    #x location_name - optional, text description (e.g., "NoLita";
    #"your mom's bedroom") <- ?? not now
    picture = models.ForeignKey(Picture, blank=True, null=True)
    reply_email = models.EmailField(blank=True, null=True)
    modify_password_hash = models.CharField(blank=True, max_length=50)
    created = models.DateTimeField(auto_now_add=True)
    ipaddr = models.CharField(blank=True, max_length=15) # used to track spam

    utm_x_rounded = models.IntegerField()
    utm_y_rounded = models.IntegerField()
    utm_zone = models.IntegerField()

    reply_to = models.ForeignKey('self', blank=True, null=True)
    latest_update = models.DateTimeField(auto_now_add=True) # this is the latest time of all replies or sub-replies
    # (sub-replies: include later. for now, replies are just sorted by time.)

    source = models.IntegerField()

    censored = models.BooleanField(default=False)
    deleted = models.BooleanField(default=False)
    owner_token = models.TextField(blank=True, null=True)

    def save(self):
        utmz, utmx, utmy = util.get_utm(self.latitude, self.longitude)
        self.utm_zone = utmz
        if self.rounding:
            utmx = util.round_to_nearest(utmx, self.rounding)
            utmy = util.round_to_nearest(utmy, self.rounding)
        self.utm_x_rounded = utmx
        self.utm_y_rounded = utmy

        super(Post, self).save()

    def isDeletableBy(self, otherOwnerToken):
        return otherOwnerToken and self.owner_token and \
            otherOwnerToken == self.owner_token
