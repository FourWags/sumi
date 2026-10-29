package dev.fourwags.sumi.inputmethod.compat

import android.view.inputmethod.InputMethodSubtype
import dev.fourwags.sumi.inputmethod.latin.common.LocaleUtils
import java.util.*

object InputMethodSubtypeCompatUtils {
    @kotlin.jvm.JvmStatic
    fun getLocaleObject(subtype: InputMethodSubtype): Locale { // Locale.forLanguageTag() is available only in Android L and later.
        val languageTag = subtype.languageTag
        if (!languageTag.isNullOrEmpty()) return Locale.forLanguageTag(languageTag)
        return LocaleUtils.constructLocaleFromString(subtype.locale)
    }

}