/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.systemTray.util

import java.io.File

// temp kotlin utilities as a placeholder until the project is kotlin...
internal object KotlinUtils {
    /**
     * Reads the contents of the supplied input stream into a list of lines.
     *
     * @return Always returns a list, even if the file does not exist, or there are errors reading it.
     */
    fun readLines(file: File): List<String> {
        return file.readLines()
    }

    /**
     * Reads the contents of the supplied input stream into a single string.
     */
    fun readText(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    fun toInteger(string: String?): Int? {
        return string?.toIntOrNull()
    }

    /**
     * Executes the given command and returns its output.
     */
    fun execute(vararg args: String): String {
        return dorkbox.executor.Executor()
            .command(args.toList())
            .enableRead()
            .startBlocking(timeout = 60).output.utf8().trim()
    }
}
