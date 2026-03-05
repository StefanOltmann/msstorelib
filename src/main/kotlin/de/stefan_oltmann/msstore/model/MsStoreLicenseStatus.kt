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
package de.stefan_oltmann.msstore.model

public enum class MsStoreLicenseStatus {

    /**
     * The app is not in the store.
     */
    NotInStore,

    /**
     * The license is active and not a trial.
     * This is what we want. ;)
     */
    Licensed,

    /**
     * The license is expired.
     */
    Expired,

    /**
     * The user has a valid trial license.
     */
    Trial,

    /**
     * The trial expired.
     */
    ExpiredTrial
}
