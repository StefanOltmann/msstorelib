/*
 * Copyright 2026 Stefan Oltmann
 * https://github.com/StefanOltmann/msstorelib
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
package de.stefan_oltmann.msstore

/**
 * The only exception type thrown by the public msstorelib API.
 *
 * The message is a best-effort string from the native layer, which can include
 * WinRT error messages or fallback text. The cause preserves the original
 * error, so no failure is ever silently lost.
 */
public class MsStoreLicenseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
