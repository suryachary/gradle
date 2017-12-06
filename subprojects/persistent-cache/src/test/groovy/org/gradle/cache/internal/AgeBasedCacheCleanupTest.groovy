/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.PersistentCache
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.cache.internal.AgeBasedCacheCleanup.canBeDeleted

@Subject(AgeBasedCacheCleanup)
class AgeBasedCacheCleanupTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def persistentCache = Mock(PersistentCache)

    def "filters for cache entry files"() {
        expect:
        !canBeDeleted("cache.properties")
        !canBeDeleted("gc.properties")
        !canBeDeleted("cache.lock")

        canBeDeleted("0" * 32)
        canBeDeleted("ABCDEFABCDEFABCDEFABCDEFABCDEF00")
        canBeDeleted("abcdefabcdefabcdefabcdefabcdef00")
    }

    def "finds files to delete"() {
        def cacheEntries = [
            createCacheEntry(10000), // newest file
            createCacheEntry(5000),
            createCacheEntry(3000),
            createCacheEntry(0), // oldest file
        ]
        expect:
        def filesToDelete = AgeBasedCacheCleanup.findFilesToDelete(cacheDir, 2000)
        filesToDelete.size() == 1
        // we should only delete the last one
        filesToDelete[0] == cacheEntries.last()
    }

    def "deletes files"() {
        def cacheEntries = [
            createCacheEntry(10000), // newest file
            createCacheEntry(5000),
            createCacheEntry(0), // oldest file
        ]
        when:
        AgeBasedCacheCleanup.cleanupFiles(persistentCache, cacheEntries)
        then:
        cacheEntries.each {
            it.assertDoesNotExist()
        }
    }

    def createCacheEntry(long timestamp) {
        def cacheEntry = cacheDir.file(String.format("%032x", timestamp))
        def data = new byte[1024]
        new Random().nextBytes(data)
        cacheEntry.bytes = data
        cacheEntry.lastModified = timestamp
        return cacheEntry
    }
}
