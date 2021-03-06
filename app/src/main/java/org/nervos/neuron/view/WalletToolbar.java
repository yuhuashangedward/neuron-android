package org.nervos.neuron.view;

import android.content.Context;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.nervos.neuron.R;

/**
 * Created by BaojunCZ on 2018/8/1.
 */
public class WalletToolbar extends Toolbar {
    private TextView mTxtMiddleTitle;
    private ImageView mIVRight;
    private Context context;

    public WalletToolbar(Context context) {
        this(context, null);
    }

    public WalletToolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WalletToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTxtMiddleTitle = findViewById(R.id.tv_main_title);
        mIVRight = findViewById(R.id.iv_right);
    }

    public void setMainTitle(String text) {
        this.setTitle(" ");
        mTxtMiddleTitle.setVisibility(View.VISIBLE);
        mTxtMiddleTitle.setText(text);
    }

    //set title font color
    public void setMainTitleColor(int color) {
        mTxtMiddleTitle.setTextColor(color);
    }


    //set right pic
    public void setRightTitleDrawable(int res) {
        mIVRight.setImageResource(res);
    }

    //set right clicklistener
    public void setRightTitleClickListener(OnClickListener onClickListener) {
        mIVRight.setOnClickListener(onClickListener);
    }

    public void setmIVRight(int id) {
        Glide.with(context)
                .load(id)
                .into(mIVRight);
    }
}
