package com.walmartlabs.ollie.lifecycle;

/*-
 * *****
 * Ollie
 * -----
 * Copyright (C) 2018 - 2019 Takari
 * -----
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
 * =====
 */

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.walmartlabs.ollie.OllieServerBuilder;

public class LifecycleManager {

  private final OllieServerBuilder builder;
  private final Injector injector;

  public LifecycleManager(Module module, OllieServerBuilder builder) {
    this.builder = builder;
    this.injector = Guice.createInjector(enableLifeCycleManagement(builder.taskRepository(), module));
    addShutdownHook();
  }

  private void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(builder.shutdownManager()::shutdown));
  }

  public Injector injector() {
    return injector;
  }

  private static Module enableLifeCycleManagement(TaskRepository repository, Module module) {
    return new LifecycleAwareModule(repository, module);
  }
}
