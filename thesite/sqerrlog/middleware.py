# based on http://djangosnippets.org/snippets/638/
import syslog
import traceback
import sys
from django import http
class SQErrLog(object):
    def process_exception(self, request, exception):

        if isinstance(exception, http.Http404):
            pass #return self.handle_404(request, exception)
        else:
            return self.handle_500(request, exception)

    # def handle_404(self, request, exception):
    #     if settings.DEBUG:
    #         from django.views import debug
    #         return debug.technical_404_response(request, exception)
    #     else:
    #         callback, param_dict = resolver(request).resolve404()
    #         return callback(request, **param_dict)


    def handle_500(self, request, exception):
        syslog.openlog("SQ")
        tbstr = '\n'.join(traceback.format_exception(*sys.exc_info()))
        syslog.syslog("req: " + repr(request))
        syslog.syslog("exception: " + str(exception))
        syslog.syslog("traceback: " + tbstr)

