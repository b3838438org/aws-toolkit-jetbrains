// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.java

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.io.isFile
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.utils.zipEntries
import software.aws.toolkits.jetbrains.services.lambda.BuiltLambda
import software.aws.toolkits.jetbrains.services.lambda.LambdaBuilder
import software.aws.toolkits.jetbrains.services.lambda.execution.PathMapping
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.settings.SamSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

abstract class BaseLambdaBuilderTest {
    protected abstract val lambdaBuilder: LambdaBuilder

    @Before
    open fun setUp() {
        SamSettings.getInstance().savedExecutablePath = System.getenv()["SAM_CLI_EXEC"]
    }

    protected fun buildLambda(
        module: Module,
        handlerElement: PsiElement,
        runtime: Runtime,
        handler: String,
        useContainer: Boolean = false
    ): BuiltLambda {
        val samOptions = SamOptions()
        samOptions.buildInContainer = useContainer
        val completableFuture = runInEdtAndGet {
            lambdaBuilder.buildLambda(module, handlerElement, handler, runtime, emptyMap(), samOptions)
                .toCompletableFuture()
        }

        return completableFuture.get(3, TimeUnit.MINUTES)
    }

    protected fun buildLambdaFromTemplate(
        module: Module,
        template: Path,
        logicalId: String,
        useContainer: Boolean = false
    ): BuiltLambda {
        val samOptions = SamOptions()
        samOptions.buildInContainer = useContainer
        val completableFuture = runInEdtAndGet {
            lambdaBuilder.buildLambdaFromTemplate(module, template, logicalId, samOptions).toCompletableFuture()
        }

        return completableFuture.get(3, TimeUnit.MINUTES)
    }

    protected fun packageLambda(
        module: Module,
        handlerElement: PsiElement,
        runtime: Runtime,
        handler: String,
        useContainer: Boolean = false
    ): Path {
        val samOptions = SamOptions()
        samOptions.buildInContainer = useContainer
        val completableFuture = runInEdtAndGet {
            lambdaBuilder.packageLambda(module, handlerElement, handler, runtime, samOptions).toCompletableFuture()
        }

        return completableFuture.get(3, TimeUnit.MINUTES)
    }

    protected fun verifyEntries(builtLambda: BuiltLambda, vararg entries: String) {
        val basePath = builtLambda.codeLocation
        Files.walk(builtLambda.codeLocation).use {
            val lambdaEntries = it.filter(Path::isFile)
                .map { path -> FileUtil.toSystemIndependentName(basePath.relativize(path).toString()) }
                .toList()
            assertThat(lambdaEntries).containsAll(entries.toList())
        }
    }

    protected fun verifyZipEntries(lambdaZip: Path, vararg entries: String) {
        assertThat(zipEntries(lambdaZip)).containsAll(entries.toList())
    }

    protected fun verifyPathMappings(module: Module, builtLambda: BuiltLambda, vararg mappings: Pair<String, String>) {
        val basePath = ModuleRootManager.getInstance(module).contentRoots[0].path
        val updatedPaths = mappings
            .map { (path, file) ->
                PathMapping(
                    path.replace("%PROJECT_ROOT%", basePath)
                        .replace("%BUILD_ROOT%", builtLambda.codeLocation.toString()),
                    file
                )
            }
        Assertions.assertThat(builtLambda.mappings).containsAll(updatedPaths)
    }
}