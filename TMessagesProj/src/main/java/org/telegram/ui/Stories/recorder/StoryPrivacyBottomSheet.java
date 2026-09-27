package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

import java.util.ArrayList;
import java.util.HashMap;

// LoogriGram: this was the sheet where a story's audience, comments and
// screenshots were chosen before posting or editing. Stories are not posted
// here; what is left is the privacy model the viewer and the story cells
// read, and the user row UniversalAdapter lists.
public class StoryPrivacyBottomSheet {

    public static final int TYPE_CLOSE_FRIENDS = 1;
    public static final int TYPE_CONTACTS = 2;
    public static final int TYPE_SELECTED_CONTACTS = 3;
    public static final int TYPE_EVERYONE = 4;
    public static final int TYPE_AS_MESSAGE = 5;

    public static class UserCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final BackupImageView imageView;

        private final SimpleTextView titleTextView;
        private final SimpleTextView subtitleTextView;

        public final CheckBox2 checkBox;
        public final RadioButton radioButton;

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean sendAs = false;
        private boolean needCheck = true;
        private boolean drawArrow = true;

        public UserCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            avatarDrawable.setRoundRadius(AndroidUtilities.dp(40));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(20));
            addView(imageView);

            titleTextView = new SimpleTextView(context);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setTextSize(16);
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            NotificationCenter.listenEmojiLoading(titleTextView);
            addView(titleTextView);

            subtitleTextView = new SimpleTextView(context);
            subtitleTextView.setTextSize(14);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            subtitleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            NotificationCenter.listenEmojiLoading(subtitleTextView);
            addView(subtitleTextView);

            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
            checkBox.setDrawUnchecked(true);
            checkBox.setDrawBackgroundAsArc(10);
            addView(checkBox);
            checkBox.setChecked(false, false);
            checkBox.setVisibility(View.GONE);

            radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setColor(Theme.getColor(Theme.key_checkboxDisabled, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
            addView(radioButton);
            radioButton.setVisibility(View.GONE);

            updateLayouts();
        }

        private void updateLayouts() {
            imageView.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), needCheck ? 53 : 16, 0, needCheck ? 53 : 16, 0));
            titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck ? 105 : 68), 0, LocaleController.isRTL ? (needCheck ? 105 : 68) : 20, 0));
            subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (needCheck ? 105 : 68), 0, LocaleController.isRTL ? (needCheck ? 105 : 68) : 20, 0));
            checkBox.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 13, 0, 14, 0));
            radioButton.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 14, 0, 15, 0));
        }

        public void setIsSendAs(boolean isSendAs, boolean needsCheck) {
            sendAs = isSendAs;
            if (needsCheck != needCheck) {
                this.needCheck = needsCheck;
                updateLayouts();
            }
            if (!needCheck) {
                radioButton.setVisibility(View.GONE);
                checkBox.setVisibility(View.GONE);
            }
            setWillNotDraw(!(needDivider || (!needCheck && sendAs)));
        }

        public void setRedCheckbox(boolean red) {
            checkBox.setColor(red ? Theme.key_color_red : Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
        }

        public void setChecked(boolean checked, boolean animated) {
            if (checkBox.getVisibility() == View.VISIBLE) {
                checkBox.setChecked(checked, animated);
            }
            if (radioButton.getVisibility() == View.VISIBLE) {
                radioButton.setChecked(checked, animated);
            }
        }

        public void setCheckboxAlpha(float alpha, boolean animated) {
            if (animated) {
                if (Math.abs(checkBox.getAlpha() - alpha) > .1) {
                    checkBox.animate().cancel();
                    checkBox.animate().alpha(alpha).start();
                }
                if (Math.abs(radioButton.getAlpha() - alpha) > .1) {
                    radioButton.animate().cancel();
                    radioButton.animate().alpha(alpha).start();
                }
            } else {
                checkBox.animate().cancel();
                checkBox.setAlpha(alpha);
                radioButton.animate().cancel();
                radioButton.setAlpha(alpha);
            }
        }

        private boolean[] isOnline = new boolean[1];


        public void set(Object object) {
            if (object instanceof TLRPC.User) {
                titleTextView.setTypeface(AndroidUtilities.bold());
                titleTextView.setTranslationX(0);
                setUser((TLRPC.User) object);
            } else if (object instanceof TLRPC.Chat) {
                titleTextView.setTypeface(AndroidUtilities.bold());
                titleTextView.setTranslationX(0);
                setChat((TLRPC.Chat) object, 0);
            } else if (object instanceof String) {
                titleTextView.setTypeface(null);
                titleTextView.setTranslationX(-dp(52) * (LocaleController.isRTL ? -1 : 1));
                titleTextView.setText((String) object);
            }
        }

        public long dialogId;

        public void setUser(TLRPC.User user) {
            dialogId = user == null ? 0 : user.id;

            avatarDrawable.setInfo(user);
            imageView.setRoundRadius(dp(20));
            imageView.setForUserOrChat(user, avatarDrawable);

            CharSequence text = UserObject.getUserName(user);
            text = Emoji.replaceEmoji(text, titleTextView.getPaint().getFontMetricsInt(), false);
            titleTextView.setText(text);
            isOnline[0] = false;
            if (sendAs) {
                setSubtitle(getString(R.string.VoipGroupPersonalAccount));
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            } else {
                setSubtitle(LocaleController.formatUserStatus(UserConfig.selectedAccount, user, isOnline));
                subtitleTextView.setTextColor(Theme.getColor(isOnline[0] ? Theme.key_dialogTextBlue2 : Theme.key_dialogTextGray3, resourcesProvider));
            }

            checkBox.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            checkBox.setAlpha(1f);
            radioButton.setVisibility(View.GONE);
        }

        public void setChat(TLRPC.Chat chat, int participants_count) {
            dialogId = chat == null ? 0 : -chat.id;

            avatarDrawable.setInfo(chat);
            imageView.setRoundRadius(dp(ChatObject.isForum(chat) ? 12 : 20));
            imageView.setForUserOrChat(chat, avatarDrawable);

            CharSequence text = chat.title;
            text = Emoji.replaceEmoji(text, titleTextView.getPaint().getFontMetricsInt(), false);
            titleTextView.setText(text);

            isOnline[0] = false;
            String subtitle;
            if (sendAs) {
                if (participants_count <= 0) {
                    participants_count = chat.participants_count;
                }
                boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralString(isChannel ? "Subscribers" : "Members", participants_count);
                } else {
                    subtitle = getString(isChannel ? R.string.DiscussChannel : R.string.AccDescrGroup);
                }
            } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralStringComma("Subscribers", participants_count - 1);
                } else {
                    if (!ChatObject.isPublic(chat)) {
                        subtitle = getString(R.string.ChannelPrivate).toLowerCase();
                    } else {
                        subtitle = getString(R.string.ChannelPublic).toLowerCase();
                    }
                }
            } else {
                if (participants_count >= 1) {
                    subtitle = LocaleController.formatPluralStringComma("Members", participants_count - 1);
                } else {
                    if (chat.has_geo) {
                        subtitle = getString(R.string.MegaLocation);
                    } else if (!ChatObject.isPublic(chat)) {
                        subtitle = getString(R.string.MegaPrivate).toLowerCase();
                    } else {
                        subtitle = getString(R.string.MegaPublic).toLowerCase();
                    }
                }
            }
            setSubtitle(subtitle);
            subtitleTextView.setTextColor(Theme.getColor(isOnline[0] ? Theme.key_dialogTextBlue2 : Theme.key_dialogTextGray3, resourcesProvider));

            checkBox.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            radioButton.setVisibility(View.GONE);
            setCheckboxAlpha(participants_count > 200 ? .3f : 1f, false);
        }

        private CharSequence withArrow(CharSequence text) {
            SpannableString arrow = new SpannableString(">");
            Drawable arrowDrawable = getContext().getResources().getDrawable(R.drawable.attach_arrow_right);
            ColoredImageSpan span = new ColoredImageSpan(arrowDrawable, ColoredImageSpan.ALIGN_CENTER);
            arrowDrawable.setBounds(0, dp(1), dp(11), dp(1 + 11));
            arrow.setSpan(span, 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableStringBuilder finalText = new SpannableStringBuilder();
            finalText.append(text).append(" ").append(arrow);
            return finalText;
        }

        public void setType(int type, int count, TLRPC.User singleUser) {
            if (type == TYPE_EVERYONE) {
                titleTextView.setText(getString(R.string.StoryPrivacyOptionEveryone));
                if (count == 1 && singleUser != null) {
                    CharSequence text = LocaleController.formatString(R.string.StoryPrivacyOptionExcludePerson, UserObject.getUserName(singleUser));
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionExcludePeople", count)));
                } else {
                    setSubtitle(withArrow(getString(R.string.StoryPrivacyOptionContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                avatarDrawable.setColor(0xFF16A5F2, 0xFF1180F7);
            } else if (type == TYPE_CONTACTS) {
                titleTextView.setText(getString(R.string.StoryPrivacyOptionContacts));
                if (count == 1 && singleUser != null) {
                    CharSequence text = LocaleController.formatString(R.string.StoryPrivacyOptionExcludePerson, UserObject.getUserName(singleUser));
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionExcludePeople", count)));
                } else {
                    setSubtitle(withArrow(getString(R.string.StoryPrivacyOptionContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                avatarDrawable.setColor(0xFFC468F2, 0xFF965CFA);
            } else if (type == TYPE_CLOSE_FRIENDS) {
                titleTextView.setText(getString(R.string.StoryPrivacyOptionCloseFriends));
                if (count == 1 && singleUser != null) {
                    CharSequence text = UserObject.getUserName(singleUser);
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionPeople", count)));
                } else {
                    setSubtitle(withArrow(getString(R.string.StoryPrivacyOptionCloseFriendsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_CLOSE_FRIENDS);
                avatarDrawable.setColor(0xFF88D93A, 0xFF2DB63B);
            } else if (type == TYPE_SELECTED_CONTACTS) {
                titleTextView.setText(getString(R.string.StoryPrivacyOptionSelectedContacts));
                if (count == 1 && singleUser != null) {
                    CharSequence text = UserObject.getUserName(singleUser);
                    text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                    setSubtitle(withArrow(text));
                } else if (count > 0) {
                    setSubtitle(withArrow(LocaleController.formatPluralString("StoryPrivacyOptionPeople", count)));
                } else {
                    setSubtitle(withArrow(getString(R.string.StoryPrivacyOptionSelectedContactsDetail)));
                }
                subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                avatarDrawable.setColor(0xFFFFB743, 0xFFF68E34);
            }
            checkBox.setVisibility(View.GONE);
            radioButton.setVisibility(needCheck ? View.VISIBLE : View.GONE);
            imageView.setImageDrawable(avatarDrawable);
            imageView.setRoundRadius(dp(20));
        }

        private void setSubtitle(CharSequence text) {
            if (text == null) {
                titleTextView.setTranslationY(0);
                subtitleTextView.setVisibility(View.GONE);
            } else {
                titleTextView.setTranslationY(AndroidUtilities.dp(-9));
                subtitleTextView.setTranslationY(AndroidUtilities.dp(12));
                subtitleTextView.setText(text);
                subtitleTextView.setVisibility(View.VISIBLE);
            }
        }

        private boolean needDivider;
        public void setDivider(boolean divider) {
            setWillNotDraw(!((needDivider = divider) || (!needCheck && sendAs)));
        }

        private Path arrowPath;
        private Paint arrowPaint;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(sendAs && !needCheck ? 62 : 56), MeasureSpec.EXACTLY)
            );

            if (!needCheck && sendAs) {
                if (arrowPath == null) {
                    arrowPath = new Path();
                } else {
                    arrowPath.rewind();
                }
                final float cx = LocaleController.isRTL ? dp(31) : getMeasuredWidth() - dp(31);
                final float cy = getMeasuredHeight() / 2f;
                final float m = LocaleController.isRTL ? -1 : 1;
                arrowPath.moveTo(cx, cy - dp(6));
                arrowPath.lineTo(cx + m * dp(6), cy);
                arrowPath.lineTo(cx, cy + dp(6));
                if (arrowPaint == null) {
                    arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    arrowPaint.setStyle(Paint.Style.STROKE);
                    arrowPaint.setStrokeCap(Paint.Cap.ROUND);
                }
                arrowPaint.setStrokeWidth(dpf2(1.86f));
                arrowPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .3f));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                if (LocaleController.isRTL) {
                    canvas.drawRect(0, getHeight() - 1, getWidth() - dp(105), getHeight(), dividerPaint);
                } else {
                    canvas.drawRect(dp(105), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
                }
            }
            if (arrowPath != null && arrowPaint != null && !needCheck && sendAs && drawArrow) {
                canvas.drawPath(arrowPath, arrowPaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            try {
                boolean checkboxVisible = checkBox != null && checkBox.getVisibility() == View.VISIBLE;
                boolean radioVisible = radioButton != null && radioButton.getVisibility() == View.VISIBLE;
                if (checkboxVisible || radioVisible) {
                    info.setCheckable(true);
                    info.setChecked(checkboxVisible ? checkBox.isChecked() : radioButton.isChecked());
                    info.setClassName(checkboxVisible ? "android.widget.CheckBox" : "android.widget.RadioButton");
                }
            } catch (Exception ignored) {}
        }
    }

    public static class StoryPrivacy {
        public final int type;

        public final ArrayList<TLRPC.InputPrivacyRule> rules = new ArrayList<>();
        public final ArrayList<Long> selectedUserIds = new ArrayList<>();
        public final HashMap<Long, ArrayList<Long>> selectedUserIdsByGroup = new HashMap();
        public final ArrayList<TLRPC.InputUser> selectedInputUsers = new ArrayList<>();
        public final ArrayList<Long> sendToUsers = new ArrayList<>();

        public StoryPrivacy(int currentAccount, ArrayList<TLRPC.PrivacyRule> rules) {
            TLRPC.TL_privacyValueAllowUsers allowUsers;
            if (containsRule(rules, TLRPC.TL_privacyValueAllowAll.class) != null) {
                type = TYPE_EVERYONE;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());

                TLRPC.TL_privacyValueDisallowUsers disallowUsers = containsRule(rules, TLRPC.TL_privacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        long userId = disallowUsers.users.get(i);
                        TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                        if (!(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(inputUser);
                            selectedUserIds.add(userId);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (containsRule(rules, TLRPC.TL_privacyValueAllowCloseFriends.class) != null) {
                type = TYPE_CLOSE_FRIENDS;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if ((allowUsers = containsRule(rules, TLRPC.TL_privacyValueAllowUsers.class)) != null) {
                type = TYPE_SELECTED_CONTACTS;

                final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                for (int i = 0; i < allowUsers.users.size(); ++i) {
                    long userId = allowUsers.users.get(i);
                    TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                    if (inputUser != null && !(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                        rule.users.add(inputUser);
                        selectedUserIds.add(userId);
                        selectedInputUsers.add(inputUser);
                    }
                }
                this.rules.add(rule);
            } else if (containsRule(rules, TLRPC.TL_privacyValueAllowContacts.class) != null) {
                type = TYPE_CONTACTS;
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());

                TLRPC.TL_privacyValueDisallowUsers disallowUsers = containsRule(rules, TLRPC.TL_privacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        long userId = disallowUsers.users.get(i);
                        TLRPC.InputUser inputUser = messagesController.getInputUser(userId);
                        if (!(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(inputUser);
                            selectedUserIds.add(userId);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                    this.rules.add(rule);
                }
            } else {
                type = TYPE_EVERYONE;
            }
        }

        public StoryPrivacy(ArrayList<TLRPC.InputPrivacyRule> rules) {
            this.rules.addAll(rules);
            TLRPC.TL_inputPrivacyValueAllowUsers allowUsers;
            if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowAll.class) != null) {
                type = TYPE_EVERYONE;
                TLRPC.TL_inputPrivacyValueDisallowUsers disallowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        TLRPC.InputUser inputUser = disallowUsers.users.get(i);
                        if (inputUser != null) {
                            selectedUserIds.add(inputUser.user_id);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                }
            } else if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowCloseFriends.class) != null) {
                type = TYPE_CLOSE_FRIENDS;
            } else if ((allowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowUsers.class)) != null) {
                type = TYPE_SELECTED_CONTACTS;
                for (int i = 0; i < allowUsers.users.size(); ++i) {
                    TLRPC.InputUser inputUser = allowUsers.users.get(i);
                    if (inputUser != null) {
                        selectedUserIds.add(inputUser.user_id);
                        selectedInputUsers.add(inputUser);
                    }
                }
            } else if (containsInputRule(rules, TLRPC.TL_inputPrivacyValueAllowContacts.class) != null) {
                type = TYPE_CONTACTS;
                TLRPC.TL_inputPrivacyValueDisallowUsers disallowUsers = containsInputRule(rules, TLRPC.TL_inputPrivacyValueDisallowUsers.class);
                if (disallowUsers != null) {
                    for (int i = 0; i < disallowUsers.users.size(); ++i) {
                        TLRPC.InputUser inputUser = disallowUsers.users.get(i);
                        if (inputUser != null) {
                            selectedUserIds.add(inputUser.user_id);
                            selectedInputUsers.add(inputUser);
                        }
                    }
                }
            } else {
                type = TYPE_EVERYONE;
            }
        }

        private <T> T containsRule(ArrayList<TLRPC.PrivacyRule> rules, Class<T> clazz) {
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.PrivacyRule rule = rules.get(i);
                if (clazz.isInstance(rule)) {
                    return (T) rule;
                }
            }
            return null;
        }

        private <T> T containsInputRule(ArrayList<TLRPC.InputPrivacyRule> rules, Class<T> clazz) {
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule rule = rules.get(i);
                if (clazz.isInstance(rule)) {
                    return (T) rule;
                }
            }
            return null;
        }

        public StoryPrivacy() {
            this.type = TYPE_EVERYONE;
            this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
        }

        public StoryPrivacy(int type, int currentAccount, ArrayList<Long> userIds) {
            this.type = type;
            if (type == TYPE_EVERYONE) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_CLOSE_FRIENDS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if (type == TYPE_CONTACTS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_SELECTED_CONTACTS) {
                final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                if (currentAccount >= 0 && userIds != null && !userIds.isEmpty()) {
                    for (int i = 0; i < userIds.size(); ++i) {
                        long userId = userIds.get(i);
                        selectedUserIds.add(userId);
                        TLRPC.InputUser user = MessagesController.getInstance(currentAccount).getInputUser(userId);
                        if (user != null && !(user instanceof TLRPC.TL_inputUserEmpty)) {
                            rule.users.add(user);
                            selectedInputUsers.add(user);
                        }
                    }
                }
                this.rules.add(rule);
            } else if (type == TYPE_AS_MESSAGE) {
                if (userIds != null) {
                    this.sendToUsers.addAll(userIds);
                }
            }
        }

        public StoryPrivacy(int type, ArrayList<TLRPC.InputUser> inputUserIds, int a) {
            this.type = type;
            if (type == TYPE_EVERYONE) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_CLOSE_FRIENDS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
            } else if (type == TYPE_CONTACTS) {
                this.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    final TLRPC.TL_inputPrivacyValueDisallowUsers rule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                    this.rules.add(rule);
                }
            } else if (type == TYPE_SELECTED_CONTACTS) {
                final TLRPC.TL_inputPrivacyValueAllowUsers rule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                if (inputUserIds != null && !inputUserIds.isEmpty()) {
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            rule.users.add(user);
                            selectedUserIds.add(user.user_id);
                            selectedInputUsers.add(user);
                        }
                    }
                }
                this.rules.add(rule);
            } else if (type == TYPE_AS_MESSAGE) {
                if (inputUserIds != null) {
                    for (int i = 0; i < inputUserIds.size(); ++i) {
                        TLRPC.InputUser user = inputUserIds.get(i);
                        if (user != null) {
                            this.sendToUsers.add(user.user_id);
                        }
                    }
                }
            }
        }

        public boolean isShare() {
            return type == TYPE_AS_MESSAGE;
        }

        public boolean isNone() {
            return sendToUsers.isEmpty() && rules.isEmpty();
        }

        public boolean isCloseFriends() {
            return type == TYPE_CLOSE_FRIENDS;
        }

        @NonNull
        @Override
        public String toString() {
            if (!sendToUsers.isEmpty()) {
                return LocaleController.formatPluralString("StoryPrivacyRecipients", sendToUsers.size());
            }
            if (rules.isEmpty()) {
                return getString(R.string.StoryPrivacyNone);
            }
            TLRPC.InputPrivacyRule rule1 = rules.get(0);
            if (type == TYPE_EVERYONE) {
                TLRPC.InputPrivacyRule rule2 = rules.size() >= 2 ? rules.get(1) : null;
                if (rule2 instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueDisallowUsers) rule2).users.size();
                    if (usersCount > 0) {
                        return LocaleController.formatPluralString("StoryPrivacyEveryoneExclude", usersCount);
                    }
                }
                return getString(R.string.StoryPrivacyEveryone);
            } else if (type == TYPE_CLOSE_FRIENDS) {
                return getString(R.string.StoryPrivacyCloseFriends);
            } else if (type == TYPE_SELECTED_CONTACTS && rule1 instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                final int usersCount = ((TLRPC.TL_inputPrivacyValueAllowUsers) rule1).users.size();
                return LocaleController.formatPluralString("StoryPrivacyContacts", usersCount);
            } else if (type == TYPE_CONTACTS) {
                TLRPC.InputPrivacyRule rule2 = rules.size() >= 2 ? rules.get(1) : null;
                if (rule2 instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueDisallowUsers) rule2).users.size();
                    if (usersCount > 0) {
                        return LocaleController.formatPluralString("StoryPrivacyContactsExclude", usersCount);
                    } else {
                        return getString(R.string.StoryPrivacyAllContacts);
                    }
                } else {
                    return getString(R.string.StoryPrivacyAllContacts);
                }
            } else if (type == 0) {
                if (rule1 instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    final int usersCount = ((TLRPC.TL_inputPrivacyValueAllowUsers) rule1).users.size();
                    if (usersCount <= 0) {
                        return getString(R.string.StoryPrivacyNone);
                    } else {
                        return LocaleController.formatPluralString("StoryPrivacyContacts", usersCount);
                    }
                } else {
                    return getString(R.string.StoryPrivacyNone);
                }
            }
            return getString(R.string.StoryPrivacyNone);
        }

        public ArrayList<TLRPC.PrivacyRule> toValue() {
            ArrayList<TLRPC.PrivacyRule> result = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule inputPrivacyRule = rules.get(i);
                if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowAll) {
                    result.add(new TLRPC.TL_privacyValueAllowAll());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowCloseFriends) {
                    result.add(new TLRPC.TL_privacyValueAllowCloseFriends());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowContacts) {
                    result.add(new TLRPC.TL_privacyValueAllowContacts());
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    TLRPC.TL_inputPrivacyValueDisallowUsers inputRule = (TLRPC.TL_inputPrivacyValueDisallowUsers) inputPrivacyRule;
                    TLRPC.TL_privacyValueDisallowUsers rule = new TLRPC.TL_privacyValueDisallowUsers();
                    for (int j = 0; j < inputRule.users.size(); ++j) {
                        rule.users.add(inputRule.users.get(j).user_id);
                    }
                    result.add(rule);
                } else if (inputPrivacyRule instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    TLRPC.TL_inputPrivacyValueAllowUsers inputRule = (TLRPC.TL_inputPrivacyValueAllowUsers) inputPrivacyRule;
                    TLRPC.TL_privacyValueAllowUsers rule = new TLRPC.TL_privacyValueAllowUsers();
                    for (int j = 0; j < inputRule.users.size(); ++j) {
                        rule.users.add(inputRule.users.get(j).user_id);
                    }
                    result.add(rule);
                }
            }
            return result;
        }

        public static ArrayList<TLRPC.InputPrivacyRule> toInput(int currentAccount, ArrayList<TLRPC.PrivacyRule> rules) {
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final ArrayList<TLRPC.InputPrivacyRule> arr = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.PrivacyRule rule = rules.get(i);
                if (rule == null) {
                    continue;
                }
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                } else if (rule instanceof TLRPC.TL_privacyValueAllowCloseFriends) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowCloseFriends());
                } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                    arr.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                    TLRPC.TL_privacyValueDisallowUsers rule2 = (TLRPC.TL_privacyValueDisallowUsers) rule;
                    TLRPC.TL_inputPrivacyValueDisallowUsers inputRule = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        TLRPC.InputUser user = messagesController.getInputUser(rule2.users.get(j));
                        if (!(user instanceof TLRPC.TL_inputUserEmpty)) {
                            inputRule.users.add(user);
                        }
                    }
                    arr.add(inputRule);
                } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                    TLRPC.TL_privacyValueAllowUsers rule2 = (TLRPC.TL_privacyValueAllowUsers) rule;
                    TLRPC.TL_inputPrivacyValueAllowUsers inputRule = new TLRPC.TL_inputPrivacyValueAllowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        TLRPC.InputUser user = messagesController.getInputUser(rule2.users.get(j));
                        if (!(user instanceof TLRPC.TL_inputUserEmpty)) {
                            inputRule.users.add(user);
                        }
                    }
                    arr.add(inputRule);
                }
            }
            return arr;
        }

        public static ArrayList<TLRPC.PrivacyRule> toOutput(ArrayList<TLRPC.InputPrivacyRule> rules) {
            final ArrayList<TLRPC.PrivacyRule> arr = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                TLRPC.InputPrivacyRule rule = rules.get(i);
                if (rule == null) {
                    continue;
                }
                if (rule instanceof TLRPC.TL_inputPrivacyValueAllowAll) {
                    arr.add(new TLRPC.TL_privacyValueAllowAll());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowCloseFriends) {
                    arr.add(new TLRPC.TL_privacyValueAllowCloseFriends());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowContacts) {
                    arr.add(new TLRPC.TL_privacyValueAllowContacts());
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    TLRPC.TL_inputPrivacyValueDisallowUsers rule2 = (TLRPC.TL_inputPrivacyValueDisallowUsers) rule;
                    TLRPC.TL_privacyValueDisallowUsers outputRule = new TLRPC.TL_privacyValueDisallowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        outputRule.users.add(rule2.users.get(j).user_id);
                    }
                    arr.add(outputRule);
                } else if (rule instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    TLRPC.TL_inputPrivacyValueAllowUsers rule2 = (TLRPC.TL_inputPrivacyValueAllowUsers) rule;
                    TLRPC.TL_privacyValueAllowUsers outputRule = new TLRPC.TL_privacyValueAllowUsers();
                    for (int j = 0; j < rule2.users.size(); ++j) {
                        outputRule.users.add(rule2.users.get(j).user_id);
                    }
                    arr.add(outputRule);
                }
            }
            return arr;
        }

        public boolean containsUser(TLRPC.User user) {
            if (user == null) {
                return false;
            }
            if (type == TYPE_EVERYONE) {
                return !selectedUserIds.contains(user.id);
            } else if (type == TYPE_CONTACTS) {
                return !selectedUserIds.contains(user.id) && user.contact;
            } else if (type == TYPE_CLOSE_FRIENDS) {
                return user.close_friend;
            } else if (type == TYPE_SELECTED_CONTACTS) {
                if (selectedUserIds.contains(user.id)) {
                    return true;
                }
                for (ArrayList<Long> userIds : selectedUserIdsByGroup.values()) {
                    if (userIds.contains(user.id)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
