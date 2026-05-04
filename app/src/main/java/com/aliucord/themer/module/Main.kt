package com.aliucord.themer.module

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.content.res.XResources
import android.content.res.XResources.DrawableLoader
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.Window
import androidx.core.graphics.ColorUtils
import com.aliucord.themer.*
import com.aliucord.themer.module.Utils.context
import com.aliucord.themer.module.Utils.parseColor
import com.aliucord.themer.preferences.disabledPref
import com.aliucord.themer.preferences.sharedPreferences
import com.aliucord.themer.utils.ThemeManager
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject


@SuppressLint("UseCompatLoadingForDrawables")
class Main : IXposedHookInitPackageResources, IXposedHookLoadPackage {
    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        val res = resparam.res
        val packageName = resparam.packageName.let { if (it == Constants.ALIUCORD) Constants.DISCORD else it }
        if (packageName == BuildConfig.APPLICATION_ID) res.setReplacement(R.bool.xposed, true)
        if (packageName != Constants.DISCORD && !packageName.startsWith(Constants.CTC)) return

        if (sharedPreferences == null) {
            sharedPreferences = getPrefs()
            ThemeManager.init()
        }
        val theme = ThemeManager.themes.find { it.enabled } ?: return
        if (theme.advanced && disabledPref.get()) return

        val json = theme.json

        // Replacing can't happen until a context is available, for system colors.
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    context = param.thisObject as Context

                    replaceAll(json, res, packageName)
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val getColor = Context::class.java.getDeclaredMethod("getColor", Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(getColor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) =
                    with(param.thisObject as Context) { param.result = resources.getColor(param.args[0] as Int, getTheme()) }
            })
        }
    }

    private fun replaceAll(
        json: JSONObject,
        res: XResources,
        packageName: String
    ) {
        json.optJSONObject("simple_colors")?.run {
            keys().forEach {
                val v = parseColor(this.getString(it))
                when (it) {
                    "accent" -> {
                        res.replaceAllColors(packageName, Constants.ACCENT_NAMES, v)
                        res.fixNitroIcon(packageName, v)
                        res.tintDrawable(packageName, "drawable_voice_indicator_speaking", v)
                    }

                    "background" -> {
                        res.replaceAllColors(packageName, Constants.BACKGROUND_NAMES, v)
                        res.replaceAllAttrs(packageName, Constants.BACKGROUND_ATTRS, v)
                    }

                    "background_secondary" -> {
                        res.replaceAllColors(packageName, Constants.BACKGROUND_SECONDARY_NAMES, v)
                        res.replaceAllAttrs(packageName, Constants.BACKGROUND_SECONDARY_ATTRS, v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_selected_dark", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_selected_light", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_active_dark", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_active_light", v)
                    }

                    "mention_highlight" -> {
                        res.trySetReplacement(packageName, "color", "status_yellow_500", ColorUtils.setAlphaComponent(v, 0xff))
                        res.trySetReplacement(packageName, "attr", "status_yellow_500", ColorUtils.setAlphaComponent(v, 0xff))
                    }

                    "active_channel" -> {
                        res.tintDrawable(packageName, "drawable_overlay_channels_selected_dark", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_selected_light", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_active_dark", v)
                        res.tintDrawable(packageName, "drawable_overlay_channels_active_light", v)
                        res.trySetReplacement(packageName, "color", it, v)
                    }

                    "statusbar", "input_background", "blocked_bg" -> res.trySetReplacement(packageName, "color", it, v)
                }
            }
        }

        json.optJSONObject("colors")?.run {
            if (has("brand_500"))
                res.fixNitroIcon(packageName, parseColor(this.getString("brand_500")))
            keys().forEach { key ->
                val v = parseColor(this.getString(key))
                res.trySetReplacement(packageName, "color", key, v)
                res.trySetReplacement(packageName, "attr", key, v)
            }
        }

        json.optJSONObject("drawable_tints")?.run {
            keys().forEach {
                res.tintDrawable(packageName, it, parseColor(this.getString(it)))
            }
        }
    }

    private fun XResources.trySetReplacement(packageName: String, type: String, name: String, color: Int) {
        try {
            setReplacement(packageName, type, name, color)
        } catch (e: Throwable) {
            logError(e)
        }
    }

    private fun XResources.replaceAllColors(packageName: String, colors: Array<String>, color: Int) {
        for (name in colors) try {
            setReplacement(packageName, "color", name, color)
        } catch (e: Throwable) {
            logError(e)
        }
    }

    private fun XResources.replaceAllAttrs(packageName: String, attrs: Array<String>, color: Int) {
        for (name in attrs) try {
            setReplacement(packageName, "attr", name, color)
        } catch (e: Throwable) {
            logError(e)
        }
    }

    private fun XResources.fixNitroIcon(packageName: String, color: Int) =
        tintDrawable(packageName, "ic_nitro_rep", color)

    private fun XResources.tintActiveChannel(packageName: String, color: Int) {
        tintDrawable(packageName, "drawable_overlay_channels_active_dark", color)
        tintDrawable(packageName, "drawable_overlay_channels_active_light", color)
    }

    private fun XResources.tintDrawable(packageName: String, drawable: String, color: Int) {
        setReplacement(packageName, "drawable", drawable, object : DrawableLoader() {
            override fun newDrawable(res: XResources, id: Int): Drawable? = res.getDrawable(id, null).apply { setTint(color) }
        })
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        val packageName = lpparam.packageName
        if (packageName == BuildConfig.APPLICATION_ID) hookSharedPreferences()
        if (packageName != Constants.DISCORD && packageName != Constants.ALIUCORD && !packageName.startsWith(Constants.CTC)) return

        fixNormalDiscordSupport(packageName, cl)

        if (sharedPreferences == null) {
            sharedPreferences = getPrefs()
            ThemeManager.init()
        }
        val theme = ThemeManager.themes.find { it.enabled } ?: return
        val json = theme.json

        if (theme.advanced) {
            if (disabledPref.get()) return
            if (json.has(Constants.INPUT_BG_COLOR)) setInputBackground(cl, json.getInt(Constants.INPUT_BG_COLOR))
            if (json.has(Constants.STATUSBAR_COLOR)) setStatusBarColor(cl, json.getInt(Constants.STATUSBAR_COLOR))
        } else {
            if (json.has(Constants.SIMPLE_BG_SECONDARY_COLOR)) json.getInt(Constants.SIMPLE_BG_SECONDARY_COLOR).let {
                setInputBackground(cl, it)
                setStatusBarColor(cl, it)
            }
        }
    }

    private fun setInputBackground(classLoader: ClassLoader, color: Int) {
        XposedHelpers.findAndHookMethod(
            "com.google.android.material.textfield.TextInputLayout",
            classLoader,
            "calculateBoxBackgroundColor",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = color
                }
            }
        )
    }

    private fun setStatusBarColor(classLoader: ClassLoader, color: Int) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.discord.utilities.color.ColorCompat",
                classLoader,
                "setStatusBarColor",
                Window::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[1] = color
                    }
                }
            )
        } catch (e: Throwable) {
            logError(e)
        }
    }

    private fun logError(e: Throwable) {
        XposedBridge.log("DiscordThemer error: ")
        XposedBridge.log(e)
    }
}
