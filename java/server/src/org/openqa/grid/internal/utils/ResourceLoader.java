package org.openqa.grid.internal.utils;
/*
Copyright 2011 WebDriver committers
Copyright 2011 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.io.InputStream;

/*
* Loads resources in a way that works transparently in IDE and build.
*/
public class ResourceLoader {
    public static InputStream load(String name) {
        InputStream resourceAsStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (resourceAsStream == null) {
            if (name.contains("/defaults/")) {
                String newName = name.substring(name.indexOf("/defaults/") + 1);
                resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(newName);
            }
        }
        return resourceAsStream;
    }
}
