/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.buildinit.plugins.internal;

import com.google.common.base.Throwables;
import org.gradle.api.JavaVersion;

import java.io.IOException;
import java.util.Properties;

public class DefaultTemplateLibraryVersionProvider implements TemplateLibraryVersionProvider {

    private final Properties libraryVersions = new Properties();

    public DefaultTemplateLibraryVersionProvider() {
        try {
            this.libraryVersions.load(getClass().getResourceAsStream("/org/gradle/buildinit/tasks/templates/library-versions.properties"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public String getVersion(String module) {
        if (module.equals("testng") && !JavaVersion.current().isJava8Compatible()) {
            // Use the highest version that runs on Java 7
            return "6.9.12";
        }
        return libraryVersions.getProperty(module);
    }
}
