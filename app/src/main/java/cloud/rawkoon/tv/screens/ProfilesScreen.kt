package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.data.profileToAutoOpen
import cloud.rawkoon.tv.jellyfin.JellyfinSdk
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.ProfileTile
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.applyClockSetting
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.userApi

/**
 * "Who's watching?", the first screen on every launch. One tile per saved profile, the one used
 * last already focused, then "Add". In manage mode, picking a profile offers to remove it or to
 * set or clear its PIN.
 */
class ProfilesScreen(activity: MainActivity) : Screen(activity) {
    override val view = FrameLayout(context)
    private val tiles = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
    }
    private val manage = PillButton(context, string(R.string.manage_profiles), Icon.PEN)
    private var managing = false
    private var dialog: ChoiceDialog? = null
    private val clockView = clock(context).apply { applyClockSetting() }

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        column.addView(label(context, 36f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.who_watching) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = context.dp(34) })
        column.addView(tiles)
        view.clipChildren = false
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.addView(manage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = context.dp(30)
        })
        view.addView(clockView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        manage.setOnClickListener { setManaging(!managing) }
    }

    override fun onShown(first: Boolean) {
        clockView.applyClockSetting()
        rebuild()
        // Nobody signed in yet: straight to the sign-in, with this screen below for Back
        if (first && app.profiles.all().isEmpty()) {
            addProfile()
            return
        }
        // One profile without a PIN and the setting on: its home right away, this screen below for Back
        if (first) {
            profileToAutoOpen(app.profiles.all(), Prefs.skipProfilePicker(context))?.let {
                open(it)
                return
            }
        }
        super.onShown(first)
        refreshAvatars()
    }

    /**
     * Asks Jellyfin for each profile's picture: profiles imported from the first RawkoonTV came
     * without one, and a picture changed on the server shows here the next time.
     */
    private fun refreshAvatars() {
        for (profile in app.profiles.all()) {
            scope.launch {
                val tag = try {
                    JellyfinSdk.get(context).createApi(profile.jellyfinUrl, profile.jellyfinToken).userApi.getCurrentUser().content.primaryImageTag
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline or signed out: the tile keeps what it has
                    return@launch
                }
                val current = app.profiles.get(profile.id) ?: return@launch
                if (tag == current.avatarTag) return@launch
                app.profiles.save(current.copy(avatarTag = tag))
                refresh()
            }
        }
    }

    override fun focusDefault() {
        val last = app.profiles.lastUsed()?.id
        (tileFor(last) ?: tiles.getChildAt(0))?.requestFocus()
    }

    private fun tileFor(id: String?): View? =
        (0 until tiles.childCount).map { tiles.getChildAt(it) }.firstOrNull { (it as? ProfileTile)?.profile?.id == id }

    /** Rebuilds the tiles, keeping focus on the same profile when it is still there. */
    private fun refresh() {
        val focusedId = (view.findFocus() as? ProfileTile)?.profile?.id
        rebuild()
        if (focusedId != null) tileFor(focusedId)?.requestFocus()
    }

    private fun rebuild() {
        tiles.removeAllViews()
        val gap = context.dp(40)
        for (profile in app.profiles.all()) {
            val tile = ProfileTile(context, profile, app.images, managing)
            tile.setOnClickListener { pick(profile) }
            tiles.addView(tile, LinearLayout.LayoutParams(context.dp(ProfileTile.W), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
        }
        val add = ProfileTile(context, null, app.images, managing)
        add.setOnClickListener { addProfile() }
        tiles.addView(add, LinearLayout.LayoutParams(context.dp(ProfileTile.W), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun setManaging(value: Boolean) {
        managing = value
        manage.label.text = string(if (value) R.string.done else R.string.manage_profiles)
        val focusedId = (view.findFocus() as? ProfileTile)?.profile?.id
        rebuild()
        if (focusedId != null) {
            tileFor(focusedId)?.requestFocus()
        } else {
            manage.requestFocus()
        }
    }

    private fun pick(profile: Profile) {
        if (managing) {
            options(profile)
            return
        }
        if (profile.hasPin) {
            activity.push(PinScreen(activity, PinScreen.Mode.Verify(profile)) { open(profile) })
        } else {
            open(profile)
        }
    }

    private fun open(profile: Profile) {
        app.profiles.markUsed(profile.id)
        activity.resetTo(ProfilesScreen(activity), HomeScreen(activity, app.profiles.get(profile.id) ?: profile))
    }

    private fun addProfile() {
        val url = Prefs.rawkoonUrl(context)
        activity.push(if (url == null) ServerScreen(activity) else SignInScreen(activity, url))
    }

    private fun options(profile: Profile) {
        val pin = if (profile.hasPin) {
            string(R.string.remove_pin) to { app.profiles.save(profile.withPin(null)); rebuild() }
        } else {
            string(R.string.set_pin) to {
                activity.push(PinScreen(activity, PinScreen.Mode.Create(profile)) { pin ->
                    // The screen pops before this runs, so it has already rebuilt with the old tile
                    app.profiles.save(profile.withPin(pin))
                    refresh()
                })
            }
        }
        dialog = ChoiceDialog(
            context,
            profile.name,
            listOf(
                pin,
                string(R.string.remove_profile) to {
                    app.profiles.remove(profile.id)
                    rebuild()
                    focusDefault()
                },
                string(R.string.cancel) to {},
            ),
        ).also { it.open(view) }
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        if (managing) {
            setManaging(false)
            return true
        }
        return false
    }
}
