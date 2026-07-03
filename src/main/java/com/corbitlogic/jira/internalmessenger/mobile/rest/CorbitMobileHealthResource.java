package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Mobile BFF liveness probe. Mounted under {@code /rest/corbit-mobile/1.0/health}.
 *
 * <p>Additive to the plugin: the mobile client (CorbitChat Mobile, see
 * {@code docs/mobile-roadmap/}) uses a dedicated REST module so its endpoints
 * evolve independently from the web surface at {@code /rest/jim/1.0/*}.</p>
 */
@Path("/health")
@Produces({"application/json"})
public class CorbitMobileHealthResource {

    @GET
    public Response health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("module", "corbit-mobile");
        body.put("apiVersion", "1.0");
        body.put("status", "running");
        // Live plugin (bundle) version so the mobile About screen can surface a
        // client/server runtime mismatch. Read from OSGi so it never drifts.
        body.put("version", pluginVersion());
        return JimRestResponses.okJson(body);
    }

    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";

    private static String pluginVersion() {
        try {
            PluginAccessor accessor = ComponentAccessor.getPluginAccessor();
            if (accessor != null) {
                Plugin plugin = accessor.getPlugin(PLUGIN_KEY);
                if (plugin != null && plugin.getPluginInformation() != null) {
                    String v = plugin.getPluginInformation().getVersion();
                    if (v != null && !v.isEmpty()) {
                        return v;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to "unknown".
        }
        return "unknown";
    }
}
