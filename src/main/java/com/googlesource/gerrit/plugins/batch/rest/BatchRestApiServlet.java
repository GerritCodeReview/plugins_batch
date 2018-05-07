// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.batch.rest;

import com.google.gerrit.extensions.annotations.PluginCanonicalWebUrl;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.batch.BatchRemover;
import com.googlesource.gerrit.plugins.batch.MergeChange;
import com.googlesource.gerrit.plugins.batch.api.extensions.MergeInput;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BatchRestApiServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(BatchRestApiServlet.class);
  private static final long serialVersionUID = 1L;

  protected final Provider<CurrentUser> userProvider;
  protected final BatchRemover batchRemover;
  protected final String pluginName;
  protected final String pluginUrl;
  protected final PermissionBackend permissionBackend;
  protected final MergeChange.Factory mergeChangeFactory;

  protected final Gson gson = new Gson();

  @Inject
  BatchRestApiServlet(
      Provider<CurrentUser> userProvider,
      BatchRemover batchRemover,
      @PluginName String pluginName,
      @PluginCanonicalWebUrl String pluginUrl,
      PermissionBackend permissionBackend,
      MergeChange.Factory mergeChangeFactory) {
    this.userProvider = userProvider;
    this.batchRemover = batchRemover;
    this.pluginName = pluginName;
    this.pluginUrl = pluginUrl;
    this.permissionBackend = permissionBackend;
    this.mergeChangeFactory = mergeChangeFactory;

    log.info(String.format("Plugin '%s' at url %s", pluginName, pluginUrl));
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    // getPathInfo() will always return the pathinfo with the intial slash present
    // example being /0644a132-5b79-4c88-bf22-9364a1d02deb
    // .substring(1) is needed to remove the slash at the beginning of the string
    String batchId = req.getPathInfo().substring(1);
    // this is a valid rest endpoint for the plugin, but not for DELETE
    if ("batches".equals(batchId)) {
      rsp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    } else if (batchId.isEmpty()) {
      rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Batch ID was not provided");
      return;
    }
    try {
      permissionBackend.user(userProvider.get()).check(GlobalPermission.ADMINISTRATE_SERVER);
      String output = OutputFormat.JSON.newGson().toJson(batchRemover.remove(batchId));
      rsp.setContentType("application/json");
      rsp.setCharacterEncoding("UTF-8");
      PrintWriter out = rsp.getWriter();
      out.print(output);
      out.flush();
    } catch (NoSuchBatchException | NoSuchProjectException e) {
      rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      return;
    } catch (IOException e) {
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    } catch (IllegalStateException e) {
      rsp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
      return;
    } catch (AuthException | PermissionBackendException e) {
      rsp.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    String requestLine, wholeRequest = "";
    try (BufferedReader reader = req.getReader()) {
      if (reader == null) {
        rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request body empty");
        return;
      }
      while ((requestLine = reader.readLine()) != null) {
        wholeRequest += requestLine;
      }
      MergeInput mergeInput = gson.fromJson(wholeRequest, MergeInput.class);
      if (mergeInput == null) {
        rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request body invalid");
        return;
      }
      MergeChange mergeChange = mergeChangeFactory.create(mergeInput);
      try {
        String output = OutputFormat.JSON.newGson().toJson(mergeChange.createBatch());
        PrintWriter out = rsp.getWriter();
        out.print(output);
        out.flush();
      } catch (NoSuchBatchException
          | NoSuchProjectException
          | IOException
          | IllegalStateException e) {
        rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        return;
      }
    } catch (final Exception e) {
      rsp.sendError(
          HttpServletResponse.SC_BAD_REQUEST,
          wholeRequest + " -= Invalid request body =- " + e.getMessage());
      return;
    }
  }
}
