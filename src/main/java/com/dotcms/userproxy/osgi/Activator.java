package com.dotcms.userproxy.osgi;

import com.dotcms.filters.interceptor.FilterWebInterceptorProvider;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotcms.filters.interceptor.WebInterceptorDelegate;
import com.dotcms.security.apps.AppSecretSavedEvent;
import com.dotcms.system.event.local.business.LocalSystemEventsAPI;
import com.dotcms.userproxy.interceptor.UserProxyInterceptor;
import com.dotcms.userproxy.listener.UserProxyAppListener;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.filters.InterceptorFilter;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;

import java.io.IOException;

import org.osgi.framework.BundleContext;

public class Activator extends GenericBundleActivator {

    final WebInterceptorDelegate delegate = FilterWebInterceptorProvider.getInstance(Config.CONTEXT).getDelegate(
            InterceptorFilter.class);

    final WebInterceptor interceptor = new UserProxyInterceptor();
    final LocalSystemEventsAPI localSystemEventsAPI = APILocator.getLocalSystemEventsAPI();
    private final UserProxyAppListener appListener = new UserProxyAppListener();

    public void start(final org.osgi.framework.BundleContext context) throws IOException {

        Logger.info(Activator.class.getName(), "Starting UserProxy Plugin");

        delegate.addFirst(interceptor);

        // Adding APP yaml
        Logger.info(Activator.class.getName(), "Copying UserProxy APP");
        new FileMoverUtil().copyAppYml();


        // set up app listener
        Logger.info(Activator.class.getName(), "Starting App Listener");
        localSystemEventsAPI.subscribe(AppSecretSavedEvent.class, appListener);
    }

    @Override
    public void stop(BundleContext context) throws IOException {

        Logger.info(Activator.class.getName(), "Stopping UserProxy Plugin");

        Logger.info(Activator.class.getName(), "Stopping Interceptor");
        delegate.remove(interceptor.getName(), true);

        Logger.info(Activator.class.getName(), "Removing UserProxy App");
        new FileMoverUtil().deleteYml();
        localSystemEventsAPI.unsubscribe(appListener);
    }

}
