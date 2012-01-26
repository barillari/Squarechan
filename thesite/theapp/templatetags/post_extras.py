""" templatetags

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

from django import template
from django.template.defaultfilters import truncatewords
register = template.Library()
from thesite.theapp import util


@register.filter
def reldate(post):
    return util.relative_date(post.created)

@register.filter
def reldistance_rounded(post, loc):
    return util.relative_distance_rounded(post, loc)

@register.filter
def slide_url(post):
    return post.picture.get_slide_url() if post.picture else None

@register.filter
def thumb_url(post):
    return post.picture.get_thumb_url() if post.picture else None

MAX_WORDS_WITHOUT_TRUNCATION = 150
MWWT_SLOP = 15 # hysteresis for truncation

@register.filter
def needs_truncation(post):
    if not post or not post.content:
        return False
    tlen = len(truncatewords(post.content, MAX_WORDS_WITHOUT_TRUNCATION))
    return tlen + MWWT_SLOP < len(post.content)


MAXWORDLEN = 33
@register.filter
def shyphenate(text):
    # walk through text, keeping a counter since the last whitespace
    # character. if the counter exceeds MAXWORDLEN, insert a soft
    # hyphen
    out = []
    shy = '-'#u'\u00ad'
    whitespace = ('\t', '\n', '\r', ' ')
    i = 0
    last = 0
    counter = 0
    tlen = len(text)
    while i < tlen:
        if text[i] in whitespace:
            counter = 0
        if counter > MAXWORDLEN:
            out.append(text[last:i])
            out.append(shy)
            last = i
            counter = 0
        i += 1
        counter += 1

    out.append(text[last:i])

    return "".join(out)


@register.filter
def text_field_value(field):
    """ get the string value for a text field, see django bug #10427 """
#    import pdb;pdb.set_trace()
    value = field.form.initial.get(field.name, None)
    return value if value else ''


@register.filter
def get_children(post, threadmap):
    tobj = threadmap.get(post.id, None)
    if tobj:
        return tobj.postlist
    return []

@register.filter
def child_count(post, threadmap):
    return len(get_children(post, threadmap))


@register.filter
def has_children(post, threadmap):
    return child_count(post, threadmap) > 0



@register.filter
def hidden_count(post, threadmap):
    tobj = threadmap.get(post.id, None)
    return tobj.hidden if tobj else 0


@register.filter
def is_deletable(post, remote_ot):
    return post.isDeletableBy(remote_ot)
