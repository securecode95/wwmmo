package au.com.codeka.warworlds.server.handlers.pages;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.warworlds.server.OpenIdAuth;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.RequestHandler;
import net.asfun.jangod.template.TemplateEngine;
import net.asfun.jangod.interpret.InterpretException;
import net.asfun.jangod.interpret.JangodInterpreter;
import net.asfun.jangod.lib.Filter;
import net.asfun.jangod.lib.FilterLibrary;

public class BasePageHandler extends RequestHandler {
    private final Logger log = LoggerFactory.getLogger(BasePageHandler.class);

    private static TemplateEngine sTemplateEngine;
    static {
        sTemplateEngine = new TemplateEngine();

        sTemplateEngine.getConfiguration().setWorkspace(new File(getBasePath(), "data/tmpl").getAbsolutePath());

        FilterLibrary.addFilter(new NumberFilter());
        FilterLibrary.addFilter(new AttrEscapeFilter());
        FilterLibrary.addFilter(new LocalDateFilter());
    }

    protected void render(String path, Map<String, Object> data) {
        data.put("realm", getRealm());

        getResponse().setContentType("text/html");
        getResponse().setHeader("Content-Type", "text/html");
        try {
            getResponse().getWriter().write(sTemplateEngine.process(path, data));
        } catch (IOException e) {
            log.error("Error rendering template!", e);
        }
    }

    protected void write(String text) {
        getResponse().setContentType("text/plain");
        getResponse().setHeader("Content-Type", "text/plain");
        try {
            getResponse().getWriter().write(text);;
        } catch (IOException e) {
            log.error("Error writing output!", e);
        }
    }

    @Override
    protected boolean isAdmin() throws RequestException {
        if (getSessionNoError() == null || !getSessionNoError().isAdmin()) {
            // if they're not authenticated yet, we'll have to redirect them to the authentication
            // page first.
            authenticate();
            return false;
        }

        return true;
    }

    protected void authenticate() throws RequestException {
        URI requestUrl;
        try {
            requestUrl = new URI(getRequestUrl());
        } catch (URISyntaxException e) {
            throw new RequestException(e);
        }

        String finalUrl = requestUrl.getPath();
        String returnUrl = requestUrl.resolve("/realms/"+getRealm()+"/login").toString();
        try {
            returnUrl += "?continue="+URLEncoder.encode(finalUrl, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RequestException(e);
        }

        String url = OpenIdAuth.getAuthenticateUrl(getRequest(), returnUrl);
        redirect(url);
    }

    private static class NumberFilter implements Filter {
        private static DecimalFormat sFormat = new DecimalFormat("#,##0");

        @Override
        public String getName() {
            return "number";
        }

        @Override
        public Object filter(Object object, JangodInterpreter interpreter,
                             String... args) throws InterpretException {
            if (object == null) {
                return object;
            }

            if (object instanceof Integer) {
                int n = (int) object;
                return sFormat.format(n);
            }
            if (object instanceof Long) {
                long n = (long) object;
                return sFormat.format(n);
            }
            if (object instanceof Float) {
                float n = (float) object;
                return sFormat.format(n);
            }
            if (object instanceof Double) {
                double n = (double) object;
                return sFormat.format(n);
            }

            throw new InterpretException("Expected a number.");
        }
    }

    private static class AttrEscapeFilter implements Filter {
        @Override
        public String getName() {
            return "attr-escape";
        }

        @Override
        public Object filter(Object object, JangodInterpreter interpreter,
                             String... args) throws InterpretException {
            return object.toString().replace("\"", "&quot;")
                    .replace("'", "&squot;");
        }
    }

    private static class LocalDateFilter implements Filter {

        @Override
        public String getName() {
            return "local-date";
        }

        @Override
        public Object filter(Object object, JangodInterpreter interpreter,
                String... args) throws InterpretException {
            if (object instanceof DateTime) {
                DateTime dt = (DateTime) object;
                return String.format(Locale.ENGLISH,
                        "<script>(function() {" +
                          " var dt = new Date(\"%s\");" +
                          " +document.write(dt.toLocaleString());" +
                        "})();</script>", dt);
            }

            throw new InterpretException("Expected a DateTime.");
        }
    }
}
