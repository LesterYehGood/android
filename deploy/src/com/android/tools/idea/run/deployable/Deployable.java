/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.deployable;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.DeploymentApplicationService;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface Deployable {
  /**
   * Returns the API level of the device.
   */
  @NotNull
  AndroidVersion getVersion();

  /**
   * Returns whether the current project's application is already running on this {@link Deployable}.
   */
  boolean isApplicationRunningOnDeployable();

  @NotNull
  static List<Client> searchClientsForPackage(@NotNull IDevice device, @NotNull String packageName) {
    return DeploymentApplicationService.getInstance().findClient(device, packageName);
  }
}