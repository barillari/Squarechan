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

from django.template import Context, loader
from django.utils.http import int_to_base36
from django.contrib.auth.models import User
from django import forms 
from django.contrib.auth.tokens import default_token_generator
from django.forms.widgets import RadioSelect
from django.db import connection
import datetime
from util import string_to_location



class PostForm(forms.Form):
    """ make a post """
    content = forms.CharField(required=False, widget=forms.Textarea(attrs={'class':'mobile_content_textarea','rows':2}))
    picture_file = forms.FileField(required=False)
#    is_anonymous = forms.BooleanField(initial=True, required=False)
    loc = forms.CharField(widget=forms.HiddenInput)
    rounding = forms.IntegerField(required=False) # in meters
#    reply_email = forms.EmailField(required=False)
#    modify_password = forms.CharField(required=False, widget=forms.PasswordInput)

    replyto =  forms.IntegerField(required=False) # post reply to

    def clean(self):
        " clean "
        rounding = self.cleaned_data.get('rounding', None) 
        if rounding != None and rounding < 0:
            self._errors['rounding'] = self.error_class(["Rounding must be non-negative."])
            raise forms.ValidationError("Rounding radius must be non-negative.")
            
        if not string_to_location(self.cleaned_data.get('loc', None)):
            self._errors['content'] = self.error_class(["Invalid location."])
            raise forms.ValidationError("Location invalid.")
        # FIXME: require pic for OPs?
        if not (self.cleaned_data.get('content', None) \
                    or self.files.get('picture_file', None)):
            self._errors['content'] = \
                self.error_class(["You must supply a picture or some text."])
            raise forms.ValidationError("Please provide a picture or some text.")
        return self.cleaned_data
