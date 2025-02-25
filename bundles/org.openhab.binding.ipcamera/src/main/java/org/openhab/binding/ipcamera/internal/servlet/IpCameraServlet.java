/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ipcamera.internal.servlet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IpCameraServlet} is responsible for serving files to the Jetty
 * server normally found on port 8080
 *
 * @author Matthew Skinner - Initial contribution
 */
@NonNullByDefault
public abstract class IpCameraServlet extends HttpServlet {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final long serialVersionUID = 1L;
    protected final ThingHandler handler;
    protected final HttpService httpService;

    public IpCameraServlet(ThingHandler handler, HttpService httpService) {
        this.handler = handler;
        this.httpService = httpService;
        startListening();
    }

    public void startListening() {
        try {
            httpService.registerServlet("/ipcamera/" + handler.getThing().getUID().getId(), this, null,
                    httpService.createDefaultHttpContext());
        } catch (NamespaceException | ServletException e) {
            logger.warn("Registering servlet failed:{}", e.getMessage());
        }
    }

    protected void sendSnapshotImage(HttpServletResponse response, String contentType, byte[] snapshot) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setContentType(contentType);
        if (snapshot.length == 1) {
            logger.warn("ipcamera.jpg was requested but there was no jpg in ram to send.");
            return;
        }
        try {
            response.setContentLength(snapshot.length);
            ServletOutputStream servletOut = response.getOutputStream();
            servletOut.write(snapshot);
        } catch (IOException e) {
        }
    }

    protected void sendString(HttpServletResponse response, String contents, String contentType) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setContentType(contentType);
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "max-age=0, no-cache, no-store");
        byte[] bytes = contents.getBytes();
        try {
            response.setContentLength(bytes.length);
            ServletOutputStream servletOut = response.getOutputStream();
            servletOut.write(bytes);
            servletOut.write("\r\n".getBytes());
        } catch (IOException e) {
        }
    }

    protected void sendFile(HttpServletResponse response, String filename, String contentType) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setBufferSize((int) file.length());
        response.setContentType(contentType);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "max-age=0, no-cache, no-store");
        BufferedInputStream input = null;
        BufferedOutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(file), (int) file.length());
            output = new BufferedOutputStream(response.getOutputStream(), (int) file.length());
            byte[] buffer = new byte[(int) file.length()];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        }
    }

    public void dispose() {
        try {
            httpService.unregister("/ipcamera/" + handler.getThing().getUID().getId());
            this.destroy();
        } catch (IllegalArgumentException e) {
            logger.warn("Unregistration of servlet failed:{}", e.getMessage());
        }
    }
}
