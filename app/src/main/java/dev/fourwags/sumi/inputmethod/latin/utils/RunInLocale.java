/*
 * Copyright (C) 2013 The Android Open Source Project
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

package dev.fourwags.sumi.inputmethod.latin.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public abstract class RunInLocale<T> {

    protected abstract T job(final Resources res);

    /**
     * Execute {@link #job(Resources)} with isolated locale resources. Never change
     * the shared application's Configuration, including while the job is running.
     *
     * @param context the context whose configuration is copied.
     * @param newLocale the locale to change to. Run in system locale if null.
     * @return the value returned from {@link #job(Resources)}.
     */
    public T runInLocale(final Context context, final Locale newLocale) {
        final Resources res = context.getResources();
        if (newLocale == null || newLocale.equals(res.getConfiguration().getLocales().get(0))) {
            return job(res);
        }
        final Configuration conf = new Configuration(res.getConfiguration());
        conf.setLocale(newLocale);
        return job(context.createConfigurationContext(conf).getResources());
    }
}
