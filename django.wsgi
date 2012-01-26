import os
import sys
sys.path.append(os.path.dirname(__file__))
sys.path.append(os.path.join(os.path.dirname(__file__), "3rdparty/s3python"))
os.environ['DJANGO_SETTINGS_MODULE'] = 'thesite.settings'
import django.core.handlers.wsgi

#class Debugger:
#    def __init__(self, object):
#        self.__object = object
#    def __call__(self, *args, **kwargs):
## for some reason, I can't django to stop redirecting its pdb into error.log, 
## even with MaxClients 1 and starting apache2 with -X. hence rdb.
#        import rdb	       
#        import sys
#        debugger = rdb.Rdb()
#        debugger.reset()
#        sys.settrace(debugger.trace_dispatch)
#        try:
#            return self.__object(*args, **kwargs)
#        finally:
#            debugger.quitting = 1
#            sys.settrace(None)
#
#application = Debugger(django.core.handlers.wsgi.WSGIHandler())


application = django.core.handlers.wsgi.WSGIHandler()

#import local_constants as lc
#if lc.DEVMODE:
#import vdj.monitor
#vdj.monitor.start(interval=1.0)