
package com.android.systemui.navigation.editor;

import android.content.Context;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.RelativeLayout;
import android.widget.PopupWindow.OnDismissListener;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;

import java.util.List;
import java.util.ArrayList;

import com.android.systemui.R;
import com.android.systemui.navigation.editor.ActionItem;
import com.android.systemui.navigation.editor.PopupWindows;
import com.android.systemui.navigation.editor.QuickAction;

/**
 * @author Pontus Holmberg (EndLessMind) Email: the_mr_hb@hotmail.com
 **/

public class QuickAction extends PopupWindows implements OnDismissListener {
    private View mRootView;
    private ImageView mArrowUp;
    private ImageView mArrowDown;
    private LayoutInflater mInflater;
    private ViewGroup mTrack;
    private ScrollView mScroller;
    private OnActionItemClickListener mItemClickListener;
    private OnDismissListener mDismissListener;

    private List<ActionItem> actionItems = new ArrayList<ActionItem>();

    private boolean mDidAction;
    public boolean isDismissed = false;

    private int mChildPos;
    private int mInsertPos;
    private int rootWidth = 0;
    private int mAnimStyle;
    private int mXpos = 0;
    private int mBtop;

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public static final int ANIM_GROW_FROM_LEFT = 1;
    public static final int ANIM_GROW_FROM_RIGHT = 2;
    public static final int ANIM_GROW_FROM_CENTER = 3;
    public static final int ANIM_REFLECT = 4;
    public static final int ANIM_AUTO = 5;
    public static final int ANIM_NONE = 6;

    /**
     * Constructor for default vertical layout
     * 
     * @param context Context
     */
    public QuickAction(Context context) {
        this(context, VERTICAL);
    }

    /**
     * Constructor allowing orientation override
     * 
     * @param context Context
     * @param orientation Layout orientation, can be vartical or horizontal
     */
    public QuickAction(Context context, int orientation) {
        super(context);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setRootViewId(R.layout.popup_vertical);
        mChildPos = 0;
        mAnimStyle = ANIM_AUTO;
    }

    /**
     * Get action item at an index
     * 
     * @param index Index of item (position from callback)
     * @return Action Item at the position
     */
    public ActionItem getActionItem(int index) {
        return actionItems.get(index);
    }

    /**
     * Set root view.
     * 
     * @param id Layout resource id
     */
    public void setRootViewId(int id) {
        // setOutsideTouchable(true);
        mRootView = (ViewGroup) mInflater.inflate(id, null);
        mTrack = (ViewGroup) mRootView.findViewById(R.id.tracks);
        mArrowDown = (ImageView) mRootView.findViewById(R.id.arrow_down);
        mArrowUp = (ImageView) mRootView.findViewById(R.id.arrow_up);
        mScroller = (ScrollView) mRootView.findViewById(R.id.scroller);

        // This was previously defined on show() method, moved here to prevent force close that
        // occured
        // when tapping fastly on a view to show quickaction dialog.
        // thanks to zammbi (github.com/zammbi)
        mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        setContentView(mRootView);
    }

    /**
     * Set listener for action item clicked.
     * 
     * @param listener Listener
     */
    public void setOnActionItemClickListener(OnActionItemClickListener listener) {
        mItemClickListener = listener;
    }

    /**
     * Add action item
     * 
     * @param action {@link ActionItem}
     */
    public void addActionItem(ActionItem action) {
        actionItems.add(action);

        String title = action.getTitle();
        Drawable icon = action.getIcon();
        View container = mInflater.inflate(R.layout.action_item_vertical, null);
        ImageView img = (ImageView) container.findViewById(R.id.iv_icon);
        TextView text = (TextView) container.findViewById(R.id.tv_title);

        if (icon != null) {
            img.setImageDrawable(icon);
        } else {
            img.setVisibility(View.GONE);
        }

        if (title != null) {
            text.setText(title);
        } else {
            text.setVisibility(View.GONE);
        }

        final int pos = mChildPos;
        final int actionId = action.getActionId();
        final boolean isActionSticky = action.isSticky();

        container.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mItemClickListener != null) {
                    mItemClickListener.onItemClick(QuickAction.this, pos, actionId);
                }

                if (!isActionSticky) {
                    mDidAction = true;
                    dismiss();
                }
            }
        });

        container.setFocusable(true);
        container.setClickable(true);

        mTrack.addView(container, mInsertPos);

        mChildPos++;
        mInsertPos++;
    }

    public void clearViews() {
        actionItems.clear();
        mTrack.removeAllViews();
        mChildPos = 0;
        mInsertPos = 0;
    }

    /**
     * Show quickaction popup. Popup is automatically positioned, on top or bottom of anchor view.
     */
    public void show(View anchor) {
        preShow();
        int xPos, yPos, arrowPos;

        mDidAction = false;

        int[] location = new int[2];

        anchor.getLocationOnScreen(location);

        Rect anchorRect = new Rect(location[0], location[1], location[0] + anchor.getWidth(),
                location[1]
                        + anchor.getHeight());

        // mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
        // LayoutParams.WRAP_CONTENT));

        mRootView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        int rootHeight = mRootView.getMeasuredHeight();

        if (rootWidth == 0) {
            rootWidth = mRootView.getMeasuredWidth();
        }

        int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        int screenHeight = mWindowManager.getDefaultDisplay().getHeight();

        // automatically get X coord of popup (top left)
        if ((anchorRect.left + rootWidth) > screenWidth) {
            xPos = anchorRect.left - (rootWidth - anchor.getWidth());
            xPos = (xPos < 0) ? 0 : xPos;

            arrowPos = anchorRect.centerX() - xPos;

        } else {
            if (anchor.getWidth() > rootWidth) {
                xPos = anchorRect.centerX() - (rootWidth / 2);
            } else {
                xPos = anchorRect.left;
            }

            arrowPos = anchorRect.centerX() - xPos;
        }

        int dyTop = anchorRect.top;
        mBtop = dyTop;
        int dyBottom = screenHeight - anchorRect.bottom;

        boolean onTop = (dyTop > dyBottom) ? true : false;

        if (onTop) {
            if (rootHeight > dyTop) {
                yPos = 15;
                LayoutParams l = mScroller.getLayoutParams();
                l.height = dyTop - anchor.getHeight();
            } else {
                yPos = anchorRect.top - rootHeight;
            }
        } else {
            yPos = anchorRect.bottom;

            if (rootHeight > dyBottom) {
                LayoutParams l = mScroller.getLayoutParams();
                l.height = dyBottom;
            }
        }

        showArrow(((onTop) ? R.id.arrow_down : R.id.arrow_up), arrowPos);

        setAnimationStyle(screenWidth, anchorRect.centerX());
        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
        mXpos = xPos;
    }

    public int getNavButtonTopY() {
        return mBtop;
    }

    public int getXpos() {
        return mXpos;
    }

    /**
     * Set animation style
     * 
     * @param screenWidth screen width
     * @param requestedX distance from left edge
     * @param onTop flag to indicate where the popup should be displayed. Set TRUE if displayed on
     *            top of anchor view and vice versa
     */
    private void setAnimationStyle(int screenWidth, int requestedX) {
        int arrowPos = requestedX - mArrowUp.getMeasuredWidth() / 2;

        switch (mAnimStyle) {
            case ANIM_GROW_FROM_LEFT:
                mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Left);
                break;

            case ANIM_GROW_FROM_RIGHT:
                mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Right);
                break;

            case ANIM_GROW_FROM_CENTER:
                mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Center);
                break;

            case ANIM_REFLECT:
                mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Reflect);
                break;

            case ANIM_AUTO:
                if (arrowPos <= screenWidth / 4) {
                    mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Left);
                } else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4)) {
                    mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Center);
                } else {
                    mWindow.setAnimationStyle(R.style.Animations_PopUpMenu_Right);
                }
                break;
        }
    }

    /**
     * Set animation style
     * 
     * @param mAnimStyle animation style, default is set to ANIM_AUTO
     */
    public void setAnimStyle(int mAnimStyle) {
        this.mAnimStyle = mAnimStyle;
    }

    /**
     * Show arrow
     * 
     * @param whichArrow arrow type resource id
     * @param requestedX distance from left screen
     */
    private void showArrow(int whichArrow, int requestedX) {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp
                : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown
                : mArrowUp;

        final int arrowWidth = mArrowDown.getMeasuredWidth();

        showArrow.setVisibility(View.VISIBLE);

        ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) showArrow
                .getLayoutParams();

        param.leftMargin = requestedX - arrowWidth / 2;

        hideArrow.setVisibility(View.INVISIBLE);
    }

    /**
     * Set listener for window dismissed. This listener will only be fired if the quicakction dialog
     * is dismissed by clicking outside the dialog or clicking on sticky item.
     */
    public void setOnDismissListener(QuickAction.OnDismissListener listener) {
        setOnDismissListener(this);

        mDismissListener = listener;
    }

    @Override
    public void onDismiss() {
        if (!mDidAction && mDismissListener != null) {
            isDismissed = true;
            Log.d("Qick", "Dismissed-inside");
            mDismissListener.onDismiss();
        }
    }

    /**
     * Listener for item click
     */
    public interface OnActionItemClickListener {
        public abstract void onItemClick(QuickAction source, int pos, int actionId);
    }

    /**
     * Listener for window dismiss
     */
    public interface OnDismissListener {
        public abstract void onDismiss();
    }
}
