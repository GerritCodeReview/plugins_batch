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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.batch.BatchRemover;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
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

  @Inject
  BatchRestApiServlet(
      Provider<CurrentUser> userProvider,
      BatchRemover batchRemover,
      @PluginName String pluginName,
      @PluginCanonicalWebUrl String pluginUrl,
      PermissionBackend permissionBackend) {
    this.userProvider = userProvider;
    this.batchRemover = batchRemover;
    this.pluginName = pluginName;
    this.pluginUrl = pluginUrl;
    this.permissionBackend = permissionBackend;

    log.info(String.format("Plugin '%s' at url %s", pluginName, pluginUrl));
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    if (req.getQueryString() == null) {
      rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Batch ID was not provided");
      return;
    }
    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
      String output = OutputFormat.JSON.newGson().toJson(batchRemover.remove(req.getQueryString()));
      PrintWriter out = rsp.getWriter();
      out.print(output);
      out.flush();
    } catch (AuthException
        | PermissionBackendException
        | NoSuchBatchException
        | NoSuchProjectException
        | IOException
        | IllegalStateException e) {
      rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      return;
    }
  }
}
