package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.Locale;

public class ChatGreetingsView extends LinearLayout {

    private TextView titleView;
    private TextView descriptionView;
    private Listener listener;

    private final int currentAccount;

    public FrameLayout stickerContainer;
    public BackupImageView stickerToSendView;
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean hasSticker;

    public ChatGreetingsView(Context context, TLRPC.User user, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        setPadding(0, dp(8), 0, 0);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setGravity(Gravity.CENTER);

        descriptionView = new TextView(context);
        descriptionView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);

        stickerContainer = new FrameLayout(context);
        stickerToSendView = new BackupImageView(context);
        stickerToSendView.getImageReceiver().setAspectFit(true);
        stickerContainer.addView(stickerToSendView, LayoutHelper.createFrame(112, 112));
        ScaleStateListAnimator.apply(stickerToSendView);

        updateLayout();

        updateColors();

        setText(getString(R.string.NoMessages), getString(R.string.NoMessagesGreetingsDescription));
    }

    public void setText(CharSequence title, CharSequence description) {
        titleView.setText(title);
        descriptionView.setText(description);
        descriptionView.setMaxWidth(HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint()));
        stickerToSendView.setContentDescription(descriptionView.getText());
    }

    private RLottieImageView premiumIconView;
    private TextView premiumTextView;

    private boolean premiumLock;

    public void resetPremiumLock() {
        setPremiumLock(false, null);
    }
    public void setPremiumLock(boolean lock, CharSequence text) {
        setPremiumLock(lock, false, text);
    }

    // LoogriGram: the lock explains itself and offers nothing. Upstream had a
    // button under it - "Unlock" with Premium, or a suggestion's - that the
    // getter already kept out of the layout.
    public void setPremiumLock(boolean lock, boolean isSuggestion, CharSequence text) {
        if (premiumLock == lock) return;
        premiumLock = lock;
        if (premiumLock) {
            if (premiumIconView == null) {
                premiumIconView = new RLottieImageView(getContext());
                premiumIconView.setScaleType(ImageView.ScaleType.CENTER);
                premiumIconView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                premiumIconView.setBackground(Theme.createCircleDrawable(dp(78), 0x1c000000));

                if (isSuggestion) {
                    premiumIconView.setImageResource(R.drawable.filled_chatlist2);
                } else {
                    premiumIconView.setAnimation(R.raw.large_message_lock, 80, 80);
                    premiumIconView.setOnClickListener(v -> {
                        premiumIconView.setProgress(0);
                        premiumIconView.playAnimation();
                    });
                }
            }
            premiumIconView.playAnimation();
            if (premiumTextView == null) {
                premiumTextView = new TextView(getContext());
                premiumTextView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                premiumTextView.setGravity(Gravity.CENTER);
                premiumTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            }
            premiumTextView.setText(text);
            premiumTextView.setMaxWidth(HintView2.cutInFancyHalf(premiumTextView.getText(), premiumTextView.getPaint()));
            premiumTextView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            premiumTextView.setLineSpacing(dp(2f), 1f);
        }
        updateLayout();
    }

    private void updateLayout() {
        removeAllViews();
        if (premiumLock) {
            addView(premiumIconView, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 9, 20, 9));
            addView(premiumTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 0, 20, 13));
        } else {
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 6));
            addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 6));
            // LoogriGram: the sticker here was a random one from the server's
            // greeting set (MediaDataController.getGreetingsSticker), offered to
            // send. Only a chat intro's own sticker is shown now, as on desktop.
            if (hasSticker) {
                addView(stickerContainer, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL, 16, 10, 16, 16));
            }
        }
    }

    public void setSticker(TLRPC.Document sticker) {
        if (sticker == null) {
            return;
        }
        if (!hasSticker) {
            hasSticker = true;
            updateLayout();
        }
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f);
        if (svgThumb != null) {
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), svgThumb, 0, sticker);
        } else {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), ImageLocation.getForDocument(thumb, sticker), null, 0, sticker);
        }
        stickerToSendView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGreetings(sticker);
            }
        });
    }

    public static String createFilter(TLRPC.Document document) {
        float maxHeight;
        float maxWidth;
        int photoWidth = 0;
        int photoHeight = 0;
        if (AndroidUtilities.isTablet()) {
            maxHeight = maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f;
        } else {
            maxHeight = maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                photoWidth = attribute.w;
                photoHeight = attribute.h;
                break;
            }
        }
        if (MessageObject.isAnimatedStickerDocument(document, true) && photoWidth == 0 && photoHeight == 0) {
            photoWidth = photoHeight = 512;
        }

        if (photoWidth == 0) {
            photoHeight = (int) maxHeight;
            photoWidth = photoHeight + dp(100);
        }
        photoHeight *= maxWidth / photoWidth;
        photoWidth = (int) maxWidth;
        if (photoHeight > maxHeight) {
            photoWidth *= maxHeight / photoHeight;
            photoHeight = (int) maxHeight;
        }

        int w = (int) (photoWidth / AndroidUtilities.density);
        int h = (int) (photoHeight / AndroidUtilities.density);
        return String.format(Locale.US, "%d_%d", w, h);
    }

    private void updateColors() {
        titleView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        descriptionView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onGreetings(TLRPC.Document sticker);
    }

    boolean ignoreLayot;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayot = true;
        if (!preview) {
            descriptionView.setVisibility(View.VISIBLE);
        }
        stickerToSendView.setVisibility(View.VISIBLE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() > MeasureSpec.getSize(heightMeasureSpec) && !preview) {
            descriptionView.setVisibility(View.GONE);
            stickerToSendView.setVisibility(View.GONE);
        } else {
            if (!preview) {
                descriptionView.setVisibility(View.VISIBLE);
            }
            stickerToSendView.setVisibility(View.VISIBLE);
        }
        ignoreLayot = false;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean preview;
    public void setPreview(CharSequence title, CharSequence message) {
        preview = true;
        titleView.setText(TextUtils.isEmpty(title == null ? null : title.toString().trim()) ? getString(R.string.NoMessages) : title);
        descriptionView.setText(TextUtils.isEmpty(message == null ? null : message.toString().trim()) ? getString(R.string.NoMessagesGreetingsDescription) : message);
        descriptionView.setMaxWidth(
            descriptionView.getText().length() > 60 ?
                Math.min((int) (AndroidUtilities.displaySize.x * .5f), HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint())) :
                (int) (AndroidUtilities.displaySize.x * .5f)
        );
    }

    private float viewTop;
    private float viewTranslationX;
    private int backgroundHeight;
    private boolean visiblePartSet;

    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
        viewTranslationX = 0;
    }

    private boolean disableBackground;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!disableBackground) {
            if (resourcesProvider != null) {
                resourcesProvider.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            } else {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, viewTranslationX, viewTop + dp(4));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(16), dp(16), Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider));
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayot) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
        disableBackground = true;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public static void showPremiumSheet(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), 0, dp(16), 0);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setAnimation(R.raw.large_message_lock, 80, 80);
        imageView.playAnimation();
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        TextView headerView = new TextView(context);
        headerView.setTypeface(AndroidUtilities.bold());
        headerView.setGravity(Gravity.CENTER);
        headerView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerView.setText(LocaleController.getString(R.string.PremiumMessageHeaderLocked));
        layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 0));

        TextView descriptionView = new TextView(context);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        String username = "";
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            username = UserObject.getFirstName(user);
        }
        descriptionView.setText(AndroidUtilities.replaceTags(formatString(R.string.PremiumMessageTextLocked, username, username)));
        layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 9, 12, 19));

        sheet.setCustomView(layout);
        sheet.show();
    }
}
