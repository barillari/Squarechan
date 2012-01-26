""" urls

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
from django.conf.urls.defaults import *
from settings import STATICFILES_ROOT
from thesite.theapp.views import mainpage, post_ajax, get_latest_ajax, \
    conventional_post, get_one_thread, get_single_post_ajax, \
    validator_test_page, goto_non_mobile, goto_mobile, post_android, post_comment, \
    delete_post, fail

from thesite.theapp.mobile_views import mobile_map, mobile_select_address, \
    mobile_mainpage, mobile_get_one_thread, mobile_first, mobile_prefs

from thesite.theapp.admin_views import modlist, censor

# Uncomment the next two lines to enable the admin:
# from django.contrib import admin
# admin.autodiscover()

urlpatterns = patterns('',
    # Example:
#    (r'^/', include('thesite.theapp.urls')),

(r'^static/(?P<path>.*)$', 'django.views.static.serve',
        {'document_root': STATICFILES_ROOT}),

    # Uncomment the admin/doc line below and add 'django.contrib.admindocs' 
    # to INSTALLED_APPS to enable admin documentation:
    # (r'^admin/doc/', include('django.contrib.admindocs.urls')),

    # Uncomment the next line to enable the admin:
    # (r'^admin/', include(admin.site.urls)),


  ('^validator-test-page/?$', validator_test_page),

  ('^post-comment/?$', post_comment),
  ('^fail/?$', fail),
  ('^delete-post/?$', delete_post),

  ('^mod/list/?$', modlist),

  ('^mod/censor/?$', censor),

  ('^post-ajax/?$', post_ajax),
  ('^post-android/?$', post_android),
  ('^conventional-post/?$', conventional_post),
  ('^latest-ajax/?$', get_latest_ajax),

  ('^single-post-ajax/?$', get_single_post_ajax),

  ('^goto-mobile/?$', goto_mobile),
  ('^goto-non-mobile/?$', goto_non_mobile),

  ('^m/map/?$', mobile_map),
  ('^m/select-address/?$', mobile_select_address),
  ('^m/?$', mobile_mainpage),
  ('^m/first/?$', mobile_first),
  ('^m/prefs/?$', mobile_prefs),
  ('^m/thread/([0-9]+)/?$', mobile_get_one_thread),

  ('^thread/([0-9]+)/?$', get_one_thread),

   ('^/?$', mainpage)
)

