/*
 * Copyright 2000-2018 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.server;

import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.internal.ResponseWriter;
import com.vaadin.flow.server.frontend.FrontendUtils;
import elemental.json.Json;
import elemental.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static com.vaadin.flow.server.Constants.VAADIN_BUILD_FILES_PATH;
import static com.vaadin.flow.server.Constants.VAADIN_MAPPING;
import static com.vaadin.flow.shared.ApplicationConstants.VAADIN_STATIC_FILES_PATH;

/**
 * Handles sending of resources from the WAR root (web content) or
 * META-INF/resources in the case that {@link VaadinServlet} is mapped using
 * "/*".
 * <p>
 * This class is primarily meant to be used during developing time. For a
 * production mode site you should consider serving static resources directly
 * from the servlet (using a default servlet if such exists) or through a stand
 * alone static file server.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class StaticFileServer implements StaticFileHandler {
    private final ResponseWriter responseWriter;
    private final VaadinServletService servletService;
    private DeploymentConfiguration deploymentConfiguration;

    private List<String> bundleFiles = null;


    /**
     * Constructs a file server.
     *
     * @param servletService
     *            servlet service for the deployment, not <code>null</code>
     */
    public StaticFileServer(VaadinServletService servletService) {
        this.servletService = servletService;
        deploymentConfiguration = servletService.getDeploymentConfiguration();
        responseWriter = new ResponseWriter(deploymentConfiguration);
    }

    @Override
    public boolean isStaticResourceRequest(HttpServletRequest request) {
        URL resource;

        String requestFilename = getRequestFilename(request);
        if (requestFilename.endsWith("/")) {
            // Directories are not static resources although
            // servletContext.getResource will return a URL for them, at
            // least with Jetty
            return false;
        }

        if (requestFilename.startsWith("/" + VAADIN_STATIC_FILES_PATH) || requestFilename.startsWith("/" + VAADIN_BUILD_FILES_PATH)) {
            // The path is reserved for internal resources only
            // We rather serve 404 than let it fall through
            return true;
        }
        resource = servletService.getStaticResource(requestFilename);

        return resource != null;
    }

    @Override
    public boolean serveStaticResource(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String filenameWithPath = getRequestFilename(request);
        URL resourceUrl;
        if (filenameWithPath.startsWith("/" + VAADIN_BUILD_FILES_PATH)
                && isAllowedVAADINBuildUrl(filenameWithPath)) {
            resourceUrl = servletService.getClassLoader()
                    .getResource("META-INF" + filenameWithPath);
        } else {
            resourceUrl = servletService.getStaticResource(filenameWithPath);
        }
        if (resourceUrl == null) {
            // Not found in webcontent or in META-INF/resources in some JAR
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        // There is a resource!

        // Intentionally writing cache headers also for 304 responses
        writeCacheHeaders(filenameWithPath, response);

        long timestamp = writeModificationTimestamp(resourceUrl, request,
                response);
        if (browserHasNewestVersion(request, timestamp)) {
            // Browser is up to date, nothing further to do than set the
            // response code
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }
        responseWriter.writeResponseContents(filenameWithPath, resourceUrl,
                request, response);
        return true;
    }

    /**
     * Check if it is ok to serve the requested file from the classpath.
     * <p>
     * ClassLoader is applicable for use when we are in NPM production mode and
     * are serving the bundle.js or webcomponent.js
     *
     * @param filenameWithPath
     * @return
     */
    private boolean isAllowedVAADINBuildUrl(String filenameWithPath) {
        if (deploymentConfiguration.isBowerMode() || !deploymentConfiguration
                .isProductionMode()) {
            getLogger().trace("Serving from the classpath in bower or "
                            + "development mode is not accepted. "
                            + "Letting request for '{}' go to servlet context.",
                    filenameWithPath);
            return false;
        }
        // Check that we target VAADIN/build and do not have '/../'
        if (!filenameWithPath.startsWith("/" + VAADIN_BUILD_FILES_PATH)
                || filenameWithPath.contains("/../")) {
            getLogger().info("Blocked attempt to access file: {}",
                    filenameWithPath);
            return false;
        }

        // As of now we only accept polyfills and bundleFiles to be gotten from the build folder through the classloader.
        if (deploymentConfiguration.getPolyfills()
                .contains(filenameWithPath.replace("/" + VAADIN_MAPPING, ""))
                || getBundles().contains(filenameWithPath)) {
            getLogger()
                    .trace("Accepted access to a build file using a class loader {}",
                            filenameWithPath);

            return true;
        }

        return false;
    }

    /**
     * Collect bundle files from stats.json on first get and store the result.
     */
    private List<String> getBundles() {
        if(bundleFiles == null) {
            bundleFiles = getBundleChunks();
        }
        return bundleFiles;
    }

    private List<String> getBundleChunks() {
        List<String> bundles = new ArrayList<>();
        try {
            String content = FrontendUtils.getStatsContent(servletService);
            JsonObject chunks = Json.parse(content)
                    .getObject("assetsByChunkName");

            for (String key : chunks.keys()) {
                bundles.add("/" + VAADIN_MAPPING + chunks.getString(key));
            }
        } catch (IOException e) {
            getLogger().error("Failed to load stats file.", e);
        }
        return bundles;
    }

    /**
     * Writes the modification timestamp info for the file into the response.
     *
     * @param resourceUrl
     *            the internal URL of the file
     * @param request
     *            the request object
     * @param response
     *            the response object
     * @return the written timestamp or -1 if no timestamp was written
     */
    protected long writeModificationTimestamp(URL resourceUrl,
            HttpServletRequest request, HttpServletResponse response) {
        // Find the modification timestamp
        long lastModifiedTime;
        URLConnection connection = null;
        try {
            connection = resourceUrl.openConnection();
            lastModifiedTime = connection.getLastModified();
            // Remove milliseconds to avoid comparison problems (milliseconds
            // are not returned by the browser in the "If-Modified-Since"
            // header).
            lastModifiedTime = lastModifiedTime - lastModifiedTime % 1000;
            response.setDateHeader("Last-Modified", lastModifiedTime);
            return lastModifiedTime;
        } catch (Exception e) {
            getLogger().trace(
                    "Failed to find out last modified timestamp. Continuing without it.",
                    e);
        } finally {
            try {
                // Explicitly close the input stream to prevent it
                // from remaining hanging
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4257700
                if (connection != null) {
                    InputStream is = connection.getInputStream();
                    if (is != null) {
                        is.close();
                    }
                }
            } catch (IOException e) {
                getLogger().warn("Error closing URLConnection input stream", e);
            }
        }
        return -1L;
    }

    /**
     * Writes cache headers for the file into the response.
     *
     * @param filenameWithPath
     *            the name and path of the file being sent
     * @param response
     *            the response object
     */
    protected void writeCacheHeaders(String filenameWithPath,
            HttpServletResponse response) {
        int resourceCacheTime = getCacheTime(filenameWithPath);
        String cacheControl;
        if (!deploymentConfiguration.isProductionMode()) {
            cacheControl = "no-cache";
        } else if (resourceCacheTime > 0) {
            cacheControl = "max-age=" + resourceCacheTime;
        } else {
            cacheControl = "public, max-age=0, must-revalidate";
        }
        response.setHeader("Cache-Control", cacheControl);
    }

    /**
     * Returns the (decoded) requested file name, relative to the context path.
     * <p>
     * Package private for testing purposes.
     *
     * @param request
     *            the request object
     * @return the requested file name, starting with a {@literal /}
     */
    String getRequestFilename(HttpServletRequest request) {
        // http://localhost:8888/context/servlet/folder/file.js
        // ->
        // /servlet/folder/file.js
        //
        // http://localhost:8888/context/servlet/VAADIN/folder/file.js
        // ->
        // /VAADIN/folder/file.js
        if (request.getPathInfo() == null) {
            return request.getServletPath();
        } else if (request.getPathInfo().startsWith("/" + VAADIN_MAPPING)) {
            return request.getPathInfo();
        }
        return request.getServletPath() + request.getPathInfo();
    }

    /**
     * Calculates the cache lifetime for the given filename in seconds.
     * <p>
     * By default filenames containing ".nocache." return 0, filenames
     * containing ".cache." return one year and all other files return 1 hour.
     *
     * @param filenameWithPath
     *            the name of the file being sent
     * @return cache lifetime for the given filename in seconds
     */
    protected int getCacheTime(String filenameWithPath) {
        /*
         * GWT conventions:
         *
         * - files containing .nocache. will not be cached.
         *
         * - files containing .cache. will be cached for one year.
         *
         * https://developers.google.com/web-toolkit/doc/latest/
         * DevGuideCompilingAndDebugging#perfect_caching
         */
        if (filenameWithPath.contains(".nocache.")) {
            return 0;
        }
        if (filenameWithPath.contains(".cache.")) {
            return 60 * 60 * 24 * 365;
        }
        /*
         * For all other files, the browser is allowed to cache for 1 hour
         * without checking if the file has changed.
         */
        return 3600;
    }

    /**
     * Checks if the browser has an up to date cached version of requested
     * resource using the "If-Modified-Since" header.
     *
     * @param request
     *            The HttpServletRequest from the browser.
     * @param resourceLastModifiedTimestamp
     *            The timestamp when the resource was last modified. -1 if the
     *            last modification time is unknown.
     * @return true if the If-Modified-Since header tells the cached version in
     *         the browser is up to date, false otherwise
     */
    protected boolean browserHasNewestVersion(HttpServletRequest request,
            long resourceLastModifiedTimestamp) {
        assert resourceLastModifiedTimestamp >= -1L;

        if (resourceLastModifiedTimestamp == -1L) {
            // We do not know when it was modified so the browser cannot have an
            // up-to-date version
            return false;
        }
        /*
         * The browser can request the resource conditionally using an
         * If-Modified-Since header. Check this against the last modification
         * time.
         */
        try {
            // If-Modified-Since represents the timestamp of the version cached
            // in the browser
            long headerIfModifiedSince = request
                    .getDateHeader("If-Modified-Since");

            if (headerIfModifiedSince >= resourceLastModifiedTimestamp) {
                // Browser has this an up-to-date version of the resource
                return true;
            }
        } catch (Exception e) {
            // Failed to parse header.
            getLogger().trace("Unable to parse If-Modified-Since", e);
        }
        return false;
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(StaticFileServer.class.getName());
    }

}
