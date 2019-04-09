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

import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ProvisionListener;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;

public class TaskListener implements ProvisionListener {

    private final TaskRepository repository;

    public TaskListener(TaskRepository repo) {
        this.repository = repo;
    }

    @Override
    public <T> void onProvision(ProvisionInvocation<T> provisionInvocation) {
        T provision = provisionInvocation.provision();
        if (provision instanceof Task && shouldManage(provisionInvocation)) {
            repository.register((Task) provision);
        }
    }

    private boolean shouldManage(ProvisionInvocation<?> provisionInvocation) {
        return provisionInvocation.getBinding().acceptScopingVisitor(new BindingScopingVisitor<Boolean>() {
            @Override
            public Boolean visitEagerSingleton() {
                return true;
            }

            @Override
            public Boolean visitScope(Scope scope) {
                return scope == Scopes.SINGLETON;
            }

            @Override
            public Boolean visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
                return scopeAnnotation.isAssignableFrom(Singleton.class);
            }

            @Override
            public Boolean visitNoScoping() {
                return false;
            }
        });
    }
}