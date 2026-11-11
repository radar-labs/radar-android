package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.symbol_album_tilt_24),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.symbol_file_24),
  // Radar: Emoji takes the Payment slot (payments have their own button in the compose bar).
  EMOJI(R.string.AttachmentKeyboard_emoji, org.signal.core.ui.R.drawable.symbol_emoji_24),
  CONTACT(R.string.AttachmentKeyboard_contact, org.signal.core.ui.R.drawable.symbol_person_circle_24),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.symbol_location_circle_24),
  POLL(R.string.AttachmentKeyboard_poll, R.drawable.symbol_poll_24);

  private final int titleRes;
  private final int iconRes;

  AttachmentKeyboardButton(@StringRes int titleRes, @DrawableRes int iconRes) {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
  }

  public @StringRes int getTitleRes() {
    return titleRes;
  }

  public @DrawableRes int getIconRes() {
    return iconRes;
  }
}
