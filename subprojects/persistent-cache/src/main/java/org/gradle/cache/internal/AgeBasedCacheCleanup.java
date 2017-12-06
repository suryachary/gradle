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

package org.gradle.cache.internal;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@NonNullApi
public final class AgeBasedCacheCleanup implements Action<PersistentCache> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgeBasedCacheCleanup.class);

    private final BuildOperationExecutor buildOperationExecutor;
    private final int removeUnusedEntriesAfterDays;

    public AgeBasedCacheCleanup(BuildOperationExecutor buildOperationExecutor, int removeUnusedEntriesAfterDays) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.removeUnusedEntriesAfterDays = removeUnusedEntriesAfterDays;
    }

    @Override
    public void execute(final PersistentCache persistentCache) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                cleanup(persistentCache);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Clean up " + persistentCache);
            }
        });
    }

    private void cleanup(final PersistentCache persistentCache) {
        final File[] filesForDeletion = buildOperationExecutor.call(new CallableBuildOperation<File[]>() {
            @Override
            public File[] call(BuildOperationContext context) {
                long oldestTimestampToKeep = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(removeUnusedEntriesAfterDays);
                return findFilesToDelete(persistentCache.getBaseDir(), oldestTimestampToKeep);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Scan " + persistentCache.getBaseDir());
            }
        });

        if (filesForDeletion.length > 0) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    cleanupFiles(persistentCache, Arrays.asList(filesForDeletion));
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Delete files for " + persistentCache);
                }
            });
        }
    }

    @VisibleForTesting
    static File[] findFilesToDelete(File cacheDir, final long oldestTimestampToKeep) {
        // TODO: This doesn't descend subdirectories.
        return cacheDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (!canBeDeleted(file.getName())) {
                    return false;
                }
                return file.lastModified() < oldestTimestampToKeep;
            }
        });
    }

    @VisibleForTesting
    static void cleanupFiles(PersistentCache persistentCache, List<File> filesForDeletion) {
        // Need to remove some files
        long removedSize = deleteFiles(filesForDeletion);
        LOGGER.info("{} removing {} cache entries ({} reclaimed).", persistentCache, filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
    }

    private static long deleteFiles(List<File> files) {
        long removedSize = 0;
        for (File file : files) {
            try {
                long size = file.length();
                if (file.delete()) {
                    removedSize += size;
                }
            } catch (Exception e) {
                LOGGER.debug("Could not clean up cache " + file, e);
            }
        }
        return removedSize;
    }

    @VisibleForTesting
    static boolean canBeDeleted(String name) {
        return !(name.endsWith(".properties") || name.endsWith(".lock"));
    }
}
